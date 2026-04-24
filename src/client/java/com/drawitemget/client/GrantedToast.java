package com.drawitemget.client;

import com.drawitemget.DrawItemGetMod;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A big animated banner that appears center-screen when the server sends a
 * {@link com.drawitemget.net.DrawNetwork.GrantedItemPayload}. Shows the item
 * icon + display name + confidence, fades in then out.
 */
public final class GrantedToast {
    private GrantedToast() {}

    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(DrawItemGetMod.MOD_ID, "granted_toast");

    private static long shownAtMs = 0L;
    private static String itemId = "";
    private static String displayName = "";
    private static float confidence = 0f;
    private static ItemStack iconStack = ItemStack.EMPTY;

    private static final long DURATION_MS = 3600L;

    public static void register() {
        HudElementRegistry.addLast(HUD_ID, (HudElement) (g, tick) -> {
            if (shownAtMs == 0L) return;
            long now = System.currentTimeMillis();
            long elapsed = now - shownAtMs;
            if (elapsed > DURATION_MS) { shownAtMs = 0; return; }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            int w = g.guiWidth(), h = g.guiHeight();
            int bw = 220, bh = 62;
            int x0 = (w - bw) / 2;
            int y0 = (int) (h * 0.20f);

            float t = elapsed / (float) DURATION_MS;
            float alpha;
            if (t < 0.08f) alpha = t / 0.08f;
            else if (t > 0.75f) alpha = 1f - (t - 0.75f) / 0.25f;
            else alpha = 1f;
            alpha = Math.max(0f, Math.min(1f, alpha));
            int aMask = (int)(0xFF * alpha) << 24;

            // Outer glow.
            for (int k = 10; k >= 1; k--) {
                int a = (int)((0x04 + (10 - k) * 0x06) * alpha);
                g.fill(x0 - k, y0 - k, x0 + bw + k, y0 + bh + k, (a << 24) | 0xFFD060);
            }

            // Body.
            UiFx.fancyPanel(g,
                    x0, y0, x0 + bw, y0 + bh,
                    UiFx.withAlpha(0xFF2B1860, 0xF0 & 0xFF),
                    UiFx.withAlpha(0xFF110728, 0xF0 & 0xFF),
                    UiFx.withAlpha(0xFFFFD060, (int)(0xE0 * alpha)),
                    UiFx.withAlpha(0xFFFFF2B8, (int)(0xD0 * alpha)));

            // Item icon on the left.
            if (iconStack.isEmpty()) iconStack = resolveStack(itemId);
            int iconX = x0 + 14;
            int iconY = y0 + (bh - 16) / 2;
            g.item(iconStack, iconX, iconY);
            g.itemDecorations(mc.font, iconStack, iconX, iconY);

            // Text.
            int tx = x0 + 40;
            Component heading = Component.translatable("drawitemget.ui.toast.heading")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            g.text(mc.font, heading, tx, y0 + 10, (aMask | 0xFFD060));

            Component name = Component.literal(displayName)
                    .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
            g.text(mc.font, name, tx, y0 + 24, (aMask | 0xFFFFFF));

            Component conf = Component.literal(String.format("%.0f%% ", confidence * 100))
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.translatable("drawitemget.ui.toast.confidence")
                            .withStyle(ChatFormatting.GRAY));
            g.text(mc.font, conf, tx, y0 + 40, (aMask | 0xBFA0FF));

            // Sparkle band.
            long tMs = now - shownAtMs;
            int sparkles = 8;
            for (int i = 0; i < sparkles; i++) {
                double phase = (tMs / 180.0 + i * 0.9) % (Math.PI * 2);
                int sx = x0 + 10 + (int)(bw - 20) * i / sparkles + (int)(Math.sin(phase) * 3);
                int sy = y0 + 2 + (int)(Math.abs(Math.cos(phase)) * 2);
                int sa = (int)(0xC0 * alpha);
                g.fill(sx, sy, sx + 2, sy + 2, (sa << 24) | 0xFFF2B8);
            }
        });
    }

    public static void show(String id, String name, float conf) {
        itemId = id;
        displayName = name;
        confidence = conf;
        iconStack = resolveStack(id);
        shownAtMs = System.currentTimeMillis();
    }

    private static ItemStack resolveStack(String id) {
        try {
            Identifier parsed = Identifier.parse(id);
            Item item = BuiltInRegistries.ITEM.getValue(parsed);
            if (item != null) return new ItemStack(item);
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }
}
