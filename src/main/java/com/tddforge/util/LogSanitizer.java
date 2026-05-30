package com.tddforge.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class LogSanitizer {

    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(
            "(?i)(password|passwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key|credential|auth)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SSH_KEY_PATTERN = Pattern.compile(
            "-----BEGIN [A-Z ]*PRIVATE KEY-----"
    );

    private static final Set<String> SENSITIVE_EXACT_KEYS = Set.of(
            "password", "passwd", "secret", "token", "api_key", "api-key",
            "access_key", "access-key", "private_key", "private-key",
            "credential", "auth", "authorization"
    );

    private static final int MAX_ENV_VALUE_LENGTH = 80;
    private static final int MAX_PROMPT_LOG_LENGTH = 200;
    private static final int MAX_OUTPUT_LOG_LENGTH = 200;

    private LogSanitizer() {
    }

    public static String sanitizeValue(String key, String value) {
        if (value == null) {
            return null;
        }
        if (isSensitiveKey(key)) {
            return "[REDACTED]";
        }
        return value;
    }

    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (SENSITIVE_EXACT_KEYS.contains(lower)) {
            return true;
        }
        return SENSITIVE_KEY_PATTERN.matcher(key).find();
    }

    public static String sanitizeEnvironmentVariable(String entry) {
        if (entry == null) {
            return null;
        }
        int eqIdx = entry.indexOf('=');
        if (eqIdx < 0) {
            return entry;
        }
        String key = entry.substring(0, eqIdx);
        String value = entry.substring(eqIdx + 1);
        if (isSensitiveKey(key)) {
            return key + "=[REDACTED]";
        }
        if (SSH_KEY_PATTERN.matcher(value).find()) {
            return key + "=[REDACTED:SSH_KEY]";
        }
        if (value.length() > MAX_ENV_VALUE_LENGTH) {
            return key + "=" + value.substring(0, MAX_ENV_VALUE_LENGTH) + "...[TRUNCATED]";
        }
        return entry;
    }

    public static String sanitizeEnvString(String envString) {
        if (envString == null || envString.isBlank()) {
            return envString;
        }
        String[] entries = envString.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(sanitizeEnvironmentVariable(entries[i].trim()));
        }
        return sb.toString();
    }

    public static String truncatePrompt(String prompt) {
        if (prompt == null) {
            return null;
        }
        if (prompt.length() <= MAX_PROMPT_LOG_LENGTH) {
            return prompt;
        }
        return prompt.substring(0, MAX_PROMPT_LOG_LENGTH) + "...[TRUNCATED " + prompt.length() + " chars]";
    }

    public static String truncateOutput(String output) {
        if (output == null) {
            return null;
        }
        if (output.length() <= MAX_OUTPUT_LOG_LENGTH) {
            return output;
        }
        return output.substring(0, MAX_OUTPUT_LOG_LENGTH) + "...[TRUNCATED " + output.length() + " chars]";
    }

    public static String redactSensitiveInText(String text) {
        if (text == null) {
            return null;
        }
        String redacted = SSH_KEY_PATTERN.matcher(text).replaceAll("[REDACTED:SSH_KEY]");
        return redacted;
    }
}
