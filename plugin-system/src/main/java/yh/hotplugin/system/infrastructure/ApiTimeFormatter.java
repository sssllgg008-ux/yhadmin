package yh.hotplugin.system.infrastructure;

import java.lang.reflect.Array;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Formats all temporal values exposed by pluginSystem JSON APIs. */
public final class ApiTimeFormatter {
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(DATE_PATTERN);

    private ApiTimeFormatter() {}

    public static Object normalize(Object value) { return normalize(null, value); }

    public static Object normalize(String field, Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp) return DATE_TIME.format(((Timestamp) value).toLocalDateTime());
        if (value instanceof java.sql.Date) return DATE.format(((java.sql.Date) value).toLocalDate());
        if (value instanceof LocalDateTime) return DATE_TIME.format((LocalDateTime) value);
        if (value instanceof LocalDate) return DATE.format((LocalDate) value);
        if (value instanceof Instant) return formatInstant((Instant) value, false);
        if (value instanceof OffsetDateTime) return DATE_TIME.format(((OffsetDateTime) value).atZoneSameInstant(ZoneId.systemDefault()));
        if (value instanceof ZonedDateTime) return DATE_TIME.format(((ZonedDateTime) value).withZoneSameInstant(ZoneId.systemDefault()));
        if (value instanceof java.util.Date) return formatInstant(((java.util.Date) value).toInstant(), false);
        if (value instanceof Map) {
            Map<Object, Object> result = new LinkedHashMap<Object, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                String key = entry.getKey() == null ? null : String.valueOf(entry.getKey());
                result.put(entry.getKey(), normalize(key, entry.getValue()));
            }
            return result;
        }
        if (value instanceof Collection) {
            List<Object> result = new ArrayList<Object>();
            for (Object item : (Collection<?>) value) result.add(normalize(null, item));
            return result;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<Object>(length);
            for (int i = 0; i < length; i++) result.add(normalize(null, Array.get(value, i)));
            return result;
        }
        if (isDateTimeField(field)) {
            if (value instanceof Number) return formatEpoch(((Number) value).longValue(), false);
            if (value instanceof CharSequence) return normalizeText(String.valueOf(value), false);
        } else if (isDateField(field)) {
            if (value instanceof Number) return formatEpoch(((Number) value).longValue(), true);
            if (value instanceof CharSequence) return normalizeText(String.valueOf(value), true);
        }
        return value;
    }

    private static boolean isDateTimeField(String field) {
        if (field == null) return false;
        String key = field.toLowerCase(Locale.ROOT);
        if (key.equals("costtime") || key.equals("elapsedtime") || key.equals("durationtime")
                || key.equals("duration") || key.endsWith("cost") || key.endsWith("duration")) return false;
        return key.endsWith("time") || key.endsWith("at");
    }

    private static boolean isDateField(String field) {
        return field != null && field.toLowerCase(Locale.ROOT).endsWith("date");
    }

    private static String normalizeText(String value, boolean dateOnly) {
        String text = value.trim();
        try {
            if (text.matches("^-?\\d{10}$")) return formatInstant(Instant.ofEpochSecond(Long.parseLong(text)), dateOnly);
            if (text.matches("^-?\\d{13}$")) return formatInstant(Instant.ofEpochMilli(Long.parseLong(text)), dateOnly);
            if (dateOnly && text.matches("^\\d{4}-\\d{2}-\\d{2}$")) return text;
            LocalDateTime parsed = LocalDateTime.parse(text.replace(' ', 'T'));
            return dateOnly ? DATE.format(parsed.toLocalDate()) : DATE_TIME.format(parsed);
        } catch (DateTimeParseException | NumberFormatException ignored) {
            return value;
        }
    }

    private static String formatEpoch(long value, boolean dateOnly) {
        Instant instant = Math.abs(value) < 100_000_000_000L ? Instant.ofEpochSecond(value) : Instant.ofEpochMilli(value);
        return formatInstant(instant, dateOnly);
    }

    private static String formatInstant(Instant instant, boolean dateOnly) {
        LocalDateTime value = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return dateOnly ? DATE.format(value.toLocalDate()) : DATE_TIME.format(value);
    }
}
