package com.cube.memorygames.api.online;

import org.junit.Test;

import static org.junit.Assert.*;

public class MatchExitProtocolTest {
    @Test public void resignRetriesCarryTheirOriginalMatchId() {
        OnlineMessages.Resign request = new OnlineMessages.Resign();
        request.matchId = "practice-old";
        String json = OnlineMessages.envelope(OnlineMessages.T_RESIGN, request);
        assertEquals("resign", OnlineMessages.typeOf(json));
        assertEquals("practice-old", OnlineMessages.payloadOf(json, OnlineMessages.Resign.class).matchId);
    }

    @Test public void ackOnlyConfirmsTheMatchBeingExited() {
        OnlineMessages.ResignAck ack = OnlineMessages.payloadOf(
                "{\"t\":\"resign_ack\",\"d\":{\"matchId\":\"practice-old\"}}",
                OnlineMessages.ResignAck.class);
        assertTrue(ack.confirms("practice-old", "practice-old"));
        assertFalse(ack.confirms("friend-new", "practice-old"));
        assertFalse(ack.confirms("practice-old", null));
        assertFalse(ack.confirms(null, "practice-old"));
        assertFalse(OnlineMessages.payloadOf("{\"d\":{}}", OnlineMessages.ResignAck.class)
                .confirms("practice-old", "practice-old"));
    }

    @Test public void oldExitFramesCannotClearANewFriendMatch() {
        for (String type : new String[] {OnlineMessages.T_RESIGN_ACK,
                OnlineMessages.T_MATCH_CANCELLED, OnlineMessages.T_MATCH_END}) {
            String json = "{\"t\":\"" + type + "\",\"d\":{\"matchId\":\"practice-old\"}}";
            assertTrue(OnlineMessages.belongsToMatch(json, "practice-old"));
            assertFalse(OnlineMessages.belongsToMatch(json, "friend-new"));
            assertFalse(OnlineMessages.belongsToMatch(json, null));
        }
    }

    @Test public void legacyQueueCancellationRemainsSupported() {
        assertTrue(OnlineMessages.belongsToMatch("{\"t\":\"match_cancelled\",\"d\":{}}", null));
        assertTrue(OnlineMessages.belongsToMatch("{\"t\":\"match_cancelled\",\"d\":{}}", "legacy"));
    }
}
