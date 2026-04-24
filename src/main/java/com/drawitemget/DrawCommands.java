package com.drawitemget;

import com.drawitemget.net.DrawNetwork;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;

/**
 * Registers {@code /draw} — a simple command that tells the calling player's
 * client to open the drawing screen.
 */
public final class DrawCommands {
    private DrawCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, selection) -> {
            dispatcher.register(Commands.literal("draw")
                    .executes(ctx -> {
                        var player = ctx.getSource().getPlayerOrException();
                        ServerPlayNetworking.send(player, new DrawNetwork.OpenDrawScreenPayload());
                        return 1;
                    }));
        });
    }
}
