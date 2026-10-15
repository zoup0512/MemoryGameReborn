package com.cube.memorygames.api.online;

import com.google.gson.Gson;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProfileRulesTest {
    @Test public void nicknameUsesUnicodeCodePointsNotUtf16Units() {
        assertTrue(ProfileRules.validNickname("清风"));
        String supplementary=new String(Character.toChars(0x20000));
        assertTrue(ProfileRules.validNickname(repeat(supplementary,16)));
        assertFalse(ProfileRules.validNickname(repeat(supplementary,17)));
        assertFalse(ProfileRules.validNickname("鹿"));
        assertFalse(ProfileRules.validNickname(null));
        assertFalse(ProfileRules.validNickname(""));
    }
    @Test public void refusesControlsInvisibleAndMalformedUnicode() {
        String[] bad={"清\n风","清\t风","清\u200b风","清\u202e风","清\ufeff风","清\u00a0风",
                "清\u3000风","清\ud800风","清\ue000风","清\u034f风","清\u3164风","清\ufe0f风","清\u2800风"};
        for(String name:bad) assertFalse(name,ProfileRules.validNickname(name));
    }
    @Test public void normalizationIsCanonicalAndDoesNotSilentlyTrimControls() {
        assertEquals("é猫",ProfileRules.normalize("e\u0301猫"));
        assertEquals("清 风",ProfileRules.normalize(" 清 风 "));
        assertTrue(ProfileRules.validNickname("清 风"));
        assertFalse(ProfileRules.validNickname("    "));
        assertFalse(ProfileRules.validNickname(ProfileRules.normalize("小猫\n")));
    }
    @Test public void unchangedHistoricalNicknameNeverGetsResubmittedOrRevalidated() {
        for(String old:new String[]{" Player1 ","旧\u200b名","e\u0301猫","X"}) {
            assertNull(ProfileRules.nicknameChange(old,old));
        }
        assertNull(ProfileRules.nicknameChange("清风小鹿"," 清风小鹿 "));
        assertEquals("é猫",ProfileRules.nicknameChange("清风小鹿","e\u0301猫"));
    }
    @Test public void generatedDefaultsAreAlwaysControlledAndValid() {
        Random random=new Random(41);
        java.util.Set<String> names=new java.util.HashSet<>();
        for(int i=0;i<1000;i++) {
            String name=ProfileRules.inspiration(random);
            assertTrue(ProfileRules.validNickname(name)); assertFalse(name.startsWith("Player")); names.add(name);
        }
        assertTrue(names.size()>100);
    }
    @Test public void avatarMappingIsBoundedAndExplicit() {
        for(int i=1;i<=12;i++) { assertTrue(ProfileRules.validAvatar("avatar_"+i)); assertEquals(i-1,ProfileRules.avatarIndex("avatar_"+i)); }
        for(String value:new String[]{"avatar_0","avatar_13","avatar_01","https://example.org/p.png","../avatar_2",""}) {
            assertFalse(ProfileRules.validAvatar(value)); assertEquals(0,ProfileRules.avatarIndex(value));
        }
        assertEquals(0,ProfileRules.avatarIndex(null));
    }
    @Test public void cooldownUsesServerTimeWithoutInventingAnInterval() {
        assertFalse(ProfileRules.coolingDown(0,100)); assertFalse(ProfileRules.coolingDown(100,100));
        assertTrue(ProfileRules.coolingDown(101,100)); assertFalse(ProfileRules.coolingDown(99,100));
    }
    @Test public void userIdMismatchCannotBeAccepted() {
        assertTrue(ProfileRules.matchesUser(42,42)); assertFalse(ProfileRules.matchesUser(42,43));
        assertFalse(ProfileRules.matchesUser(0,0));
    }
    @Test public void exactModerationContractsMapToFriendlyCategories() {
        assertEquals("rejected",ProfileRules.moderationError(422,"NICKNAME_REJECTED"));
        assertEquals("pending",ProfileRules.moderationError(422,"NICKNAME_MODERATION_PENDING"));
        assertEquals("pending",ProfileRules.moderationError(422,"NICKNAME_PENDING"));
        assertEquals("unavailable",ProfileRules.moderationError(503,"NICKNAME_MODERATION_UNAVAILABLE"));
        assertEquals("cooldown",ProfileRules.moderationError(429,"NICKNAME_COOLDOWN"));
        assertNull(ProfileRules.moderationError(500,"SERVER_ERROR"));
    }
    @Test public void profileRoundTripRetainsServerCooldownAndOldCacheDefaultsToZero() {
        Gson gson=new Gson();
        AccountManager.UserProfile old=gson.fromJson("{\"userId\":7,\"nickname\":\"旧名字\",\"avatarValue\":\"avatar_2\"}",AccountManager.UserProfile.class);
        assertEquals(0,old.nextNicknameChangeAt); assertEquals("旧名字",old.nickname);
        old.nextNicknameChangeAt=1792100000000L;
        AccountManager.UserProfile restored=gson.fromJson(gson.toJson(old),AccountManager.UserProfile.class);
        assertEquals(old.nextNicknameChangeAt,restored.nextNicknameChangeAt); assertEquals(7,restored.userId);
    }
    private static String repeat(String value,int count) { StringBuilder out=new StringBuilder(); for(int i=0;i<count;i++)out.append(value); return out.toString(); }
}
