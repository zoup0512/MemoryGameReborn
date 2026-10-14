package com.memorygames.pk.user;

/** Security denials whose defensive writes (attempt counter/replay revocation) must commit. */
final class CommittedAccountException extends AccountException {
    CommittedAccountException(int status,String code) { super(status,code,code.replace('_',' ')); }
}
