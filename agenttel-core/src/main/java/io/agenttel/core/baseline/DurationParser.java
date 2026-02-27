package io.agenttel.core.baseline;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses human-friendly duration strings to milliseconds.
 * Supports: "200ms", "1s", "1.5s", "2m", "500".
 */
public final class DurationParser {
    private DurationParser() {}

    private static final Pattern DURATION_PATTERN = Pattern.compile("^([0-9]*\\.?[0-9]+)\\s*(ms|s|m)?$");

    /**
     * Parses a duration string to milliseconds.
     *
     * @param duration duration string (e.g., "200ms", "1s", "1.5s", "2m")
     * @return duration in milliseconds, or -1.0 if the string is empty or unparseable
     */
    public static double parseToMs(String duration) {
        if (duration == null || duration.isBlank()) {
            return -1.0;
        }

        Matcher matcher = DURATION_PATTERN.matcher(duration.trim());
        if (!matcher.matches()) {
            return -1.0;
        }

        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2);

        if (unit == null) {
            return value; // assume milliseconds if no unit
        }

        return switch (unit) {
            case "ms" -> value;
            case "s" -> value * 1000.0;
            case "m" -> value * 60_000.0;
            default -> -1.0;
        };
    }
}
