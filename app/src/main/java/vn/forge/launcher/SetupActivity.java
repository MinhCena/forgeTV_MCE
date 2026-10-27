package vn.forge.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Hướng dẫn đặt FORGE TV làm launcher mặc định, kể cả khi box khóa ADB. */
public class SetupActivity extends Activity {
    private LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.loadFonts(this);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(UiKit.BG);
        sv.setFillViewport(true);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = UiKit.dp(this, 48);
        box.setPadding(pad, UiKit.dp(this, 28), pad, UiKit.dp(this, 40));
        sv.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        box.removeAllViews();
        box.addView(UiKit.label(this, "FORGE TV – THIẾT LẬP", 24, UiKit.GLOW, true));
        box.addView(UiKit.label(this,
                "Làm theo thứ tự. Mọi bước đều không cần máy tính. Launcher này không mở khóa gói cước hay hợp đồng của FPT.",
                13, UiKit.MUTED, false), lp(0, 6, 0, 18));

        // Bước 1
        box.addView(step("1. Đặt FORGE TV làm launcher mặc định"));
        box.addView(UiKit.label(this,
                "Bấm nút bên dưới. Nếu màn hình không mở, bấm nút Home trên remote rồi chọn FORGE TV → Luôn luôn (Always).",
                13, UiKit.TEXT, false), lp(0, 2, 0, 8));
        TextView b1 = UiKit.button(this, "Chọn launcher mặc định");
        b1.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                open(new Intent(Settings.ACTION_HOME_SETTINGS),
                        "Máy không có màn hình này. Hãy bấm Home trên remote và chọn FORGE TV → Luôn luôn.");
            }
        });
        box.addView(b1, lp(0, 0, 0, 18));

        // Bước 2
        boolean on = guardOn(this);
        box.addView(step("2. Bảo vệ nút Home (thay cho ADB)  –  " + (on ? "ĐANG BẬT" : "CHƯA BẬT")));
        box.addView(UiKit.label(this,
                "Bật FORGE TV trong Trợ năng (Accessibility). Mỗi khi launcher gốc của FPT hiện lên, FORGE TV sẽ đưa bạn về lại. "
                        + "Nếu muốn vào launcher gốc (xem gói cước...), dùng Cài đặt → \"Mở launcher gốc\".",
                13, UiKit.TEXT, false), lp(0, 2, 0, 8));
        TextView b2 = UiKit.button(this, "Mở cài đặt Trợ năng");
        b2.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "Không mở được Trợ năng trên box này.");
            }
        });
        box.addView(b2, lp(0, 0, 0, 18));

        // Bước 3
        box.addView(step("3. Launcher khác đang cài trên box"));
        List<ResolveInfo> others = Homes.others(this);
        if (others.isEmpty()) {
            box.addView(UiKit.label(this, "Không thấy launcher nào khác.", 13, UiKit.MUTED, false), lp(0, 2, 0, 18));
        }
        for (final ResolveInfo ri : others) {
            final String pkg = ri.activityInfo.packageName;
            CharSequence name = ri.loadLabel(getPackageManager());
            box.addView(UiKit.label(this, name + "  (" + pkg + ")", 14, UiKit.GLOW, true), lp(0, 8, 0, 2));
            TextView cmd = UiKit.label(this, "adb shell pm disable-user --user 0 " + pkg, 12, UiKit.MUTED, false);
            cmd.setTypeface(Typeface.MONOSPACE);
            box.addView(cmd, lp(0, 0, 0, 6));
            TextView bi = UiKit.button(this, "Mở thông tin app này (thử Tắt / Xóa mặc định)");
            bi.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)),
                            "Không mở được thông tin app.");
                }
            });
            box.addView(bi, lp(0, 0, 0, 6));
        }
        box.addView(UiKit.label(this,
                "Dòng lệnh adb ở trên chỉ dùng được nếu bạn bật được gỡ lỗi ADB. Trên nhiều box FPT, nút Tắt trong thông tin app bị mờ.",
                12, UiKit.MUTED, false), lp(0, 4, 0, 18));

        // Bước 4
        box.addView(step("4. Thử mở Tùy chọn nhà phát triển (nếu bị ẩn)"));
        TextView b4 = UiKit.button(this, "Mở Tùy chọn nhà phát triển");
        b4.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                open(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                        "Box này chặn màn hình đó. Thử Cài đặt → Giới thiệu → nhấn 7 lần vào Số bản dựng.");
            }
        });
        box.addView(b4, lp(0, 8, 0, 8));
        TextView b5 = UiKit.button(this, "Mở màn hình Giới thiệu");
        b5.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                open(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS), "Không mở được màn hình Giới thiệu.");
            }
        });
        box.addView(b5, lp(0, 0, 0, 18));

        TextView back = UiKit.button(this, "Xong – về màn hình chính");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        box.addView(back, lp(0, 8, 0, 0));
        back.requestFocus();
    }

    static boolean guardOn(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName()) && s.contains("HomeGuardService");
    }

    private TextView step(String t) {
        TextView v = UiKit.label(this, t, 16, UiKit.COPPER, true);
        return v;
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(UiKit.dp(this, l), UiKit.dp(this, t), UiKit.dp(this, r), UiKit.dp(this, b));
        p.gravity = Gravity.START;
        return p;
    }

    private void open(Intent i, String err) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException ex) {
            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
        }
    }
}
