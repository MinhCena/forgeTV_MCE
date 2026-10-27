package vn.forge.launcher;

import android.content.ComponentName;
import android.content.pm.ResolveInfo;

/** Một ứng dụng có thể mở. Icon được nạp riêng bởi IconStore. */
final class AppEntry {
    final String key;     // "pkg/cls"
    final String label;
    final String pkg;
    final ComponentName cn;
    final ResolveInfo ri;

    AppEntry(String key, String label, String pkg, ComponentName cn, ResolveInfo ri) {
        this.key = key;
        this.label = label;
        this.pkg = pkg;
        this.cn = cn;
        this.ri = ri;
    }
}
