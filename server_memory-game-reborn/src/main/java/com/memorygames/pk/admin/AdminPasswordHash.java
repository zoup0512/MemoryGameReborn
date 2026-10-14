package com.memorygames.pk.admin;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.Console;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Local terminal-only credential generator; never run during server startup. */
public final class AdminPasswordHash {
    private AdminPasswordHash() {}

    public static void main(String[] args) {
        Console console = System.console();
        if (args.length != 0 || console == null) {
            System.err.println("Run from an interactive terminal with no arguments; passwords cannot be piped or passed as arguments.");
            System.exit(1);
            return;
        }
        char[] password = null;
        char[] confirmation = null;
        int status = 0;
        try {
            password = console.readPassword("New admin password (at least 12 characters): ");
            confirmation = console.readPassword("Confirm admin password: ");
            if (password == null || confirmation == null || !Arrays.equals(password, confirmation)) {
                System.err.println("Passwords do not match.");
                status = 1;
            } else {
                System.out.println(hashPassword(password));
            }
        } catch (IllegalArgumentException exception) {
            System.err.println("Use at least 12 characters and at most 72 UTF-8 bytes.");
            status = 1;
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
        if (status != 0) System.exit(status);
    }

    public static String hashPassword(char[] password) {
        if (password == null || password.length < 12 || password.length > 72) throw new IllegalArgumentException("invalid length");
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        try {
            if (encoded.remaining() > 72) throw new IllegalArgumentException("invalid length");
            // BCrypt requires a CharSequence; the temporary String is not persisted or logged.
            return new BCryptPasswordEncoder(12).encode(CharBuffer.wrap(password));
        } finally {
            if (encoded.hasArray()) Arrays.fill(encoded.array(), (byte) 0);
        }
    }
}
