/*
 * Copyright (C) 2026 Mufasa
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * Custom Canvas-based Stylus Charging Pill View with Liquid-Glass Capsule Design.
 * All layers—liquid-glass surface, top reflection, charging glow, stylus icon,
 * text hierarchy, and battery indicator—are rendered on Canvas and strictly clipped
 * to a single outer rounded capsule.
 */
public class StylusChargingPillView extends View {

    public enum StylusPopupState {
        CHARGING,
        FULLY_CHARGED,
        CONNECTED,
        DISCONNECTED,
        LOW_BATTERY,
        CRITICAL_BATTERY,
        UNKNOWN
    }

    public static class StylusPopupModel {
        public final String title;
        public final String subtitle;
        public final Integer batteryPercent;
        public final StylusPopupState state;

        public StylusPopupModel(String title, String subtitle, Integer batteryPercent, StylusPopupState state) {
            this.title = title;
            this.subtitle = subtitle;
            this.batteryPercent = batteryPercent;
            this.state = state;
        }
    }

    private final float density;

    private float dp(float value) {
        return value * density;
    }

    private final RectF outerRect = new RectF();
    private final RectF iconRect = new RectF();
    private final RectF batteryRect = new RectF();

    private final Paint glassSurfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint glassTopReflectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint glassBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint glassInnerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint chargingGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint percentagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint batteryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private StylusPopupModel model = new StylusPopupModel(
            "Stylus",
            "Unknown",
            null,
            StylusPopupState.UNKNOWN
    );

    private ValueAnimator glowAnimator;
    private float glowPulse = 1f;

    public StylusChargingPillView(Context context) {
        this(context, null);
    }

    public StylusChargingPillView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StylusChargingPillView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;

        glassSurfacePaint.setStyle(Paint.Style.FILL);

        glassTopReflectionPaint.setStyle(Paint.Style.FILL);
        glassTopReflectionPaint.setColor(Color.argb(18, 255, 255, 255));

        glassBorderPaint.setStyle(Paint.Style.STROKE);
        glassBorderPaint.setStrokeWidth(dp(1f));
        glassBorderPaint.setColor(Color.argb(48, 255, 255, 255));

        glassInnerBorderPaint.setStyle(Paint.Style.STROKE);
        glassInnerBorderPaint.setStrokeWidth(dp(1f));
        glassInnerBorderPaint.setColor(Color.argb(20, 255, 255, 255));

        chargingGlowPaint.setStyle(Paint.Style.FILL);

        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextSize(dp(13.5f));
        titlePaint.setColor(Color.WHITE);

        subtitlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        subtitlePaint.setTextSize(dp(11.5f));
        subtitlePaint.setColor(Color.argb(175, 255, 255, 255));

        percentagePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        percentagePaint.setTextSize(dp(13f));
        percentagePaint.setTextAlign(Paint.Align.CENTER);
        percentagePaint.setColor(Color.WHITE);

        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(dp(2.2f));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        iconPaint.setColor(Color.WHITE);

        accentPaint.setStyle(Paint.Style.FILL);

        batteryPaint.setStyle(Paint.Style.STROKE);
        batteryPaint.setStrokeWidth(dp(1.5f));
        batteryPaint.setColor(Color.WHITE);

        setClipToOutline(true);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setElevation(dp(4f));
    }

    public void setModel(StylusPopupModel newModel) {
        this.model = newModel;
        updateGlowAnimation();
        invalidate();
    }

    /**
     * Pulses the charging glow while actively charging (OEM pill style).
     * Fully charged and other states keep a steady glow or none at all.
     */
    private void updateGlowAnimation() {
        boolean activelyCharging = model.state == StylusPopupState.CHARGING;

        if (activelyCharging) {
            if (glowAnimator != null) return;
            glowAnimator = ValueAnimator.ofFloat(0.35f, 1f);
            glowAnimator.setDuration(1300L);
            glowAnimator.setRepeatCount(ValueAnimator.INFINITE);
            glowAnimator.setRepeatMode(ValueAnimator.REVERSE);
            glowAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            glowAnimator.addUpdateListener(animation -> {
                glowPulse = (float) animation.getAnimatedValue();
                invalidate();
            });
            glowAnimator.start();
        } else if (glowAnimator != null) {
            glowAnimator.cancel();
            glowAnimator = null;
            glowPulse = 1f;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (glowAnimator != null) {
            glowAnimator.cancel();
            glowAnimator = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = (int) dp(310f);
        int desiredHeight = (int) dp(72f);

        int width = resolveSize(desiredWidth, widthMeasureSpec);
        int height = resolveSize(desiredHeight, heightMeasureSpec);

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float viewWidth = getWidth();
        float viewHeight = getHeight();

        if (viewWidth <= 0f || viewHeight <= 0f) {
            return;
        }

        outerRect.set(
                dp(1f),
                dp(1f),
                viewWidth - dp(1f),
                viewHeight - dp(1f)
        );

        float outerRadius = viewHeight / 2f;

        int saveCount = canvas.save();

        // Absolutely prevents any glass effect or icon from drawing outside the popup capsule
        canvas.clipPath(roundedPath(outerRect, outerRadius));

        drawChargingGlow(canvas, outerRect, outerRadius);

        drawLiquidGlassSurface(canvas, outerRect, outerRadius);

        // Render sharp content inside the capsule
        drawContents(canvas, viewWidth, viewHeight);

        canvas.restoreToCount(saveCount);

        // Draw outer border inside the popup bounds
        canvas.drawRoundRect(
                outerRect,
                outerRadius,
                outerRadius,
                glassBorderPaint
        );

        // Draw inner border
        RectF innerRect = new RectF(
                outerRect.left + dp(2f),
                outerRect.top + dp(2f),
                outerRect.right - dp(2f),
                outerRect.bottom - dp(2f)
        );

        canvas.drawRoundRect(
                innerRect,
                outerRadius - dp(2f),
                outerRadius - dp(2f),
                glassInnerBorderPaint
        );
    }

    private void drawLiquidGlassSurface(Canvas canvas, RectF rect, Float radius) {
        int topColor = Color.argb(190, 72, 75, 84);
        int middleColor = Color.argb(145, 38, 40, 47);
        int bottomColor = Color.argb(178, 18, 20, 25);

        glassSurfacePaint.setShader(new LinearGradient(
                0f,
                rect.top,
                0f,
                rect.bottom,
                new int[]{topColor, middleColor, bottomColor},
                new float[]{0f, 0.42f, 1f},
                Shader.TileMode.CLAMP
        ));

        canvas.drawRoundRect(
                rect,
                radius,
                radius,
                glassSurfacePaint
        );

        glassSurfacePaint.setShader(null);

        // Soft upper reflection, clipped inside the capsule
        RectF reflectionRect = new RectF(
                rect.left + dp(10f),
                rect.top - dp(8f),
                rect.right - dp(10f),
                rect.top + rect.height() * 0.38f
        );

        int saveCount = canvas.save();

        canvas.clipPath(roundedPath(rect, radius));

        canvas.drawOval(
                reflectionRect,
                glassTopReflectionPaint
        );

        canvas.restoreToCount(saveCount);
    }

    private void drawChargingGlow(Canvas canvas, RectF rect, float radius) {
        boolean isCharging = (model.state == StylusPopupState.CHARGING ||
                              model.state == StylusPopupState.FULLY_CHARGED);

        if (!isCharging) {
            return;
        }

        float glowCenterX = rect.right - dp(38f);
        float glowCenterY = rect.centerY();

        chargingGlowPaint.setShader(new RadialGradient(
                glowCenterX,
                glowCenterY,
                dp(58f),
                new int[]{
                        Color.argb((int) (52 * glowPulse), 48, 209, 88),
                        Color.argb((int) (20 * glowPulse), 48, 209, 88),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.52f, 1f},
                Shader.TileMode.CLAMP
        ));

        int saveCount = canvas.save();

        canvas.clipPath(roundedPath(rect, radius));

        canvas.drawRoundRect(
                rect,
                radius,
                radius,
                chargingGlowPaint
        );

        canvas.restoreToCount(saveCount);

        chargingGlowPaint.setShader(null);
    }

    private void drawContents(Canvas canvas, float width, float height) {
        float horizontalPadding = dp(13f);
        float iconSize = dp(40f);
        float gap = dp(9f);

        float iconLeft = horizontalPadding;
        float iconTop = (height - iconSize) / 2f;

        iconRect.set(
                iconLeft,
                iconTop,
                iconLeft + iconSize,
                iconTop + iconSize
        );

        int accentColor = getAccentColor();

        // Small icon surface. It is deliberately inside the outer capsule.
        accentPaint.setColor(Color.argb(
                48,
                Color.red(accentColor),
                Color.green(accentColor),
                Color.blue(accentColor)
        ));

        canvas.drawCircle(
                iconRect.centerX(),
                iconRect.centerY(),
                dp(19f),
                accentPaint
        );

        drawStylusIcon(
                canvas,
                iconRect.centerX(),
                iconRect.centerY(),
                Color.WHITE
        );

        // Reserve a fixed right-side area
        float batteryWidth = dp(25f);
        float batteryHeight = dp(14f);
        float rightPadding = dp(13f);

        float batteryLeft = width - rightPadding - batteryWidth;
        float batteryTop = (height - batteryHeight) / 2f;

        batteryRect.set(
                batteryLeft,
                batteryTop,
                batteryLeft + batteryWidth,
                batteryTop + batteryHeight
        );

        float percentageCenterX = batteryLeft - dp(25f);
        drawBatteryPercentage(
                canvas,
                percentageCenterX,
                height / 2f
        );

        drawBattery(
                canvas,
                batteryRect,
                model.batteryPercent,
                accentColor
        );

        // Text is placed only in the flexible middle area
        float textLeft = iconRect.right + gap;
        float textRight = percentageCenterX - dp(25f);

        float availableTextWidth = textRight - textLeft;

        if (availableTextWidth > dp(45f)) {
            drawTextBlock(
                    canvas,
                    textLeft,
                    textRight,
                    height / 2f
            );
        }
    }

    private void drawTextBlock(Canvas canvas, float left, float right, float centerY) {
        String title = ellipsize(
                model.title != null ? model.title : "",
                titlePaint,
                right - left
        );

        String subtitle = ellipsize(
                model.subtitle != null ? model.subtitle : "",
                subtitlePaint,
                right - left
        );

        Paint.FontMetrics titleMetrics = titlePaint.getFontMetrics();
        Paint.FontMetrics subtitleMetrics = subtitlePaint.getFontMetrics();

        float titleHeight = titleMetrics.bottom - titleMetrics.top;
        float subtitleHeight = subtitleMetrics.bottom - subtitleMetrics.top;

        float spacing = dp(1f);
        float totalHeight = titleHeight + subtitleHeight + spacing;
        float firstBaseline = centerY - totalHeight / 2f - titleMetrics.top;

        canvas.drawText(
                title,
                left,
                firstBaseline,
                titlePaint
        );

        float subtitleBaseline = firstBaseline + titleHeight + spacing;

        canvas.drawText(
                subtitle,
                left,
                subtitleBaseline,
                subtitlePaint
        );
    }

    private void drawBatteryPercentage(Canvas canvas, float centerX, float centerY) {
        Integer percent = model.batteryPercent;

        if (percent == null || percent < 0) {
            return;
        }

        String text = percent + "%";

        Paint.FontMetrics metrics = percentagePaint.getFontMetrics();
        float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;

        canvas.drawText(
                text,
                centerX,
                baseline,
                percentagePaint
        );
    }

    private void drawBattery(Canvas canvas, RectF rect, Integer percent, int color) {
        int safePercent = (percent != null) ? Math.max(0, Math.min(100, percent)) : 0;

        batteryPaint.setColor(Color.argb(
                220,
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        ));

        // Battery body
        RectF body = new RectF(
                rect.left,
                rect.top,
                rect.right - dp(3f),
                rect.bottom
        );

        canvas.drawRoundRect(
                body,
                dp(3f),
                dp(3f),
                batteryPaint
        );

        // Battery terminal
        float terminalLeft = rect.right - dp(2.5f);
        float terminalTop = rect.centerY() - dp(3f);
        float terminalRight = rect.right;
        float terminalBottom = rect.centerY() + dp(3f);

        canvas.drawRoundRect(
                terminalLeft,
                terminalTop,
                terminalRight,
                terminalBottom,
                dp(1f),
                dp(1f),
                batteryPaint
        );

        if (percent != null) {
            float fillWidth = (body.width() - dp(3f)) * (safePercent / 100f);

            if (fillWidth > 0f) {
                accentPaint.setColor(color);

                RectF fill = new RectF(
                        body.left + dp(2f),
                        body.top + dp(2f),
                        body.left + dp(2f) + fillWidth,
                        body.bottom - dp(2f)
                );

                canvas.drawRoundRect(
                        fill,
                        dp(1.5f),
                        dp(1.5f),
                        accentPaint
                );
            }
        }

        // Lightning bolt inside the battery glyph while actively charging
        if (model.state == StylusPopupState.CHARGING) {
            float h = body.height();
            float cx = body.centerX();
            float cy = body.centerY();

            Path bolt = new Path();
            bolt.moveTo(cx + h * 0.14f, cy - h * 0.42f);
            bolt.lineTo(cx - h * 0.26f, cy + h * 0.07f);
            bolt.lineTo(cx - h * 0.03f, cy + h * 0.07f);
            bolt.lineTo(cx - h * 0.14f, cy + h * 0.42f);
            bolt.lineTo(cx + h * 0.26f, cy - h * 0.07f);
            bolt.lineTo(cx + h * 0.03f, cy - h * 0.07f);
            bolt.close();

            accentPaint.setColor(Color.argb(235, 255, 255, 255));
            canvas.drawPath(bolt, accentPaint);
        }
    }

    private void drawStylusIcon(Canvas canvas, float centerX, float centerY, int color) {
        iconPaint.setColor(color);

        int save = canvas.save();

        // Keep the entire icon inside a 28dp square centered inside the 40dp icon area
        canvas.clipRect(
                centerX - dp(14f),
                centerY - dp(14f),
                centerX + dp(14f),
                centerY + dp(14f)
        );

        // Draw a simple original stylus icon at a 45-degree angle
        canvas.rotate(-45f, centerX, centerY);

        float penLeft = centerX - dp(11f);
        float penRight = centerX + dp(10f);
        float penTop = centerY - dp(4.5f);
        float penBottom = centerY + dp(4.5f);

        // Pen body
        canvas.drawRoundRect(
                penLeft,
                penTop,
                penRight,
                penBottom,
                dp(3f),
                dp(3f),
                iconPaint
        );

        // Pen tip
        Path tip = new Path();
        tip.moveTo(penRight, penTop);
        tip.lineTo(penRight + dp(6f), centerY);
        tip.lineTo(penRight, penBottom);

        canvas.drawPath(tip, iconPaint);

        // Small cap line
        canvas.drawLine(
                centerX - dp(5f),
                penTop,
                centerX - dp(5f),
                penBottom,
                iconPaint
        );

        canvas.restoreToCount(save);
    }

    private Path roundedPath(RectF rect, float radius) {
        Path path = new Path();
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        return path;
    }

    private int getAccentColor() {
        if (model.state == null) return Color.rgb(170, 170, 175);
        switch (model.state) {
            case CHARGING:
            case FULLY_CHARGED:
                return Color.rgb(48, 209, 88);

            case LOW_BATTERY:
                return Color.rgb(255, 159, 10);

            case CRITICAL_BATTERY:
                return Color.rgb(255, 69, 58);

            case CONNECTED:
                return Color.rgb(90, 200, 250);

            case DISCONNECTED:
            case UNKNOWN:
            default:
                return Color.rgb(170, 170, 175);
        }
    }

    private String ellipsize(String text, Paint paint, float maxWidth) {
        if (paint.measureText(text) <= maxWidth) {
            return text;
        }

        String suffix = "…";
        String result = text;

        while (!result.isEmpty() && paint.measureText(result + suffix) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }

        return result + suffix;
    }
}
