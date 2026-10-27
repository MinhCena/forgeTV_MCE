package vn.forge.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/** Biểu tượng riêng của FORGE TV: lục giác vàng đồng bao quanh lõi đỏ phát sáng (thiết kế gốc). */
final class EmblemView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path hex = new Path();
    private boolean anim = true;

    EmblemView(Context c) {
        super(c);
    }

    void setAnimated(boolean a) {
        anim = a;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int s = UiKit.dp(getContext(), 46);
        setMeasuredDimension(s, s);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f - 2f;
        float pulse = anim ? (0.8f + 0.2f * (float) Math.sin(SystemClock.uptimeMillis() / 380.0)) : 1f;

        // lục giác ngoài
        hex.reset();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(-90 + 60 * i);
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            if (i == 0) hex.moveTo(x, y); else hex.lineTo(x, y);
        }
        hex.close();
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(UiKit.dp(getContext(), 2));
        p.setColor(UiKit.GOLD);
        cv.drawPath(hex, p);

        // vòng đỏ
        p.setColor(UiKit.RED_HI);
        p.setStrokeWidth(UiKit.dp(getContext(), 1.5f));
        cv.drawCircle(cx, cy, r * 0.62f, p);

        // lõi phát sáng
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, r * 0.62f,
                new int[]{0xFFFFF1C2, UiKit.GLOW, UiKit.RED, 0x00B3121C},
                new float[]{0f, 0.28f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        p.setAlpha((int) (255 * pulse));
        cv.drawCircle(cx, cy, r * 0.62f, p);
        p.setShader(null);
        p.setAlpha(255);

        if (anim) postInvalidateDelayed(50);
    }
}
