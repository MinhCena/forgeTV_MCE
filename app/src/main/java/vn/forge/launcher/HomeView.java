package vn.forge.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Màn hình chính Minh Cena TV, tự vẽ toàn bộ bằng Canvas.
 * Mọi kích thước tính theo "đơn vị thiết kế" 1280x720 rồi nhân hệ số u = min(w/1280, h/720)
 * nên cùng một giao diện đúng tỷ lệ và sắc nét từ TV HD 24 inch đến 4K 55 inch
 * (chữ và hình vector được vẽ lại ở độ phân giải thật, không phóng ảnh).
 */
public class HomeView extends View {

    interface Host {
        void openSettings();
        void openAppMenu(AppEntry e);
    }

    private static final float W = 1280f, H = 720f;
    private static final int ZONE_HERO = 0, ZONE_DOCK = 1;
    private static final int OV_NONE = 0, OV_APPS = 1, OV_MENU = 2;

    private final Context ctx;
    private Host host;

    // dữ liệu
    private final List<AppEntry> apps = new ArrayList<>();
    private final List<AppEntry> pinned = new ArrayList<>();
    private WidgetData wd;
    private IconStore icons;
    private Bitmap poster;
    private Bitmap posterBmp;
    private static final float POSTER_X = 780f, POSTER_Y = 100f, POSTER_W = 540f, POSTER_H = 410f;

    // hệ số co giãn
    private float u = 1f, ox = 0f, oy = 0f;

    // focus
    private int zone = ZONE_DOCK, heroIdx = 0, dockIdx = 0;
    private int overlay = OV_NONE;
    private int appsIdx = 0, appsScroll = 0;
    private String menuTitle = "";
    private List<MenuItemSpec> menuItems = new ArrayList<>();
    private int menuIdx = 0, menuScroll = 0;

    // hoạt ảnh
    private final float[] dockSize = new float[16];
    private final float[] heroScale = {1f, 1f};
    private float ovAlpha = 0f;
    private long lastFrame;
    private final long t0 = SystemClock.uptimeMillis();

    // đồng hồ và mạng
    private String clockText = "", dateText = "", netText = "";
    private long clockAt, netAt;
    private boolean anim = true;

    // đồ vẽ dùng lại
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG);
    private final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final RectF r = new RectF();
    private final RectF r2 = new RectF();
    private final RectF tile = new RectF();
    private final Path path = new Path();
    private final Matrix mx = new Matrix();
    private Shader gFill, gBorder, gPanel, gRed, gSheen, gBar, gDiv, gCloud, gPost, gGoldTile;

    public HomeView(Context c) {
        super(c);
        ctx = c;
        setFocusable(true);
        setFocusableInTouchMode(true);
        Ui.loadFonts(c);
        gFill = new LinearGradient(0, 0, 1, 1, 0x29FFFFFF, 0x0DFFFFFF, Shader.TileMode.CLAMP);
        gBorder = new LinearGradient(0, 0, 0, 1, 0x80FFFFFF, 0x26FFFFFF, Shader.TileMode.CLAMP);
        gPanel = new LinearGradient(0, 0, 0, 1, 0xFF34343A, 0xFF0C0C0E, Shader.TileMode.CLAMP);
        gRed = new LinearGradient(0, 0, 1, 1, 0xFFB80A10, 0xFF5A0104, Shader.TileMode.CLAMP);
        gSheen = new LinearGradient(0, 0, 0, 1, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        gBar = new LinearGradient(0, 0, 0, 1, 0x24FFFFFF, 0x0FFFFFFF, Shader.TileMode.CLAMP);
        gDiv = new LinearGradient(0, 0, 0, 1, new int[]{0x00FFFFFF, 0x59FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        gCloud = new LinearGradient(0, 0, 0, 1, 0xFFFFFFFF, 0xFFC9CED8, Shader.TileMode.CLAMP);
        gPost = new LinearGradient(0, 0, 0, 1, new int[]{Ui.GOLD_HI, Ui.GOLD, Ui.GOLD_LO},
                new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP);
        gGoldTile = new LinearGradient(0, 0, 0, 1, 0xFFFFDF6E, 0xFFE6A912, Shader.TileMode.CLAMP);
        for (int i = 0; i < dockSize.length; i++) dockSize[i] = 64f;
        icons = new IconStore(c, new Runnable() {
            @Override public void run() { invalidate(); }
        });
        anim = Prefs.anim(c);
    }

    // ---------------------------------------------------------------- nạp dữ liệu

    void setHost(Host h) { host = h; }
    void setWidgetData(WidgetData d) { wd = d; }
    void setPoster(Bitmap b) { poster = b; invalidate(); }
    void release() { icons.shutdown(); }

    void setApps(List<AppEntry> list) {
        apps.clear();
        apps.addAll(list);
        refreshPinned();
        invalidate();
    }

    /** Tính lại 6 ô trên Dock: ưu tiên đã ghim, rồi ứng dụng mở gần đây, rồi theo tên. */
    void refreshPinned() {
        anim = Prefs.anim(ctx);
        pinned.clear();
        java.util.Set<String> hidden = Prefs.hidden(ctx);
        java.util.Map<String, AppEntry> byKey = new java.util.HashMap<>();
        for (AppEntry e : apps) byKey.put(e.key, e);
        for (String k : Prefs.favs(ctx)) {
            AppEntry e = byKey.get(k);
            if (e != null && !hidden.contains(k) && !pinned.contains(e) && pinned.size() < 6) pinned.add(e);
        }
        for (String k : Prefs.recent(ctx)) {
            AppEntry e = byKey.get(k);
            if (e != null && !hidden.contains(k) && !pinned.contains(e) && pinned.size() < 6) pinned.add(e);
        }
        for (AppEntry e : apps) {
            if (pinned.size() >= 6) break;
            if (!hidden.contains(e.key) && !pinned.contains(e)) pinned.add(e);
        }
        if (dockIdx > dockCount() - 1) dockIdx = Math.max(0, dockCount() - 1);
        invalidate();
    }

    private List<AppEntry> visibleApps() {
        java.util.Set<String> hidden = Prefs.hidden(ctx);
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : apps) if (!hidden.contains(e.key)) out.add(e);
        return out;
    }

    private int dockCount() { return pinned.size() + 2; }

    // ---------------------------------------------------------------- menu kính

    void showMenu(String title, List<MenuItemSpec> items, int focus) {
        menuTitle = title;
        menuItems = items;
        menuIdx = Math.max(0, Math.min(items.size() - 1, focus));
        menuScroll = Math.max(0, Math.min(menuIdx, items.size() - MENU_VIS));
        if (menuIdx < menuScroll + MENU_VIS && menuIdx >= menuScroll) { /* đã nằm trong khung nhìn */ }
        else menuScroll = Math.max(0, menuIdx - MENU_VIS + 1);
        overlay = OV_MENU;
        invalidate();
    }

    void closeOverlay() {
        overlay = OV_NONE;
        invalidate();
    }

    boolean isOverlayOpen() { return overlay != OV_NONE; }

    // ---------------------------------------------------------------- kích thước

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        u = Math.min(w / W, h / H);
        if (u <= 0f) u = 1f;
        ox = (w - W * u) / 2f;
        oy = (h - H * u) / 2f;
        icons.setSize(Math.round(88f * u));
        if (posterBmp != null) { posterBmp.recycle(); posterBmp = null; }
    }

    // ---------------------------------------------------------------- vẽ

    @Override
    protected void onDraw(Canvas cv) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 16f : Math.min(64f, now - lastFrame);
        lastFrame = now;
        boolean animating = stepAnimations(dt);
        tickStatus();

        cv.drawColor(Ui.BLACK);
        cv.save();
        cv.translate(ox, oy);
        cv.scale(u, u);

        drawBackground(cv);
        drawPoster(cv);
        drawMenuBar(cv);
        drawHero(cv);
        if (Prefs.widgets(ctx)) drawWidgets(cv, now);
        drawDock(cv);

        if (ovAlpha > 0.01f) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.alpha(Ui.BLACK, 0.72f * ovAlpha));
            cv.drawRect(-ox / u - 2, -oy / u - 2, W + ox / u + 2, H + oy / u + 2, p);
            if (overlay == OV_APPS) drawAllApps(cv);
            else if (overlay == OV_MENU) drawMenu(cv);
        }
        cv.restore();

        if (animating) postInvalidateOnAnimation();
        else if (anim) postInvalidateDelayed(40);
        else postInvalidateDelayed(20000);
    }

    private boolean stepAnimations(float dt) {
        boolean moving = false;
        float k = 1f - (float) Math.exp(-dt / 70f);
        int n = dockCount();
        for (int i = 0; i < n; i++) {
            float target = 64f;
            if (overlay == OV_NONE && zone == ZONE_DOCK) {
                int d = Math.abs(i - dockIdx);
                target = d == 0 ? 88f : (d == 1 ? 74f : 64f);
            }
            dockSize[i] += (target - dockSize[i]) * k;
            if (Math.abs(target - dockSize[i]) > 0.05f) moving = true;
        }
        for (int i = 0; i < 2; i++) {
            float target = (overlay == OV_NONE && zone == ZONE_HERO && heroIdx == i) ? 1.06f : 1f;
            heroScale[i] += (target - heroScale[i]) * k;
            if (Math.abs(target - heroScale[i]) > 0.002f) moving = true;
        }
        float ot = overlay == OV_NONE ? 0f : 1f;
        ovAlpha += (ot - ovAlpha) * k;
        if (Math.abs(ot - ovAlpha) > 0.01f) moving = true; else ovAlpha = ot;
        return moving;
    }

    private void tickStatus() {
        long now = System.currentTimeMillis();
        if (now - clockAt > 1000L || clockText.length() == 0) {
            clockAt = now;
            Calendar c = Calendar.getInstance();
            clockText = String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
            String[] wd = {"Chủ nhật", "Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy"};
            dateText = wd[c.get(Calendar.DAY_OF_WEEK) - 1] + " " + c.get(Calendar.DAY_OF_MONTH) + "/" + (c.get(Calendar.MONTH) + 1);
        }
        if (now - netAt > 5000L || netText.length() == 0) {
            netAt = now;
            netText = "Mất mạng";
            try {
                ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
                if (ni != null && ni.isConnected()) {
                    netText = ni.getType() == ConnectivityManager.TYPE_ETHERNET ? "Có dây"
                            : (ni.getType() == ConnectivityManager.TYPE_WIFI ? "Wi-Fi" : "Mạng");
                }
            } catch (Exception ignored) {
            }
        }
    }

    // ---------------------------------------------------------------- tiện ích vẽ

    private void text(Canvas c, String s, float x, float y, Typeface tf, float size, int color,
                      Paint.Align al, float spacingEm) {
        tp.setTypeface(tf);
        tp.setTextSize(size);
        tp.setColor(color);
        tp.setTextAlign(al);
        tp.setLetterSpacing(spacingEm);
        tp.setShader(null);
        tp.clearShadowLayer();
        c.drawText(s, x, y, tp);
    }

    private float textW(String s, Typeface tf, float size, float spacingEm) {
        tp.setTypeface(tf);
        tp.setTextSize(size);
        tp.setLetterSpacing(spacingEm);
        return tp.measureText(s);
    }

    private String fit(String s, Typeface tf, float size, float maxW) {
        tp.setTypeface(tf);
        tp.setTextSize(size);
        tp.setLetterSpacing(0f);
        return TextUtils.ellipsize(s, tp, maxW, TextUtils.TruncateAt.END).toString();
    }

    private void setUnit(Shader s, float x, float y, float w, float h) {
        mx.reset();
        mx.setScale(w, h);
        mx.postTranslate(x, y);
        s.setLocalMatrix(mx);
    }

    /** Tấm kính trung tính: nền trắng trong 16% → 5%, viền sáng mép trên, bóng đổ. */
    private void glass(Canvas c, float x, float y, float w, float h, float rad, boolean shadow) {
        if (shadow) softShadow(c, x, y, w, h, rad, 14f, 5, 0.55f);
        r.set(x, y, x + w, y + h);
        boolean g = Prefs.glass(ctx);
        p.setStyle(Paint.Style.FILL);
        if (g) {
            setUnit(gFill, x, y, w, h);
            p.setShader(gFill);
            c.drawRoundRect(r, rad, rad, p);
            p.setShader(null);
        } else {
            p.setShader(null);
            p.setColor(0xFF1A1A1C);
            c.drawRoundRect(r, rad, rad, p);
        }
        r2.set(x + 0.5f, y + 0.5f, x + w - 0.5f, y + h - 0.5f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        setUnit(gBorder, x, y, w, h);
        p.setShader(gBorder);
        c.drawRoundRect(r2, rad, rad, p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
    }

    /** Bóng đổ mềm bằng vài lớp mờ dần xếp chồng: rẻ hơn nhiều so với làm mờ thật trên GPU yếu. */
    private void softShadow(Canvas c, float x, float y, float w, float h, float rad, float dy, int steps, float maxAlpha) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        for (int i = steps; i >= 1; i--) {
            float g = i * 3f;
            r.set(x - g, y - g + dy, x + w + g, y + h + g + dy);
            p.setColor(Ui.alpha(Ui.BLACK, maxAlpha / steps));
            c.drawRoundRect(r, rad + g, rad + g, p);
        }
    }

    private void glow(Canvas c, RectF rect, float rad, float strength) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        for (int i = 3; i >= 1; i--) {
            p.setStrokeWidth(2f + i * 3.5f);
            p.setColor(Ui.alpha(Ui.GOLD, 0.14f * strength));
            c.drawRoundRect(rect, rad, rad, p);
        }
        p.setStrokeWidth(3f);
        p.setColor(Ui.GOLD);
        c.drawRoundRect(rect, rad, rad, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void triangle(Canvas c, float cx, float cy, float s, boolean up, int color) {
        path.reset();
        if (up) { path.moveTo(cx, cy - s); path.lineTo(cx + s, cy + s); path.lineTo(cx - s, cy + s); }
        else { path.moveTo(cx, cy + s); path.lineTo(cx + s, cy - s); path.lineTo(cx - s, cy - s); }
        path.close();
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawPath(path, p);
    }

    // ---------------------------------------------------------------- nền và poster

    private void drawBackground(Canvas c) {
        float ex = ox / u + 4f, ey = oy / u + 4f;
        float slope = 160f / 720f;                       // cạnh trái mảng đỏ: x = 860 - slope*y
        float yTop = -ey, yBot = H + ey;
        path.reset();
        path.moveTo(860f - slope * yTop, yTop);
        path.lineTo(W + ex, yTop);
        path.lineTo(W + ex, yBot);
        path.lineTo(860f - slope * yBot, yBot);
        path.close();
        setUnit(gRed, 700f, 0f, 580f, 720f);
        p.setShader(gRed);
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path, p);
        p.setShader(null);
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

    /** Vẽ sẵn chữ MINH CENA (vàng kim loại + bóng mềm) một lần ở đúng độ phân giải thật của màn hình. */
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
            mx.reset();
            mx.setScale(1f, 176f);
            mx.postTranslate(0f, baseline - 176f);
            Shader sh = new LinearGradient(0, 0, 0, 1, new int[]{Ui.GOLD_HI, Ui.GOLD, Ui.GOLD_LO},
                    new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP);
            sh.setLocalMatrix(mx);
            t.setShader(sh);
            t.setColor(Ui.GOLD);
            c.drawText(line, 800f, baseline, t);
            baseline += 184f;
        }
        posterBmp = b;
    }

    // ---------------------------------------------------------------- thanh menu

    private void drawMenuBar(Canvas c) {
        float ex = ox / u + 4f;
        r.set(-ex, 0f, W + ex, 40f);
        p.setStyle(Paint.Style.FILL);
        setUnit(gBar, 0f, 0f, 1f, 40f);
        p.setShader(gBar);
        c.drawRect(r, p);
        p.setShader(null);
        p.setColor(0x29FFFFFF);
        c.drawRect(-ex, 39f, W + ex, 40f, p);

        // biểu tượng MC
        path.reset();
        path.moveTo(40f, 7f); path.lineTo(52f, 14f); path.lineTo(52f, 28f);
        path.lineTo(40f, 35f); path.lineTo(28f, 28f); path.lineTo(28f, 14f); path.close();
        p.setColor(Ui.GOLD);
        c.drawPath(path, p);
        text(c, "MC", 40f, 27f, Ui.anton, 13f, Ui.BLACK, Paint.Align.CENTER, 0f);

        float x = 62f;
        text(c, "Minh Cena TV", x, 26f, Ui.bold, 15f, Ui.WHITE, Paint.Align.LEFT, 0.01f);
        x += textW("Minh Cena TV", Ui.bold, 15f, 0.01f) + 26f;
        String[] items = {"Trang chủ", "Ứng dụng", "Cài đặt"};
        for (int i = 0; i < items.length; i++) {
            Typeface tf = i == 0 ? Ui.bold : Ui.semi;
            text(c, items[i], x, 26f, tf, 14f, i == 0 ? Ui.WHITE : 0xB8FFFFFF, Paint.Align.LEFT, 0f);
            x += textW(items[i], tf, 14f, 0f) + 26f;
        }

        float rx = W - 28f;
        text(c, clockText, rx, 27f, Ui.bold, 15f, Ui.WHITE, Paint.Align.RIGHT, 0f);
        rx -= textW(clockText, Ui.bold, 15f, 0f) + 18f;
        text(c, dateText, rx, 26f, Ui.semi, 14f, 0xE6FFFFFF, Paint.Align.RIGHT, 0f);
        rx -= textW(dateText, Ui.semi, 14f, 0f) + 18f;
        text(c, netText, rx, 26f, Ui.semi, 14f, 0xE6FFFFFF, Paint.Align.RIGHT, 0f);
        rx -= textW(netText, Ui.semi, 14f, 0f) + 7f;
        drawWifi(c, rx - 18f, 11f, 18f);
    }

    private void drawWifi(Canvas c, float x, float y, float s) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(Ui.GOLD);
        float cx = x + s / 2f, cy = y + s * 0.85f;
        float[] rad = {s * 0.80f, s * 0.54f, s * 0.28f};
        for (float rr : rad) {
            r.set(cx - rr, cy - rr, cx + rr, cy + rr);
            c.drawArc(r, 225f, 90f, false, p);
        }
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy - 1f, 1.6f, p);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    // ---------------------------------------------------------------- khu chào và nút

    private String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g = h < 11 ? "Chào buổi sáng" : (h < 14 ? "Chào buổi trưa" : (h < 18 ? "Chào buổi chiều" : "Chào buổi tối"));
        return g + ", " + Prefs.userName(ctx);
    }

    private AppEntry continueApp() {
        for (String k : Prefs.recent(ctx)) {
            for (AppEntry e : apps) if (e.key.equals(k)) return e;
        }
        return pinned.isEmpty() ? null : pinned.get(0);
    }

    private void drawHero(Canvas c) {
        text(c, "TIẾP TỤC", 56f, 96f, Ui.bold, 13f, Ui.GOLD, Paint.Align.LEFT, 0.3f);
        text(c, greeting(), 56f, 148f, Ui.bold, 42f, Ui.WHITE, Paint.Align.LEFT, -0.01f);
        AppEntry ca = continueApp();
        String sub;
        if (ca == null) sub = "Chọn một ứng dụng bên dưới để bắt đầu";
        else {
            long at = Prefs.recentAt(ctx);
            if (at > 0 && !Prefs.recent(ctx).isEmpty()) {
                Calendar cc = Calendar.getInstance();
                cc.setTimeInMillis(at);
                sub = fit(ca.label, Ui.regular, 17f, 300f) + " · bạn dừng lại lúc "
                        + String.format(Locale.US, "%02d:%02d", cc.get(Calendar.HOUR_OF_DAY), cc.get(Calendar.MINUTE));
            } else sub = fit(ca.label, Ui.regular, 17f, 400f);
        }
        text(c, sub, 56f, 182f, Ui.regular, 17f, Ui.BRONZE, Paint.Align.LEFT, 0f);

        // nút chính
        float bx = 56f, by = 204f, bh = 50f, bw = 158f;
        float s0 = heroScale[0];
        c.save();
        c.scale(s0, s0, bx + bw / 2f, by + bh / 2f);
        r.set(bx, by, bx + bw, by + bh);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        r2.set(r);
        r2.inset(-5f, -5f);
        p.setColor(Ui.alpha(Ui.GOLD, 0.16f));
        c.drawRoundRect(r2, 30f, 30f, p);
        p.setColor(Ui.GOLD);
        c.drawRoundRect(r, 25f, 25f, p);
        if (zone == ZONE_HERO && heroIdx == 0 && overlay == OV_NONE) {
            r2.set(r); r2.inset(-3f, -3f);
            glow(c, r2, 28f, 1f);
        }
        triangle2(c, bx + 30f, by + bh / 2f, 8f);
        text(c, "Tiếp tục", bx + 54f, by + 31f, Ui.bold, 16f, Ui.BLACK, Paint.Align.LEFT, 0f);
        c.restore();

        // nút phụ (kính)
        float cx2 = 228f, cw = 190f;
        float s1 = heroScale[1];
        c.save();
        c.scale(s1, s1, cx2 + cw / 2f, by + bh / 2f);
        glass(c, cx2, by, cw, bh, 25f, true);
        if (zone == ZONE_HERO && heroIdx == 1 && overlay == OV_NONE) {
            r2.set(cx2, by, cx2 + cw, by + bh); r2.inset(-3f, -3f);
            glow(c, r2, 28f, 1f);
        }
        text(c, "Tất cả ứng dụng", cx2 + cw / 2f, by + 31f, Ui.semi, 16f, Ui.WHITE, Paint.Align.CENTER, 0f);
        c.restore();
    }

    private void triangle2(Canvas c, float x, float cy, float s) {
        path.reset();
        path.moveTo(x - s * 0.5f, cy - s);
        path.lineTo(x + s, cy);
        path.lineTo(x - s * 0.5f, cy + s);
        path.close();
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.BLACK);
        c.drawPath(path, p);
    }

    // ---------------------------------------------------------------- widget

    private void drawWidgets(Canvas c, long now) {
        drawWeather(c, now);
        drawGold(c);
        drawFuel(c);
    }

    private void drawWeather(Canvas c, long now) {
        float x = 56f, y = 290f, w = 268f, h = 196f;
        glass(c, x, y, w, h, 24f, true);
        if (wd == null || !wd.weatherOk) {
            text(c, "Thời tiết", x + 18f, y + 34f, Ui.bold, 12f, Ui.GOLD, Paint.Align.LEFT, 0.16f);
            text(c, "Chưa có dữ liệu", x + w / 2f, y + 106f, Ui.semi, 15f, 0xCCFFFFFF, Paint.Align.CENTER, 0f);
            text(c, "Kiểm tra kết nối mạng", x + w / 2f, y + 130f, Ui.regular, 12f, 0x99FFFFFF, Paint.Align.CENTER, 0f);
            return;
        }
        synchronized (wd) {
            text(c, wd.city.toUpperCase(Locale.getDefault()), x + 18f, y + 34f, Ui.bold, 12f, Ui.GOLD, Paint.Align.LEFT, 0.16f);
            text(c, wd.temp + "°", x + 16f, y + 92f, Ui.bold, 54f, Ui.WHITE, Paint.Align.LEFT, -0.02f);
            text(c, fit(wd.wText, Ui.regular, 13f, 150f) + " · " + wd.hi + "° / " + wd.lo + "°", x + 18f, y + 116f,
                    Ui.regular, 13f, 0xD9FFFFFF, Paint.Align.LEFT, 0f);
            drawWeatherIcon(c, wd.wIcon, x + w - 62f, y + 56f, 1.0f, now);

            int n = wd.hourCount;
            if (n >= 2) {
                int mn = 999, mxv = -999;
                for (int i = 0; i < n; i++) { mn = Math.min(mn, wd.hourTemp[i]); mxv = Math.max(mxv, wd.hourTemp[i]); }
                float sx = x + 28f, ex = x + w - 28f, sy = y + 142f, sh = 24f;
                float span = Math.max(1, mxv - mn);
                path.reset();
                float[] px = new float[n], py = new float[n];
                for (int i = 0; i < n; i++) {
                    px[i] = sx + (ex - sx) * i / (n - 1);
                    py[i] = sy + sh - sh * (wd.hourTemp[i] - mn) / span;
                    if (i == 0) path.moveTo(px[i], py[i]); else path.lineTo(px[i], py[i]);
                }
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.4f);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(Ui.GOLD);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < n; i++) {
                    p.setColor(i == 0 ? Ui.GOLD : Ui.WHITE);
                    c.drawCircle(px[i], py[i], i == 0 ? 3.6f : 3f, p);
                    String lab = wd.hourLabel[i] + (i == 0 ? "" : " · " + wd.hourTemp[i] + "°");
                    text(c, lab, px[i], y + h - 14f, Ui.regular, 11.5f, 0xBFFFFFFF,
                            i == 0 ? Paint.Align.LEFT : (i == n - 1 ? Paint.Align.RIGHT : Paint.Align.CENTER), 0f);
                }
                p.setStrokeCap(Paint.Cap.BUTT);
            }
        }
    }

    /** Biểu tượng thời tiết động: mặt trời quay chậm, mây trôi, giọt mưa rơi, tia chớp nhấp nháy. */
    private void drawWeatherIcon(Canvas c, int type, float cx, float cy, float s, long now) {
        float t = (now - t0) / 1000f;
        boolean a = anim;
        c.save();
        c.translate(cx - 42f * s, cy - 42f * s);
        c.scale(s, s);
        p.setShader(null);

        boolean sun = type == WidgetData.W_SUN || type == WidgetData.W_PARTLY;
        boolean cloud = type != WidgetData.W_SUN;
        float sunX = type == WidgetData.W_SUN ? 42f : 28f, sunY = type == WidgetData.W_SUN ? 42f : 27f;
        float sunR = type == WidgetData.W_SUN ? 15f : 11f;
        if (sun) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.GOLD);
            c.drawCircle(sunX, sunY, sunR, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.4f);
            p.setStrokeCap(Paint.Cap.ROUND);
            float rot = a ? (t * 25f) % 360f : 0f;
            c.save();
            c.rotate(rot, sunX, sunY);
            for (int i = 0; i < 8; i++) {
                c.drawLine(sunX, sunY - sunR - 5f, sunX, sunY - sunR - 9f, p);
                c.rotate(45f, sunX, sunY);
            }
            c.restore();
        }
        if (cloud) {
            float drift = a ? (float) Math.sin(t * 1.0f) * 2.5f : 0f;
            c.save();
            c.translate(drift, type == WidgetData.W_CLOUD ? -4f : 0f);
            p.setStyle(Paint.Style.FILL);
            setUnit(gCloud, 0f, 36f, 1f, 26f);
            p.setShader(gCloud);
            c.drawCircle(31f, 49f, 11f, p);
            c.drawCircle(45f, 43f, 15f, p);
            c.drawCircle(56f, 52f, 9.5f, p);
            r.set(31f, 49f, 56f, 60.5f);
            c.drawRect(r, p);
            p.setShader(null);
            c.restore();
        }
        if (type == WidgetData.W_RAIN || type == WidgetData.W_STORM) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.4f);
            p.setStrokeCap(Paint.Cap.ROUND);
            if (type == WidgetData.W_RAIN) {
                for (int i = 0; i < 3; i++) {
                    float ph = a ? ((t / 1.1f) + i * 0.33f) % 1f : 0.4f + i * 0.2f;
                    float yy = 64f + ph * 14f - 4f;
                    float al = (float) Math.sin(Math.PI * ph);
                    p.setColor(Ui.alpha(Ui.WHITE, 0.9f * al));
                    float xx = 38f + i * 9f - ph * 2f;
                    c.drawLine(xx, yy, xx - 3f, yy + 8f, p);
                }
            } else {
                float flash = a ? (0.6f + 0.4f * (float) Math.sin(t * 6f)) : 1f;
                path.reset();
                path.moveTo(46f, 58f); path.lineTo(38f, 70f); path.lineTo(44f, 70f);
                path.lineTo(40f, 80f); path.lineTo(52f, 66f); path.lineTo(46f, 66f); path.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(Ui.alpha(Ui.GOLD, flash));
                c.drawPath(path, p);
            }
        }
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL);
        c.restore();
    }

    private String fmt1(double v) {
        return String.format(Locale.US, "%.1f", v).replace('.', ',');
    }

    private String fmtVnd(int v) {
        String s = String.valueOf(Math.abs(v));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) sb.append('.');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    private void chip(Canvas c, float right, float cy, boolean up, String txt) {
        float tw = textW(txt, Ui.bold, 11.5f, 0f);
        float w = 10f + 10f + 4f + tw + 6f, h = 20f;
        float x = right - w;
        int col = up ? Ui.GOLD : 0xFFFF6B70;
        r.set(x, cy - h / 2f, right, cy + h / 2f);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.alpha(col, 0.18f));
        c.drawRoundRect(r, h / 2f, h / 2f, p);
        triangle(c, x + 11f, cy, 3.6f, up, col);
        text(c, txt, x + 20f, cy + 4.2f, Ui.bold, 11.5f, col, Paint.Align.LEFT, 0f);
    }

    private void priceRow(Canvas c, float x, float right, float y, String label, String value, String unit,
                          boolean hasDelta, boolean up, String delta) {
        text(c, label, x, y, Ui.regular, 12f, 0xA3FFFFFF, Paint.Align.LEFT, 0f);
        text(c, value, x, y + 24f, Ui.bold, 21f, Ui.WHITE, Paint.Align.LEFT, 0f);
        float vw = textW(value, Ui.bold, 21f, 0f);
        text(c, unit, x + vw + 4f, y + 24f, Ui.semi, 11.5f, 0xA3FFFFFF, Paint.Align.LEFT, 0f);
        if (hasDelta) chip(c, right, y + 14f, up, delta);
    }

    private void drawGold(Canvas c) {
        float x = 338f, y = 290f, w = 200f, h = 196f;
        glass(c, x, y, w, h, 24f, true);
        text(c, "GIÁ VÀNG", x + 18f, y + 34f, Ui.bold, 12f, Ui.GOLD, Paint.Align.LEFT, 0.16f);
        if (wd == null || !wd.goldOk) {
            text(c, "Chưa có dữ liệu", x + w / 2f, y + 106f, Ui.semi, 15f, 0xCCFFFFFF, Paint.Align.CENTER, 0f);
            text(c, "Sẽ thử lại sau", x + w / 2f, y + 130f, Ui.regular, 12f, 0x99FFFFFF, Paint.Align.CENTER, 0f);
            return;
        }
        float right = x + w - 14f;
        boolean hasD = !Double.isNaN(wd.sjcDelta) && Math.abs(wd.sjcDelta) > 1e-6;
        priceRow(c, x + 18f, right, y + 62f, "SJC miếng · bán", fmt1(wd.sjcSell), "tr/lượng",
                hasD, wd.sjcDelta > 0, hasD ? fmt1(Math.abs(wd.sjcDelta)) : "");
        if (!Double.isNaN(wd.ringSell)) {
            priceRow(c, x + 18f, right, y + 118f, "Nhẫn 9999 · bán", fmt1(wd.ringSell), "tr/lượng", false, true, "");
        } else if (!Double.isNaN(wd.sjcBuy)) {
            priceRow(c, x + 18f, right, y + 118f, "SJC miếng · mua", fmt1(wd.sjcBuy), "tr/lượng", false, true, "");
        }
        text(c, wd.goldTime.length() > 0 ? "Cập nhật " + wd.goldTime : "", x + 18f, y + h - 14f,
                Ui.regular, 11.5f, 0x8CFFFFFF, Paint.Align.LEFT, 0f);
    }

    private void drawFuel(Canvas c) {
        float x = 552f, y = 290f, w = 200f, h = 196f;
        glass(c, x, y, w, h, 24f, true);
        text(c, "GIÁ XĂNG", x + 18f, y + 34f, Ui.bold, 12f, Ui.GOLD, Paint.Align.LEFT, 0.16f);
        if (wd == null || !wd.fuelOk) {
            text(c, "Chưa có dữ liệu", x + w / 2f, y + 106f, Ui.semi, 15f, 0xCCFFFFFF, Paint.Align.CENTER, 0f);
            text(c, "Sẽ thử lại sau", x + w / 2f, y + 130f, Ui.regular, 12f, 0x99FFFFFF, Paint.Align.CENTER, 0f);
            return;
        }
        float right = x + w - 14f;
        if (wd.ron95 > 0) {
            priceRow(c, x + 18f, right, y + 62f, "RON 95-III", fmtVnd(wd.ron95), "đ/lít",
                    wd.ron95Delta != 0, wd.ron95Delta > 0, wd.ron95Delta != 0 ? fmtVnd(wd.ron95Delta) : "");
        }
        if (wd.e5 > 0) {
            priceRow(c, x + 18f, right, y + 118f, "E5 RON 92", fmtVnd(wd.e5), "đ/lít",
                    wd.e5Delta != 0, wd.e5Delta > 0, wd.e5Delta != 0 ? fmtVnd(wd.e5Delta) : "");
        }
        text(c, fit(wd.fuelNote, Ui.regular, 11.5f, w - 30f), x + 18f, y + h - 14f, Ui.regular, 11.5f, 0x8CFFFFFF, Paint.Align.LEFT, 0f);
    }

    // ---------------------------------------------------------------- Dock

    private void drawDock(Canvas c) {
        int n = dockCount();
        float x0 = 56f, top = 572f, hgt = 120f;
        float contentW = 36f + 64f * n + 12f * (n - 1) + 13f + 34f;
        glass(c, x0, top, contentW, hgt, 32f, true);

        float x = x0 + 18f;
        float baseY = top + hgt - 8f - 10f;   // đáy ô icon
        int focused = (overlay == OV_NONE && zone == ZONE_DOCK) ? dockIdx : -1;
        float focusX = 0f, focusTop = 0f;
        float focusW = 0f;
        int pcount = pinned.size();
        for (int i = 0; i < n; i++) {
            float size = dockSize[i];
            float lift = Math.max(0f, Math.min(1f, (size - 64f) / 24f)) * 8f;
            float ty = baseY - lift - size;
            r.set(x, ty, x + size, ty + size);
            tile.set(r);
            if (i < pcount) drawAppTile(c, tile, pinned.get(i), size, i == focused);
            else drawSpecialTile(c, tile, size, i == focused, i == pcount ? 0 : 1);
            // chấm báo ứng dụng vừa mở
            if (i < pcount && isRecent(pinned.get(i))) {
                p.setShader(null);
                p.setStyle(Paint.Style.FILL);
                p.setColor(i == focused ? Ui.GOLD : 0xBFFFFFFF);
                c.drawCircle(x + size / 2f, baseY + 7f, 2.6f, p);
            }
            if (i == focused) { focusX = x + size / 2f; focusTop = ty; focusW = size; }
            x += size + 12f;
            if (i == pcount - 1) {      // vạch ngăn trước hai nút cuối
                setUnit(gDiv, 0f, top + 30f, 1f, hgt - 60f);
                p.setShader(gDiv);
                p.setStyle(Paint.Style.FILL);
                c.drawRect(x - 2f, top + 30f, x - 1f, top + hgt - 30f, p);
                p.setShader(null);
                x += 6f;
            }
        }
        if (focused >= 0) {
            String lab = focused < pcount ? pinned.get(focused).label : (focused == pcount ? "Tất cả ứng dụng" : "Cài đặt");
            lab = fit(lab, Ui.bold, 13f, 220f);
            float tw = textW(lab, Ui.bold, 13f, 0f) + 28f;
            float tx = focusX - tw / 2f;
            tx = Math.max(56f, tx);
            glass(c, tx, focusTop - 44f, tw, 30f, 15f, true);
            text(c, lab, tx + tw / 2f, focusTop - 24f, Ui.bold, 13f, Ui.WHITE, Paint.Align.CENTER, 0f);
        }
    }

    private boolean isRecent(AppEntry e) {
        List<String> rc = Prefs.recent(ctx);
        return rc.size() > 0 && (rc.get(0).equals(e.key) || (rc.size() > 1 && rc.get(1).equals(e.key)));
    }

    private void drawAppTile(Canvas c, RectF t, AppEntry e, float size, boolean focused) {
        float rad = size * 0.26f;
        // bóng
        softShadow(c, t.left, t.top, t.width(), t.height(), rad, focused ? 10f : 4f, focused ? 5 : 3, focused ? 0.6f : 0.4f);
        Bitmap bm = icons.get(e);
        if (bm != null) {
            c.drawBitmap(bm, null, t, bp);
        } else {
            setUnit(gPanel, t.left, t.top, t.width(), t.height());
            p.setShader(gPanel);
            c.drawRoundRect(t, rad, rad, p);
            p.setShader(null);
            String ch = e.label.length() > 0 ? e.label.substring(0, 1).toUpperCase(Locale.getDefault()) : "?";
            text(c, ch, t.centerX(), t.centerY() + size * 0.17f, Ui.bold, size * 0.5f, Ui.GOLD, Paint.Align.CENTER, 0f);
        }
        // phản quang nhẹ phía trên
        p.setStyle(Paint.Style.FILL);
        setUnit(gSheen, 0f, t.top, 1f, t.height() * 0.5f);
        p.setShader(gSheen);
        c.drawRoundRect(t, rad, rad, p);
        p.setShader(null);
        if (focused) {
            r2.set(t); r2.inset(-2f, -2f);
            glow(c, r2, rad + 2f, 1f);
        }
    }

    /** Ô "Tất cả ứng dụng" (lưới) và "Cài đặt" (bánh răng), vẽ bằng nét vector. */
    private void drawSpecialTile(Canvas c, RectF t, float size, boolean focused, int kind) {
        float rad = size * 0.26f;
        softShadow(c, t.left, t.top, t.width(), t.height(), rad, focused ? 10f : 4f, focused ? 5 : 3, focused ? 0.6f : 0.4f);
        if (focused) {
            setUnit(gGoldTile, 0f, t.top, 1f, t.height());
            p.setShader(gGoldTile);
        } else {
            setUnit(gPanel, t.left, t.top, t.width(), t.height());
            p.setShader(gPanel);
        }
        c.drawRoundRect(t, rad, rad, p);
        p.setShader(null);
        if (!focused) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1f);
            p.setColor(0x38FFFFFF);
            r2.set(t); r2.inset(0.5f, 0.5f);
            c.drawRoundRect(r2, rad, rad, p);
            p.setStyle(Paint.Style.FILL);
            setUnit(gSheen, 0f, t.top, 1f, t.height() * 0.5f);
            p.setShader(gSheen);
            c.drawRoundRect(t, rad, rad, p);
            p.setShader(null);
        }
        // biểu tượng trong hệ tọa độ 32x32
        float g = size * 0.5f / 32f;
        c.save();
        c.translate(t.centerX() - 16f * g, t.centerY() - 16f * g);
        c.scale(g, g);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(focused ? 0xFF140A00 : Ui.GOLD);
        if (kind == 0) {
            float[][] sq = {{5, 5}, {18, 5}, {5, 18}, {18, 18}};
            for (float[] q : sq) { r.set(q[0], q[1], q[0] + 9f, q[1] + 9f); c.drawRoundRect(r, 2f, 2f, p); }
        } else {
            c.drawCircle(16f, 16f, 4.5f, p);
            c.drawCircle(16f, 16f, 9.5f, p);
            for (int i = 0; i < 8; i++) {
                c.save();
                c.rotate(45f * i, 16f, 16f);
                c.drawLine(16f, 3f, 16f, 6.5f, p);
                c.restore();
            }
        }
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL);
        c.restore();
        if (focused) {
            r2.set(t); r2.inset(-2f, -2f);
            glow(c, r2, rad + 2f, 1f);
        }
    }

    // ---------------------------------------------------------------- lớp phủ: tất cả ứng dụng

    private static final int COLS = 8;
    private static final int MENU_VIS = 8;

    private void drawAllApps(Canvas c) {
        float a = ovAlpha;
        float px = 56f, py = 62f, pw = 1168f, ph = 596f;
        glass(c, px, py, pw, ph, 30f, true);
        List<AppEntry> list = visibleApps();
        text(c, "Tất cả ứng dụng", px + 32f, py + 50f, Ui.bold, 22f, Ui.alpha(Ui.WHITE, a), Paint.Align.LEFT, 0f);
        text(c, list.size() + " ứng dụng", px + pw - 32f, py + 48f, Ui.regular, 13f, 0x99FFFFFF, Paint.Align.RIGHT, 0f);
        if (list.isEmpty()) {
            text(c, "Chưa có ứng dụng nào để hiển thị", px + pw / 2f, py + ph / 2f, Ui.semi, 16f, 0xCCFFFFFF, Paint.Align.CENTER, 0f);
            return;
        }
        float cellW = 136f, cellH = 124f;
        float gx = px + (pw - COLS * cellW) / 2f, gy = py + 82f;
        int rows = (list.size() + COLS - 1) / COLS;
        int visRows = 4;
        c.save();
        c.clipRect(px + 8f, gy - 12f, px + pw - 8f, gy + visRows * cellH + 6f);
        for (int i = 0; i < list.size(); i++) {
            int row = i / COLS, col = i % COLS;
            if (row < appsScroll - 1 || row > appsScroll + visRows) continue;
            float cx = gx + col * cellW + cellW / 2f;
            float cy = gy + (row - appsScroll) * cellH;
            boolean f = i == appsIdx;
            float size = f ? 80f : 72f;
            r.set(cx - size / 2f, cy + 10f - (f ? 4f : 0f), cx + size / 2f, cy + 10f - (f ? 4f : 0f) + size);
            tile.set(r);
            AppEntry e = list.get(i);
            drawAppTile(c, tile, e, size, f);
            String lab = fit(e.label, f ? Ui.bold : Ui.semi, 12.5f, cellW - 16f);
            text(c, lab, cx, cy + 10f + 88f, f ? Ui.bold : Ui.semi, 12.5f, f ? Ui.GOLD : 0xE6FFFFFF, Paint.Align.CENTER, 0f);
        }
        c.restore();
        if (rows > visRows) {
            float trackH = visRows * cellH - 10f;
            float thumbH = Math.max(30f, trackH * visRows / rows);
            float ty = gy + (trackH - thumbH) * appsScroll / Math.max(1, rows - visRows);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x40FFFFFF);
            r.set(px + pw - 20f, ty, px + pw - 15f, ty + thumbH);
            c.drawRoundRect(r, 2.5f, 2.5f, p);
        }
        text(c, "OK: mở   ·   Giữ OK: tùy chọn   ·   Back: đóng", px + pw / 2f, py + ph - 18f, Ui.regular, 12.5f, 0x99FFFFFF, Paint.Align.CENTER, 0f);
    }

    // ---------------------------------------------------------------- lớp phủ: menu kính

    private void drawMenu(Canvas c) {
        float rowH = 56f;
        int n = menuItems.size();
        int vis = Math.min(n, MENU_VIS);
        float w = 560f, h = 78f + vis * rowH + 18f;
        float x = (W - w) / 2f, y = Math.max(54f, (H - h) / 2f - 10f);
        glass(c, x, y, w, h, 28f, true);
        text(c, menuTitle, x + 28f, y + 50f, Ui.bold, 21f, Ui.WHITE, Paint.Align.LEFT, 0f);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x29FFFFFF);
        c.drawRect(x + 1f, y + 64f, x + w - 1f, y + 65f, p);
        if (menuScroll > 0) triangle(c, x + w - 34f, y + 38f, 6f, true, 0xA6FFFFFF);
        if (menuScroll + vis < n) triangle(c, x + w - 34f, y + h - 12f, 6f, false, 0xA6FFFFFF);
        for (int i = menuScroll; i < Math.min(n, menuScroll + vis); i++) {
            MenuItemSpec m = menuItems.get(i);
            float ry = y + 74f + (i - menuScroll) * rowH;
            boolean f = i == menuIdx;
            r.set(x + 12f, ry, x + w - 12f, ry + rowH - 4f);
            if (f) {
                p.setColor(Ui.GOLD);
                c.drawRoundRect(r, 16f, 16f, p);
            }
            int fg = f ? Ui.BLACK : (m.type == MenuItemSpec.DANGER ? 0xFFFF8A8E : Ui.WHITE);
            Typeface tf = f ? Ui.bold : Ui.semi;
            text(c, fit(m.label, tf, 16f, w - 190f), x + 32f, ry + 34f, tf, 16f, fg, Paint.Align.LEFT, 0f);
            if (m.type == MenuItemSpec.TOGGLE) {
                float sx = x + w - 28f - 50f, sy = ry + 12f;
                r2.set(sx, sy, sx + 50f, sy + 28f);
                p.setColor(m.on ? (f ? Ui.BLACK : Ui.GOLD) : 0x33FFFFFF);
                c.drawRoundRect(r2, 14f, 14f, p);
                p.setColor(m.on ? (f ? Ui.GOLD : Ui.WHITE) : Ui.WHITE);
                c.drawCircle(m.on ? sx + 50f - 14f : sx + 14f, sy + 14f, 10f, p);
            } else if (m.type == MenuItemSpec.VALUE && m.value != null) {
                text(c, fit(m.value, Ui.semi, 14f, 150f), x + w - 32f, ry + 33f, Ui.semi, 14f,
                        f ? Ui.BLACK : Ui.GOLD, Paint.Align.RIGHT, 0f);
            }
        }
    }

    // ---------------------------------------------------------------- điều khiển

    @Override
    public boolean onKeyDown(int code, KeyEvent ev) {
        boolean center = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
        if (center) {
            ev.startTracking();
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SETTINGS) {
            if (host != null) host.openSettings();
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (overlay != OV_NONE) closeOverlay();
            return true;     // launcher không thoát
        }
        int dx = 0, dy = 0;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) dx = -1;
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) dx = 1;
        else if (code == KeyEvent.KEYCODE_DPAD_UP) dy = -1;
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN) dy = 1;
        else return super.onKeyDown(code, ev);
        move(dx, dy);
        invalidate();
        return true;
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent ev) {
        boolean center = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
        if (center && ev.isTracking() && !ev.isCanceled()) {
            activate();
            invalidate();
            return true;
        }
        return super.onKeyUp(code, ev);
    }

    @Override
    public boolean onKeyLongPress(int code, KeyEvent ev) {
        boolean center = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
        if (!center || host == null) return false;
        if (overlay == OV_APPS) {
            List<AppEntry> l = visibleApps();
            if (appsIdx >= 0 && appsIdx < l.size()) host.openAppMenu(l.get(appsIdx));
            return true;
        }
        if (overlay == OV_NONE && zone == ZONE_DOCK && dockIdx < pinned.size()) {
            host.openAppMenu(pinned.get(dockIdx));
            return true;
        }
        return false;
    }

    private void move(int dx, int dy) {
        if (overlay == OV_MENU) {
            if (dy != 0) {
                menuIdx = Math.max(0, Math.min(menuItems.size() - 1, menuIdx + dy));
                if (menuIdx < menuScroll) menuScroll = menuIdx;
                if (menuIdx >= menuScroll + MENU_VIS) menuScroll = menuIdx - MENU_VIS + 1;
            }
            return;
        }
        if (overlay == OV_APPS) {
            int n = visibleApps().size();
            if (n == 0) return;
            int i = appsIdx + dx + dy * COLS;
            if (dy != 0 && (i < 0 || i >= n)) i = appsIdx;       // không nhảy ra ngoài
            i = Math.max(0, Math.min(n - 1, i));
            appsIdx = i;
            int row = i / COLS;
            if (row < appsScroll) appsScroll = row;
            if (row > appsScroll + 3) appsScroll = row - 3;
            return;
        }
        if (zone == ZONE_HERO) {
            if (dx != 0) heroIdx = Math.max(0, Math.min(1, heroIdx + dx));
            if (dy > 0) { zone = ZONE_DOCK; dockIdx = Math.min(dockIdx, dockCount() - 1); }
        } else {
            if (dx != 0) dockIdx = Math.max(0, Math.min(dockCount() - 1, dockIdx + dx));
            if (dy < 0) { zone = ZONE_HERO; heroIdx = dockIdx > 1 ? 1 : 0; }
        }
    }

    private void activate() {
        if (overlay == OV_MENU) {
            if (menuIdx >= 0 && menuIdx < menuItems.size()) {
                MenuItemSpec m = menuItems.get(menuIdx);
                if (m.type == MenuItemSpec.INFO) return;
                if (m.action != null) m.action.run();
            }
            return;
        }
        if (overlay == OV_APPS) {
            List<AppEntry> l = visibleApps();
            if (appsIdx >= 0 && appsIdx < l.size()) {
                closeOverlay();
                launch(l.get(appsIdx));
            }
            return;
        }
        if (zone == ZONE_HERO) {
            if (heroIdx == 0) {
                AppEntry e = continueApp();
                if (e != null) launch(e);
            } else {
                openAllApps();
            }
            return;
        }
        if (dockIdx < pinned.size()) launch(pinned.get(dockIdx));
        else if (dockIdx == pinned.size()) openAllApps();
        else if (host != null) host.openSettings();
    }

    void openAllApps() {
        overlay = OV_APPS;
        appsIdx = 0;
        appsScroll = 0;
        invalidate();
    }

    void launch(AppEntry e) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setComponent(e.cn);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
            Prefs.pushRecent(ctx, e.key);
            refreshPinned();
        } catch (Exception ex) {
            Toast.makeText(ctx, "Không mở được " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    /** Bấm Home khi đang ở launcher: về nút đầu tiên của Dock. */
    void goHome() {
        overlay = OV_NONE;
        zone = ZONE_DOCK;
        dockIdx = 0;
        invalidate();
    }
}
