package vn.forge.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dữ liệu cho ba widget: thời tiết (Open-Meteo), giá vàng (vnappmob), giá xăng (VietFuelAPI).
 * Hai nguồn sau là dịch vụ bên thứ ba, không phải API chính thức: có thể đổi hoặc ngừng bất cứ lúc nào.
 * Khi lấy lỗi, widget giữ số liệu lần trước (kèm giờ cập nhật) hoặc báo "chưa có dữ liệu".
 */
final class WidgetData {
    static final String[] CITY_NAME = {"TP.HCM", "Hà Nội", "Đà Nẵng", "Cần Thơ", "Hải Phòng", "Nha Trang", "Huế"};
    static final double[] CITY_LAT  = {10.82, 21.03, 16.05, 10.03, 20.86, 12.24, 16.46};
    static final double[] CITY_LON  = {106.63, 105.85, 108.20, 105.78, 106.68, 109.19, 107.59};

    // Biểu tượng thời tiết
    static final int W_SUN = 0, W_PARTLY = 1, W_CLOUD = 2, W_RAIN = 3, W_STORM = 4;

    // thời tiết
    volatile boolean weatherOk;
    String city = "";
    int temp, hi, lo, wIcon;
    String wText = "";
    final int[] hourTemp = new int[4];
    final String[] hourLabel = new String[4];
    int hourCount;

    // vàng (triệu đồng/lượng)
    volatile boolean goldOk;
    double sjcSell, sjcBuy, ringSell = Double.NaN, sjcDelta = Double.NaN;
    String goldTime = "";

    // xăng (đồng/lít)
    volatile boolean fuelOk;
    int ron95, e5, ron95Delta, e5Delta;
    String fuelNote = "";

    private final Context ctx;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private long lastFetch;

    WidgetData(Context c) {
        ctx = c.getApplicationContext();
        loadCache();
    }

    void shutdown() { io.shutdownNow(); }

    /** Lấy dữ liệu mới nếu đã quá 30 phút (hoặc force). onDone chạy trên luồng nền. */
    void refresh(boolean force, final Runnable onDone) {
        long now = System.currentTimeMillis();
        if (!force && now - lastFetch < 30 * 60 * 1000L) return;
        lastFetch = now;
        io.execute(new Runnable() {
            @Override public void run() {
                try { fetchWeather(); } catch (Throwable ignored) { }
                try { fetchGold(); } catch (Throwable ignored) { }
                try { fetchFuel(); } catch (Throwable ignored) { }
                if (onDone != null) onDone.run();
            }
        });
    }

    // ---------------------------------------------------------------- thời tiết

    private void fetchWeather() throws Exception {
        int ci = Math.max(0, Math.min(CITY_NAME.length - 1, Prefs.city(ctx)));
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + CITY_LAT[ci]
                + "&longitude=" + CITY_LON[ci]
                + "&current=temperature_2m,weather_code"
                + "&hourly=temperature_2m"
                + "&daily=temperature_2m_max,temperature_2m_min"
                + "&timezone=Asia%2FBangkok&forecast_days=2";
        JSONObject j = new JSONObject(http(url, null));
        JSONObject cur = j.getJSONObject("current");
        int t = (int) Math.round(cur.getDouble("temperature_2m"));
        int code = cur.getInt("weather_code");
        JSONObject daily = j.getJSONObject("daily");
        int h = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
        int l = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
        JSONObject hourly = j.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        JSONArray temps = hourly.getJSONArray("temperature_2m");
        String now = cur.getString("time");
        String hourKey = now.length() >= 13 ? now.substring(0, 13) + ":00" : now;
        int idx = 0;
        for (int i = 0; i < times.length(); i++) {
            if (hourKey.equals(times.getString(i))) { idx = i; break; }
        }
        synchronized (this) {
            city = CITY_NAME[ci];
            temp = t; hi = h; lo = l;
            describe(code);
            hourCount = 0;
            for (int k = 0; k < 4; k++) {
                int i = idx + k * 2;
                if (i >= temps.length()) break;
                hourTemp[hourCount] = (int) Math.round(temps.getDouble(i));
                String ts = times.getString(i);
                hourLabel[hourCount] = k == 0 ? "Bây giờ" : ts.substring(11, 13) + "h";
                hourCount++;
            }
            weatherOk = true;
        }
        SharedPreferences.Editor e = Prefs.sp(ctx).edit();
        e.putString("w_city", city).putInt("w_t", t).putInt("w_hi", h).putInt("w_lo", l).putInt("w_code", code);
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < hourCount; k++) sb.append(hourTemp[k]).append('|').append(hourLabel[k]).append(';');
        e.putString("w_hours", sb.toString()).apply();
    }

    private void describe(int code) {
        if (code == 0) { wText = "Trời quang"; wIcon = W_SUN; }
        else if (code <= 2) { wText = "Ít mây"; wIcon = W_PARTLY; }
        else if (code == 3) { wText = "Nhiều mây"; wIcon = W_CLOUD; }
        else if (code == 45 || code == 48) { wText = "Sương mù"; wIcon = W_CLOUD; }
        else if (code >= 51 && code <= 57) { wText = "Mưa phùn"; wIcon = W_RAIN; }
        else if (code >= 61 && code <= 67) { wText = "Mưa"; wIcon = W_RAIN; }
        else if (code >= 80 && code <= 82) { wText = "Mưa rào"; wIcon = W_RAIN; }
        else if (code >= 95) { wText = "Dông"; wIcon = W_STORM; }
        else { wText = "Nhiều mây"; wIcon = W_CLOUD; }
    }

    // ---------------------------------------------------------------- vàng

    private void fetchGold() throws Exception {
        SharedPreferences sp = Prefs.sp(ctx);
        String key = sp.getString("gold_key", null);
        long keyAt = sp.getLong("gold_key_at", 0L);
        long now = System.currentTimeMillis();
        if (key == null || now - keyAt > 12L * 24 * 3600 * 1000L) {
            String kb = http("https://vapi.vnappmob.com/api/request_api_key?scope=gold", null);
            key = new JSONObject(kb).optString("results", null);
            if (key == null || key.length() == 0) throw new IOException("no key");
            sp.edit().putString("gold_key", key).putLong("gold_key_at", now).apply();
        }
        String body;
        try {
            body = http("https://vapi.vnappmob.com/api/v2/gold/sjc", key);
        } catch (IOException ex) {
            sp.edit().remove("gold_key").apply();
            throw ex;
        }
        JSONArray rs = new JSONObject(body).getJSONArray("results");
        JSONObject r0 = rs.getJSONObject(0);
        double buy = r0.optDouble("buy_1l", Double.NaN);
        double sell = r0.optDouble("sell_1l", Double.NaN);
        if (Double.isNaN(sell)) throw new IOException("no sell");
        double ring = Double.NaN;
        Iterator<String> it = r0.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (k.startsWith("sell") && k.contains("nhan")) ring = r0.optDouble(k, Double.NaN);
        }
        double last = Double.longBitsToDouble(sp.getLong("gold_last", Double.doubleToLongBits(Double.NaN)));
        double prev = Double.longBitsToDouble(sp.getLong("gold_prev", Double.doubleToLongBits(Double.NaN)));
        double s = sell / 1e6;
        if (!Double.isNaN(last) && Math.abs(last - s) > 1e-9) { prev = last; }
        sp.edit().putLong("gold_last", Double.doubleToLongBits(s))
                .putLong("gold_prev", Double.doubleToLongBits(prev)).apply();
        synchronized (this) {
            sjcSell = s;
            sjcBuy = Double.isNaN(buy) ? Double.NaN : buy / 1e6;
            ringSell = Double.isNaN(ring) ? Double.NaN : ring / 1e6;
            sjcDelta = Double.isNaN(prev) ? Double.NaN : s - prev;
            goldTime = clock();
            goldOk = true;
        }
        sp.edit().putString("g_time", goldTime).putLong("g_buy", Double.doubleToLongBits(sjcBuy))
                .putLong("g_ring", Double.doubleToLongBits(ringSell)).apply();
    }

    // ---------------------------------------------------------------- xăng

    private void fetchFuel() throws Exception {
        String body = http("https://vietfuel-api.tranqui.workers.dev/api/fuel-prices", null);
        JSONObject j = new JSONObject(body);
        JSONArray d = j.getJSONArray("data");
        int r95 = 0, r95iii = 0, e5v = 0;
        for (int i = 0; i < d.length(); i++) {
            JSONObject o = d.getJSONObject(i);
            String n = o.optString("name", "").toLowerCase();
            int v = o.optInt("region1", 0);
            if (v <= 0) continue;
            if (n.contains("ron 95") || n.contains("ron95")) {
                if (n.contains("iii")) r95iii = v; else if (r95 == 0) r95 = v;
            } else if (n.contains("e5")) {
                if (e5v == 0) e5v = v;
            }
        }
        int ron = r95iii > 0 ? r95iii : r95;
        if (ron <= 0 && e5v <= 0) throw new IOException("no fuel");
        SharedPreferences sp = Prefs.sp(ctx);
        int lastR = sp.getInt("fuel_ron_last", 0), prevR = sp.getInt("fuel_ron_prev", 0);
        int lastE = sp.getInt("fuel_e5_last", 0), prevE = sp.getInt("fuel_e5_prev", 0);
        if (lastR > 0 && lastR != ron) prevR = lastR;
        if (lastE > 0 && lastE != e5v) prevE = lastE;
        sp.edit().putInt("fuel_ron_last", ron).putInt("fuel_ron_prev", prevR)
                .putInt("fuel_e5_last", e5v).putInt("fuel_e5_prev", prevE).apply();
        String date = "";
        try { date = j.getJSONObject("meta").optString("priceDateDisplay", ""); } catch (Exception ignored) { }
        synchronized (this) {
            ron95 = ron; e5 = e5v;
            ron95Delta = prevR > 0 ? ron - prevR : 0;
            e5Delta = prevE > 0 ? e5v - prevE : 0;
            fuelNote = date.length() > 0 ? "Giá ngày " + date : "Điều chỉnh mỗi thứ Năm";
            fuelOk = true;
        }
        sp.edit().putString("f_note", fuelNote).apply();
    }

    // ---------------------------------------------------------------- tiện ích

    private static String clock() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return String.format(java.util.Locale.US, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE));
    }

    private void loadCache() {
        SharedPreferences sp = Prefs.sp(ctx);
        if (sp.contains("w_t")) {
            city = sp.getString("w_city", "");
            temp = sp.getInt("w_t", 0); hi = sp.getInt("w_hi", 0); lo = sp.getInt("w_lo", 0);
            describe(sp.getInt("w_code", 3));
            hourCount = 0;
            String h = sp.getString("w_hours", "");
            for (String part : h.split(";")) {
                int bar = part.indexOf('|');
                if (bar <= 0 || hourCount >= 4) continue;
                try {
                    hourTemp[hourCount] = Integer.parseInt(part.substring(0, bar));
                    hourLabel[hourCount] = part.substring(bar + 1);
                    hourCount++;
                } catch (NumberFormatException ignored) { }
            }
            weatherOk = hourCount > 0;
        }
        if (sp.contains("gold_last")) {
            sjcSell = Double.longBitsToDouble(sp.getLong("gold_last", 0));
            sjcBuy = Double.longBitsToDouble(sp.getLong("g_buy", Double.doubleToLongBits(Double.NaN)));
            ringSell = Double.longBitsToDouble(sp.getLong("g_ring", Double.doubleToLongBits(Double.NaN)));
            double prev = Double.longBitsToDouble(sp.getLong("gold_prev", Double.doubleToLongBits(Double.NaN)));
            sjcDelta = Double.isNaN(prev) ? Double.NaN : sjcSell - prev;
            goldTime = sp.getString("g_time", "");
            goldOk = !Double.isNaN(sjcSell) && sjcSell > 0;
        }
        if (sp.contains("fuel_ron_last") || sp.contains("fuel_e5_last")) {
            ron95 = sp.getInt("fuel_ron_last", 0); e5 = sp.getInt("fuel_e5_last", 0);
            int pr = sp.getInt("fuel_ron_prev", 0), pe = sp.getInt("fuel_e5_prev", 0);
            ron95Delta = pr > 0 ? ron95 - pr : 0; e5Delta = pe > 0 ? e5 - pe : 0;
            fuelNote = sp.getString("f_note", "Điều chỉnh mỗi thứ Năm");
            fuelOk = ron95 > 0 || e5 > 0;
        }
    }

    private static String http(String url, String bearer) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "MinhCenaTV/1.0");
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 600 * 1024) throw new IOException("too large");
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
