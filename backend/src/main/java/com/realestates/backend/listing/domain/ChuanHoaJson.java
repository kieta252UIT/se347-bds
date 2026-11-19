package com.realestates.backend.listing.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ChuanHoaJson {

    private ChuanHoaJson() {
    }

    public static String bamYeuCau(String httpMethod, String path, Object payloadCanonicalInput) {
        String nguon = httpMethod + "|" + path + "|" + toTypedCanonicalString(payloadCanonicalInput);
        return sha256Base64Url(nguon);
    }

    public static String toTypedCanonicalString(Object value) {
        StringBuilder sb = new StringBuilder();
        appendTypedCanonical(sb, value);
        return sb.toString();
    }

    public static String toJsonString(Object value) {
        StringBuilder sb = new StringBuilder();
        appendJson(sb, value);
        return sb.toString();
    }

    private static void appendTypedCanonical(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof String s) {
            sb.append("S:");
            appendJsonString(sb, s);
            return;
        }
        if (value instanceof Number n) {
            sb.append("N:").append(n);
            return;
        }
        if (value instanceof Boolean b) {
            sb.append("B:").append(b);
            return;
        }
        if (value instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                appendTypedCanonical(sb, list.get(i));
            }
            sb.append(']');
            return;
        }
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appendJsonString(sb, entry.getKey());
                sb.append(':');
                appendTypedCanonical(sb, entry.getValue());
            }
            sb.append('}');
            return;
        }
        sb.append("O:");
        appendJsonString(sb, value.toString());
    }

    private static void appendJson(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            sb.append(value);
            return;
        }
        if (value instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                appendJson(sb, list.get(i));
            }
            sb.append(']');
            return;
        }
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appendJsonString(sb, entry.getKey());
                sb.append(':');
                appendJson(sb, entry.getValue());
            }
            sb.append('}');
            return;
        }
        appendJsonString(sb, value.toString());
    }

    private static void appendJsonString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    public static String sha256Base64Url(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Không khởi tạo được SHA-256", ex);
        }
    }
}
