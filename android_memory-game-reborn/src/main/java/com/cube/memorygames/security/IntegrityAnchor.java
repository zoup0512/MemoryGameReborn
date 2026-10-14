package com.cube.memorygames.security;

/**
 * Release certificate anchor. GENERATED FILE - rewritten in place by
 * `gradlew writeIntegrityAnchor` (see build.gradle); do not hand-edit
 * the arrays.
 *
 * The 32-byte SHA-256 of the release signing certificate never appears as a
 * literal in the dex: it is stored XOR-split across two random int arrays
 * and only reassembled inside fingerprint(). With ANCHORED false
 * the sentinel enforces nothing - that is the state until the maintainer
 * stamps the real fingerprint (hardening design 5.1).
 */
final class IntegrityAnchor {

    static final boolean ANCHORED = false;
    static final int[] PART_A = new int[0];
    static final int[] PART_B = new int[0];

    static byte[] fingerprint() {
        if (!ANCHORED || PART_A.length == 0 || PART_A.length != PART_B.length) {
            return null;
        }
        byte[] out = new byte[PART_A.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) ((PART_A[i] ^ PART_B[i]) & 0xFF);
        }
        return out;
    }

    private IntegrityAnchor() {
    }
}
