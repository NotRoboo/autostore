package com.roboo.autostore;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Deque;

public class HudHelper {

    private static final Minecraft mc = Minecraft.getInstance();

    public static void register(AutoStoreClient.StateRef stateRef, Deque<Integer> clickQueue, long[] nextCycleTime) {

        // AutoStore HUD
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("autostore", "hud"),
                (graphics, tickCounter) -> {
                    if (mc.player == null || !cfg().hudVisible) return;

                    long remaining = Math.max(0, (nextCycleTime[0] - System.currentTimeMillis()) / 1000);

                    graphics.drawString(mc.font,
                            "§7AutoStore: " + (AutoStoreClient.enabled ? "§aON" : "§cOFF"),
                            cfg().hudX, cfg().hudY, 0xFFFFFFFF, true);

                    if (AutoStoreClient.enabled) {
                        String detail = switch (stateRef.get()) {
                            case IDLE, WAITING_TO_OPEN -> "§7Next in §e" + remaining + "s";
                            case OPENING               -> "§7Opening bag...";
                            case DRAINING              -> "§7Depositing... §e(" + clickQueue.size() + " left)";
                            case RESCANNING            -> "§7Rescanning...";
                        };
                        graphics.drawString(mc.font, detail, cfg().hudX, cfg().hudY + 10, 0xFFFFFFFF, true);
                    }
                }
        );

        // Sprint HUD
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("autostore", "sprint_hud"),
                (graphics, tickCounter) -> {
                    if (mc.player == null || !cfg().sprintHudVisible) return;

                    boolean toggled = mc.options.toggleSprint().get();
                    boolean keyDown = mc.options.keySprint.isDown();
                    boolean sprinting = mc.player.isSprinting();

                    String mode = null;
                    boolean active = false;

                    if (toggled && (keyDown || sprinting)) {
                        active = true;
                        mode = "Toggled";
                    } else if (keyDown || sprinting) {
                        active = true;
                        mode = "Holding";
                    }

                    String status = (active ? "§aON" : "§cOFF") + (mode != null ? " §a(" + mode + ")" : "");
                    graphics.drawString(mc.font,
                            "§7Sprint: " + status,
                            cfg().sprintHudX, cfg().sprintHudY, 0xFFFFFFFF, true);
                }
        );

        // Right-click HUD
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("autostore", "use_hud"),
                (graphics, tickCounter) -> {
                    if (mc.player == null || !cfg().useHudVisible) return;

                    boolean toggleUse = mc.options.toggleUse().get();
                    boolean keyDown = mc.options.keyUse.isDown();
                    boolean usingItem = mc.player.isUsingItem();

                    String mode = null;
                    boolean active = false;

                    if (toggleUse && (keyDown || usingItem)) {
                        active = true;
                        mode = "Toggled";
                    } else if (keyDown || usingItem) {
                        active = true;
                        mode = "Holding";
                    }

                    String status = (active ? "§aON" : "§cOFF") + (mode != null ? " §a(" + mode + ")" : "");
                    graphics.drawString(mc.font,
                            "§7Right Click: " + status,
                            cfg().useHudX, cfg().useHudY, 0xFFFFFFFF, true);
                }
        );
    }

    private static ModConfig cfg() { return ModConfig.get(); }
}