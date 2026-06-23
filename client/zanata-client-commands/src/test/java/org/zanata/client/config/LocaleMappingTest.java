package org.zanata.client.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.zanata.common.LocaleId;

class LocaleMappingTest {

    @Test
    void serverLocaleCanonicalisesJavaUnderscoreToBcp47() {
        // A config may list the Java-style "zh_TW"; the server locale id must be
        // the BCP-47 "zh-TW" (LocaleId rejects underscores).
        assertEquals("zh-TW", new LocaleMapping("zh_TW").getLocale());
        assertEquals("zh-TW", new LocaleMapping("zh-TW").getLocale());
        assertNull(new LocaleMapping().getLocale());
        // The canonical server id must build a valid LocaleId (no exception).
        assertEquals("zh-TW",
                new LocaleId(new LocaleMapping("zh_TW").getLocale()).getId());
    }

    @Test
    void onDiskLocaleFormIsPreserved() {
        // The local/disk side keeps the Java underscore form so folder/file
        // names still resolve.
        LocaleMapping m = new LocaleMapping("zh_TW");
        assertEquals("zh_TW", m.getLocalLocale());
        assertEquals("zh_TW", m.getJavaLocale());

        // An explicit mapFrom is honoured untouched.
        LocaleMapping mapped = new LocaleMapping("zh-TW", "zh_TW");
        assertEquals("zh-TW", mapped.getLocale());
        assertEquals("zh_TW", mapped.getLocalLocale());
    }
}
