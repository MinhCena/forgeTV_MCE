package vn.forge.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Màn hình chính Minh Cena TV: nạp ứng dụng, dữ liệu widget, menu và cài đặt. */
public class MainActivity extends Activity implements HomeView.Host {
    private static final int REQ_POSTER = 41;
    private static boolean sAutostartDone;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private BackdropView backdrop;
    private HomeView home;
    private WidgetData wd;
    private Updater updater;
    private final List<AppEntry> all = new ArrayList<>();

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { reload(); }
    };
    private final Runnable repaint = new Runnable() {
        @Override public void run() { if (home != null) home.invalidate(); }
    };

    // ---------------------------------------------------------------- vòng đời

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.loadFonts(this);
        FrameLayout root = new FrameLayout(this);
        backdrop = new BackdropView(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        home = new HomeView(this);
        home.setHost(this);
        wd = new WidgetData(this);
        home.setWidgetData(wd);
        updater = new Updater(this);
        root.addView(home, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        loadPoster();
        reload();
        refreshWidgets(false);
        final boolean fresh = b == null;
        ui.postDelayed(new Runnable() {
            @Override public void run() { maybeAutostart(fresh); }
        }, 1800L);
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
        home.refreshPinned();
        home.setGuardHint(Prefs.guard(this) && !SetupActivity.guardOn(this));
        updater.check(false, new Runnable() {
            @Override public void run() { applyUpdateHint(); }
        });
        refreshWidgets(false);
        home.requestFocus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        home.goHome();
    }

    @Override
    protected void onDestroy() {
        home.release();
        wd.shutdown();
        updater.shutdown();
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // launcher không thoát
    }

    private void applyUpdateHint() {
        home.setUpdateHint(updater.available
                ? "Có bản mới " + updater.latestName + " · Cài đặt > Cài bản mới" : null);
    }

    private void startUpdate() {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            home.closeOverlay();
            Toast.makeText(this, "Hãy bật \"Cho phép từ nguồn này\" cho Minh Cena TV rồi quay lại chọn Cài bản mới.",
                    Toast.LENGTH_LONG).show();
            updater.install(this);
            return;
        }
        showUpdateInfo("Đang tải bản " + updater.latestName + "… 0%");
        updater.download(new Updater.Listener() {
            @Override public void onProgress(int pct) {
                showUpdateInfo("Đang tải bản " + updater.latestName + "… " + pct + "%");
            }
            @Override public void onDone(boolean ok, String err) {
                if (ok) {
                    home.closeOverlay();
                    updater.install(MainActivity.this);
                } else {
                    showUpdateInfo("Lỗi: " + err);
                }
            }
        });
    }

    private void showUpdateInfo(String msg) {
        List<MenuItemSpec> items = new ArrayList<>();
        items.add(new MenuItemSpec(msg, null, MenuItemSpec.INFO, false, null));
        items.add(MenuItemSpec.action("Đóng", new Runnable() {
            @Override public void run() { home.closeOverlay(); }
        }));
        home.showMenu("Cập nhật Minh Cena TV", items, 1);
    }

    private void refreshWidgets(boolean force) {
        wd.refresh(force, new Runnable() {
            @Override public void run() { ui.post(repaint); }
        });
    }

    // ---------------------------------------------------------------- danh sách ứng dụng

    private void reload() {
        io.execute(new Runnable() {
            @Override public void run() {
                final List<AppEntry> list = loadApps();
                ui.post(new Runnable() {
                    @Override public void run() {
                        all.clear();
                        all.addAll(list);
                        home.setApps(list);
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
                map.put(key, new AppEntry(key, String.valueOf(ri.loadLabel(pm)), pkg, cn, ri));
            }
        }
        List<AppEntry> out = new ArrayList<>(map.values());
        Collections.sort(out, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) { return a.label.compareToIgnoreCase(b.label); }
        });
        return out;
    }

    private void maybeAutostart(boolean fresh) {
        if (!fresh || sAutostartDone) return;
        sAutostartDone = true;
        String key = Prefs.autostart(this);
        if (key == null) return;
        for (AppEntry e : all) if (e.key.equals(key)) { home.launch(e); return; }
    }

    // ---------------------------------------------------------------- menu ứng dụng (giữ OK)

    @Override
    public void openAppMenu(final AppEntry e) {
        final boolean isFav = Prefs.favs(this).contains(e.key);
        final boolean isAuto = e.key.equals(Prefs.autostart(this));
        List<MenuItemSpec> items = new ArrayList<>();
        items.add(MenuItemSpec.action(isFav ? "Bỏ ghim khỏi Dock" : "Ghim vào Dock", new Runnable() {
            @Override public void run() {
                List<String> f = Prefs.favs(MainActivity.this);
                if (f.contains(e.key)) f.remove(e.key); else f.add(0, e.key);
                Prefs.setFavs(MainActivity.this, f);
                home.closeOverlay();
                home.refreshPinned();
            }
        }));
        items.add(MenuItemSpec.action("Ẩn khỏi danh sách", new Runnable() {
            @Override public void run() {
                Set<String> h = Prefs.hidden(MainActivity.this);
                h.add(e.key);
                Prefs.setHidden(MainActivity.this, h);
                home.closeOverlay();
                home.refreshPinned();
                Toast.makeText(MainActivity.this, "Đã ẩn " + e.label + " (bỏ ẩn trong Cài đặt)", Toast.LENGTH_SHORT).show();
            }
        }));
        items.add(MenuItemSpec.action(isAuto ? "Tắt tự mở khi bật box" : "Tự mở khi bật box", new Runnable() {
            @Override public void run() {
                Prefs.setAutostart(MainActivity.this, isAuto ? null : e.key);
                home.closeOverlay();
            }
        }));
        items.add(MenuItemSpec.action("Thông tin ứng dụng", new Runnable() {
            @Override public void run() {
                home.closeOverlay();
                tryStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", e.pkg, null)));
            }
        }));
        items.add(MenuItemSpec.danger("Gỡ cài đặt", new Runnable() {
            @Override public void run() {
                home.closeOverlay();
                tryStart(new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", e.pkg, null)));
            }
        }));
        home.showMenu(e.label, items, 0);
    }

    // ---------------------------------------------------------------- cài đặt

    @Override
    public void openSettings() {
        showSettings(0);
    }

    private void showSettings(int focus) {
        final Context c = this;
        List<MenuItemSpec> items = new ArrayList<>();
        final boolean needGuard = !SetupActivity.guardOn(c);
        final boolean hasUpd = updater.available;
        final int o = (needGuard ? 1 : 0) + (hasUpd ? 1 : 0);
        if (needGuard) {
            items.add(MenuItemSpec.action("Bật nút Home (mở Trợ năng)", new Runnable() {
                @Override public void run() {
                    home.closeOverlay();
                    tryStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                }
            }));
        }
        if (hasUpd) {
            items.add(MenuItemSpec.value("Cài bản mới", updater.latestName, new Runnable() {
                @Override public void run() { startUpdate(); }
            }));
        }
        items.add(MenuItemSpec.toggle("Hiệu ứng động", Prefs.anim(c), new Runnable() {
            @Override public void run() { Prefs.setAnim(c, !Prefs.anim(c)); home.refreshPinned(); showSettings(o); }
        }));
        items.add(MenuItemSpec.toggle("Chế độ kính (tắt = màu phẳng, nhẹ máy)", Prefs.glass(c), new Runnable() {
            @Override public void run() { Prefs.setGlass(c, !Prefs.glass(c)); showSettings(o + 1); }
        }));
        items.add(MenuItemSpec.toggle("Widget thời tiết, giá vàng, giá xăng", Prefs.widgets(c), new Runnable() {
            @Override public void run() { Prefs.setWidgets(c, !Prefs.widgets(c)); showSettings(o + 2); }
        }));
        items.add(MenuItemSpec.value("Thành phố thời tiết", WidgetData.CITY_NAME[Prefs.city(c)], new Runnable() {
            @Override public void run() {
                Prefs.setCity(c, (Prefs.city(c) + 1) % WidgetData.CITY_NAME.length);
                refreshWidgets(true);
                showSettings(o + 3);
            }
        }));
        String auto = "Tắt";
        String key = Prefs.autostart(c);
        if (key != null) for (AppEntry e : all) if (e.key.equals(key)) auto = e.label;
        items.add(MenuItemSpec.value("Tự mở ứng dụng khi bật box", auto, new Runnable() {
            @Override public void run() { chooseAutostart(); }
        }));
        items.add(MenuItemSpec.toggle("Bảo vệ nút Home"
                + (SetupActivity.guardOn(c) ? "" : " (chưa bật Trợ năng)"), Prefs.guard(c), new Runnable() {
            @Override public void run() { Prefs.setGuard(c, !Prefs.guard(c)); showSettings(o + 5); }
        }));
        items.add(MenuItemSpec.action("Mở launcher gốc (60 giây)", new Runnable() {
            @Override public void run() {
                home.closeOverlay();
                if (!Homes.openOriginal(c)) Toast.makeText(c, "Không tìm thấy launcher gốc", Toast.LENGTH_SHORT).show();
            }
        }));
        boolean hasPoster = new File(getFilesDir(), "poster.png").exists();
        items.add(MenuItemSpec.action(hasPoster ? "Đổi ảnh poster của bạn" : "Chọn ảnh poster của bạn", new Runnable() {
            @Override public void run() { home.closeOverlay(); pickPoster(); }
        }));
        if (hasPoster) {
            items.add(MenuItemSpec.action("Dùng lại chữ MINH CENA", new Runnable() {
                @Override public void run() {
                    new File(getFilesDir(), "poster.png").delete();
                    loadPoster();
                    showSettings(o + 8);
                }
            }));
        }
        items.add(MenuItemSpec.action("Bỏ ẩn tất cả ứng dụng (" + Prefs.hidden(c).size() + ")", new Runnable() {
            @Override public void run() {
                Prefs.setHidden(c, new HashSet<String>());
                home.refreshPinned();
                showSettings(0);
            }
        }));
        items.add(MenuItemSpec.value("Kiểm tra cập nhật", "v" + updater.currentName(), new Runnable() {
            @Override public void run() {
                home.closeOverlay();
                Toast.makeText(c, "Đang kiểm tra bản mới…", Toast.LENGTH_SHORT).show();
                updater.check(true, new Runnable() {
                    @Override public void run() {
                        applyUpdateHint();
                        String msg = updater.available ? "Có bản mới " + updater.latestName + " – vào Cài đặt > Cài bản mới"
                                : (updater.error.length() > 0 ? "Không kiểm tra được: " + updater.error : "Bạn đang dùng bản mới nhất");
                        Toast.makeText(c, msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }));
        items.add(MenuItemSpec.action("Hướng dẫn thiết lập", new Runnable() {
            @Override public void run() {
                home.closeOverlay();
                startActivity(new Intent(c, SetupActivity.class));
            }
        }));
        home.showMenu("Cài đặt", items, focus);
    }

    private void chooseAutostart() {
        List<MenuItemSpec> items = new ArrayList<>();
        items.add(MenuItemSpec.action("Tắt (không tự mở)", new Runnable() {
            @Override public void run() { Prefs.setAutostart(MainActivity.this, null); showSettings(5); }
        }));
        for (final AppEntry e : all) {
            items.add(MenuItemSpec.action(e.label, new Runnable() {
                @Override public void run() { Prefs.setAutostart(MainActivity.this, e.key); showSettings(5); }
            }));
        }
        home.showMenu("Tự mở khi bật box", items, 0);
    }

    // ---------------------------------------------------------------- ảnh poster

    private void loadPoster() {
        File f = new File(getFilesDir(), "poster.png");
        Bitmap b = null;
        if (f.exists()) {
            try { b = BitmapFactory.decodeFile(f.getAbsolutePath()); } catch (Throwable ignored) { }
        }
        backdrop.setPoster(b);
    }

    private void pickPoster() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(i, REQ_POSTER);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "Box này chưa có ứng dụng chọn ảnh. Hãy cài một trình quản lý tệp rồi thử lại.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, final Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_POSTER || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        io.execute(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                try {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    InputStream in = getContentResolver().openInputStream(uri);
                    BitmapFactory.decodeStream(in, null, o);
                    if (in != null) in.close();
                    int sample = 1;
                    while (o.outWidth / sample > 1600 || o.outHeight / sample > 1600) sample *= 2;
                    o = new BitmapFactory.Options();
                    o.inSampleSize = sample;
                    in = getContentResolver().openInputStream(uri);
                    Bitmap b = BitmapFactory.decodeStream(in, null, o);
                    if (in != null) in.close();
                    if (b != null) {
                        FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "poster.png"));
                        b.compress(Bitmap.CompressFormat.PNG, 100, out);
                        out.close();
                        ok = true;
                    }
                } catch (Throwable ignored) {
                }
                final boolean fine = ok;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (fine) loadPoster();
                        else Toast.makeText(MainActivity.this, "Không đọc được ảnh này", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void tryStart(Intent i) {
        try {
            startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(this, "Máy không hỗ trợ thao tác này", Toast.LENGTH_SHORT).show();
        }
    }
}
