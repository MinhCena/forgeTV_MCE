package vn.forge.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewParent;

/**
 * Ô ứng dụng tự vẽ: khung tối viền đồng, icon nằm trong tấm lục giác;
 * khi được chọn sẽ phóng to, viền đỏ-vàng và quầng sáng nhấp nháy.
 */
final class AppTileView extends View {
    private AppEntry e;
    private boolean fav, anim = true, active;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF panel = new RectF(), img = new RectF(), strip = new RectF();
    private final Path clip = new Path(), hex = new Path();
    private Shader panelShader, plateShader, borderShader;
    private final int labelH;

    AppTileView(Context c) {
        super(c);
        labelH = UiKit.dp(c, 26);
        tp.setTextSize(UiKit.sp(c, 12.5f));
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTypeface(Typeface.DEFAULT_BOLD);
        tp.setColor(UiKit.TEXT);
        setWillNotDraw(false);
    }

    void bind(AppEntry entry, boolean isFav, boolean animated) {
        e = entry;
        fav = isFav;
        anim = animated;
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        if (w <= 0) w = UiKit.dp(getContext(), 160);
        setMeasuredDimension(w, Math.round(w * 0.78f));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float pad = UiKit.dp(getContext(), 5);
        panel.set(pad, pad, w - pad, h - pad);
        img.set(panel.left, panel.top, panel.right, panel.bottom - labelH);
        strip.set(panel.left, panel.bottom - labelH, panel.right, panel.bottom);
        panelShader = new LinearGradient(0, panel.top, 0, panel.bottom,
                0xFF1E1213, 0xFF0D0808, Shader.TileMode.CLAMP);
        plateShader = new LinearGradient(0, img.top, 0, img.bottom,
                0xFF4A1014, 0xFF120909, Shader.TileMode.CLAMP);
        borderShader = new LinearGradient(panel.left, panel.top, panel.right, panel.bottom,
                UiKit.RED_HI, UiKit.GLOW, Shader.TileMode.CLAMP);
        clip.reset();
        float rad = UiKit.dp(getContext(), 10);
        clip.addRoundRect(panel, rad, rad, Path.Direction.CW);
    }

    /** Gọi khi trạng thái chọn hoặc focus của lưới thay đổi. */
    void refreshActive() {
        boolean now = isSelected() && parentHasFocus();
        if (now != active) {
            active = now;
            animate().scaleX(now ? 1.1f : 1f).scaleY(now ? 1.1f : 1f).setDuration(140).start();
            setTranslationZ(now ? 40f : 0f);
            invalidate();
        }
    }

    private boolean parentHasFocus() {
        ViewParent pp = getParent();
        return pp instanceof View && ((View) pp).hasFocus();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        refreshActive();
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (e == null || panelShader == null) return;
        float rad = UiKit.dp(getContext(), 10);

        // nền tấm
        p.setStyle(Paint.Style.FILL);
        p.setShader(panelShader);
        cv.drawRoundRect(panel, rad, rad, p);
        p.setShader(null);

        cv.save();
        cv.clipPath(clip);
        if (e.banner && e.image != null) {
            e.image.setBounds(Math.round(img.left), Math.round(img.top),
                    Math.round(img.right), Math.round(img.bottom));
            e.image.draw(cv);
        } else {
            drawHexIcon(cv);
        }
        // dải tên app
        p.setColor(0xCC000000);
        cv.drawRect(strip, p);
        cv.restore();

        CharSequence t = TextUtils.ellipsize(e.label, tp, strip.width() - UiKit.dp(getContext(), 10),
                TextUtils.TruncateAt.END);
        float ty = strip.centerY() - (tp.descent() + tp.ascent()) / 2f;
        tp.setColor(active ? UiKit.GLOW : UiKit.TEXT);
        cv.drawText(t, 0, t.length(), strip.centerX(), ty, tp);

        if (fav) {
            p.setColor(UiKit.GLOW);
            tp.setColor(UiKit.GLOW);
            cv.drawText("★", panel.right - UiKit.dp(getContext(), 12), panel.top + UiKit.dp(getContext(), 18), tp);
        }

        // viền + quầng sáng
        p.setStyle(Paint.Style.STROKE);
        if (active) {
            float pulse = anim ? 0.7f + 0.3f * (float) Math.sin(SystemClock.uptimeMillis() / 240.0) : 1f;
            for (int i = 3; i >= 1; i--) {
                p.setStrokeWidth(UiKit.dp(getContext(), 2 + i * 3));
                p.setColor(UiKit.alpha(UiKit.GLOW, (int) (34 * pulse)));
                cv.drawRoundRect(panel, rad, rad, p);
            }
            p.setStrokeWidth(UiKit.dp(getContext(), 2.5f));
            p.setShader(borderShader);
            cv.drawRoundRect(panel, rad, rad, p);
            p.setShader(null);
            if (anim) postInvalidateOnAnimation();
        } else {
            p.setStrokeWidth(UiKit.dp(getContext(), 1));
            p.setColor(UiKit.alpha(UiKit.COPPER, 90));
            cv.drawRoundRect(panel, rad, rad, p);
        }
    }

    private void drawHexIcon(Canvas cv) {
        float cx = img.centerX(), cy = img.centerY();
        float r = Math.min(img.width(), img.height()) * 0.44f;
        hex.reset();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(-90 + 60 * i);
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            if (i == 0) hex.moveTo(x, y); else hex.lineTo(x, y);
        }
        hex.close();
        p.setStyle(Paint.Style.FILL);
        p.setShader(plateShader);
        cv.drawPath(hex, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(UiKit.dp(getContext(), 1.5f));
        p.setColor(UiKit.alpha(active ? UiKit.GLOW : UiKit.GOLD, active ? 230 : 140));
        cv.drawPath(hex, p);

        if (e.image != null) {
            int s = Math.round(r * 1.2f);
            e.image.setBounds(Math.round(cx - s / 2f), Math.round(cy - s / 2f),
                    Math.round(cx + s / 2f), Math.round(cy + s / 2f));
            e.image.draw(cv);
        }
    }
}
