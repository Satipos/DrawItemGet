package com.drawitemget.net;

import com.drawitemget.recognition.Classifier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Handles incoming {@link DrawNetwork.SubmitDrawingPayload} — runs the drawing
 * through {@link Classifier} and gives the resulting item to the player.
 */
public final class DrawServerReceivers {
    private DrawServerReceivers() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(DrawNetwork.SubmitDrawingPayload.TYPE,
                (payload, ctx) -> ctx.server().execute(() -> {
                    ServerPlayer player = ctx.player();
                    if (payload.pixels().length != DrawNetwork.CANVAS_SIZE * DrawNetwork.CANVAS_SIZE) return;

                    Classifier.Result result = Classifier.classify(payload.pixels(), DrawNetwork.CANVAS_SIZE);
                    ItemStack stack = new ItemStack(result.item());

                    // Hand the item to the player — mirrors /give behavior.
                    boolean gave = player.getInventory().add(stack.copy());
                    if (!gave) {
                        player.drop(stack, false);
                    }

                    String displayName = stack.getHoverName().getString();
                    ServerPlayNetworking.send(player,
                            new DrawNetwork.GrantedItemPayload(result.displayId(), displayName, result.confidence()));

                    // Also echo to chat for persistence.
                    Component msg = Component.literal("✎ ")
                            .withStyle(ChatFormatting.LIGHT_PURPLE)
                            .append(Component.translatable("drawitemget.msg.recognized")
                                    .withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(" "))
                            .append(stack.getHoverName().copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                            .append(Component.literal(" — ")
                                    .withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal(String.format("%.0f%%", result.confidence() * 100))
                                    .withStyle(ChatFormatting.AQUA));
                    player.sendSystemMessage(msg);
                }));
    }
}
