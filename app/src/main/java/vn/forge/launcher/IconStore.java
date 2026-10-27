package vn.forge.launcher;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Nạp icon thật của từng app ở độ phân giải cao nhất (XXXHDPI) rồi vẽ sẵn thành bitmap
 * đúng bằng kích thước điểm ảnh sẽ hiển thị, nên không bị kéo giãn và luôn sắc nét.
 * Icon adaptive được bo lại thành squircle cùng bán kính; icon cũ được đặt vào ô kính tối.
 */
final class IconStore {
    private static final int DENSITY_XXXHIGH = 640;

    private final PackageManager pm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Set<String> pending = Collections.synchronizedSet(new HashSet<String>());
    private final Runnable onReady;
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(40 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    IconStore(Context c, Runnable onReady) {
        this.pm = c.getPackageManager();
        this.onReady = onReady;
    }

    /** Lấy icon đã vẽ sẵn đúng bằng sizePx điểm ảnh; chưa có thì nạp nền và vẽ lại khi xong. */
    Bitmap get(final AppEntry e, final int sizePx) {
        final int size = Math.max(32, sizePx);
        final String k = e.key + "@" + size;
        Bitmap b = cache.get(k);
        if (b != null) return b;
        if (pending.add(k)) {
            io.execute(new Runnable() {
                @Override public void run() {
                    try {
                        Bitmap bm = render(e.ri, size);
                        if (bm != null) cache.put(k, bm);
                    } catch (Throwable ignored) {
                    }
                    pending.remove(k);
                    ui.post(onReady);
                }
            });
        }
        return null;
    }

    void shutdown() {
        io.shutdownNow();
    }

    // ---------------------------------------------------------------- vẽ icon

    private Drawable loadDrawable(ResolveInfo ri) {
        Drawable d = null;
        try {
            ActivityInfo ai = ri.activityInfo;
            int res = ai.getIconResource();
            if (res == 0 && ai.applicationInfo != null) res = ai.applicationInfo.icon;
            if (res != 0) {
                Resources r = pm.getResourcesForApplication(ai.applicationInfo);
                d = r.getDrawableForDensity(res, DENSITY_XXXHIGH, null);
            }
        } catch (Throwable ignored) {
        }
        if (d == null) d = ri.loadIcon(pm);
        return d;
    }

    private Bitmap render(ResolveInfo ri, int size) {
        Drawable d = loadDrawable(ri);
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(out);
        float r = size * 0.26f;
        RectF rect = new RectF(0, 0, size, size);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

        if (Build.VERSION.SDK_INT >= 26 && d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            Bitmap content = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas cc = new Canvas(content);
            int big = Math.round(size * 1.5f);
            int off = (size - big) / 2;
            Drawable bg = a.getBackground();
            Drawable fg = a.getForeground();
            if (bg != null) { bg.setBounds(off, off, off + big, off + big); bg.draw(cc); }
            if (fg != null) { fg.setBounds(off, off, off + big, off + big); fg.draw(cc); }
            p.setShader(new BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            oc.drawRoundRect(rect, r, r, p);
            return out;
        }

        // icon cũ hoặc không phải adaptive: đặt vào ô kính tối
        p.setShader(new LinearGradient(0, 0, 0, size, 0xFF34343A, 0xFF0C0C0E, Shader.TileMode.CLAMP));
        oc.drawRoundRect(rect, r, r, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, size / 64f));
        p.setColor(0x42FFFFFF);
        RectF inner = new RectF(p.getStrokeWidth() / 2f, p.getStrokeWidth() / 2f,
                size - p.getStrokeWidth() / 2f, size - p.getStrokeWidth() / 2f);
        oc.drawRoundRect(inner, r, r, p);
        if (d != null) {
            int pad = Math.round(size * 0.2f);
            d.setBounds(pad, pad, size - pad, size - pad);
            d.draw(oc);
        }
        return out;
    }
}
