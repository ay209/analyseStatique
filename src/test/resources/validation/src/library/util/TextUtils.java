package library.util;

/**
 * Small helpers on character strings.
 */
public final class TextUtils {

    private TextUtils() {
    }

    public static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    public static String repeat(String text, int times) {
        if (times <= 0) {
            return "";
        }
        return text + repeat(text, times - 1);
    }

    public static String pad(String text, int width) {
        return pad(text, width, ' ');
    }

    public static String pad(String text, int width, char filler) {
        if (text.length() >= width) {
            return text;
        }
        return text + repeat(String.valueOf(filler), width - text.length());
    }
}
