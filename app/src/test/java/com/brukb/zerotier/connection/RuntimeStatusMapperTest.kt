package com.brukb.zerotier.connection

import com.brukb.zerotier.proxy.ProxyServiceState
import com.brukb.zerotier.vpn.VpnServiceState
import com.brukb.zerotier.ztlib.ZtNetworkStatus
import com.brukb.zerotier.ztlib.ZtNodeState
import com.zerotier.sdk.VirtualNetworkStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeStatusMapperTest {
    @Test
    fun ztStatusToJoinStatus_allValues() {
        assertEquals(JoinStatus.JOINING, ztStatusToJoinStatus(ZtNetworkStatus.Status.JOINING))
        assertEquals(
            JoinStatus.REQUESTING_CONFIG,
            ztStatusToJoinStatus(ZtNetworkStatus.Status.REQUESTING_CONFIG),
        )
        assertEquals(JoinStatus.OK, ztStatusToJoinStatus(ZtNetworkStatus.Status.OK))
        assertEquals(JoinStatus.ACCESS_DENIED, ztStatusToJoinStatus(ZtNetworkStatus.Status.ACCESS_DENIED))
        assertEquals(JoinStatus.NOT_FOUND, ztStatusToJoinStatus(ZtNetworkStatus.Status.NOT_FOUND))
        assertEquals(JoinStatus.DOWN, ztStatusToJoinStatus(ZtNetworkStatus.Status.DOWN))
        assertEquals(JoinStatus.ERROR, ztStatusToJoinStatus(ZtNetworkStatus.Status.PORT_ERROR))
        assertEquals(JoinStatus.ERROR, ztStatusToJoinStatus(ZtNetworkStatus.Status.CLIENT_TOO_OLD))
        assertEquals(JoinStatus.UNKNOWN, ztStatusToJoinStatus(ZtNetworkStatus.Status.UNKNOWN))
    }

    @Test
    fun vpnVirtualStatusToJoinStatus_requestingAndOk() {
        assertEquals(
            JoinStatus.REQUESTING_CONFIG,
            vpnVirtualStatusToJoinStatus(VirtualNetworkStatus.NETWORK_STATUS_REQUESTING_CONFIGURATION),
        )
        assertEquals(
            JoinStatus.OK,
            vpnVirtualStatusToJoinStatus(VirtualNetworkStatus.NETWORK_STATUS_OK),
        )
        assertEquals(
            JoinStatus.ERROR,
            vpnVirtualStatusToJoinStatus(VirtualNetworkStatus.NETWORK_STATUS_PORT_ERROR),
        )
    }

    @Test
    fun resolveNetworkRuntime_proxyWinsOverVpnWhenRuntimeProxy() {
        val proxy = ProxyServiceState(
            networkStatuses = listOf(
                NetworkRuntimeStatus(networkId = "8056c2e21c000001", joinStatus = JoinStatus.OK),
            ),
        )
        val vpn = VpnServiceState(
            networkStatuses = listOf(
                NetworkRuntimeStatus(networkId = "8056c2e21c000001", joinStatus = JoinStatus.JOINING),
            ),
        )
        val result = resolveNetworkRuntime(Runtime.PROXY, proxy, vpn, "8056c2e21c000001")
        assertEquals(JoinStatus.OK, result?.joinStatus)
    }

    @Test
    fun resolveNetworkRuntime_returnsNullWhenOff() {
        val proxy = ProxyServiceState(
            networkStatuses = listOf(
                NetworkRuntimeStatus(networkId = "8056c2e21c000001", joinStatus = JoinStatus.OK),
            ),
        )
        assertNull(resolveNetworkRuntime(Runtime.OFF, proxy, VpnServiceState(), "8056c2e21c000001"))
        assertNull(resolveNetworkRuntime(null, proxy, VpnServiceState(), "8056c2e21c000001"))
    }

    @Test
    fun resolveNetworkRuntime_normalizesNetworkId() {
        val proxy = ProxyServiceState(
            networkStatuses = listOf(
                NetworkRuntimeStatus(networkId = "8056c2e21c000001", joinStatus = JoinStatus.OK),
            ),
        )
        val result = resolveNetworkRuntime(Runtime.PROXY, proxy, VpnServiceState(), "8056C2E21C000001")
        assertEquals(JoinStatus.OK, result?.joinStatus)
    }

    @Test
    fun resolveNodeLifecycle_pausedDozeFromProxy() {
        val proxy = ProxyServiceState(nodeLifecycle = NodeLifecycleStatus.PAUSED_DOZE)
        assertEquals(
            NodeLifecycleStatus.PAUSED_DOZE,
            resolveNodeLifecycle(Runtime.PROXY, proxy, VpnServiceState()),
        )
        assertEquals(
            NodeLifecycleStatus.STOPPED,
            resolveNodeLifecycle(Runtime.OFF, proxy, VpnServiceState()),
        )
    }

    @Test
    fun ztNodeStateToLifecycle_table() {
        assertEquals(
            NodeLifecycleStatus.PAUSED_DOZE,
            ztNodeStateToLifecycle(ZtNodeState(isOnline = true), pausedDoze = true),
        )
        assertEquals(
            NodeLifecycleStatus.ERROR,
            ztNodeStateToLifecycle(ZtNodeState(lastError = "Fatal node error"), pausedDoze = false),
        )
        assertEquals(
            NodeLifecycleStatus.ONLINE,
            ztNodeStateToLifecycle(ZtNodeState(isOnline = true, nodeId = 1L), pausedDoze = false),
        )
        assertEquals(
            NodeLifecycleStatus.STARTING,
            ztNodeStateToLifecycle(ZtNodeState(isOnline = false, nodeId = 1L), pausedDoze = false),
        )
    }
}
