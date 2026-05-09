package com.roboo.autostore;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;

import java.util.ArrayDeque;
import java.util.Deque;

public class AutoStoreClient implements ClientModInitializer {

    public static boolean enabled = false;
    public static int delaySeconds = 1200;

    public static int hudX = 10;
    public static int hudY = 30;

    private static final Minecraft mc = Minecraft.getInstance();

    private enum State { IDLE, WAITING_TO_OPEN, OPENING, DRAINING, RESCANNING }
    private static State state = State.IDLE;

    private static long nextCycleTime = 0;
    private static int tickCooldown = 0;

    private static final Deque<Integer> clickQueue = new ArrayDeque<>();

    @Override
    public void onInitializeClient() {

        // =========================
        // DISCONNECT / SERVER CHANGE
        // =========================
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            enabled = false;
            InputHelper.stopAll();
            state = State.IDLE;
            clickQueue.clear();
            tickCooldown = 0;
        });

        // =========================
        // COMMANDS
        // =========================
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommandManager.literal("mbag")
                            .then(ClientCommandManager.literal("toggle")
                                    .executes(ctx -> {
                                        if (!enabled) {
                                            // Only allow enabling if material bag is in hotbar
                                            if (InventoryHelper.findMaterialBagSlot() == -1) {
                                                if (mc.player != null) {
                                                    mc.player.displayClientMessage(
                                                            Component.literal("§e[AutoStore] §cNo Material Bag found in hotbar!" +
                                                                    " Please place Material Bag in hotbar and try again!"), false

                                                    );
                                                }
                                                return 1;
                                            }
                                        }
                                        enabled = !enabled;
                                        if (mc.player != null) {
                                            mc.player.displayClientMessage(
                                                    Component.literal("§e[AutoStore] " + (enabled ? "§aON" : "§cOFF")), false
                                            );
                                        }
                                        if (enabled) {
                                            nextCycleTime = System.currentTimeMillis() + (delaySeconds * 1000L);
                                            InputHelper.holdRightClick(true);
                                        } else {
                                            InputHelper.stopAll();
                                            state = State.IDLE;
                                            clickQueue.clear();
                                            tickCooldown = 0;
                                        }
                                        return 1;
                                    })
                            )
                            .then(ClientCommandManager.literal("timer")
                                    .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(5, 6000))
                                            .executes(ctx -> {
                                                delaySeconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                                if (mc.player != null) {
                                                    mc.player.displayClientMessage(
                                                            Component.literal("§e[AutoStore] §ftimer set to §e" + delaySeconds + "s"), false
                                                    );
                                                }
                                                return 1;
                                            })
                                    )
                            )
                            .then(ClientCommandManager.literal("pos")
                                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer(0, 1920))
                                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer(0, 1080))
                                                    .executes(ctx -> {
                                                        hudX = IntegerArgumentType.getInteger(ctx, "x");
                                                        hudY = IntegerArgumentType.getInteger(ctx, "y");
                                                        if (mc.player != null) {
                                                            mc.player.displayClientMessage(
                                                                    Component.literal("AutoStore HUD moved to §e" + hudX + ", " + hudY), false
                                                            );
                                                        }
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                            .then(ClientCommandManager.literal("debug")
                                    .executes(ctx -> {
                                        if (mc.player == null) return 0;

                                        var inv = mc.player.getInventory();
                                        mc.player.displayClientMessage(Component.literal("=== Player Inventory ==="), false);
                                        for (int i = 0; i < inv.getContainerSize(); i++) {
                                            var stack = inv.getItem(i);
                                            if (!stack.isEmpty() && stack.getCount() >= 64) {
                                                mc.player.displayClientMessage(Component.literal(
                                                        "Slot " + i + ": [" + stack.getHoverName().getString() + "] x" + stack.getCount()
                                                ), false);
                                            }
                                        }

                                        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
                                            var handler = mc.player.containerMenu;
                                            mc.player.displayClientMessage(Component.literal("=== GUI Slots ==="), false);
                                            for (int i = 0; i < handler.slots.size(); i++) {
                                                var stack = handler.getSlot(i).getItem();
                                                if (!stack.isEmpty()) {
                                                    mc.player.displayClientMessage(Component.literal(
                                                            "Slot " + i + ": [" + stack.getHoverName().getString() + "]"
                                                    ), false);
                                                }
                                            }

                                            // Lore debug for mode/amount buttons
                                            mc.player.displayClientMessage(Component.literal("=== Lore Debug ==="), false);
                                            for (int i = 0; i < handler.slots.size(); i++) {
                                                var stack = handler.getSlot(i).getItem();
                                                if (stack.isEmpty()) continue;
                                                String name = stack.getHoverName().getString();
                                                if (name.contains("Material Bag Mode") || name.contains("Set Amount")) {
                                                    mc.player.displayClientMessage(Component.literal(
                                                            "Slot " + i + " name: [" + name + "]"
                                                    ), false);
                                                    var lore = stack.get(DataComponents.LORE);
                                                    if (lore != null) {
                                                        for (var line : lore.lines()) {
                                                            mc.player.displayClientMessage(Component.literal(
                                                                    "  lore: [" + line.getString() + "]"
                                                            ), false);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        return 1;
                                    })
                            )
            );
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        // =========================
        // HUD
        // =========================
        HudRenderCallback.EVENT.register((graphics, delta) -> {
            if (mc.player == null) return;

            long remaining = Math.max(0, (nextCycleTime - System.currentTimeMillis()) / 1000);

            String statusColor = enabled ? "§a" : "§c";
            String statusText = enabled ? "ON" : "OFF";
            graphics.drawString(mc.font, "§7[AutoStore] " + statusColor + statusText, hudX, hudY, 0xFFFFFF, true);

            if (enabled) {
                String detail = switch (state) {
                    case IDLE, WAITING_TO_OPEN -> "§7Next in §e" + remaining + "s";
                    case OPENING               -> "§7Opening bag...";
                    case DRAINING              -> "§7Depositing... §e(" + clickQueue.size() + " left)";
                    case RESCANNING            -> "§7Rescanning...";
                };
                graphics.drawString(mc.font, detail, hudX, hudY + 10, 0xFFFFFF, true);
            }
        });
    }

    // =========================
    // MAIN LOOP
    // =========================
    private void onTick(Minecraft client) {
        if (mc.player == null) return;

        // If enabled but bag disappears from hotbar while idle, disable
        if (enabled && state == State.IDLE && InventoryHelper.findMaterialBagSlot() == -1) {
            enabled = false;
            InputHelper.stopAll();
            clickQueue.clear();
            tickCooldown = 0;
            mc.player.displayClientMessage(
                    Component.literal("§cAutoStore disabled: Material Bag not found in hotbar"), false
            );
            return;
        }

        // Resume right click if chat or inv is closed
        if (enabled && state == State.IDLE && mc.screen == null) {
            InputHelper.holdRightClick(true);
        }

        if (!enabled) return;

        if (tickCooldown > 0) {
            tickCooldown--;
            return;
        }

        switch (state) {
            case IDLE -> {
                if (System.currentTimeMillis() >= nextCycleTime) {
                    InputHelper.holdRightClick(false);
                    state = State.WAITING_TO_OPEN;
                    tickCooldown = 20;
                }
            }
            case WAITING_TO_OPEN -> startCycle();
            case OPENING         -> tryFinishOpen();
            case DRAINING        -> drainOneTick();
            case RESCANNING      -> rescanAndContinue();
        }
    }

    // =========================
    // 1. OPEN THE BAG
    // =========================
    private void startCycle() {
        int bagSlot = InventoryHelper.findMaterialBagSlot();
        if (bagSlot == -1) {
            InputHelper.holdRightClick(true);
            nextCycleTime = System.currentTimeMillis() + 5000;
            state = State.IDLE;
            return;
        }

        InventoryHelper.cacheCurrentSlot();
        InventoryHelper.selectSlot(bagSlot);
        InventoryHelper.useItem();

        clickQueue.clear();
        state = State.OPENING;
        tickCooldown = 6;
    }

    // =========================
    // 2. WAIT FOR GUI, CHECK SETTINGS, BUILD QUEUE
    // =========================
    private void tryFinishOpen() {
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
            tickCooldown = 2;
            return;
        }

        // Step 1: ensure deposit mode
        if (!ensureDepositMode()) {
            tickCooldown = 6;
            return;
        }

        // Step 2: ensure amount is 64
        if (!ensureAmount64()) {
            tickCooldown = 6;
            return;
        }

        // Step 3: build queue and start draining
        buildClickQueue();

        if (clickQueue.isEmpty()) {
            mc.player.closeContainer();
            finishCycle();
        } else {
            state = State.DRAINING;
            tickCooldown = 1;
        }
    }

    // =========================
    // CHECK AND SET DEPOSIT MODE
    // =========================
    private boolean ensureDepositMode() {
        var handler = mc.player.containerMenu;

        for (int i = 0; i < handler.slots.size(); i++) {
            var stack = handler.getSlot(i).getItem();
            if (stack.isEmpty()) continue;
            if (!stack.getHoverName().getString().contains("Material Bag Mode")) continue;

            var lore = stack.get(DataComponents.LORE);
            if (lore == null) continue;

            for (var line : lore.lines()) {
                String lineText = line.getString();
                if (lineText.contains("Current Mode: Deposit")) return true;
                if (lineText.contains("Current Mode: Withdraw")) {
                    mc.gameMode.handleInventoryMouseClick(
                            handler.containerId, i, 0, ClickType.PICKUP, mc.player
                    );
                    return false;
                }
            }
        }

        return true;
    }

    // =========================
    // CHECK AND SET AMOUNT TO 64
    // =========================
    private boolean ensureAmount64() {
        var handler = mc.player.containerMenu;

        for (int i = 0; i < handler.slots.size(); i++) {
            var stack = handler.getSlot(i).getItem();
            if (stack.isEmpty()) continue;
            if (!stack.getHoverName().getString().contains("Set Amount")) continue;

            var lore = stack.get(DataComponents.LORE);
            if (lore == null) continue;

            for (var line : lore.lines()) {
                String lineText = line.getString();
                if (lineText.contains("Current amount: 64")) return true;
                if (lineText.contains("Current amount: 1") || lineText.contains("Current amount: 8")) {
                    mc.gameMode.handleInventoryMouseClick(
                            handler.containerId, i, 0, ClickType.PICKUP, mc.player
                    );
                    return false;
                }
            }
        }

        return true;
    }

    // =========================
    // 3. BUILD CLICK QUEUE
    // =========================
    private void buildClickQueue() {
        clickQueue.clear();

        var inv = mc.player.getInventory();
        var handler = mc.player.containerMenu;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            int fullStacks = stack.getCount() / 64;
            if (fullStacks == 0) continue;

            String itemName = stack.getHoverName().getString();
            int guiSlot = findGuiSlotByName(itemName, handler);
            if (guiSlot == -1) continue;

            for (int s = 0; s < fullStacks; s++) {
                clickQueue.add(guiSlot);
            }
        }
    }

    // =========================
    // 4. CLICK ONE SLOT PER TICK
    // =========================
    private void drainOneTick() {
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
            finishCycle();
            return;
        }

        if (clickQueue.isEmpty()) {
            state = State.RESCANNING;
            tickCooldown = 6;
            return;
        }

        int slot = clickQueue.poll();

        mc.gameMode.handleInventoryMouseClick(
                mc.player.containerMenu.containerId,
                slot,
                0,
                ClickType.PICKUP,
                mc.player
        );

        tickCooldown = 2;
    }

    // =========================
    // 5. RESCAN AFTER DRAIN
    // =========================
    private void rescanAndContinue() {
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
            finishCycle();
            return;
        }

        buildClickQueue();

        if (clickQueue.isEmpty()) {
            mc.player.closeContainer();
            finishCycle();
        } else {
            state = State.DRAINING;
            tickCooldown = 1;
        }
    }

    // =========================
    // FIND GUI BUTTON BY NAME
    // =========================
    private int findGuiSlotByName(String itemName, net.minecraft.world.inventory.AbstractContainerMenu handler) {

        int bagSlotCount = handler.slots.size() - 36;

        for (int i = 0; i < bagSlotCount; i++) {
            var stack = handler.getSlot(i).getItem();
            if (!stack.isEmpty() && stack.getHoverName().getString().equals(itemName)) {
                return i;
            }
        }
        return -1;
    }

    // =========================
    // FINISH
    // =========================
    private void finishCycle() {
        state = State.IDLE;
        clickQueue.clear();
        tickCooldown = 0;
        InventoryHelper.restoreSlot();
        nextCycleTime = System.currentTimeMillis() + (delaySeconds * 1000L);
        InputHelper.holdRightClick(true);
    }
}