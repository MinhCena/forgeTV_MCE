package vn.forge.launcher;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

/** Một ứng dụng hiển thị trên màn hình chính. */
final class AppEntry {
    final String key;       // component dạng "pkg/cls"
    final String label;
    final String pkg;
    final ComponentName cn;
    final Drawable image;   // banner (16:9) nếu có, ngược lại là icon
    final boolean banner;

    AppEntry(String key, String label, String pkg, ComponentName cn, Drawable image, boolean banner) {
        this.key = key;
        this.label = label;
        this.pkg = pkg;
        this.cn = cn;
        this.image = image;
        this.banner = banner;
    }
}
