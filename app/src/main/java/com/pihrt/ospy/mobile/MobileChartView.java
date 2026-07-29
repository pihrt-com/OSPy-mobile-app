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

/** Small dependency-free chart for the normalized plug-in mobile series. */
final class MobileChartView extends View {
    private static final int[] COLORS = {
            Color.rgb(43, 138, 30),
            Color.rgb(70, 91, 210),
            Color.rgb(204, 132, 0),
            Color.rgb(146, 27, 37)
    };
    private final List<float[]> lines = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    MobileChartView(Context context, JSONArray series) {
        super(context);
        setMinimumHeight(dp(150));
        parse(series);
    }

    private void parse(JSONArray series) {
        for (int i = 0; i < series.length(); i++) {
            JSONObject item = series.optJSONObject(i);
            JSONArray points = item == null ? null : item.optJSONArray("points");
            if (points == null) continue;
            float[] values = new float[points.length()];
            int count = 0;
            for (int j = 0; j < points.length(); j++) {
                Object point = points.opt(j);
                double value = Double.NaN;
                if (point instanceof JSONObject) {
                    value = ((JSONObject) point).optDouble("value", Double.NaN);
                } else if (point instanceof JSONArray) {
                    value = ((JSONArray) point).optDouble(1, Double.NaN);
                } else if (point instanceof Number) {
                    value = ((Number) point).doubleValue();
                }
                if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                    values[count++] = (float) value;
                }
            }
            if (count > 0) {
                float[] compact = new float[count];
                System.arraycopy(values, 0, compact, 0, count);
                lines.add(compact);
            }
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, dp(160));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int left = dp(12);
        int top = dp(10);
        int right = getWidth() - dp(8);
        int bottom = getHeight() - dp(18);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.rgb(205, 210, 220));
        canvas.drawRect(left, top, right, bottom, paint);
        if (lines.isEmpty()) return;
        float minimum = Float.MAX_VALUE;
        float maximum = -Float.MAX_VALUE;
        for (float[] line : lines) {
            for (float value : line) {
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
            }
        }
        if (maximum <= minimum) maximum = minimum + 1;
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            float[] values = lines.get(lineIndex);
            Path path = new Path();
            for (int i = 0; i < values.length; i++) {
                float x = values.length == 1
                        ? left : left + (right - left) * i / (values.length - 1f);
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
        canvas.drawText(String.format("%.1f", maximum), left, top + dp(10), paint);
        canvas.drawText(String.format("%.1f", minimum), left, bottom - dp(3), paint);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
