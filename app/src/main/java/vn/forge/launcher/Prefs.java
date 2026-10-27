package vn.forge.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Cài đặt người dùng và dữ liệu nhỏ lưu cục bộ. */
final class Prefs {
    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("forge", Context.MODE_PRIVATE);
    }

    static boolean anim(Context c) { return sp(c).getBoolean("anim", true); }
    static void setAnim(Context c, boolean v) { sp(c).edit().putBoolean("anim", v).apply(); }

    static boolean glass(Context c) { return sp(c).getBoolean("glass", true); }
    static void setGlass(Context c, boolean v) { sp(c).edit().putBoolean("glass", v).apply(); }

    static boolean widgets(Context c) { return sp(c).getBoolean("widgets", true); }
    static void setWidgets(Context c, boolean v) { sp(c).edit().putBoolean("widgets", v).apply(); }

    /** 0 = Vừa, 1 = Lớn (+12%). */
    static int uiSize(Context c) { return sp(c).getInt("uisize", 0); }
    static void setUiSize(Context c, int v) { sp(c).edit().putInt("uisize", v).apply(); }

    static int city(Context c) { return sp(c).getInt("city", 0); }
    static void setCity(Context c, int v) { sp(c).edit().putInt("city", v).apply(); }

    static boolean guard(Context c) { return sp(c).getBoolean("guard", true); }
    static void setGuard(Context c, boolean v) { sp(c).edit().putBoolean("guard", v).apply(); }

    static String userName(Context c) { return sp(c).getString("name", "Minh"); }

    static String autostart(Context c) { return sp(c).getString("autostart", null); }
    static void setAutostart(Context c, String key) {
        SharedPreferences.Editor e = sp(c).edit();
        if (key == null) e.remove("autostart"); else e.putString("autostart", key);
        e.apply();
    }

    static Set<String> hidden(Context c) {
        return new HashSet<>(sp(c).getStringSet("hidden", Collections.<String>emptySet()));
    }
    static void setHidden(Context c, Set<String> s) {
        sp(c).edit().putStringSet("hidden", new HashSet<>(s)).apply();
    }

    static List<String> favs(Context c) { return list(sp(c).getString("favs", "")); }
    static void setFavs(Context c, List<String> l) { sp(c).edit().putString("favs", join(l)).apply(); }

    /** Ứng dụng đã mở gần đây, mới nhất ở đầu. */
    static List<String> recent(Context c) { return list(sp(c).getString("recent", "")); }
    static void pushRecent(Context c, String key) {
        List<String> l = recent(c);
        l.remove(key);
        l.add(0, key);
        while (l.size() > 6) l.remove(l.size() - 1);
        sp(c).edit().putString("recent", join(l)).putLong("recent_at", System.currentTimeMillis()).apply();
    }
    static long recentAt(Context c) { return sp(c).getLong("recent_at", 0L); }

    static long bypassUntil(Context c) { return sp(c).getLong("bypass", 0L); }
    static void setBypass(Context c, long untilMs) { sp(c).edit().putLong("bypass", untilMs).apply(); }

    private static List<String> list(String s) {
        List<String> out = new ArrayList<>();
        if (s != null && s.length() > 0) {
            for (String x : s.split("\n")) if (x.length() > 0) out.add(x);
        }
        return out;
    }
    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String x : l) { if (sb.length() > 0) sb.append('\n'); sb.append(x); }
        return sb.toString();
    }
}
