package vn.forge.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** View dùng chung cho màn Hướng dẫn: nút viên thuốc, vàng khi được chọn. */
final class UiKit {
    private UiKit() {}

    static final int BG     = 0xFF000000;
    static final int PANEL  = 0xFF161618;
    static final int RED    = 0xFFD61F26;
    static final int GOLD   = 0xFFFFC732;
    static final int COPPER = 0xFFC3AA79;
    static final int GLOW   = 0xFFFFC732;
    static final int TEXT   = 0xFFFFFFFF;
    static final int MUTED  = 0xFFA8A29A;

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView button(Context c, String text) {
        final TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(TEXT);
        t.setTextSize(14);
        t.setTypeface(Ui.bold != null ? Ui.bold : Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 22), dp(c, 11), dp(c, 22), dp(c, 11));
        t.setFocusable(true);
        t.setClickable(true);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0x24FFFFFF);
        normal.setCornerRadius(dp(c, 40));
        normal.setStroke(dp(c, 1), 0x47FFFFFF);

        GradientDrawable focused = new GradientDrawable();
        focused.setColor(GOLD);
        focused.setCornerRadius(dp(c, 40));

        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, focused);
        sl.addState(new int[]{}, normal);
        t.setBackground(sl);

        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                t.setTextColor(f ? Color.BLACK : TEXT);
                v.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
            }
        });
        return t;
    }

    static TextView label(Context c, String text, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setTypeface(bold ? (Ui.bold != null ? Ui.bold : Typeface.DEFAULT_BOLD)
                : (Ui.regular != null ? Ui.regular : Typeface.DEFAULT));
        return t;
    }
}
