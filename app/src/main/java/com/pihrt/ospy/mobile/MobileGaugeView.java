package com.pihrt.ospy.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Native radial gauge matching the limits configured by Weather Dashboard. */
final class MobileGaugeView extends View {
    private static final float START_ANGLE = 135f;
    private static final float SWEEP_ANGLE = 270f;

    private static final class Range {
        double from;
        double to;
        int color;
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Double> ticks = new ArrayList<>();
    private final List<Range> ranges = new ArrayList<>();
    private final String label;
    private final String unit;
    private final boolean available;
    private final double value;
    private final double minimum;
    private final double maximum;
    private final int textColor;
    private final int mutedColor;
    private final int trackColor;

    MobileGaugeView(Context context, JSONObject gauge, boolean darkTheme) {
        super(context);
        label = gauge.optString("label", gauge.optString("id"));
        unit = gauge.optString("unit");
        available = gauge.optBoolean("available", !gauge.isNull("value"));
        value = gauge.optDouble("value", 0);
        minimum = gauge.optDouble("minimum", 0);
        double parsedMaximum = gauge.optDouble("maximum", 100);
        maximum = parsedMaximum > minimum ? parsedMaximum : minimum + 1;
        textColor = darkTheme ? Color.rgb(230, 234, 240) : Color.DKGRAY;
        mutedColor = darkTheme ? Color.rgb(165, 174, 187) : Color.GRAY;
        trackColor = darkTheme ? Color.rgb(68, 77, 90) : Color.rgb(225, 228, 233);
        JSONArray tickValues = gauge.optJSONArray("ticks");
        if (tickValues != null) {
            for (int index = 0; index < tickValues.length(); index++) {
                double tick = tickValues.optDouble(index, Double.NaN);
                if (!Double.isNaN(tick)) ticks.add(tick);
            }
        }
        JSONArray configuredRanges = gauge.optJSONArray("ranges");
        if (configuredRanges != null) {
            for (int index = 0; index < configuredRanges.length(); index++) {
                JSONObject source = configuredRanges.optJSONObject(index);
                if (source == null) continue;
                Range range = new Range();
                range.from = source.optDouble("from", minimum);
                range.to = source.optDouble("to", minimum);
                range.color = rangeColor(source.optString("color"));
                ranges.add(range);
            }
        }
        setContentDescription(label + ": " + formattedValue());
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, Math.min(dp(300), Math.max(dp(235), width)));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float centerX = getWidth() / 2f;
        float radius = Math.min(getWidth() * 0.38f, getHeight() * 0.36f);
        float centerY = dp(25) + radius;
        RectF arc = new RectF(
                centerX - radius, centerY - radius,
                centerX + radius, centerY + radius);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeWidth(dp(17));
        paint.setColor(trackColor);
        canvas.drawArc(arc, START_ANGLE, SWEEP_ANGLE, false, paint);
        for (Range range : ranges) {
            float start = valueAngle(range.from);
            float end = valueAngle(range.to);
            if (end <= start) continue;
            paint.setColor(range.color);
            canvas.drawArc(arc, start, end - start, false, paint);
        }

        List<Double> shownTicks = ticks.isEmpty() ? defaultTicks() : ticks;
        paint.setStrokeWidth(dp(1));
        paint.setTextSize(dp(10));
        paint.setTextAlign(Paint.Align.CENTER);
        for (double tick : shownTicks) {
            float angle = valueAngle(tick);
            double radians = Math.toRadians(angle);
            float outerX = centerX + (float) Math.cos(radians) * (radius + dp(10));
            float outerY = centerY + (float) Math.sin(radians) * (radius + dp(10));
            float innerX = centerX + (float) Math.cos(radians) * (radius - dp(4));
            float innerY = centerY + (float) Math.sin(radians) * (radius - dp(4));
            paint.setColor(mutedColor);
            canvas.drawLine(innerX, innerY, outerX, outerY, paint);
            float labelX = centerX + (float) Math.cos(radians) * (radius - dp(24));
            float labelY = centerY + (float) Math.sin(radians) * (radius - dp(24)) + dp(4);
            canvas.drawText(formatNumber(tick), labelX, labelY, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(textColor);
        paint.setTextSize(dp(16));
        canvas.drawText(label, centerX, centerY - dp(22), paint);

        if (available) {
            float needleAngle = valueAngle(value);
            double radians = Math.toRadians(needleAngle);
            paint.setColor(Color.rgb(232, 103, 91));
            paint.setStrokeWidth(dp(4));
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(
                    centerX, centerY,
                    centerX + (float) Math.cos(radians) * radius * 0.72f,
                    centerY + (float) Math.sin(radians) * radius * 0.72f,
                    paint);
            canvas.drawCircle(centerX, centerY, dp(9), paint);
        }

        paint.setColor(textColor);
        paint.setTextSize(dp(13));
        canvas.drawText(unit, centerX, centerY + dp(31), paint);
        paint.setTextSize(dp(18));
        paint.setColor(available ? textColor : mutedColor);
        canvas.drawText(formattedValue(), centerX, centerY + radius * 0.76f, paint);
    }

    private List<Double> defaultTicks() {
        List<Double> result = new ArrayList<>();
        for (int index = 0; index <= 4; index++) {
            result.add(minimum + (maximum - minimum) * index / 4.0);
        }
        return result;
    }

    private float valueAngle(double raw) {
        double clamped = Math.max(minimum, Math.min(maximum, raw));
        return START_ANGLE + (float) ((clamped - minimum) /
                (maximum - minimum) * SWEEP_ANGLE);
    }

    private String formattedValue() {
        return available ? formatNumber(value) : getContext().getString(R.string.not_available);
    }

    private String formatNumber(double number) {
        if (Math.abs(number - Math.rint(number)) < 0.000001) {
            return String.format(Locale.getDefault(), "%.0f", number);
        }
        return String.format(Locale.getDefault(), "%.2f", number);
    }

    private int rangeColor(String value) {
        switch (value.toLowerCase(Locale.ROOT)) {
            case "red": return Color.rgb(235, 35, 35);
            case "blue": return Color.rgb(35, 72, 220);
            case "green": return Color.rgb(25, 145, 45);
            default: return trackColor;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
