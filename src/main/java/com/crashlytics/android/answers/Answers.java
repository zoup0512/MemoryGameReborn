package com.crashlytics.android.answers;

/**
 * Local no-op replacement for Fabric Answers. The original app logged custom
 * analytics events to the network; the restored build drops every backend, so
 * events are accepted and discarded.
 */
public final class Answers {
    private static final Answers INSTANCE = new Answers();

    private Answers() {
    }

    public static Answers getInstance() {
        return INSTANCE;
    }

    public void logCustom(CustomEvent event) {
        // intentionally inert
    }
}
