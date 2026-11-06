package com.vinland.app.ai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Null-safe readers for the loosely typed maps returned by Cloud Functions and Firestore. */
final class Maps {
    private Maps() {
    }

    static String string(Map<?, ?> m, String key, String fallback) {
        Object v = m.get(key);
        return v instanceof String ? (String) v : fallback;
    }

    static long number(Map<?, ?> m, String key, long fallback) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).longValue() : fallback;
    }

    static boolean bool(Map<?, ?> m, String key, boolean fallback) {
        Object v = m.get(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    static Map<?, ?> map(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v instanceof Map ? (Map<?, ?>) v : Collections.emptyMap();
    }

    static List<?> list(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v instanceof List ? (List<?>) v : Collections.emptyList();
    }

    static List<String> strings(Map<?, ?> m, String key) {
        List<String> out = new ArrayList<>();
        for (Object o : list(m, key)) {
            if (o instanceof String && !((String) o).isEmpty()) out.add((String) o);
        }
        return out;
    }
}
