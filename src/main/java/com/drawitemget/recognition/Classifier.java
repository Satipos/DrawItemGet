package com.drawitemget.recognition;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based "AI" that turns a drawing into a Minecraft item.
 *
 * <p>The rules are ordered by specificity — the first matching rule wins.
 * Each rule has a confidence score, which we report back to the player so the
 * UI can show a "91% sure you drew …" hint.</p>
 */
public final class Classifier {

    public record Result(Item item, String displayId, float confidence) {}

    @FunctionalInterface
    private interface Rule {
        /** Returns confidence ≥ 0 when the drawing matches; 0 = no match. */
        float score(ShapeFeatures f);
    }

    private record Template(Rule rule, Item item, String id) {}

    private static final List<Template> TEMPLATES = new ArrayList<>();

    public static void warmup() {
        if (!TEMPLATES.isEmpty()) return;

        // --- fiery / red / orange shapes --------------------------------

        add("minecraft:torch", Items.TORCH, f ->
                f.ratioOf("yellow") + f.ratioOf("orange") > 0.25f
                && f.aspect() < 0.7f && f.densityInBox() > 0.2f
                && (f.maxY() - f.minY()) >= 18
                ? 0.92f : 0);

        add("minecraft:apple", Items.APPLE, f ->
                f.ratioOf("red") > 0.45f
                && f.aspect() > 0.6f && f.aspect() < 1.45f
                && f.densityInBox() > 0.55f
                && f.verticalSymmetry() > 0.62f
                ? 0.93f : 0);

        add("minecraft:blaze_rod", Items.BLAZE_ROD, f ->
                f.ratioOf("yellow") > 0.35f
                && f.aspect() < 0.4f && f.densityInBox() > 0.3f
                ? 0.88f : 0);

        add("minecraft:fire_charge", Items.FIRE_CHARGE, f ->
                (f.ratioOf("red") + f.ratioOf("orange") > 0.5f)
                && f.aspect() > 0.7f && f.aspect() < 1.3f
                && f.verticalSymmetry() > 0.6f
                ? 0.85f : 0);

        // --- green shapes ------------------------------------------------

        add("minecraft:oak_leaves", Items.OAK_LEAVES, f ->
                f.ratioOf("green") > 0.55f
                && f.densityInBox() > 0.45f
                ? 0.9f : 0);

        add("minecraft:melon", Items.MELON, f ->
                f.ratioOf("green") > 0.35f
                && f.aspect() > 0.75f && f.aspect() < 1.35f
                && f.densityInBox() > 0.6f
                ? 0.84f : 0);

        add("minecraft:emerald", Items.EMERALD, f ->
                f.ratioOf("green") > 0.5f
                && f.aspect() > 0.5f && f.aspect() < 0.9f
                && f.verticalSymmetry() > 0.7f
                && f.densityInBox() > 0.35f
                ? 0.82f : 0);

        // --- blue / water ------------------------------------------------

        add("minecraft:water_bucket", Items.WATER_BUCKET, f ->
                f.ratioOf("blue") + f.ratioOf("cyan") > 0.4f
                && f.aspect() < 1.2f
                ? 0.87f : 0);

        add("minecraft:diamond", Items.DIAMOND, f ->
                f.ratioOf("cyan") > 0.45f
                && f.aspect() > 0.6f && f.aspect() < 1.4f
                && f.verticalSymmetry() > 0.72f
                ? 0.9f : 0);

        // --- yellow / gold -----------------------------------------------

        add("minecraft:gold_ingot", Items.GOLD_INGOT, f ->
                f.ratioOf("yellow") > 0.5f
                && f.aspect() > 1.3f
                && f.densityInBox() > 0.55f
                ? 0.89f : 0);

        add("minecraft:wheat", Items.WHEAT, f ->
                (f.ratioOf("yellow") + f.ratioOf("orange")) > 0.45f
                && f.aspect() < 0.7f
                ? 0.78f : 0);

        add("minecraft:egg", Items.EGG, f ->
                f.ratioOf("white") + f.ratioOf("light_gray") > 0.55f
                && f.aspect() > 0.6f && f.aspect() < 1.1f
                && f.verticalSymmetry() > 0.75f
                ? 0.82f : 0);

        // --- brown / wood ------------------------------------------------

        add("minecraft:oak_planks", Items.OAK_PLANKS, f -> {
            int avgR = f.avgR(), avgG = f.avgG(), avgB = f.avgB();
            boolean brown = avgR > 120 && avgG > 70 && avgB < 90 && avgR > avgG && avgG > avgB;
            return (brown && f.densityInBox() > 0.6f && f.aspect() > 0.85f && f.aspect() < 1.2f) ? 0.87f : 0;
        });

        add("minecraft:stick", Items.STICK, f -> {
            int avgR = f.avgR(), avgG = f.avgG(), avgB = f.avgB();
            boolean brown = avgR > 110 && avgG > 60 && avgB < 90 && avgR > avgG && avgG > avgB;
            return (brown && f.aspect() < 0.4f && f.densityInBox() > 0.25f) ? 0.85f : 0;
        });

        // --- gray / metal ------------------------------------------------

        add("minecraft:iron_ingot", Items.IRON_INGOT, f ->
                (f.ratioOf("light_gray") + f.ratioOf("white")) > 0.55f
                && f.aspect() > 1.3f
                && f.densityInBox() > 0.55f
                ? 0.88f : 0);

        add("minecraft:cobblestone", Items.COBBLESTONE, f ->
                f.ratioOf("gray") > 0.45f
                && f.aspect() > 0.8f && f.aspect() < 1.25f
                && f.densityInBox() > 0.7f
                ? 0.82f : 0);

        // --- black ------------------------------------------------------

        add("minecraft:coal", Items.COAL, f ->
                f.ratioOf("black") > 0.5f
                && f.aspect() > 0.6f && f.aspect() < 1.6f
                && f.densityInBox() > 0.45f
                ? 0.87f : 0);

        add("minecraft:ink_sac", Items.INK_SAC, f ->
                f.ratioOf("black") > 0.55f
                && f.aspect() > 0.7f && f.aspect() < 1.3f
                && f.verticalSymmetry() > 0.65f
                ? 0.8f : 0);

        // --- purple / magic ---------------------------------------------

        add("minecraft:amethyst_shard", Items.AMETHYST_SHARD, f ->
                f.ratioOf("purple") > 0.45f
                && f.aspect() < 1.2f
                ? 0.86f : 0);

        add("minecraft:ender_pearl", Items.ENDER_PEARL, f ->
                (f.ratioOf("cyan") + f.ratioOf("green") + f.ratioOf("purple")) > 0.45f
                && f.aspect() > 0.75f && f.aspect() < 1.25f
                && f.verticalSymmetry() > 0.75f
                ? 0.8f : 0);

        // --- pink / flower ----------------------------------------------

        add("minecraft:poppy", Items.POPPY, f ->
                (f.ratioOf("red") + f.ratioOf("pink")) > 0.4f
                && f.ratioOf("green") > 0.08f
                && f.aspect() < 1.1f
                ? 0.78f : 0);

        add("minecraft:rose_bush", Items.ROSE_BUSH, f ->
                f.ratioOf("pink") > 0.35f
                ? 0.74f : 0);

        // --- white / bone -----------------------------------------------

        add("minecraft:bone", Items.BONE, f ->
                (f.ratioOf("white") + f.ratioOf("light_gray")) > 0.6f
                && f.aspect() > 1.2f
                && f.densityInBox() > 0.35f
                ? 0.83f : 0);

        // --- multi-color / colorful -------------------------------------

        add("minecraft:bundle", Items.BUNDLE, f -> {
            int distinct = 0;
            for (var e : f.bucketCounts().entrySet()) {
                if (e.getValue() > Math.max(4, f.pixelsPainted() / 20)) distinct++;
            }
            return distinct >= 4 ? 0.7f : 0;
        });

        // --- fallbacks by dominant color --------------------------------
        // Lower confidence, kept as a last resort so /draw always grants *something*.

        add("minecraft:red_dye", Items.RED_DYE,    f -> "red".equals(f.dominantBucket())    ? 0.45f : 0);
        add("minecraft:orange_dye", Items.ORANGE_DYE, f -> "orange".equals(f.dominantBucket()) ? 0.45f : 0);
        add("minecraft:yellow_dye", Items.YELLOW_DYE, f -> "yellow".equals(f.dominantBucket()) ? 0.45f : 0);
        add("minecraft:lime_dye", Items.LIME_DYE,   f -> "green".equals(f.dominantBucket())   ? 0.45f : 0);
        add("minecraft:cyan_dye", Items.CYAN_DYE,   f -> "cyan".equals(f.dominantBucket())    ? 0.45f : 0);
        add("minecraft:blue_dye", Items.BLUE_DYE,   f -> "blue".equals(f.dominantBucket())    ? 0.45f : 0);
        add("minecraft:purple_dye", Items.PURPLE_DYE, f -> "purple".equals(f.dominantBucket()) ? 0.45f : 0);
        add("minecraft:pink_dye", Items.PINK_DYE,   f -> "pink".equals(f.dominantBucket())    ? 0.45f : 0);
        add("minecraft:black_dye", Items.BLACK_DYE, f -> "black".equals(f.dominantBucket())   ? 0.45f : 0);
        add("minecraft:white_dye", Items.WHITE_DYE, f -> "white".equals(f.dominantBucket())   ? 0.45f : 0);
        add("minecraft:gray_dye",  Items.GRAY_DYE,  f -> ("gray".equals(f.dominantBucket())
                                                       || "light_gray".equals(f.dominantBucket())) ? 0.45f : 0);
    }

    private static void add(String id, Item item, Rule rule) {
        TEMPLATES.add(new Template(rule, item, id));
    }

    /** Analyse a {@code size × size} grid and return the best match. */
    public static Result classify(int[] pixels, int size) {
        ShapeFeatures f = ShapeFeatures.extract(pixels, size);
        if (f.isBlank()) {
            return new Result(Items.PAPER, "minecraft:paper", 0.1f);
        }
        warmup();

        float bestScore = 0f;
        Item bestItem = null;
        String bestId = null;
        for (Template t : TEMPLATES) {
            float s = t.rule.score(f);
            if (s > bestScore) {
                bestScore = s;
                bestItem = t.item;
                bestId = t.id;
            }
        }

        // High-confidence specific-item rule wins outright.
        if (bestItem != null && bestScore >= 0.7f) {
            return new Result(bestItem, bestId, bestScore);
        }

        // Otherwise try the wide block-by-color index (covers ~600+ blocks).
        BlockColorIndex.Match blockMatch = BlockColorIndex.findClosest(f.avgR(), f.avgG(), f.avgB());
        if (blockMatch != null && blockMatch.entry() != null) {
            int dist = blockMatch.distance();
            // 0  px → 0.95 confidence; 80 px → 0.55; >120 px → fall through.
            if (dist <= 120) {
                float conf = 0.95f - (dist / 200.0f);
                if (conf < 0.5f) conf = 0.5f;
                if (bestItem == null || conf > bestScore) {
                    BlockColorIndex.Entry e = blockMatch.entry();
                    return new Result(e.item(), e.id(), conf);
                }
            }
        }

        // Specific rule with sub-threshold score wins over no match at all.
        if (bestItem != null) {
            return new Result(bestItem, bestId, bestScore);
        }

        // Extreme fallback — dye based on average color.
        Identifier dyeId = dyeForBucket(f.dominantBucket());
        Item dye = BuiltInRegistries.ITEM.getValue(dyeId);
        if (dye == Items.AIR) dye = Items.WHITE_DYE;
        return new Result(dye, dyeId.toString(), 0.2f);
    }

    private static Identifier dyeForBucket(String bucket) {
        String dye = switch (bucket) {
            case "red" -> "red_dye";
            case "orange" -> "orange_dye";
            case "yellow" -> "yellow_dye";
            case "green" -> "lime_dye";
            case "cyan" -> "cyan_dye";
            case "blue" -> "blue_dye";
            case "purple" -> "purple_dye";
            case "pink" -> "pink_dye";
            case "black" -> "black_dye";
            case "white" -> "white_dye";
            default -> "gray_dye";
        };
        return Identifier.fromNamespaceAndPath("minecraft", dye);
    }

    private Classifier() {}
}
