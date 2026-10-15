package com.cube.memorygames.api.online;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AccountEntryTest {
    @Test public void guestGetsEmailBindingShortcut() {
        assertEquals(AccountEntry.BIND_EMAIL,AccountEntry.forState(false,"GUEST",true));
        assertEquals(AccountEntry.BIND_EMAIL,AccountEntry.forState(false,"guest",true));
    }

    @Test public void registeredAccountHasNoManagementShortcut() {
        assertEquals(AccountEntry.NONE,AccountEntry.forState(false,"EMAIL",true));
        assertEquals(AccountEntry.NONE,AccountEntry.forState(false,"REGISTERED",true));
    }

    @Test public void expiredSessionTakesPriorityOverGuestBinding() {
        assertEquals(AccountEntry.LOGIN,AccountEntry.forState(true,"GUEST",true));
        assertEquals(AccountEntry.LOGIN,AccountEntry.forState(true,"EMAIL",true));
    }

    @Test public void expiredSessionWithoutProfileStillGetsLogin() {
        assertEquals(AccountEntry.LOGIN,AccountEntry.forState(true,null,false));
    }

    @Test public void offlineOrUnknownIdentityDoesNotSuggestBinding() {
        assertEquals(AccountEntry.NONE,AccountEntry.forState(false,null,false));
        assertEquals(AccountEntry.NONE,AccountEntry.forState(false,"GUEST",false));
        assertEquals(AccountEntry.NONE,AccountEntry.forState(false,null,true));
    }
}
