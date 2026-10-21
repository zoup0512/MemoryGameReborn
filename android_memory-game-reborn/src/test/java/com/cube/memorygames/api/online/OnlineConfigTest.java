package com.cube.memorygames.api.online;

import org.junit.Test;
import static org.junit.Assert.*;

public class OnlineConfigTest {
    @Test public void temporaryCompatibilityAllowsPublicAndLocalWs() {
        assertTrue(OnlineConfig.accountTransportAllowed("ws://124.220.35.87:8091/ws",true));
        assertTrue(OnlineConfig.accountTransportAllowed("ws://127.0.0.1:18091/ws",true));
        assertTrue(OnlineConfig.accountTransportAllowed("wss://example.com/ws",true));
    }
    @Test public void disablingCompatibilityRequiresTls() {
        assertFalse(OnlineConfig.accountTransportAllowed("ws://124.220.35.87:8091/ws",false));
        assertFalse(OnlineConfig.accountTransportAllowed("ws://127.0.0.1:18091/ws",false));
        assertTrue(OnlineConfig.accountTransportAllowed("wss://example.com/ws",false));
    }
    @Test public void compatibilityStillRejectsInvalidEndpoints() {
        String[] invalid={"http://example.com/ws","https://example.com/ws","ws://example.com/other",
                "ws://user:secret@example.com/ws","ws://example.com/ws?token=x","ws://example.com/ws#x"};
        for(String value:invalid) {
            try {
                OnlineConfig.accountTransportAllowed(value,true);
                fail("Accepted invalid endpoint: "+value);
            } catch(IllegalArgumentException expected) { }
        }
    }
}
