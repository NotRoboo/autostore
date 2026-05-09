package com.roboo.autostore;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.context.UseOnContext;

public class InventoryHelper {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final String MATERIAL_BAG_NAME = "Material Bag";

    private static int cachedSlot = -1;

    // =========================
    // FIND MATERIAL BAG IN HOTBAR
    // =========================
    public static int findMaterialBagSlot() {
        if (mc.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);

            if (!stack.isEmpty()) {
                String name = stack.getHoverName().getString();

                if (name.equals(MATERIAL_BAG_NAME) || name.contains(MATERIAL_BAG_NAME)) {
                    return i;
                }
            }
        }

        return -1;
    }

    // =========================
    // CACHE CURRENT SELECTED SLOT
    // =========================
    public static void cacheCurrentSlot() {
        if (mc.player == null) return;

        cachedSlot = mc.player.getInventory().getSelectedSlot();
    }

    // =========================
    // RESTORE SLOT
    // =========================
    public static void restoreSlot() {
        if (mc.player == null) return;
        if (cachedSlot == -1) return;

        mc.player.getInventory().setSelectedSlot(cachedSlot);
        cachedSlot = -1;
    }

    // =========================
    // SELECT SLOT
    // =========================
    public static void selectSlot(int slot) {
        if (mc.player == null) return;
        if (slot < 0 || slot > 8) return;

        mc.player.getInventory().setSelectedSlot(slot);
    }

    // =========================
    // USE ITEM (RIGHT CLICK)
    // =========================
    public static void useItem() {
        if (mc.player == null || mc.gameMode == null) return;

        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    // =========================
    // CLICK SLOT IN OPEN CONTAINER
    // =========================
    public static void clickSlot(int slotId, ClickType actionType, int button) {
        if (mc.player == null || mc.gameMode == null) return;

        var handler = mc.player.containerMenu;
        if (handler == null) return;

        mc.gameMode.handleInventoryMouseClick(
                handler.containerId,
                slotId,
                button,
                actionType,
                mc.player
        );
    }
}