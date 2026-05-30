package com.tddforge.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void shouldRedactPasswordKey() {
        assertThat(LogSanitizer.sanitizeValue("password", "secret123")).isEqualTo("[REDACTED]");
        assertThat(LogSanitizer.sanitizeValue("Password", "secret123")).isEqualTo("[REDACTED]");
        assertThat(LogSanitizer.sanitizeValue("mysql_password", "secret123")).isEqualTo("[REDACTED]");
        assertThat(LogSanitizer.sanitizeValue("db.passwd", "secret123")).isEqualTo("[REDACTED]");
    }

    @Test
    void shouldRedactTokenKey() {
        assertThat(LogSanitizer.sanitizeValue("token", "abc123")).isEqualTo("[REDACTED]");
        assertThat(LogSanitizer.sanitizeValue("api_token", "abc123")).isEqualTo("[REDACTED]");
        assertThat(LogSanitizer.sanitizeValue("access-token", "abc123")).isEqualTo("[REDACTED]");
    }

    @Test
    void shouldRedactSecretKey() {
        assertThat(LogSanitizer.sanitizeValue("secret", "mysecret")).isEqualTo("[REDACTED]");
        assertThat(LogSanitizer.sanitizeValue("client_secret", "mysecret")).isEqualTo("[REDACTED]");
    }

    @Test
    void shouldRedactApiKey() {
        assertThat(LogSanitizer.sanitizeValue("api_key", "key123")).isEqualTo("[REDACTED]");
        assertThat(LogSanitizer.sanitizeValue("api-key", "key123")).isEqualTo("[REDACTED]");
        assertThat(LogSanitizer.sanitizeValue("apiKey", "key123")).isEqualTo("[REDACTED]");
    }

    @Test
    void shouldRedactPrivateKey() {
        assertThat(LogSanitizer.sanitizeValue("private_key", "keydata")).isEqualTo("[REDACTED]");
        assertThat(LogSanitizer.sanitizeValue("private-key", "keydata")).isEqualTo("[REDACTED]");
    }

    @Test
    void shouldNotRedactNormalKeys() {
        assertThat(LogSanitizer.sanitizeValue("username", "admin")).isEqualTo("admin");
        assertThat(LogSanitizer.sanitizeValue("model", "gpt-4")).isEqualTo("gpt-4");
        assertThat(LogSanitizer.sanitizeValue("taskId", "task-123")).isEqualTo("task-123");
        assertThat(LogSanitizer.sanitizeValue("host", "localhost")).isEqualTo("localhost");
    }

    @Test
    void shouldHandleNullValue() {
        assertThat(LogSanitizer.sanitizeValue("password", null)).isNull();
    }

    @Test
    void shouldHandleNullKey() {
        assertThat(LogSanitizer.sanitizeValue(null, "value")).isEqualTo("value");
    }

    @Test
    void shouldIdentifySensitiveKeys() {
        assertThat(LogSanitizer.isSensitiveKey("password")).isTrue();
        assertThat(LogSanitizer.isSensitiveKey("MYSQL_PASSWORD")).isTrue();
        assertThat(LogSanitizer.isSensitiveKey("token")).isTrue();
        assertThat(LogSanitizer.isSensitiveKey("api_key")).isTrue();
        assertThat(LogSanitizer.isSensitiveKey("secret")).isTrue();
        assertThat(LogSanitizer.isSensitiveKey("authorization")).isTrue();
        assertThat(LogSanitizer.isSensitiveKey("credential")).isTrue();
    }

    @Test
    void shouldNotIdentifyNormalKeysAsSensitive() {
        assertThat(LogSanitizer.isSensitiveKey("username")).isFalse();
        assertThat(LogSanitizer.isSensitiveKey("model")).isFalse();
        assertThat(LogSanitizer.isSensitiveKey("host")).isFalse();
        assertThat(LogSanitizer.isSensitiveKey("port")).isFalse();
    }

    @Test
    void shouldSanitizeEnvironmentVariableWithSensitiveKey() {
        String result = LogSanitizer.sanitizeEnvironmentVariable("MYSQL_PASSWORD=secret123");
        assertThat(result).isEqualTo("MYSQL_PASSWORD=[REDACTED]");
    }

    @Test
    void shouldSanitizeEnvironmentVariableWithSshKey() {
        String sshValue = "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA...";
        String result = LogSanitizer.sanitizeEnvironmentVariable("SSH_KEY=" + sshValue);
        assertThat(result).isEqualTo("SSH_KEY=[REDACTED:SSH_KEY]");
    }

    @Test
    void shouldTruncateLongEnvironmentVariableValue() {
        String longValue = "a".repeat(200);
        String result = LogSanitizer.sanitizeEnvironmentVariable("MY_VAR=" + longValue);
        assertThat(result).startsWith("MY_VAR=");
        assertThat(result).contains("[TRUNCATED]");
        assertThat(result.length()).isLessThan(200);
    }

    @Test
    void shouldNotTruncateShortEnvironmentVariableValue() {
        String result = LogSanitizer.sanitizeEnvironmentVariable("MY_VAR=short");
        assertThat(result).isEqualTo("MY_VAR=short");
    }

    @Test
    void shouldSanitizeEnvStringWithMultipleEntries() {
        String envString = "HOME=/home/user, MYSQL_PASSWORD=secret, MODEL=gpt-4";
        String result = LogSanitizer.sanitizeEnvString(envString);
        assertThat(result).contains("HOME=/home/user");
        assertThat(result).contains("MYSQL_PASSWORD=[REDACTED]");
        assertThat(result).contains("MODEL=gpt-4");
    }

    @Test
    void shouldHandleNullEnvString() {
        assertThat(LogSanitizer.sanitizeEnvString(null)).isNull();
        assertThat(LogSanitizer.sanitizeEnvString("")).isEmpty();
    }

    @Test
    void shouldTruncateLongPrompt() {
        String longPrompt = "x".repeat(500);
        String result = LogSanitizer.truncatePrompt(longPrompt);
        assertThat(result).startsWith("xxx");
        assertThat(result).contains("[TRUNCATED 500 chars]");
        assertThat(result.length()).isLessThan(300);
    }

    @Test
    void shouldNotTruncateShortPrompt() {
        String shortPrompt = "short prompt";
        assertThat(LogSanitizer.truncatePrompt(shortPrompt)).isEqualTo(shortPrompt);
    }

    @Test
    void shouldHandleNullPrompt() {
        assertThat(LogSanitizer.truncatePrompt(null)).isNull();
    }

    @Test
    void shouldTruncateLongOutput() {
        String longOutput = "y".repeat(500);
        String result = LogSanitizer.truncateOutput(longOutput);
        assertThat(result).contains("[TRUNCATED 500 chars]");
    }

    @Test
    void shouldNotTruncateShortOutput() {
        String shortOutput = "short output";
        assertThat(LogSanitizer.truncateOutput(shortOutput)).isEqualTo(shortOutput);
    }

    @Test
    void shouldHandleNullOutput() {
        assertThat(LogSanitizer.truncateOutput(null)).isNull();
    }

    @Test
    void shouldRedactSshKeyInText() {
        String text = "Some text with -----BEGIN RSA PRIVATE KEY----- embedded";
        String result = LogSanitizer.redactSensitiveInText(text);
        assertThat(result).isEqualTo("Some text with [REDACTED:SSH_KEY] embedded");
    }

    @Test
    void shouldNotModifyNormalText() {
        String text = "Normal log message with no sensitive data";
        assertThat(LogSanitizer.redactSensitiveInText(text)).isEqualTo(text);
    }

    @Test
    void shouldHandleNullText() {
        assertThat(LogSanitizer.redactSensitiveInText(null)).isNull();
    }
}
