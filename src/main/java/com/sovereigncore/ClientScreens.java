package com.sovereigncore;

import net.minecraft.client.Minecraft;

public final class ClientScreens {
    private ClientScreens() {}
    public static void open(String page, String data) {
        Minecraft.getInstance().setScreen(new ManagerScreen(page, data));
    }
}
