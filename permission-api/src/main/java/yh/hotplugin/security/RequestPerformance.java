package yh.hotplugin.security;

import org.noear.solon.core.handle.Context;

import java.util.LinkedHashMap;
import java.util.Map;

/** Request-scoped performance stages shared by the host and hot plugins. */
public final class RequestPerformance {
    private static final String START = "security.performance.start";
    private static final String STAGES = "security.performance.stages";

    private RequestPerformance() { }

    public static void start(Context context) {
        context.attrSet(START, System.nanoTime());
        context.attrSet(STAGES, new LinkedHashMap<String, Double>());
    }

    public static long begin() { return System.nanoTime(); }

    public static void recordCurrent(String stage, long startedAt) {
        Context context = Context.current();
        if (context != null) record(context, stage, startedAt);
    }

    @SuppressWarnings("unchecked")
    public static void record(Context context, String stage, long startedAt) {
        Object value = context.attr(STAGES);
        if (!(value instanceof Map)) return;
        Map<String, Double> stages = (Map<String, Double>) value;
        synchronized (stages) {
            stages.put(stage, millis(System.nanoTime() - startedAt));
        }
    }

    public static double totalMillis(Context context) {
        Object value = context.attr(START);
        return value instanceof Long ? millis(System.nanoTime() - (Long) value) : 0D;
    }

    @SuppressWarnings("unchecked")
    public static String slowLog(Context context, double totalMillis) {
        StringBuilder out = new StringBuilder(192);
        out.append("[slow-request] method=").append(context.method())
                .append(" path=").append(context.path())
                .append(" status=").append(context.status())
                .append(" totalMs=").append(format(totalMillis));
        Object value = context.attr(STAGES);
        if (value instanceof Map) {
            synchronized (value) {
                for (Map.Entry<String, Double> entry : ((Map<String, Double>) value).entrySet())
                    out.append(' ').append(entry.getKey()).append("Ms=").append(format(entry.getValue()));
            }
        }
        return out.toString();
    }

    private static double millis(long nanos) { return nanos / 1_000_000D; }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
