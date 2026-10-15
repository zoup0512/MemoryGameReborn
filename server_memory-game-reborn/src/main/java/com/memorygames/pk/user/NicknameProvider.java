package com.memorygames.pk.user;

/** External decisions only. Test implementations belong exclusively in src/test. */
public interface NicknameProvider {
    enum Decision { PASS, REJECT, PENDING, UNAVAILABLE }
    record Result(Decision decision, String requestId) {}
    Result review(String nickname, String dataId);
}
