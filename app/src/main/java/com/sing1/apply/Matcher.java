package com.sing1.apply;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.InputStream;

/**
 * 模板匹配识别引擎。
 *
 * 蚂蚁森林的界面是 Canvas 绘制的，无障碍树里拿不到「一键收 / 找能量」这些按钮，
 * 所以这里用截图 + 归一化互相关（NCC）来"看图识别"按钮。
 *
 * 所有模板都按 1080 宽为基准截图，运行时按屏幕宽度等比缩放。
 */
public final class Matcher {

    /** 参考宽度（模板原始截图宽度） */
    public static final float REF_WIDTH = 1080f;

    private Matcher() {
    }

    /** 模板 */
    public static final class Tpl {
        final float[] data;
        public final int w;
        public final int h;
        final float mean;
        final float norm;

        Tpl(float[] data, int w, int h) {
            this.data = data;
            this.w = w;
            this.h = h;
            double s = 0;
            for (float v : data) s += v;
            mean = (float) (s / data.length);
            double n = 0;
            for (float v : data) {
                double d = v - mean;
                n += d * d;
            }
            norm = (float) Math.sqrt(n);
        }
    }

    /** 一张已缩放好的灰度截图 */
    public static final class Prepared {
        public final byte[] g;
        public final int w;
        public final int h;

        Prepared(byte[] g, int w, int h) {
            this.g = g;
            this.w = w;
            this.h = h;
        }
    }

    /** 匹配结果（坐标是 Prepared 坐标系） */
    public static final class Hit {
        public final float score;
        public final float cx;
        public final float cy;

        Hit(float score, float cx, float cy) {
            this.score = score;
            this.cx = cx;
            this.cy = cy;
        }
    }

    public static Tpl loadTemplate(Context ctx, String asset, float scale) {
        try (InputStream is = ctx.getAssets().open(asset)) {
            Bitmap src = BitmapFactory.decodeStream(is);
            if (src == null) {
                LogBus.log("模板解码失败: " + asset);
                return null;
            }
            int w = Math.max(6, Math.round(src.getWidth() * scale));
            int h = Math.max(6, Math.round(src.getHeight() * scale));
            Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
            byte[] gray = toGray(small);
            float[] d = new float[gray.length];
            for (int i = 0; i < gray.length; i++) d[i] = gray[i] & 0xff;
            if (small != src) small.recycle();
            src.recycle();
            return new Tpl(d, w, h);
        } catch (Exception e) {
            LogBus.log("模板加载异常 " + asset + ": " + e);
            return null;
        }
    }

    public static Prepared prepare(Bitmap full, float scale) {
        int w = Math.max(8, Math.round(full.getWidth() * scale));
        int h = Math.max(8, Math.round(full.getHeight() * scale));
        Bitmap small = Bitmap.createScaledBitmap(full, w, h, true);
        byte[] gray = toGray(small);
        if (small != full) small.recycle();
        return new Prepared(gray, w, h);
    }

    static byte[] toGray(Bitmap b) {
        int w = b.getWidth();
        int h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        byte[] g = new byte[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = (c >> 16) & 0xff;
            int gg = (c >> 8) & 0xff;
            int bb = c & 0xff;
            g[i] = (byte) ((r * 77 + gg * 151 + bb * 28) >> 8);
        }
        return g;
    }

    /**
     * 在 ROI（按 0~1 的比例给出）内搜索模板，返回最佳位置。
     */
    public static Hit find(Prepared p, Tpl t, float l, float top, float r, float bottom, int stride) {
        if (t == null || p == null) return null;
        int x0 = Math.max(0, Math.round(l * p.w));
        int y0 = Math.max(0, Math.round(top * p.h));
        int x1 = Math.min(p.w, Math.round(r * p.w));
        int y1 = Math.min(p.h, Math.round(bottom * p.h));
        int tw = t.w, th = t.h, iw = p.w;
        if (x1 - x0 < tw || y1 - y0 < th) return null;

        final double n = tw * th;
        final float[] td = t.data;
        final byte[] g = p.g;
        float best = -2f;
        int bx = 0, by = 0;

        for (int y = y0; y + th <= y1; y += stride) {
            for (int x = x0; x + tw <= x1; x += stride) {
                // 用 double 累加：模板约 4400 像素时 float 会出现灾难性抵消，
                // 曾导致相关系数算出 1.8（理论上限 1.0）而误判。
                double sum = 0, sq = 0, dot = 0;
                int row = y * iw + x;
                for (int ty = 0; ty < th; ty++) {
                    int off = row + ty * iw;
                    int toff = ty * tw;
                    for (int tx = 0; tx < tw; tx++) {
                        double v = g[off + tx] & 0xff;
                        sum += v;
                        sq += v * v;
                        dot += v * td[toff + tx];
                    }
                }
                double meanI = sum / n;
                double varI = sq - sum * meanI;
                if (varI < 200.0) continue;
                double corr = dot - sum * t.mean;
                float score = (float) (corr / (Math.sqrt(varI) * t.norm));
                if (score > best) {
                    best = score;
                    bx = x;
                    by = y;
                }
            }
        }
        if (best <= -1.5f) return null;
        return new Hit(best, bx + tw / 2f, by + th / 2f);
    }

    /** 两帧的粗略差异（0~255），用于判断页面动画是否结束 */
    public static float diff(Prepared a, Prepared b) {
        if (a == null || b == null || a.w != b.w || a.h != b.h) return 999f;
        long s = 0;
        int cnt = 0;
        for (int i = 0; i < a.g.length; i += 7) {
            s += Math.abs((a.g[i] & 0xff) - (b.g[i] & 0xff));
            cnt++;
        }
        return cnt == 0 ? 999f : s / (float) cnt;
    }
}
