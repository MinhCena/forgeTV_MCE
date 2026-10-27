package vn.forge.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Cài đặt người dùng, lưu trong SharedPreferences. */
final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("forge", Context.MODE_PRIVATE);
    }

    static boolean anim(Context c) { return sp(c).getBoolean("anim", true); }
    static void setAnim(Context c, boolean v) { sp(c).edit().putBoolean("anim", v).apply(); }

    static boolean clock24(Context c) { return sp(c).getBoolean("clock24", true); }
    static void setClock24(Context c, boolean v) { sp(c).edit().putBoolean("clock24", v).apply(); }

    static boolean guard(Context c) { return sp(c).getBoolean("guard", true); }
    static void setGuard(Context c, boolean v) { sp(c).edit().putBoolean("guard", v).apply(); }

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

    static List<String> favs(Context c) {
        String s = sp(c).getString("favs", "");
        List<String> out = new ArrayList<>();
        if (s.length() > 0) {
            for (String x : s.split("\n")) if (x.length() > 0) out.add(x);
        }
        return out;
    }
    static void setFavs(Context c, List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String x : l) { if (sb.length() > 0) sb.append('\n'); sb.append(x); }
        sp(c).edit().putString("favs", sb.toString()).apply();
    }

    /** Tạm tắt bảo vệ Home đến mốc thời gian này (ms) để mở launcher gốc. */
    static long bypassUntil(Context c) { return sp(c).getLong("bypass", 0L); }
    static void setBypass(Context c, long untilMs) { sp(c).edit().putLong("bypass", untilMs).apply(); }
}
