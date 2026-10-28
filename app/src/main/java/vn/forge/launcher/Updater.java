package vn.forge.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tự cập nhật qua GitHub Releases: kiểm tra bản mới, tải APK về và mở trình cài đặt của Android.
 * Android luôn hỏi xác nhận khi cài; không thể cài ngầm trên box thường.
 * Bản mới phải ký bằng CÙNG khóa (lưu trong GitHub Secrets); app tự kiểm tra chữ ký và SHA-256 trước khi cài.
 * Repo phải công khai (public) để app đọc được mà không cần mã truy cập.
 */
final class Updater {
    static final String REPO = "MinhCena/forgeTV_MCE";

    /** Chỉ tải từ các máy chủ chính thức của GitHub, chỉ qua HTTPS. */
    private static final Set<String> HOSTS = new HashSet<String>(Arrays.asList(
            "github.com", "api.github.com", "objects.githubusercontent.com",
            "release-assets.githubusercontent.com"));
    private static final long MAX_APK = 80L * 1024 * 1024;

    interface Listener {
        void onProgress(int percent);
        void onDone(boolean ok, String error);
    }

    private final Context ctx;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    volatile boolean available;
    volatile int latestCode;
    volatile String latestName = "";
    volatile String apkUrl = "";
    volatile String notes = "";
    volatile String sha256 = "";
    volatile String error = "";

    Updater(Context c) {
        ctx = c.getApplicationContext();
    }

    void shutdown() { io.shutdownNow(); }

    int currentCode() {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    String currentName() {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** Kiểm tra bản mới (tối đa mỗi 6 giờ nếu không ép). onDone chạy trên luồng giao diện. */
    void check(boolean force, final Runnable onDone) {
        long now = System.currentTimeMillis();
        long last = Prefs.sp(ctx).getLong("upd_at", 0L);
        if (!force && now - last < 6L * 3600 * 1000L) { post(onDone); return; }
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    String body = http("https://api.github.com/repos/" + REPO + "/releases/latest");
                    JSONObject j = new JSONObject(body);
                    String tag = j.optString("tag_name", "");
                    int code = 0;
                    if (tag.matches("v[0-9]{1,9}")) code = Integer.parseInt(tag.substring(1));
                    String url = "";
                    String sum = "";
                    JSONArray assets = j.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject a = assets.getJSONObject(i);
                            if (a.optString("name", "").endsWith(".apk")) {
                                String u = a.optString("browser_download_url", "");
                                if (u.startsWith("https://github.com/" + REPO + "/releases/download/")) {
                                    url = u;
                                    String d = a.optString("digest", "");
                                    if (d.startsWith("sha256:") && d.length() == 71) sum = d.substring(7).toLowerCase();
                                }
                                break;
                            }
                        }
                    }
                    latestCode = code;
                    latestName = tag;
                    apkUrl = url;
                    sha256 = sum;
                    notes = j.optString("body", "");
                    available = code > currentCode() && url.length() > 0;
                    error = "";
                    Prefs.sp(ctx).edit().putLong("upd_at", System.currentTimeMillis()).apply();
                } catch (Throwable t) {
                    String m = t.getMessage();
                    error = m == null ? t.getClass().getSimpleName() : (m.length() > 60 ? m.substring(0, 60) : m);
                }
                post(onDone);
            }
        });
    }

    /** Tải APK về bộ nhớ đệm, kiểm tra đúng gói và đúng là bản mới hơn. */
    void download(final Listener l) {
        final String url = apkUrl;
        io.execute(new Runnable() {
            @Override public void run() {
                File tmp = new File(ctx.getCacheDir(), "update.tmp");
                File out = new File(ctx.getCacheDir(), "update.apk");
                HttpURLConnection c = null;
                String err = null;
                try {
                    String cur = url;
                    int code = 0;
                    for (int hop = 0; hop < 6; hop++) {
                        checkUrl(cur);
                        c = (HttpURLConnection) new URL(cur).openConnection();
                        c.setInstanceFollowRedirects(false);
                        c.setConnectTimeout(10000);
                        c.setReadTimeout(20000);
                        c.setRequestProperty("User-Agent", "MinhCenaTV-Updater");
                        code = c.getResponseCode();
                        if (code >= 300 && code < 400) {
                            String loc = c.getHeaderField("Location");
                            c.disconnect();
                            c = null;
                            if (loc == null) throw new IOException("Chuyển hướng lỗi");
                            cur = new URL(new URL(cur), loc).toString();
                            continue;
                        }
                        break;
                    }
                    if (c == null) throw new IOException("Quá nhiều chuyển hướng");
                    if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
                    int total = c.getContentLength();
                    if (total > MAX_APK) throw new IOException("File quá lớn");
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    InputStream in = c.getInputStream();
                    FileOutputStream fo = new FileOutputStream(tmp);
                    byte[] buf = new byte[32 * 1024];
                    int n, done = 0, lastPct = -1;
                    while ((n = in.read(buf)) > 0) {
                        fo.write(buf, 0, n);
                        md.update(buf, 0, n);
                        done += n;
                        if (done > MAX_APK) throw new IOException("File quá lớn");
                        if (total > 0) {
                            final int pct = (int) (100L * done / total);
                            if (pct != lastPct) {
                                lastPct = pct;
                                ui.post(new Runnable() { @Override public void run() { l.onProgress(pct); } });
                            }
                        }
                    }
                    fo.close();
                    in.close();
                    String want = sha256;
                    if (want.length() == 64 && !want.equals(hex(md.digest()))) {
                        tmp.delete();
                        throw new IOException("Mã SHA-256 không khớp - bỏ file tải về");
                    }
                    if (out.exists()) out.delete();
                    if (!tmp.renameTo(out)) throw new IOException("Không lưu được file");
                    PackageInfo pi = ctx.getPackageManager().getPackageArchiveInfo(out.getAbsolutePath(), 0);
                    if (!sameSigner(out)) {
                        out.delete();
                        throw new IOException("Chữ ký không trùng bản đang cài - từ chối");
                    }
                    if (pi == null || !ctx.getPackageName().equals(pi.packageName)) {
                        out.delete();
                        throw new IOException("File tải về không phải bản của Minh Cena TV");
                    }
                    if (pi.versionCode <= currentCode()) {
                        out.delete();
                        throw new IOException("Không phải bản mới hơn");
                    }
                } catch (Throwable t) {
                    String m = t.getMessage();
                    err = m == null ? t.getClass().getSimpleName() : m;
                } finally {
                    if (c != null) c.disconnect();
                }
                final String e = err;
                ui.post(new Runnable() { @Override public void run() { l.onDone(e == null, e); } });
            }
        });
    }

    /** Mở trình cài đặt. Trả về false nếu cần người dùng cấp quyền "cài từ nguồn không xác định" trước. */
    boolean install(Activity a) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + a.getPackageName())));
            } catch (Exception ignored) {
            }
            return false;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.parse("content://" + ApkProvider.AUTH + "/update.apk"),
                "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            a.startActivity(i);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private static void checkUrl(String u) throws IOException {
        URL x = new URL(u);
        if (!"https".equals(x.getProtocol()) || !HOSTS.contains(x.getHost().toLowerCase()))
            throw new IOException("Máy chủ không được phép: " + x.getHost());
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static byte[][] sigs(PackageInfo pi) {
        Signature[] s = null;
        if (Build.VERSION.SDK_INT >= 28 && pi.signingInfo != null) {
            s = pi.signingInfo.hasMultipleSigners()
                    ? pi.signingInfo.getApkContentsSigners()
                    : pi.signingInfo.getSigningCertificateHistory();
        } else {
            s = pi.signatures;
        }
        if (s == null) return new byte[0][];
        byte[][] r = new byte[s.length][];
        for (int i = 0; i < s.length; i++) r[i] = s[i].toByteArray();
        return r;
    }

    /** APK tải về phải được ký bằng đúng chứng chỉ của bản đang cài (kể cả khi GitHub bị giả mạo). */
    private boolean sameSigner(File apk) {
        try {
            PackageManager pm = ctx.getPackageManager();
            int flag = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo mine = pm.getPackageInfo(ctx.getPackageName(), flag);
            PackageInfo theirs = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flag);
            if (theirs == null) return false;
            byte[][] a = sigs(mine), b = sigs(theirs);
            if (a.length == 0 || b.length == 0) return false;
            for (byte[] x : a) for (byte[] y : b) if (Arrays.equals(x, y)) return true;
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private void post(final Runnable r) {
        if (r != null) ui.post(r);
    }

    private static String http(String url) throws IOException {
        checkUrl(url);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "MinhCenaTV-Updater");
            int code = c.getResponseCode();
            if (code == 404) throw new IOException("Chưa có bản phát hành (hoặc repo không công khai)");
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
