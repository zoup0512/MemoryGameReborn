package com.activeandroid.util;

import java.io.IOException;
import java.io.InputStream;

public class Tokenizer {
    private int mCurrent;
    private boolean mIsNext;
    private final InputStream mStream;

    public Tokenizer(InputStream in) {
        this.mStream = in;
    }

    public boolean hasNext() throws IOException {
        if (!this.mIsNext) {
            this.mIsNext = true;
            this.mCurrent = this.mStream.read();
        }
        if (this.mCurrent != -1) {
            return true;
        }
        return false;
    }

    public int next() throws IOException {
        if (!this.mIsNext) {
            this.mCurrent = this.mStream.read();
        }
        this.mIsNext = false;
        return this.mCurrent;
    }

    public boolean skip(String s) throws IOException {
        if (s == null || s.length() == 0 || s.charAt(0) != this.mCurrent) {
            return false;
        }
        int len = s.length();
        this.mStream.mark(len - 1);
        for (int n = 1; n < len; n++) {
            if (this.mStream.read() != s.charAt(n)) {
                this.mStream.reset();
                return false;
            }
        }
        return true;
    }
}
