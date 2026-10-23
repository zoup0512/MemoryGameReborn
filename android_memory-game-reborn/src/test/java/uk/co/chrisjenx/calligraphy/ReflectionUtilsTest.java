package uk.co.chrisjenx.calligraphy;

import org.junit.Test;

import static org.junit.Assert.assertNull;

public class ReflectionUtilsTest {
    @Test public void missingReflectionHookIsIgnored() {
        assertNull(ReflectionUtils.getValue(null, new Object()));
        ReflectionUtils.setValue(null, new Object(), "value");
    }
}
