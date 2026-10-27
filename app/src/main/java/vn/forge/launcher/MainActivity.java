package vn.forge.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Màn hình chính FORGE TV – thay thế launcher của FPT Play Box. */
public class MainActivity extends Activity {
    private static boolean sAutostartDone;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private HudBackgroundView bg;
    private EmblemView emblem;
    private GridView grid;
    private TextView clock, date, empty;
    private final List<AppEntry> all = new ArrayList<>();
    private final List<AppEntry> shown = new ArrayList<>();
    private final Map<String, Boolean> favSet = new HashMap<>();
    private TileAdapter adapter;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateClock();
            ui.postDelayed(this, 15_000L);
        }
    };

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { reload(); }
    };

    // ---------------------------------------------------------------- vòng đời

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        applyAnimFlag();
        reload();
        maybeAutostart(b == null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
    }

    @Override
    protected void onStop() {
        try { unregisterReceiver(pkgReceiver); } catch (IllegalArgumentException ignored) { }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyAnimFlag();
        ui.removeCallbacks(tick);
        ui.post(tick);
        if (grid != null && !grid.hasFocus()) grid.requestFocus();
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Bấm Home khi đang ở launcher: về ô đầu tiên
        if (grid != null && shown.size() > 0) {
            grid.setSelection(0);
            grid.requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        // launcher không thoát
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            showSettings();
            return true;
        }
        return super.onKeyDown(keyCode, ev);
    }

    // ---------------------------------------------------------------- giao diện

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(UiKit.BG);
        root.setClipChildren(false);

        bg = new HudBackgroundView(this);
        root.addView(bg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(UiKit.dp(this, 48), UiKit.dp(this, 24), UiKit.dp(this, 48), UiKit.dp(this, 8));
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);

        emblem = new EmblemView(this);
        header.addView(emblem);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(UiKit.dp(this, 12), 0, 0, 0);
        TextView title = new TextView(this);
        SpannableStringBuilder sb = new SpannableStringBuilder("FORGE TV");
        sb.setSpan(new ForegroundColorSpan(UiKit.GLOW), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(UiKit.RED_HI), 5, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(sb);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.14f);
        titleBox.addView(title);
        TextView sub = UiKit.label(this, "DARK MODE  •  ĐỎ · ĐEN · ĐỒNG", 10, UiKit.MUTED, false);
        sub.setLetterSpacing(0.2f);
        titleBox.addView(sub);
        header.addView(titleBox);

        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView btnSettings = UiKit.button(this, "CÀI ĐẶT");
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSettings(); }
        });
        header.addView(btnSettings);

        TextView btnGuide = UiKit.button(this, "HƯỚNG DẪN");
        btnGuide.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, SetupActivity.class)); }
        });
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.leftMargin = UiKit.dp(this, 10);
        header.addView(btnGuide, gp);

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.END);
        clockBox.setPadding(UiKit.dp(this, 24), 0, 0, 0);
        clock = UiKit.label(this, "--:--", 26, UiKit.TEXT, true);
        clock.setGravity(Gravity.END);
        date = UiKit.label(this, "", 11, UiKit.COPPER, false);
        date.setGravity(Gravity.END);
        clockBox.addView(clock);
        clockBox.addView(date);
        header.addView(clockBox);

        col.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // đường kẻ vàng đồng
        View line = new View(this);
        line.setBackgroundColor(UiKit.alpha(UiKit.COPPER, 120));
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 1));
        ll.topMargin = UiKit.dp(this, 12);
        col.addView(line, ll);

        // ---- lưới ứng dụng
        float widthDp = getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density;
        int cols = Math.max(4, Math.min(7, (int) ((widthDp - 96f) / 168f)));

        grid = new GridView(this);
        grid.setNumColumns(cols);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(UiKit.dp(this, 14));
        grid.setVerticalSpacing(UiKit.dp(this, 14));
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 22), UiKit.dp(this, 10), UiKit.dp(this, 28));
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setVerticalScrollBarEnabled(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setFocusable(true);
        grid.setDrawSelectorOnTop(false);
        adapter = new TileAdapter();
        grid.setAdapter(adapter);

        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos >= 0 && pos < shown.size()) launch(shown.get(pos));
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos >= 0 && pos < shown.size()) showAppMenu(shown.get(pos));
                return true;
            }
        });
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { refreshTiles(); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        grid.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) { refreshTiles(); }
        });

        col.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        empty = UiKit.label(this, "Đang tải ứng dụng…", 16, UiKit.MUTED, false);
        empty.setGravity(Gravity.CENTER);
        root.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    private void refreshTiles() {
        for (int i = 0; i < grid.getChildCount(); i++) {
            View c = grid.getChildAt(i);
            if (c instanceof AppTileView) ((AppTileView) c).refreshActive();
        }
    }

    private void applyAnimFlag() {
        boolean a = Prefs.anim(this);
        if (bg != null) bg.setAnimated(a);
        if (emblem != null) emblem.setAnimated(a);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void updateClock() {
        boolean h24 = Prefs.clock24(this);
        Date now = new Date();
        clock.setText(new SimpleDateFormat(h24 ? "HH:mm" : "hh:mm a", Locale.getDefault()).format(now));
        date.setText(new SimpleDateFormat("EEEE, dd/MM/yyyy", new Locale("vi", "VN")).format(now).toUpperCase(new Locale("vi", "VN")));
    }

    // ---------------------------------------------------------------- danh sách app

    private void reload() {
        io.execute(new Runnable() {
            @Override public void run() {
                final List<AppEntry> list = loadApps();
                ui.post(new Runnable() {
                    @Override public void run() {
                        all.clear();
                        all.addAll(list);
                        rebuildShown();
                    }
                });
            }
        });
    }

    private List<AppEntry> loadApps() {
        PackageManager pm = getPackageManager();
        Map<String, AppEntry> map = new LinkedHashMap<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            List<ResolveInfo> l = pm.queryIntentActivities(q, 0);
            for (ResolveInfo ri : l) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                ComponentName cn = new ComponentName(pkg, ri.activityInfo.name);
                String key = cn.flattenToString();
                if (map.containsKey(key)) continue;
                Drawable banner = null;
                try { banner = ri.activityInfo.loadBanner(pm); } catch (Exception ignored) { }
                Drawable img = banner != null ? banner : ri.loadIcon(pm);
                map.put(key, new AppEntry(key, String.valueOf(ri.loadLabel(pm)), pkg, cn, img, banner != null));
            }
        }
        List<AppEntry> out = new ArrayList<>(map.values());
        Collections.sort(out, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) { return a.label.compareToIgnoreCase(b.label); }
        });
        return out;
    }

    private void rebuildShown() {
        Set<String> hidden = Prefs.hidden(this);
        List<String> favs = Prefs.favs(this);
        favSet.clear();
        for (String f : favs) favSet.put(f, Boolean.TRUE);

        shown.clear();
        Map<String, AppEntry> byKey = new HashMap<>();
        for (AppEntry e : all) byKey.put(e.key, e);
        for (String f : favs) {
            AppEntry e = byKey.get(f);
            if (e != null && !hidden.contains(e.key)) shown.add(e);
        }
        for (AppEntry e : all) {
            if (hidden.contains(e.key) || favSet.containsKey(e.key)) continue;
            shown.add(e);
        }
        adapter.notifyDataSetChanged();
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        if (shown.isEmpty()) empty.setText("Chưa có ứng dụng nào để hiển thị");
        if (!grid.hasFocus()) grid.requestFocus();
    }

    private void launch(AppEntry e) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setComponent(e.cn);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(this, "Không mở được " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void maybeAutostart(boolean fresh) {
        if (!fresh || sAutostartDone) return;
        sAutostartDone = true;
        final String key = Prefs.autostart(this);
        if (key == null) return;
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                for (AppEntry e : all) if (e.key.equals(key)) { launch(e); return; }
            }
        }, 1500L);
    }

    // ---------------------------------------------------------------- menu

    private void showAppMenu(final AppEntry e) {
        final boolean isFav = favSet.containsKey(e.key);
        final boolean isAuto = e.key.equals(Prefs.autostart(this));
        final String[] items = {
                isFav ? "Bỏ ghim" : "Ghim lên đầu",
                "Ẩn khỏi màn hình chính",
                isAuto ? "Tắt tự mở khi bật box" : "Tự mở khi bật box",
                "Thông tin ứng dụng",
                "Gỡ cài đặt"
        };
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(e.label)
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        switch (w) {
                            case 0: toggleFav(e); break;
                            case 1: hide(e); break;
                            case 2:
                                Prefs.setAutostart(MainActivity.this, isAuto ? null : e.key);
                                break;
                            case 3:
                                tryStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", e.pkg, null)));
                                break;
                            default:
                                tryStart(new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", e.pkg, null)));
                        }
                    }
                }).show();
    }

    private void toggleFav(AppEntry e) {
        List<String> f = Prefs.favs(this);
        if (f.contains(e.key)) f.remove(e.key); else f.add(0, e.key);
        Prefs.setFavs(this, f);
        rebuildShown();
    }

    private void hide(AppEntry e) {
        Set<String> h = Prefs.hidden(this);
        h.add(e.key);
        Prefs.setHidden(this, h);
        rebuildShown();
        Toast.makeText(this, "Đã ẩn " + e.label + " (bỏ ẩn trong Cài đặt)", Toast.LENGTH_SHORT).show();
    }

    private void showSettings() {
        final Context c = this;
        String auto = "Tắt";
        String key = Prefs.autostart(c);
        if (key != null) for (AppEntry e : all) if (e.key.equals(key)) auto = e.label;
        final String[] items = {
                "Hiệu ứng động: " + (Prefs.anim(c) ? "BẬT" : "TẮT (nhẹ máy hơn)"),
                "Đồng hồ 24 giờ: " + (Prefs.clock24(c) ? "BẬT" : "TẮT"),
                "Tự mở ứng dụng khi bật box: " + auto,
                "Bảo vệ nút Home: " + (Prefs.guard(c) ? "BẬT" : "TẮT")
                        + (SetupActivity.guardOn(c) ? "" : "  (chưa bật Trợ năng)"),
                "Mở launcher gốc (60 giây)",
                "Bỏ ẩn tất cả ứng dụng (" + Prefs.hidden(c).size() + ")",
                "Hướng dẫn thiết lập"
        };
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("FORGE TV")
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        switch (w) {
                            case 0: Prefs.setAnim(c, !Prefs.anim(c)); applyAnimFlag(); break;
                            case 1: Prefs.setClock24(c, !Prefs.clock24(c)); updateClock(); break;
                            case 2: chooseAutostart(); break;
                            case 3: Prefs.setGuard(c, !Prefs.guard(c)); break;
                            case 4:
                                if (!Homes.openOriginal(c)) {
                                    Toast.makeText(c, "Không tìm thấy launcher gốc", Toast.LENGTH_SHORT).show();
                                }
                                break;
                            case 5:
                                Prefs.setHidden(c, new java.util.HashSet<String>());
                                rebuildShown();
                                break;
                            default:
                                startActivity(new Intent(c, SetupActivity.class));
                        }
                    }
                }).show();
    }

    private void chooseAutostart() {
        final List<AppEntry> list = new ArrayList<>(all);
        String[] names = new String[list.size() + 1];
        names[0] = "Tắt (không tự mở)";
        for (int i = 0; i < list.size(); i++) names[i + 1] = list.get(i).label;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Tự mở khi bật box")
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        Prefs.setAutostart(MainActivity.this, w == 0 ? null : list.get(w - 1).key);
                    }
                }).show();
    }

    private void tryStart(Intent i) {
        try {
            startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(this, "Máy không hỗ trợ thao tác này", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- adapter

    private final class TileAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int pos, View convert, ViewGroup parent) {
            AppTileView v = convert instanceof AppTileView ? (AppTileView) convert : new AppTileView(MainActivity.this);
            AppEntry e = shown.get(pos);
            v.bind(e, favSet.containsKey(e.key), Prefs.anim(MainActivity.this));
            return v;
        }
    }
}
