package com.internal.knowledge.util;

import java.util.regex.Pattern;

public class PIIRedactionUtil {
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    private static final Pattern PHONE_PATTERN =
        Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b");
    private static final Pattern SSN_PATTERN =
        Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");

    public String redactPII(String text) {
        if (text == null || text.isEmpty()) return text;
        String redacted = text;
        redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("[REDACTED_EMAIL]");
        redacted = PHONE_PATTERN.matcher(redacted).replaceAll("[REDACTED_PHONE]");
        redacted = SSN_PATTERN.matcher(redacted).replaceAll("[REDACTED_SSN]");
        return redacted;
    }

    public boolean containsPII(String text) {
        if (text == null || text.isEmpty()) return false;
        return EMAIL_PATTERN.matcher(text).find() ||
               PHONE_PATTERN.matcher(text).find() ||
               SSN_PATTERN.matcher(text).find();
    }
}
