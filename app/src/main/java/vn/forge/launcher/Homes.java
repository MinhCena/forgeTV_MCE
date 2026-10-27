package vn.forge.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.List;

/** Tìm các launcher (HOME) khác đang cài trên box, ví dụ launcher của FPT. */
final class Homes {
    private Homes() {}

    static List<ResolveInfo> others(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> all = pm.queryIntentActivities(i, 0);
        List<ResolveInfo> out = new ArrayList<>();
        for (ResolveInfo ri : all) {
            if (ri.activityInfo != null && !c.getPackageName().equals(ri.activityInfo.packageName)) {
                out.add(ri);
            }
        }
        return out;
    }

    /** Mở launcher gốc và tạm tắt bảo vệ Home 60 giây để không bị đẩy ngược về. */
    static boolean openOriginal(Context c) {
        List<ResolveInfo> l = others(c);
        if (l.isEmpty()) return false;
        ResolveInfo ri = l.get(0);
        Prefs.setBypass(c, System.currentTimeMillis() + 60_000L);
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
