package com.drawitemget.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared helpers: gradients, rounded panels, starfield backdrops. Pure solid-fill
 * rendering, no textures required.
 */
public final class UiFx {
    private UiFx() {}

    public static int argb(int a, int r, int g, int b) { return (a << 24) | (r << 16) | (g << 8) | b; }
    public static int withAlpha(int argb, int a) { return (a << 24) | (argb & 0xFFFFFF); }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (a >>> 24) & 0xFF, ra = (a >>> 16) & 0xFF, ga = (a >>> 8) & 0xFF, ba = a & 0xFF;
        int ab = (b >>> 24) & 0xFF, rb = (b >>> 16) & 0xFF, gb = (b >>> 8) & 0xFF, bb = b & 0xFF;
        return ((int)(aa + (ab - aa) * t) << 24)
             | ((int)(ra + (rb - ra) * t) << 16)
             | ((int)(ga + (gb - ga) * t) <<  8)
             |  (int)(ba + (bb - ba) * t);
    }

    public static int darken(int argb, float f) {
        int a = (argb >>> 24) & 0xFF, r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        return (a << 24) | ((int)(r * f) << 16) | ((int)(g * f) << 8) | (int)(b * f);
    }
    public static int brighten(int argb, float f) {
        int a = (argb >>> 24) & 0xFF, r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        r = Math.min(0xFF, (int)(r + (0xFF - r) * f));
        g = Math.min(0xFF, (int)(g + (0xFF - g) * f));
        b = Math.min(0xFF, (int)(b + (0xFF - b) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void hGradient(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int left, int right) {
        int w = Math.max(1, x1 - x0);
        for (int i = 0; i < w; i++) {
            int c = lerp(left, right, (float) i / Math.max(1, w - 1));
            g.fill(x0 + i, y0, x0 + i + 1, y1, c);
        }
    }

    public static void vGradient(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int top, int bottom) {
        int h = Math.max(1, y1 - y0);
        for (int i = 0; i < h; i++) {
            int c = lerp(top, bottom, (float) i / Math.max(1, h - 1));
            g.fill(x0, y0 + i, x1, y0 + i + 1, c);
        }
    }

    public static void fancyPanel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1,
                                  int fillTop, int fillBottom, int border, int accentTop) {
        for (int k = 6; k >= 1; k--) {
            int a = 0x04 + (6 - k) * 0x06;
            g.fill(x0 - k, y0 - k, x1 + k, y1 + k, (a << 24) | 0x000000);
        }
        g.fill(x0 + 1, y0,     x1 - 1, y0 + 1, border);
        g.fill(x0 + 1, y1 - 1, x1 - 1, y1,     border);
        g.fill(x0,     y0 + 1, x0 + 1, y1 - 1, border);
        g.fill(x1 - 1, y0 + 1, x1,     y1 - 1, border);
        vGradient(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, fillTop, fillBottom);
        g.fill(x0 + 2, y0 + 1, x1 - 2, y0 + 3, accentTop);
        g.fill(x0 + 2, y0 + 3, x1 - 2, y0 + 4, withAlpha(brighten(accentTop, 0.4f), 0x80));
    }

    public static void cosmicBackdrop(GuiGraphicsExtractor g, int w, int h, int glow, long seed) {
        g.fill(0, 0, w, h, 0xFF040214);
        int cx = w / 2, cy = h / 2;
        for (int k = 0; k < 14; k++) {
            int r = Math.min(w, h) / 2 + 40 - k * 16;
            if (r <= 0) continue;
            int a = 0x03 + k * 0x02;
            g.fill(cx - r, cy - r, cx + r, cy + r, (a << 24) | (glow & 0xFFFFFF));
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < 120; i++) {
            double t = (now / 40.0 + i * (seed & 0xFF)) % 2048.0;
            int mx = (int) ((i * 83 + t * 0.7) % w);
            int my = (int) ((i * 59 + t * 0.4) % h);
            int col = switch (i % 5) {
                case 0 -> 0xFFB24CFF;
                case 1 -> 0xFFFFD060;
                case 2 -> 0xFF60C8FF;
                case 3 -> 0xFFFF80B0;
                default -> 0xFFFFFFFF;
            };
            int a = Math.max(0x20, Math.min(0xF0, 0x50 + (int)(Math.sin(t * 0.18 + i) * 0x90)));
            g.fill(mx, my, mx + 1, my + 1, (a << 24) | (col & 0xFFFFFF));
            if (i % 7 == 0) g.fill(mx + 1, my, mx + 2, my + 1, (a << 24) | (col & 0xFFFFFF));
        }
    }

    public static void fillCircle(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        int r2 = r * r;
        for (int y = -r; y <= r; y++) {
            int span = (int) Math.sqrt(r2 - y * y);
            g.fill(cx - span, cy + y, cx + span + 1, cy + y + 1, color);
        }
    }

    public static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int thickness, int color) {
        for (int a = 0; a < 360; a += 2) {
            double rad = Math.toRadians(a);
            int x = cx + (int) (Math.cos(rad) * r);
            int y = cy + (int) (Math.sin(rad) * r);
            g.fill(x - thickness / 2, y - thickness / 2, x + (thickness + 1) / 2, y + (thickness + 1) / 2, color);
        }
    }
}
