package com.pihrt.ospy.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Dependency-free chart with legend and a visible time range. */
final class MobileChartView extends View {
    private static final int[] COLORS = {
            Color.rgb(43, 138, 30), Color.rgb(70, 91, 210),
            Color.rgb(204, 132, 0), Color.rgb(146, 27, 37),
            Color.rgb(17, 138, 178), Color.rgb(123, 63, 152),
            Color.rgb(90, 104, 70), Color.rgb(225, 87, 89)
    };

    private static final class Series {
        String label;
        String unit;
        String firstTime = "";
        String lastTime = "";
        float[] values;
        long[] times;
    }

    private final List<Series> lines = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    MobileChartView(Context context, JSONArray series) {
        super(context);
        parse(series);
    }

    private void parse(JSONArray source) {
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            JSONArray points = item == null ? null : item.optJSONArray("points");
            if (points == null) continue;
            List<Float> values = new ArrayList<>();
            List<Long> times = new ArrayList<>();
            Series line = new Series();
            line.label = item.optString(
                    "label",
                    item.optString(
                            "id", getContext().getString(R.string.chart_series)));
            line.unit = item.optString("unit");
            for (int j = 0; j < points.length(); j++) {
                Object point = points.opt(j);
                double value = Double.NaN;
                String time = "";
                if (point instanceof JSONObject) {
                    JSONObject object = (JSONObject) point;
                    value = object.optDouble("value", Double.NaN);
                    time = object.optString("time", object.optString("timestamp"));
                } else if (point instanceof JSONArray) {
                    JSONArray array = (JSONArray) point;
                    time = array.optString(0);
                    value = array.optDouble(1, Double.NaN);
                } else if (point instanceof Number) {
                    value = ((Number) point).doubleValue();
                }
                if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                    values.add((float) value);
                    times.add(parseTime(time));
                    if (!time.isEmpty()) {
                        if (line.firstTime.isEmpty()) line.firstTime = time;
                        line.lastTime = time;
                    }
                }
            }
            if (values.isEmpty()) continue;
            line.values = new float[values.size()];
            line.times = new long[times.size()];
            for (int j = 0; j < values.size(); j++) {
                line.values[j] = values.get(j);
                line.times[j] = times.get(j);
            }
            lines.add(line);
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int legendRows = Math.max(1, (lines.size() + 1) / 2);
        setMeasuredDimension(width, dp(175 + legendRows * 18));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int left = dp(38);
        int top = dp(12);
        int right = getWidth() - dp(10);
        int bottom = getHeight() - dp(42 + Math.max(1, (lines.size() + 1) / 2) * 18);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.rgb(205, 210, 220));
        for (int i = 0; i <= 4; i++) {
            float y = top + (bottom - top) * i / 4f;
            canvas.drawLine(left, y, right, y, paint);
        }
        canvas.drawRect(left, top, right, bottom, paint);
        if (lines.isEmpty()) return;
        float minimum = Float.MAX_VALUE;
        float maximum = -Float.MAX_VALUE;
        for (Series line : lines) {
            for (float value : line.values) {
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
            }
        }
        if (maximum <= minimum) maximum = minimum + 1;
        long earliest = Long.MAX_VALUE;
        long latest = Long.MIN_VALUE;
        for (Series line : lines) {
            for (long time : line.times) {
                if (time < 0) continue;
                earliest = Math.min(earliest, time);
                latest = Math.max(latest, time);
            }
        }
        boolean useRealTime = earliest != Long.MAX_VALUE && latest > earliest;
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            Series line = lines.get(lineIndex);
            float[] values = line.values;
            Path path = new Path();
            for (int i = 0; i < values.length; i++) {
                float x;
                if (useRealTime && line.times[i] >= 0) {
                    x = left + (right - left) *
                            (line.times[i] - earliest) / (float) (latest - earliest);
                } else {
                    x = values.length == 1
                            ? left : left + (right - left) * i /
                                    (values.length - 1f);
                }
                float y = bottom - (bottom - top) *
                        (values[i] - minimum) / (maximum - minimum);
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            paint.setColor(COLORS[lineIndex % COLORS.length]);
            paint.setStrokeWidth(dp(2));
            canvas.drawPath(path, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(dp(10));
        paint.setColor(Color.DKGRAY);
        canvas.drawText(String.format(Locale.getDefault(), "%.1f", maximum),
                dp(2), top + dp(8), paint);
        canvas.drawText(String.format(Locale.getDefault(), "%.1f", minimum),
                dp(2), bottom, paint);
        String first = compactTime(globalBoundary(true));
        String last = compactTime(globalBoundary(false));
        canvas.drawText(first, left, bottom + dp(15), paint);
        float lastWidth = paint.measureText(last);
        canvas.drawText(last, right - lastWidth, bottom + dp(15), paint);
        int legendTop = bottom + dp(29);
        int columnWidth = Math.max(dp(1), (right - left) / 2);
        for (int i = 0; i < lines.size(); i++) {
            int column = i % 2;
            int row = i / 2;
            float x = left + column * columnWidth;
            float y = legendTop + row * dp(18);
            paint.setColor(COLORS[i % COLORS.length]);
            canvas.drawRect(x, y - dp(8), x + dp(12), y + dp(2), paint);
            paint.setColor(Color.DKGRAY);
            String label = lines.get(i).label +
                    (lines.get(i).unit.isEmpty() ? "" : " (" + lines.get(i).unit + ")");
            canvas.drawText(ellipsize(label, columnWidth - dp(18)),
                    x + dp(16), y + dp(1), paint);
        }
    }

    private long parseTime(String value) {
        if (value == null || value.isEmpty()) return -1;
        try {
            return OffsetDateTime.parse(value).toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(value).atZone(ZoneId.systemDefault())
                    .toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private String globalBoundary(boolean first) {
        long boundary = first ? Long.MAX_VALUE : Long.MIN_VALUE;
        String result = "";
        for (Series line : lines) {
            for (int index = 0; index < line.times.length; index++) {
                long time = line.times[index];
                if (time < 0) continue;
                if ((first && time < boundary) || (!first && time > boundary)) {
                    boundary = time;
                    result = index == 0 ? line.firstTime
                            : index == line.times.length - 1 ? line.lastTime :
                            java.time.Instant.ofEpochMilli(time)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime().toString();
                }
            }
        }
        if (!result.isEmpty()) return result;
        return first ? lines.get(0).firstTime : lines.get(0).lastTime;
    }

    private String compactTime(String value) {
        if (value == null || value.isEmpty()) return "";
        String normalized = value.replace('T', ' ');
        return normalized.length() > 16 ? normalized.substring(0, 16) : normalized;
    }

    private String ellipsize(String value, int width) {
        if (paint.measureText(value) <= width) return value;
        String result = value;
        while (result.length() > 1 &&
                paint.measureText(result + "\u2026") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "\u2026";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
