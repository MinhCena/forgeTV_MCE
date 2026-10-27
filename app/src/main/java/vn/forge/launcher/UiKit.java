package vn.forge.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** Bảng màu đỏ – đen – vàng đồng và các view dùng chung. */
final class UiKit {
    private UiKit() {}

    static final int BG      = 0xFF0A0708;
    static final int PANEL   = 0xFF1A1011;
    static final int RED     = 0xFFB3121C;
    static final int RED_HI  = 0xFFE03A2F;
    static final int GOLD    = 0xFFD4A24C;
    static final int COPPER  = 0xFFC8883A;
    static final int GLOW    = 0xFFFFC857;
    static final int TEXT    = 0xFFF2E8DA;
    static final int MUTED   = 0xFF9A8F84;

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static float sp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().scaledDensity;
    }

    /** Nút có hiệu ứng phát sáng khi chọn bằng remote. */
    static TextView button(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(TEXT);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 18), dp(c, 10), dp(c, 18), dp(c, 10));
        t.setFocusable(true);
        t.setClickable(true);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(PANEL);
        normal.setCornerRadius(dp(c, 10));
        normal.setStroke(dp(c, 1), (COPPER & 0x00FFFFFF) | 0x80000000);

        GradientDrawable focused = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{RED, COPPER});
        focused.setCornerRadius(dp(c, 10));
        focused.setStroke(dp(c, 2), GLOW);

        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, focused);
        sl.addState(new int[]{}, normal);
        t.setBackground(sl);

        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(120).start();
            }
        });
        return t;
    }

    static TextView label(Context c, String text, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static int alpha(int color, int a) {
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }
}
