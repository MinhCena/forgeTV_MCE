package vn.forge.launcher;

import android.content.Context;
import android.graphics.Typeface;

/** Màu, font và hệ số co giãn dùng chung. */
final class Ui {
    private Ui() {}

    static final int BLACK   = 0xFF000000;
    static final int RED     = 0xFFD61F26;
    static final int RED_DK  = 0xFF900405;
    static final int GOLD    = 0xFFFFC732;
    static final int GOLD_HI = 0xFFFFEFB0;
    static final int GOLD_LO = 0xFFD9930A;
    static final int BRONZE  = 0xFFC3AA79;
    static final int WHITE   = 0xFFFFFFFF;

    static Typeface anton, regular, semi, bold;
    private static boolean loaded;

    static synchronized void loadFonts(Context c) {
        if (loaded) return;
        loaded = true;
        anton   = font(c, "Anton-Regular.ttf", Typeface.DEFAULT_BOLD);
        regular = font(c, "BeVietnamPro-Regular.ttf", Typeface.DEFAULT);
        semi    = font(c, "BeVietnamPro-SemiBold.ttf", Typeface.DEFAULT_BOLD);
        bold    = font(c, "BeVietnamPro-Bold.ttf", Typeface.DEFAULT_BOLD);
    }

    private static Typeface font(Context c, String name, Typeface fallback) {
        try {
            return Typeface.createFromAsset(c.getAssets(), "fonts/" + name);
        } catch (Exception e) {
            return fallback;
        }
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static int alpha(int color, float a) {
        int al = Math.max(0, Math.min(255, Math.round(a * 255f)));
        return (al << 24) | (color & 0x00FFFFFF);
    }
}
