package vn.forge.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Thay cho việc tắt launcher gốc bằng ADB. Hai cách phối hợp:
 *  1) Bắt phím HOME (khi được cấp quyền lọc phím trong Trợ năng) và mở Minh Cena TV thay vì launcher gốc.
 *  2) Dự phòng: khi cửa sổ của một launcher khác (HOME) hiện lên thì đưa người dùng về lại đây.
 * Dịch vụ chỉ xem tên gói của cửa sổ và phím HOME, không đọc nội dung màn hình.
 */
public class HomeGuardService extends AccessibilityService {
    private long lastLaunch;
    private Set<String> homePkgs = new HashSet<>();
    private long refreshedAt;

    private boolean active() {
        return Prefs.guard(this) && System.currentTimeMillis() >= Prefs.bypassUntil(this);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_HOME && active()) {
            if (e.getAction() == KeyEvent.ACTION_UP) openHome();
            return true;           // nuốt phím để launcher gốc không mở
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent ev) {
        if (ev == null || ev.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (!active()) return;
        CharSequence pkg = ev.getPackageName();
        if (pkg == null || getPackageName().contentEquals(pkg)) return;
        long now = System.currentTimeMillis();
        if (now - refreshedAt > 30_000L || homePkgs.isEmpty()) refreshHomes(now);
        if (homePkgs.contains(pkg.toString())) openHome();
    }

    private void openHome() {
        long now = System.currentTimeMillis();
        if (now - lastLaunch < 700L) return;     // chống lặp
        lastLaunch = now;
        Intent i = new Intent(this, MainActivity.class);
        i.setAction(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private void refreshHomes(long now) {
        Set<String> s = new HashSet<>();
        List<ResolveInfo> l = Homes.others(this);
        for (ResolveInfo ri : l) s.add(ri.activityInfo.packageName);
        homePkgs = s;
        refreshedAt = now;
    }

    @Override
    public void onInterrupt() {
    }
}
