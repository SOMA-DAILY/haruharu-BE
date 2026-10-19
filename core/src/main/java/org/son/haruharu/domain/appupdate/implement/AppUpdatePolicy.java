package org.son.haruharu.domain.appupdate.implement;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import org.son.haruharu.domain.appupdate.implement.exception.InvalidAppVersionException;
public record AppUpdatePolicy(String iosMinimumVersion, String androidMinimumVersion,
        long revision, Long updatedBy, LocalDateTime updatedAt) {
    private static final Pattern VERSION = Pattern.compile("(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})");
    public AppUpdatePolicy {
        iosMinimumVersion = normalizeVersion(iosMinimumVersion);
        androidMinimumVersion = normalizeVersion(androidMinimumVersion);
    }
    public static String normalizeVersion(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (!VERSION.matcher(normalized).matches()) throw new InvalidAppVersionException();
        return normalized;
    }
    public static AppUpdatePolicy disabled() { return new AppUpdatePolicy(null, null, 0, null, null); }
}
