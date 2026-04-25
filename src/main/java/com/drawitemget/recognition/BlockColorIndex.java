package com.drawitemget.recognition;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Auto-built color index over every Block in the registry.
 *
 * <p>Each block in vanilla Minecraft (and any other registered mod) has a
 * {@link MapColor} that captures the dominant color used by maps. We use those
 * colors as a "block fingerprint" so the classifier can match a user's drawing
 * to ~600+ blocks without us having to define a rule for each one by hand.</p>
 */
public final class BlockColorIndex {

    public record Entry(Item item, String id, int r, int g, int b) {}

    /**
     * Iconic / canonical blocks that should win close-distance ties in the
     * color match. Without this bias the registry order picks weird obscure
     * blocks (e.g. {@code petrified_oak_slab}) over the obvious choice
     * (e.g. {@code oak_planks}) whenever map colors collide.
     */
    private static final Set<String> PREFERRED = Set.of(
            // wools (one per color — most "drawn block" interpretation)
            "minecraft:white_wool", "minecraft:orange_wool", "minecraft:magenta_wool",
            "minecraft:light_blue_wool", "minecraft:yellow_wool", "minecraft:lime_wool",
            "minecraft:pink_wool", "minecraft:gray_wool", "minecraft:light_gray_wool",
            "minecraft:cyan_wool", "minecraft:purple_wool", "minecraft:blue_wool",
            "minecraft:brown_wool", "minecraft:green_wool", "minecraft:red_wool",
            "minecraft:black_wool",
            // canonical building blocks
            "minecraft:stone", "minecraft:cobblestone", "minecraft:dirt", "minecraft:grass_block",
            "minecraft:sand", "minecraft:gravel", "minecraft:clay", "minecraft:obsidian",
            "minecraft:bedrock", "minecraft:netherrack", "minecraft:end_stone",
            "minecraft:snow_block", "minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice",
            // wood planks (one per family beats slabs/stairs/doors of same color)
            "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks",
            "minecraft:jungle_planks", "minecraft:acacia_planks", "minecraft:dark_oak_planks",
            "minecraft:cherry_planks", "minecraft:pale_oak_planks", "minecraft:mangrove_planks",
            "minecraft:bamboo_planks", "minecraft:crimson_planks", "minecraft:warped_planks",
            // logs
            "minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log",
            "minecraft:jungle_log", "minecraft:acacia_log", "minecraft:dark_oak_log",
            "minecraft:cherry_log", "minecraft:pale_oak_log", "minecraft:mangrove_log",
            // leaves
            "minecraft:oak_leaves", "minecraft:spruce_leaves", "minecraft:birch_leaves",
            // ores & metal blocks
            "minecraft:coal_block", "minecraft:iron_block", "minecraft:gold_block",
            "minecraft:diamond_block", "minecraft:emerald_block", "minecraft:redstone_block",
            "minecraft:lapis_block", "minecraft:netherite_block", "minecraft:copper_block",
            "minecraft:amethyst_block",
            // ore blocks themselves (so a player drawing "ore" gets ore not just block)
            "minecraft:coal_ore", "minecraft:iron_ore", "minecraft:gold_ore",
            "minecraft:diamond_ore", "minecraft:emerald_ore", "minecraft:redstone_ore",
            "minecraft:lapis_ore", "minecraft:copper_ore",
            "minecraft:deepslate", "minecraft:cobbled_deepslate",
            // quartz/concrete/terracotta canonicals (white/orange/etc dye-color blocks)
            "minecraft:quartz_block",
            "minecraft:white_concrete", "minecraft:orange_concrete", "minecraft:magenta_concrete",
            "minecraft:light_blue_concrete", "minecraft:yellow_concrete", "minecraft:lime_concrete",
            "minecraft:pink_concrete", "minecraft:gray_concrete", "minecraft:light_gray_concrete",
            "minecraft:cyan_concrete", "minecraft:purple_concrete", "minecraft:blue_concrete",
            "minecraft:brown_concrete", "minecraft:green_concrete", "minecraft:red_concrete",
            "minecraft:black_concrete",
            "minecraft:terracotta",
            // farming / nature
            "minecraft:hay_block", "minecraft:pumpkin", "minecraft:melon", "minecraft:moss_block",
            "minecraft:mycelium", "minecraft:podzol", "minecraft:rooted_dirt",
            "minecraft:bookshelf", "minecraft:crafting_table", "minecraft:furnace",
            "minecraft:glass", "minecraft:glowstone", "minecraft:sea_lantern",
            "minecraft:sponge", "minecraft:tnt", "minecraft:cake",
            // nether
            "minecraft:nether_bricks", "minecraft:basalt", "minecraft:blackstone",
            "minecraft:soul_sand", "minecraft:soul_soil", "minecraft:magma_block",
            // end / chorus
            "minecraft:purpur_block", "minecraft:end_stone_bricks"
    );

    private static List<Entry> ENTRIES;

    public static synchronized List<Entry> get() {
        if (ENTRIES != null) return ENTRIES;
        List<Entry> list = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Item item = block.asItem();
            if (item == null || item == Items.AIR) continue;
            MapColor mc;
            try {
                mc = block.defaultMapColor();
            } catch (Throwable t) {
                continue;
            }
            if (mc == null || mc == MapColor.NONE) continue;
            int rgb = mc.col;
            if (rgb == 0) continue;
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            list.add(new Entry(item, id, r, g, b));
        }
        ENTRIES = list;
        return list;
    }

    /**
     * Returns the closest block (by RGB Euclidean distance) plus that distance.
     * Returns {@code null} when the index is empty.
     */
    public static Match findClosest(int r, int g, int b) {
        List<Entry> list = get();
        if (list.isEmpty()) return null;
        Entry best = null;
        // Bias preferred blocks down by ~10 px in linear distance — keeps
        // canonical iconic blocks ahead of look-alike slabs/stairs/walls.
        final int PREFERRED_BIAS_SQ = 10 * 10;
        int bestAdjSq = Integer.MAX_VALUE;
        int bestRawSq = Integer.MAX_VALUE;
        for (Entry e : list) {
            int dr = e.r - r, dg = e.g - g, db = e.b - b;
            int rawSq = dr * dr + dg * dg + db * db;
            int adjSq = rawSq;
            if (PREFERRED.contains(e.id)) adjSq = Math.max(0, rawSq - PREFERRED_BIAS_SQ);
            if (adjSq < bestAdjSq) {
                bestAdjSq = adjSq;
                bestRawSq = rawSq;
                best = e;
            }
        }
        return new Match(best, (int) Math.sqrt(bestRawSq));
    }

    public record Match(Entry entry, int distance) {}

    private BlockColorIndex() {}
}
