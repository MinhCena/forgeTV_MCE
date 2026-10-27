package vn.forge.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.View;

/**
 * Lớp nền TĨNH nằm dưới HomeView: nền đen, mảng đỏ chéo và poster MINH CENA.
 * Chỉ vẽ lại khi đổi kích thước hoặc đổi ảnh poster, được giữ thành một texture phần cứng,
 * nên không bao giờ nhấp nháy dù lớp hiệu ứng phía trên cập nhật liên tục.
 */
public class BackdropView extends View {
    private static final float W = 1280f, H = 720f;
    private static final float POSTER_X = 780f, POSTER_Y = 100f, POSTER_W = 540f, POSTER_H = 410f;

    private float u = 1f, ox = 0f, oy = 0f;
    private Bitmap poster;
    private Bitmap posterBmp;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private Shader red;

    public BackdropView(Context c) {
        super(c);
        Ui.loadFonts(c);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        red = new LinearGradient(700f, 0f, 1280f, 720f, 0xFFB80A10, 0xFF5A0104, Shader.TileMode.CLAMP);
    }

    void setPoster(Bitmap b) {
        poster = b;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        u = Math.min(w / W, h / H);
        if (u <= 0f) u = 1f;
        ox = (w - W * u) / 2f;
        oy = (h - H * u) / 2f;
        if (posterBmp != null) { posterBmp.recycle(); posterBmp = null; }
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Ui.BLACK);
        c.save();
        c.translate(ox, oy);
        c.scale(u, u);

        // mảng đỏ chéo, kéo dài ra ngoài khung để phủ kín cả màn hình không đúng 16:9
        float ex = ox / u + 4f, ey = oy / u + 4f;
        float slope = 160f / 720f;
        float yTop = -ey, yBot = H + ey;
        path.reset();
        path.moveTo(860f - slope * yTop, yTop);
        path.lineTo(W + ex, yTop);
        path.lineTo(W + ex, yBot);
        path.lineTo(860f - slope * yBot, yBot);
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setShader(red);
        c.drawPath(path, p);
        p.setShader(null);

        drawPoster(c);
        c.restore();
    }

    private void drawPoster(Canvas c) {
        if (poster != null) {
            float bw = poster.getWidth(), bh = poster.getHeight();
            float sc = Math.min(440f / bw, 470f / bh);
            float dw = bw * sc, dh = bh * sc;
            float x = 1010f - dw / 2f, y = 340f - dh / 2f;
            r.set(x, y, x + dw, y + dh);
            c.drawBitmap(poster, null, r, bp);
            return;
        }
        if (posterBmp == null) buildPosterBitmap();
        r.set(POSTER_X, POSTER_Y, POSTER_X + POSTER_W, POSTER_Y + POSTER_H);
        c.drawBitmap(posterBmp, null, r, bp);
    }

    /** Vẽ sẵn chữ MINH CENA (vàng kim loại + bóng mềm) ở đúng độ phân giải thật của màn hình. */
    private void buildPosterBitmap() {
        int bw = Math.max(1, Math.round(POSTER_W * u)), bh = Math.max(1, Math.round(POSTER_H * u));
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(u, u);
        c.translate(-POSTER_X, -POSTER_Y);
        TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG);
        t.setTypeface(Ui.anton);
        t.setTextSize(204f);
        t.setLetterSpacing(0.01f);
        t.setTextAlign(Paint.Align.LEFT);
        String[] lines = {"MINH", "CENA"};
        float baseline = 306f;
        for (String line : lines) {
            t.setShader(null);
            t.setColor(0x55000000);
            t.setShadowLayer(24f, 0f, 16f, 0x99000000);
            c.drawText(line, 800f, baseline, t);
            t.clearShadowLayer();
            t.setShader(new LinearGradient(0f, baseline - 176f, 0f, baseline,
                    new int[]{Ui.GOLD_HI, Ui.GOLD, Ui.GOLD_LO}, new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
            t.setColor(Ui.GOLD);
            c.drawText(line, 800f, baseline, t);
            baseline += 184f;
        }
        posterBmp = b;
    }
}
