package com.brukb.zerotier.ztlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeReadyTest {
    @Test
    fun isReadyToJoin_requiresNodeUpEventNotJustValidId() {
        val kindleId = 0x2721c17d93L
        assertFalse(
            "Doze resume: get_id valid before NODE_UP must not skip restart",
            ZeroTierNodeManager.isReadyToJoin(
                ZtNodeState(receivedNodeUp = false, nodeId = kindleId),
            ),
        )
        assertTrue(
            ZeroTierNodeManager.isReadyToJoin(
                ZtNodeState(receivedNodeUp = true, nodeId = kindleId),
            ),
        )
    }

    @Test
    fun isReadyToJoin_rejectsLibztErrorAsId() {
        assertFalse(
            ZeroTierNodeManager.isReadyToJoin(
                ZtNodeState(receivedNodeUp = true, nodeId = -2L),
            ),
        )
        assertFalse(
            ZeroTierNodeManager.isReadyToJoin(
                ZtNodeState(receivedNodeUp = false, nodeId = -2L),
            ),
        )
        assertFalse(ZeroTierNodeManager.isReadyToJoin(ZtNodeState()))
    }

    @Test
    fun mapNetworkStatusCode_table() {
        val cases = listOf(
            0 to ZtNetworkStatus.Status.REQUESTING_CONFIG,
            1 to ZtNetworkStatus.Status.OK,
            2 to ZtNetworkStatus.Status.ACCESS_DENIED,
            3 to ZtNetworkStatus.Status.NOT_FOUND,
            4 to ZtNetworkStatus.Status.PORT_ERROR,
            5 to ZtNetworkStatus.Status.CLIENT_TOO_OLD,
            -2 to ZtNetworkStatus.Status.UNKNOWN,
            -4 to ZtNetworkStatus.Status.UNKNOWN,
            99 to ZtNetworkStatus.Status.UNKNOWN,
        )
        for ((code, expected) in cases) {
            assertEquals("native=$code", expected, ZeroTierNodeManager.mapNetworkStatusCode(code))
        }
    }
}
