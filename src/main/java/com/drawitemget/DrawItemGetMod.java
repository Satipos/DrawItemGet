package com.drawitemget;

import com.drawitemget.net.DrawNetwork;
import com.drawitemget.net.DrawServerReceivers;
import com.drawitemget.recognition.Classifier;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entrypoint. Registers network payloads, the /draw command, the
 * server-side submission receiver, and pre-warms the classifier templates.
 */
public class DrawItemGetMod implements ModInitializer {
    public static final String MOD_ID = "drawitemget";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[{}] Initializing Draw = Items", MOD_ID);
        DrawNetwork.registerCommon();
        DrawServerReceivers.register();
        DrawCommands.register();
        Classifier.warmup();
    }
}
