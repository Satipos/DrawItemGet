package com.drawitemget.recognition;

import java.util.HashMap;
import java.util.Map;

/**
 * Extracts per-drawing features from a square ARGB pixel grid.
 *
 * <p>The classifier works on a {@code size × size} canvas (typically 64×64). The
 * feature set is intentionally compact — it captures the visual properties a
 * user cares about: dominant color, rough shape, density, aspect ratio, symmetry,
 * and a small histogram of color buckets. That's enough to distinguish the ~25
 * templates the classifier knows about without needing an actual neural net.</p>
 */
public record ShapeFeatures(
        int size,
        int pixelsPainted,
        int minX, int minY, int maxX, int maxY,
        float densityInBox,      // fraction of bounding-box pixels that are painted
        float aspect,            // (bbox w) / (bbox h)
        float verticalSymmetry,  // [0..1], 1 = perfectly vertically symmetric
        float horizontalSymmetry,// [0..1]
        int dominantHueBucketId, // index 0..9, maps to the bucket list below
        String dominantBucket,
        Map<String, Integer> bucketCounts,
        int avgR, int avgG, int avgB
) {
    public static ShapeFeatures extract(int[] pixels, int size) {
        int minX = size, minY = size, maxX = -1, maxY = -1;
        int painted = 0;
        long sumR = 0, sumG = 0, sumB = 0;
        Map<String, Integer> counts = new HashMap<>();

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int p = pixels[y * size + x];
                if (ColorUtil.isBackground(p)) continue;
                painted++;
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
                int r = ColorUtil.r(p), g = ColorUtil.g(p), b = ColorUtil.b(p);
                sumR += r; sumG += g; sumB += b;
                float[] hsv = ColorUtil.rgbToHsv(r, g, b);
                String bucket = ColorUtil.hueBucket(hsv[0], hsv[1], hsv[2]);
                counts.merge(bucket, 1, Integer::sum);
            }
        }

        if (painted == 0) {
            return new ShapeFeatures(size, 0, 0, 0, 0, 0, 0, 1, 1, 1,
                    -1, "none", counts, 0, 0, 0);
        }

        int bw = Math.max(1, maxX - minX + 1);
        int bh = Math.max(1, maxY - minY + 1);
        float density = painted / (float) (bw * bh);
        float aspect = bw / (float) bh;
        int avgR = (int) (sumR / painted);
        int avgG = (int) (sumG / painted);
        int avgB = (int) (sumB / painted);

        // Symmetries.
        float vSym = verticalSymmetry(pixels, size, minX, minY, maxX, maxY);
        float hSym = horizontalSymmetry(pixels, size, minX, minY, maxX, maxY);

        // Dominant bucket.
        String dominant = "gray";
        int top = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > top) { top = e.getValue(); dominant = e.getKey(); }
        }
        int bucketId = bucketId(dominant);

        return new ShapeFeatures(size, painted,
                minX, minY, maxX, maxY,
                density, aspect, vSym, hSym, bucketId, dominant, counts,
                avgR, avgG, avgB);
    }

    private static float verticalSymmetry(int[] px, int size, int x0, int y0, int x1, int y1) {
        int good = 0, total = 0;
        int mid = (x0 + x1) / 2;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= mid; x++) {
                int xr = x1 - (x - x0);
                if (xr < 0 || xr >= size) continue;
                boolean lp = !ColorUtil.isBackground(px[y * size + x]);
                boolean rp = !ColorUtil.isBackground(px[y * size + xr]);
                total++;
                if (lp == rp) good++;
            }
        }
        return total == 0 ? 0 : good / (float) total;
    }

    private static float horizontalSymmetry(int[] px, int size, int x0, int y0, int x1, int y1) {
        int good = 0, total = 0;
        int mid = (y0 + y1) / 2;
        for (int y = y0; y <= mid; y++) {
            for (int x = x0; x <= x1; x++) {
                int yr = y1 - (y - y0);
                if (yr < 0 || yr >= size) continue;
                boolean tp = !ColorUtil.isBackground(px[y * size + x]);
                boolean bp = !ColorUtil.isBackground(px[yr * size + x]);
                total++;
                if (tp == bp) good++;
            }
        }
        return total == 0 ? 0 : good / (float) total;
    }

    public static int bucketId(String bucket) {
        return switch (bucket) {
            case "red" -> 0;
            case "orange" -> 1;
            case "yellow" -> 2;
            case "green" -> 3;
            case "cyan" -> 4;
            case "blue" -> 5;
            case "purple" -> 6;
            case "pink" -> 7;
            case "black" -> 8;
            case "white" -> 9;
            case "gray" -> 10;
            case "light_gray" -> 11;
            default -> -1;
        };
    }

    public float ratioOf(String bucket) {
        if (pixelsPainted == 0) return 0;
        return bucketCounts.getOrDefault(bucket, 0) / (float) pixelsPainted;
    }

    public boolean isBlank() { return pixelsPainted < 8; }
}
