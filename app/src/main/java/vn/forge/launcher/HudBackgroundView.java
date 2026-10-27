package vn.forge.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Nền tối kiểu HUD: lưới lục giác mờ, hai vòng quay chậm, vệt quét ngang, quầng đỏ phía dưới.
 * Lưới được vẽ sẵn thành bitmap ALPHA_8 (nhẹ), phần động chạy ~25 khung/giây để đỡ tốn GPU của box.
 */
final class HudBackgroundView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private Bitmap grid;
    private Shader bgShader, glowShader, sweepShader, vignette;
    private boolean anim = true;
    private final long t0 = SystemClock.uptimeMillis();
    private float sweepH;

    HudBackgroundView(Context c) {
        super(c);
    }

    void setAnimated(boolean a) {
        anim = a;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (w <= 0 || h <= 0) return;
        bgShader = new LinearGradient(0, 0, 0, h,
                new int[]{0xFF0B0708, 0xFF150909, 0xFF070405}, null, Shader.TileMode.CLAMP);
        glowShader = new RadialGradient(w * 0.5f, h * 1.08f, w * 0.75f,
                new int[]{0x70B3121C, 0x00000000}, null, Shader.TileMode.CLAMP);
        sweepH = UiKit.dp(getContext(), 90);
        sweepShader = new LinearGradient(0, 0, 0, sweepH,
                new int[]{0x00D4A24C, 0x1CD4A24C, 0x00D4A24C}, null, Shader.TileMode.CLAMP);
        vignette = new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.75f,
                new int[]{0x00000000, 0x00000000, 0xB0000000}, new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP);
        buildGrid(w, h);
    }

    private void buildGrid(int w, int h) {
        if (grid != null) grid.recycle();
        grid = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(grid);
        Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
        lp.setStyle(Paint.Style.STROKE);
        lp.setStrokeWidth(UiKit.dp(getContext(), 1));
        lp.setColor(0xFFFFFFFF);
        float s = UiKit.dp(getContext(), 44);
        float hw = (float) (Math.sqrt(3) * s);
        Path path = new Path();
        int rows = (int) (h / (1.5f * s)) + 2;
        int cols = (int) (w / hw) + 2;
        for (int r = 0; r < rows; r++) {
            for (int q = 0; q < cols; q++) {
                float cx = q * hw + ((r & 1) == 1 ? hw / 2f : 0f);
                float cy = r * 1.5f * s;
                for (int i = 0; i < 6; i++) {
                    double a = Math.toRadians(-90 + 60 * i);
                    float x = cx + (float) Math.cos(a) * s;
                    float y = cy + (float) Math.sin(a) * s;
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                path.close();
            }
        }
        c.drawPath(path, lp);
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || bgShader == null) return;
        long t = anim ? SystemClock.uptimeMillis() - t0 : 0;

        p.setStyle(Paint.Style.FILL);
        p.setShader(bgShader);
        cv.drawRect(0, 0, w, h, p);
        p.setShader(glowShader);
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // lưới lục giác mờ
        if (grid != null) {
            p.setColor(UiKit.GOLD);
            p.setAlpha(22);
            cv.drawBitmap(grid, 0, 0, p);
            p.setAlpha(255);
        }

        // hai vòng quay ở góc trên phải
        float cx = w * 0.84f, cy = h * 0.2f;
        float r1 = h * 0.52f, r2 = h * 0.36f, r3 = h * 0.24f;
        p.setStyle(Paint.Style.STROKE);

        p.setStrokeWidth(UiKit.dp(getContext(), 3));
        p.setColor(UiKit.alpha(UiKit.RED_HI, 70));
        p.setPathEffect(new DashPathEffect(new float[]{r1 * 0.35f, r1 * 0.12f, r1 * 0.05f, r1 * 0.12f}, 0));
        cv.save();
        cv.rotate((t / 90f) % 360f, cx, cy);
        oval.set(cx - r1, cy - r1, cx + r1, cy + r1);
        cv.drawOval(oval, p);
        cv.restore();

        p.setStrokeWidth(UiKit.dp(getContext(), 2));
        p.setColor(UiKit.alpha(UiKit.GOLD, 80));
        p.setPathEffect(new DashPathEffect(new float[]{r2 * 0.08f, r2 * 0.07f}, 0));
        cv.save();
        cv.rotate(-(t / 60f) % 360f, cx, cy);
        oval.set(cx - r2, cy - r2, cx + r2, cy + r2);
        cv.drawOval(oval, p);
        cv.restore();

        p.setPathEffect(null);
        p.setStrokeWidth(UiKit.dp(getContext(), 1));
        p.setColor(UiKit.alpha(UiKit.GOLD, 60));
        cv.drawCircle(cx, cy, r3, p);

        // vệt quét ngang
        if (anim && sweepShader != null) {
            float y = ((t / 28f) % (h + sweepH * 2)) - sweepH;
            p.setStyle(Paint.Style.FILL);
            p.setShader(sweepShader);
            cv.save();
            cv.translate(0, y);
            cv.drawRect(0, 0, w, sweepH, p);
            cv.restore();
            p.setShader(null);
        }

        p.setStyle(Paint.Style.FILL);
        p.setShader(vignette);
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);

        if (anim) postInvalidateDelayed(40);
    }
}
