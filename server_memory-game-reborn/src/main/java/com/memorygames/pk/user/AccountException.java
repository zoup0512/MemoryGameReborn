package com.memorygames.pk.user;

public class AccountException extends RuntimeException {
    public final int status;
    public final String code;
    public AccountException(int status, String code, String message) { super(message); this.status=status; this.code=code; }
}
