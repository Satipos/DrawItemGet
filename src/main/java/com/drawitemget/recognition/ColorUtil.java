package com.drawitemget.recognition;

/** Tiny color-math helpers used by the classifier. */
public final class ColorUtil {
    private ColorUtil() {}

    public static int a(int argb) { return (argb >>> 24) & 0xFF; }
    public static int r(int argb) { return (argb >>> 16) & 0xFF; }
    public static int g(int argb) { return (argb >>>  8) & 0xFF; }
    public static int b(int argb) { return argb & 0xFF; }

    /** Returns true if the pixel is considered background (transparent or near-white). */
    public static boolean isBackground(int argb) {
        int a = a(argb);
        if (a < 32) return true;
        int r = r(argb), g = g(argb), b = b(argb);
        // Near-white canvas.
        if (r > 235 && g > 235 && b > 235) return true;
        return false;
    }

    /**
     * HSV from 0xRRGGBB. h in degrees [0,360), s,v in [0,1].
     */
    public static float[] rgbToHsv(int r, int g, int b) {
        float rn = r / 255f, gn = g / 255f, bn = b / 255f;
        float max = Math.max(rn, Math.max(gn, bn));
        float min = Math.min(rn, Math.min(gn, bn));
        float v = max;
        float d = max - min;
        float s = (max == 0) ? 0 : d / max;
        float h;
        if (d < 1e-6f) h = 0;
        else if (max == rn) h = 60f * (((gn - bn) / d) % 6f);
        else if (max == gn) h = 60f * (((bn - rn) / d) + 2f);
        else h = 60f * (((rn - gn) / d) + 4f);
        if (h < 0) h += 360;
        return new float[]{h, s, v};
    }

    /** Human-friendly bucket name for the classifier log. */
    public static String hueBucket(float h, float s, float v) {
        if (v < 0.12f) return "black";
        if (s < 0.12f) {
            if (v > 0.85f) return "white";
            if (v > 0.55f) return "light_gray";
            return "gray";
        }
        if (h < 15 || h >= 345) return "red";
        if (h < 45) return "orange";
        if (h < 70) return "yellow";
        if (h < 170) return "green";
        if (h < 210) return "cyan";
        if (h < 260) return "blue";
        if (h < 300) return "purple";
        return "pink";
    }
}
