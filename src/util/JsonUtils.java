package util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight Zero-Dependency JSON Parser & Builder.
 * Enables the project to run on standard Java without needing Maven or third-party JARs.
 */
public class JsonUtils {

    public static String getString(String json, String key) {
        if (json == null) return null;
        // Matches "key"\s*:\s*"value"
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescape(matcher.group(1));
        }
        return null;
    }

    public static int getInt(String json, String key, int defaultValue) {
        if (json == null) return defaultValue;
        // Matches "key"\s*:\s*([0-9]+)
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    public static boolean getBoolean(String json, String key, boolean defaultValue) {
        if (json == null) return defaultValue;
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Boolean.parseBoolean(matcher.group(1));
        }
        return defaultValue;
    }

    public static String successResponse(String message, String extraJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true,\"message\":\"").append(escape(message)).append("\"");
        if (extraJson != null && !extraJson.trim().isEmpty()) {
            sb.append(",").append(extraJson);
        }
        sb.append("}");
        return sb.toString();
    }

    public static String errorResponse(String message) {
        return String.format("{\"success\":false,\"error\":\"%s\"}", escape(message));
    }

    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    public static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r");
    }
}
