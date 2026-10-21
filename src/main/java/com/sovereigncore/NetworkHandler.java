package com.sovereigncore;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.function.Supplier;

public final class NetworkHandler {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SovereignCore.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);
    private static int nextId = 0;
    private static boolean registered = false;

    private NetworkHandler() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(nextId++, ActionPacket.class, ActionPacket::encode, ActionPacket::decode,
                ActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, UiPacket.class, UiPacket::encode, UiPacket::decode,
                UiPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendAction(String action, String arg1, String arg2) {
        CHANNEL.sendToServer(new ActionPacket(action, arg1, arg2));
    }

    public static final class ActionPacket {
        private final String action;
        private final String arg1;
        private final String arg2;
        public ActionPacket(String action, String arg1, String arg2) {
            this.action = limit(action, 64); this.arg1 = limit(arg1, 1024); this.arg2 = limit(arg2, 1024);
        }
        public static void encode(ActionPacket packet, FriendlyByteBuf buffer) {
            buffer.writeUtf(packet.action, 64); buffer.writeUtf(packet.arg1, 1024); buffer.writeUtf(packet.arg2, 1024);
        }
        public static ActionPacket decode(FriendlyByteBuf buffer) {
            return new ActionPacket(buffer.readUtf(64), buffer.readUtf(1024), buffer.readUtf(1024));
        }
        public static void handle(ActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer sender = context.getSender();
                if (sender != null) SovereignCore.handleAction(sender, packet.action, packet.arg1, packet.arg2);
            });
            context.setPacketHandled(true);
        }
    }

    public static final class UiPacket {
        private final String page;
        private final String data;
        public UiPacket(String page, String data) { this.page = limit(page, 64); this.data = limit(data, 30000); }
        public static void encode(UiPacket packet, FriendlyByteBuf buffer) { buffer.writeUtf(packet.page, 64); buffer.writeUtf(packet.data, 30000); }
        public static UiPacket decode(FriendlyByteBuf buffer) { return new UiPacket(buffer.readUtf(64), buffer.readUtf(30000)); }
        public static void handle(UiPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientScreens.open(packet.page, packet.data)));
            context.setPacketHandled(true);
        }
    }

    private static String limit(String value, int length) {
        if (value == null) return "";
        return value.length() <= length ? value : value.substring(0, length);
    }
}
