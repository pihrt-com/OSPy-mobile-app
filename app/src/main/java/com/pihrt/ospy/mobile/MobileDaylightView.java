package com.pihrt.ospy.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Fixed 24-hour day/night timeline for Astro Sunrise and Sunset. */
final class MobileDaylightView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LocalDateTime start;
    private final LocalDateTime sunrise;
    private final LocalDateTime sunset;
    private final LocalDateTime now;
    private final int textColor;
    private final int gridColor;
    private final int nightColor;
    private final int dayColor;

    MobileDaylightView(Context context, JSONObject timeline, boolean darkTheme) {
        super(context);
        start = parse(timeline.optString("start"));
        sunrise = parse(timeline.optString("sunrise"));
        sunset = parse(timeline.optString("sunset"));
        now = parse(timeline.optString("now"));
        textColor = darkTheme ? Color.rgb(225, 230, 238) : Color.DKGRAY;
        gridColor = darkTheme ? Color.rgb(93, 102, 116) : Color.rgb(75, 87, 108);
        nightColor = darkTheme ? Color.rgb(67, 72, 94) : Color.rgb(194, 196, 214);
        dayColor = darkTheme ? Color.rgb(232, 232, 224) : Color.WHITE;
        setContentDescription(context.getString(R.string.sunrise_sunset_timeline));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), dp(145));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int left = dp(8);
        int right = getWidth() - dp(8);
        int top = dp(30);
        int bottom = dp(92);
        float sunriseX = xFor(sunrise, left, right);
        float sunsetX = xFor(sunset, left, right);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(nightColor);
        canvas.drawRect(left, top, right, bottom, paint);
        if (sunrise != null && sunset != null && sunsetX > sunriseX) {
            paint.setColor(dayColor);
            canvas.drawRect(sunriseX, top, sunsetX, bottom, paint);
        }

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(9));
        for (int hour = 0; hour <= 24; hour++) {
            float x = left + (right - left) * hour / 24f;
            paint.setColor(gridColor);
            paint.setStrokeWidth(hour % 3 == 0 ? dp(1) : 1f);
            canvas.drawLine(x, top, x, bottom, paint);
            if (hour % 3 == 0) {
                paint.setColor(textColor);
                String label = String.format(Locale.getDefault(), "%02d:00", hour);
                if (hour == 0) paint.setTextAlign(Paint.Align.LEFT);
                else if (hour == 24) paint.setTextAlign(Paint.Align.RIGHT);
                else paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(label, x, dp(19), paint);
            }
        }

        drawEvent(canvas, sunriseX, sunrise, Color.rgb(53, 155, 52), top, bottom);
        drawEvent(canvas, sunsetX, sunset, Color.rgb(31, 82, 156), top, bottom);
        if (now != null && start != null && now.toLocalDate().equals(start.toLocalDate())) {
            float nowX = xFor(now, left, right);
            paint.setColor(Color.rgb(174, 27, 38));
            paint.setStrokeWidth(dp(2));
            canvas.drawLine(nowX, top - dp(9), nowX, bottom + dp(7), paint);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(10));
            canvas.drawText(now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    nowX, bottom + dp(22), paint);
        }
    }

    private void drawEvent(
            Canvas canvas, float x, LocalDateTime event, int color,
            int top, int bottom) {
        if (event == null) return;
        paint.setColor(color);
        paint.setStrokeWidth(dp(3));
        canvas.drawLine(x, top, x, bottom, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(10));
        canvas.drawText(event.format(DateTimeFormatter.ofPattern("HH:mm")),
                x, bottom + dp(22), paint);
    }

    private float xFor(LocalDateTime value, int left, int right) {
        if (value == null || start == null) return left;
        double minutes = java.time.Duration.between(start, value).toMillis() / 60000.0;
        minutes = Math.max(0, Math.min(24 * 60, minutes));
        return left + (right - left) * (float) (minutes / (24 * 60));
    }

    private LocalDateTime parse(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
