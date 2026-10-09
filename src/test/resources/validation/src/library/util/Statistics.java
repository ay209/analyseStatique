package library.util;

import java.util.List;

/**
 * Aggregated figures about the library.
 */
public class Statistics {

    public static double mean(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    public static double max(double a, double b) {
        return a >= b ? a : b;
    }

    public static String report(String title, int items, int members, double fees, int refusals) {
        StringBuilder builder = new StringBuilder();
        builder.append(TextUtils.pad(title, 20)).append('\n');
        builder.append(TextUtils.repeat("-", 20)).append('\n');
        builder.append("items    : ").append(items).append('\n');
        builder.append("members  : ").append(members).append('\n');
        builder.append("fees     : ").append(String.format("%.2f", fees)).append('\n');
        builder.append("refusals : ").append(refusals).append('\n');
        return builder.toString();
    }
}
