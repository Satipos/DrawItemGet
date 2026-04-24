package com.drawitemget.net;

import com.drawitemget.DrawItemGetMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payloads for the /draw flow.
 *
 * <ul>
 *   <li>{@link OpenDrawScreenPayload} — S2C, open the drawing UI.</li>
 *   <li>{@link SubmitDrawingPayload} — C2S, the player submitted a canvas for recognition.</li>
 *   <li>{@link GrantedItemPayload} — S2C, echo back what was recognized + granted.</li>
 * </ul>
 */
public final class DrawNetwork {
    private DrawNetwork() {}

    public static Identifier id(String p) {
        return Identifier.fromNamespaceAndPath(DrawItemGetMod.MOD_ID, p);
    }

    public static final int CANVAS_SIZE = 64;

    public record OpenDrawScreenPayload() implements CustomPacketPayload {
        public static final Type<OpenDrawScreenPayload> TYPE = new Type<>(id("open_draw"));
        public static final StreamCodec<FriendlyByteBuf, OpenDrawScreenPayload> CODEC =
                StreamCodec.unit(new OpenDrawScreenPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Compact canvas transfer: width and height are fixed at {@link #CANVAS_SIZE}, pixels
     * are sent as raw ARGB ints. Total ≈ 16 KB per submission, which is fine for an
     * on-demand user action.
     */
    public record SubmitDrawingPayload(int[] pixels) implements CustomPacketPayload {
        public static final Type<SubmitDrawingPayload> TYPE = new Type<>(id("submit_drawing"));
        public static final StreamCodec<FriendlyByteBuf, SubmitDrawingPayload> CODEC =
                CustomPacketPayload.codec(SubmitDrawingPayload::write, SubmitDrawingPayload::new);

        public SubmitDrawingPayload(FriendlyByteBuf buf) {
            this(readPixels(buf));
        }

        private static int[] readPixels(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            if (n != CANVAS_SIZE * CANVAS_SIZE) {
                // Skip remaining and return transparent canvas.
                int[] empty = new int[CANVAS_SIZE * CANVAS_SIZE];
                for (int i = 0; i < n; i++) buf.readInt();
                return empty;
            }
            int[] arr = new int[n];
            for (int i = 0; i < n; i++) arr[i] = buf.readInt();
            return arr;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(pixels.length);
            for (int p : pixels) buf.writeInt(p);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record GrantedItemPayload(String itemId, String displayName, float confidence)
            implements CustomPacketPayload {
        public static final Type<GrantedItemPayload> TYPE = new Type<>(id("granted_item"));
        public static final StreamCodec<FriendlyByteBuf, GrantedItemPayload> CODEC =
                CustomPacketPayload.codec(GrantedItemPayload::write, GrantedItemPayload::new);

        public GrantedItemPayload(FriendlyByteBuf buf) {
            this(buf.readUtf(256), buf.readUtf(256), buf.readFloat());
        }
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(itemId, 256);
            buf.writeUtf(displayName, 256);
            buf.writeFloat(confidence);
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void registerCommon() {
        PayloadTypeRegistry.clientboundPlay().register(OpenDrawScreenPayload.TYPE, OpenDrawScreenPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GrantedItemPayload.TYPE, GrantedItemPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SubmitDrawingPayload.TYPE, SubmitDrawingPayload.CODEC);
    }
}
