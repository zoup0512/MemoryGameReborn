package com.crashlytics.android.answers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local no-op replacement for Fabric Answers custom events. Only the parts of
 * the original API that the game code calls are implemented.
 */
public class CustomEvent {
    private final String eventName;
    private final Map<String, Object> customAttributes = new LinkedHashMap<String, Object>();

    public CustomEvent(String eventName) {
        this.eventName = eventName;
    }

    public CustomEvent putCustomAttribute(String key, Object value) {
        if (key != null) {
            this.customAttributes.put(key, value);
        }
        return this;
    }

    public String getEventName() {
        return this.eventName;
    }

    public Map<String, Object> getCustomAttributes() {
        return this.customAttributes;
    }
}
