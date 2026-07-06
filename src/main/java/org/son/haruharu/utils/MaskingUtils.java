package org.son.haruharu.utils;

public final class MaskingUtils {

    private MaskingUtils() {
    }

    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        int keep = Math.min(4, value.length() / 2);
        return value.substring(0, keep) + "***";
    }
}
