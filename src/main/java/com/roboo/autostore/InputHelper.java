package com.roboo.autostore;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

public class InputHelper {

    private static final Minecraft mc = Minecraft.getInstance();

    private static boolean rightClickHeld = false;

    // =========================
    // HOLD RIGHT CLICK
    // =========================
    public static void holdRightClick(boolean hold) {
        rightClickHeld = hold;

        if (mc.options == null) return;

        mc.options.keyUse.setDown(hold);
    }

    // =========================
    // STOP ALL INPUTS
    // =========================
    public static void stopAll() {
        rightClickHeld = false;

        if (mc.options == null) return;
        mc.options.keyUse.setDown(false);
    }

    // =========================
    // OPTIONAL MANUAL TICK (NOT REQUIRED ANYMORE)
    // =========================
    public static void tick() {
        // Intentionally left empty or reserved for future logic
        // DO NOT spam useItem here anymore
    }
}