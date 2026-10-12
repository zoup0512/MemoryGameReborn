package com.activeandroid.util;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class SqlParser {
    public static final int STATE_COMMENT = 2;
    public static final int STATE_COMMENT_BLOCK = 3;
    public static final int STATE_NONE = 0;
    public static final int STATE_STRING = 1;

    public static List<String> parse(InputStream stream) throws IOException {
        InputStream buffer = new BufferedInputStream(stream);
        List<String> commands = new ArrayList();
        StringBuffer sb = new StringBuffer();
        try {
            Tokenizer tokenizer = new Tokenizer(buffer);
            int state = 0;
            while (tokenizer.hasNext()) {
                char c = (char) tokenizer.next();
                if (state == 3) {
                    if (tokenizer.skip("*/")) {
                        state = 0;
                    }
                } else if (state == 2) {
                    if (isNewLine(c)) {
                        state = 0;
                    }
                } else if (state == 0 && tokenizer.skip("/*")) {
                    state = 3;
                } else if (state == 0 && tokenizer.skip("--")) {
                    state = 2;
                } else if (state == 0 && c == ';') {
                    commands.add(sb.toString().trim());
                    sb.setLength(0);
                } else {
                    if (state == 0 && c == '\'') {
                        state = 1;
                    } else if (state == 1 && c == '\'') {
                        state = 0;
                    }
                    if (state == 0 || state == 1) {
                        if (state == 0) {
                            if (isWhitespace(c)) {
                                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                                    sb.append(' ');
                                }
                            }
                        }
                        sb.append(c);
                    }
                }
            }
            if (sb.length() > 0) {
                commands.add(sb.toString().trim());
            }
            return commands;
        } finally {
            IOUtils.closeQuietly(buffer);
        }
    }

    private static boolean isNewLine(char c) {
        return c == '\r' || c == '\n';
    }

    private static boolean isWhitespace(char c) {
        return c == '\r' || c == '\n' || c == '\t' || c == ' ';
    }
}
