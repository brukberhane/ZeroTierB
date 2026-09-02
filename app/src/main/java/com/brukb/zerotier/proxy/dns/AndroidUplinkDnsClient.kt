package com.brukb.zerotier.proxy.dns

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import com.brukb.zerotier.proxy.ProxyDebugLog
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

class AndroidUplinkDnsClient(context: Context) : UplinkDnsClient {
    private val connectivity =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val lastUplinkLogSig = AtomicReference<String?>(null)

    override fun lookupNetd(host: String, timeoutMs: Int): DnsLookupResult {
        if (timeoutMs <= 0) return DnsLookupResult.Failure("timeoutMs<=0")
        val network = pickUplink() ?: return DnsLookupResult.Failure("no uplink")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            lookupNetdApi29(network, host, timeoutMs)
        } else {
            lookupNetdLegacy(network, host, timeoutMs)
        }
    }

    override fun lookupUdp(server: InetAddress, host: String, timeoutMs: Int): DnsLookupResult {
        if (timeoutMs <= 0) return DnsLookupResult.Failure("timeoutMs<=0")
        val network = pickUplink() ?: return DnsLookupResult.Failure("no uplink")
        val perType = (timeoutMs / 2).coerceAtLeast(400)
        return combineAThenAaaa(udpOnce(network, server, host, DnsMessage.TYPE_A, perType)) {
            udpOnce(network, server, host, DnsMessage.TYPE_AAAA, perType)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun lookupNetdApi29(network: Network, host: String, timeoutMs: Int): DnsLookupResult {
        val latch = CountDownLatch(1)
        val box = AtomicReference<DnsLookupResult>(DnsLookupResult.Failure("unset"))
        val cancel = CancellationSignal()
        android.net.DnsResolver.getInstance().query(
            network,
            host,
            android.net.DnsResolver.FLAG_EMPTY,
            QUERY_EXECUTOR,
            cancel,
            object : android.net.DnsResolver.Callback<List<InetAddress>> {
                override fun onAnswer(answer: List<InetAddress>, rcode: Int) {
                    box.set(
                        when {
                            rcode == DnsMessage.RCODE_NXDOMAIN -> DnsLookupResult.NxDomain
                            answer.isNotEmpty() -> DnsLookupResult.Ok(answer)
                            rcode == 0 -> DnsLookupResult.NoData
                            else -> DnsLookupResult.Failure("rcode=$rcode empty")
                        },
                    )
                    latch.countDown()
                }

                override fun onError(error: android.net.DnsResolver.DnsException) {
                    box.set(DnsLookupResult.Failure(error.message ?: "dns"))
                    latch.countDown()
                }
            },
        )
        if (!latch.await(timeoutMs.toLong(), TimeUnit.MILLISECONDS)) {
            cancel.cancel()
            return DnsLookupResult.Failure("timeout")
        }
        return box.get()
    }

    @Suppress("DEPRECATION")
    private fun lookupNetdLegacy(network: Network, host: String, timeoutMs: Int): DnsLookupResult {
        val task = FutureTask { network.getAllByName(host).toList() }
        QUERY_EXECUTOR.execute(task)
        return try {
            val answer = task.get(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            if (answer.isEmpty()) {
                DnsLookupResult.NoData
            } else {
                DnsLookupResult.Ok(answer)
            }
        } catch (_: TimeoutException) {
            task.cancel(true)
            DnsLookupResult.Failure("timeout")
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is UnknownHostException -> DnsLookupResult.Failure("unknown")
                else -> DnsLookupResult.Failure(cause?.message ?: "dns")
            }
        } catch (e: Exception) {
            DnsLookupResult.Failure(e.message ?: "dns")
        }
    }

    private fun udpOnce(
        network: Network,
        server: InetAddress,
        host: String,
        type: Int,
        timeoutMs: Int,
    ): DnsLookupResult {
        return try {
            DatagramSocket().use { socket ->
                try {
                    network.bindSocket(socket)
                } catch (e: Exception) {
                    ProxyDebugLog.w("dns-udp bind FAIL server=$server host=$host err=${e.message}")
                    return DnsLookupResult.Failure("bindSocket: ${e.message}")
                }
                socket.soTimeout = timeoutMs
                val query = DnsMessage.buildQuery(host, type)
                socket.send(DatagramPacket(query, query.size, server, 53))
                val buf = ByteArray(512)
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                DnsMessage.toLookupResult(buf, packet.length)
            }
        } catch (e: SocketTimeoutException) {
            ProxyDebugLog.w("dns-udp FAIL server=$server host=$host type=$type err=Poll timed out")
            DnsLookupResult.Failure("Poll timed out")
        } catch (e: Exception) {
            ProxyDebugLog.w("dns-udp FAIL server=$server host=$host type=$type err=${e.message}")
            DnsLookupResult.Failure(e.message ?: "udp")
        }
    }

    private fun pickUplink(): Network? {
        val active = connectivity.activeNetwork
        val skippedActiveVpn = active != null && isVpn(active)
        val chosen = if (active != null && !skippedActiveVpn) {
            active
        } else {
            val networks = connectivity.allNetworks.filter { !isVpn(it) && hasInternet(it) }
            networks.firstOrNull { hasTransport(it, NetworkCapabilities.TRANSPORT_WIFI) }
                ?: networks.firstOrNull { hasTransport(it, NetworkCapabilities.TRANSPORT_CELLULAR) }
                ?: networks.firstOrNull()
        }
        if (chosen != null) {
            logUplinkIfChanged(chosen, skippedActiveVpn)
        }
        return chosen
    }

    private fun logUplinkIfChanged(network: Network, skippedActiveVpn: Boolean) {
        val handle = network.networkHandle
        val onCell = hasTransport(network, NetworkCapabilities.TRANSPORT_CELLULAR)
        val wifiUpWhileCell = onCell && connectivity.allNetworks.any { other ->
            other != network && !isVpn(other) && hasInternet(other) &&
                hasTransport(other, NetworkCapabilities.TRANSPORT_WIFI)
        }
        val wifiCount = connectivity.allNetworks.count {
            !isVpn(it) && hasInternet(it) && hasTransport(it, NetworkCapabilities.TRANSPORT_WIFI)
        }
        val cellCount = connectivity.allNetworks.count {
            !isVpn(it) && hasInternet(it) && hasTransport(it, NetworkCapabilities.TRANSPORT_CELLULAR)
        }
        val sig = "$handle:$skippedActiveVpn:$wifiUpWhileCell"
        if (lastUplinkLogSig.getAndSet(sig) != sig) {
            ProxyDebugLog.i(
                "dns-uplink net=$handle wifi=$wifiCount cell=$cellCount " +
                    "skippedActiveVpn=$skippedActiveVpn wifiUpWhileCell=$wifiUpWhileCell",
            )
        }
    }

    private fun isVpn(network: Network): Boolean =
        hasTransport(network, NetworkCapabilities.TRANSPORT_VPN)

    private fun hasInternet(network: Network): Boolean {
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun hasTransport(network: Network, transport: Int): Boolean {
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(transport)
    }

    private companion object {
        val QUERY_EXECUTOR = ThreadPoolExecutor(
            0,
            8,
            60L,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            { r -> Thread(r, "zt-dns").apply { isDaemon = true } },
            ThreadPoolExecutor.CallerRunsPolicy(),
        )
    }
}
