package vn.forge.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Thay cho việc tắt launcher gốc bằng ADB (khi box khóa ADB):
 * mỗi khi một launcher khác (HOME) hiện lên, đưa người dùng về FORGE TV.
 * Dịch vụ chỉ nhìn tên gói/lớp của cửa sổ vừa mở, không đọc nội dung màn hình.
 */
public class HomeGuardService extends AccessibilityService {
    private long lastLaunch;
    private Set<String> homeComponents = new HashSet<>();
    private long refreshedAt;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent ev) {
        if (ev == null || ev.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (!Prefs.guard(this)) return;
        long now = System.currentTimeMillis();
        if (now < Prefs.bypassUntil(this)) return;

        CharSequence pkg = ev.getPackageName();
        CharSequence cls = ev.getClassName();
        if (pkg == null || cls == null) return;
        if (getPackageName().contentEquals(pkg)) return;

        if (now - refreshedAt > 30_000L || homeComponents.isEmpty()) refreshHomes(now);
        String flat = new ComponentName(pkg.toString(), cls.toString()).flattenToString();
        if (!homeComponents.contains(flat)) return;

        if (now - lastLaunch < 800L) return;   // chống lặp
        lastLaunch = now;
        Intent i = new Intent(this, MainActivity.class);
        i.setAction(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private void refreshHomes(long now) {
        Set<String> s = new HashSet<>();
        List<ResolveInfo> l = Homes.others(this);
        for (ResolveInfo ri : l) {
            s.add(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name).flattenToString());
        }
        homeComponents = s;
        refreshedAt = now;
    }

    @Override
    public void onInterrupt() {
    }
}
