package com.brukb.zerotier.connection

import com.brukb.zerotier.data.model.GlobalMode
import com.brukb.zerotier.data.model.LinkMode
import com.brukb.zerotier.data.model.MainNetworkSelector
import com.brukb.zerotier.data.model.ZerotierBNetwork

object RuntimePlanResolver {
    fun resolve(
        globalMode: GlobalMode,
        link: PhysicalLink,
        vpnConsentGranted: Boolean,
        enabled: List<ZerotierBNetwork>,
    ): RuntimePlan {
        return when (globalMode) {
            GlobalMode.OFF -> offPlan("global OFF")
            GlobalMode.PROXY -> proxyPlan("global PROXY", enabled)
            GlobalMode.VPN -> vpnPlan("global VPN", vpnConsentGranted, enabled)
            GlobalMode.AUTO -> resolveAuto(link, vpnConsentGranted, enabled)
        }
    }

    private fun resolveAuto(
        link: PhysicalLink,
        vpnConsentGranted: Boolean,
        enabled: List<ZerotierBNetwork>,
    ): RuntimePlan {
        return when (link) {
            is PhysicalLink.None -> offPlan("AUTO no link")
            is PhysicalLink.WifiUnknown -> proxyPlan("AUTO unknown wifi", enabled)
            is PhysicalLink.WifiKnown -> planFor(link.mode, "AUTO ssid=${link.ssid}", vpnConsentGranted, enabled)
            is PhysicalLink.Mobile -> planFor(
                link.mode,
                "AUTO mobile sub=${link.subscriptionId}",
                vpnConsentGranted,
                enabled,
            )
            is PhysicalLink.Other -> planFor(link.mode, "AUTO other", vpnConsentGranted, enabled)
        }
    }

    private fun planFor(
        mode: LinkMode,
        reason: String,
        vpnConsentGranted: Boolean,
        enabled: List<ZerotierBNetwork>,
    ): RuntimePlan {
        return when (mode) {
            LinkMode.OFF -> offPlan(reason)
            LinkMode.PROXY -> proxyPlan(reason, enabled)
            LinkMode.VPN -> vpnPlan(reason, vpnConsentGranted, enabled)
        }
    }

    private fun offPlan(reason: String): RuntimePlan =
        RuntimePlan(
            runtime = Runtime.OFF,
            reason = reason,
            vpnNetworkId = null,
            joinNetworkIds = emptyList(),
            vpnConsentMissing = false,
        )

    private fun proxyPlan(reason: String, enabled: List<ZerotierBNetwork>): RuntimePlan =
        RuntimePlan(
            runtime = Runtime.PROXY,
            reason = reason,
            vpnNetworkId = null,
            joinNetworkIds = enabled.map { it.networkId },
            vpnConsentMissing = false,
        )

    private fun vpnPlan(
        reason: String,
        vpnConsentGranted: Boolean,
        enabled: List<ZerotierBNetwork>,
    ): RuntimePlan {
        val main = MainNetworkSelector.select(enabled)
        if (main == null) {
            return RuntimePlan(
                runtime = Runtime.OFF,
                reason = "$reason (no enabled networks)",
                vpnNetworkId = null,
                joinNetworkIds = emptyList(),
                vpnConsentMissing = !vpnConsentGranted,
            )
        }
        if (!vpnConsentGranted) {
            return RuntimePlan(
                runtime = Runtime.PROXY,
                reason = "$reason (consent missing)",
                vpnNetworkId = null,
                joinNetworkIds = enabled.map { it.networkId },
                vpnConsentMissing = true,
            )
        }
        return RuntimePlan(
            runtime = Runtime.VPN,
            reason = reason,
            vpnNetworkId = main.networkId,
            joinNetworkIds = listOf(main.networkId),
            vpnConsentMissing = false,
        )
    }
}
