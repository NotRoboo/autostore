package com.roboo.autostore;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;

import java.util.ArrayDeque;
import java.util.Deque;

public class AutoStoreClient implements ClientModInitializer {

    public static boolean enabled = false;

    private static final Minecraft mc = Minecraft.getInstance();

    public enum State { IDLE, WAITING_TO_OPEN, OPENING, DRAINING, RESCANNING }
    private State state = State.IDLE;

    public interface StateRef { State get(); }

    private final long[] nextCycleTime = { 0 };
    private int tickCooldown = 0;
    private final Deque<Integer> clickQueue = new ArrayDeque<>();

    private boolean savedToggleUse = false;

    private String lastContainerTitle = "";
    private long lastContainerTime = 0;
    private static final long DUPLICATE_WINDOW_MS = 5000;

    @Override
    public void onInitializeClient() {
        ModConfig.load();

        HudHelper.register(() -> state, clickQueue, nextCycleTime);
        Commands.register(this);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> disableAndCleanup());

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!enabled) return;
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return;

            String title = cs.getTitle().getString();
            if (title.contains("Material Bag")) return;

            ScreenEvents.remove(screen).register(s -> {
                long now = System.currentTimeMillis();
                if (title.equals(lastContainerTitle) && (now - lastContainerTime) < DUPLICATE_WINDOW_MS) {
                    disableAndCleanup();
                    if (mc.player != null)
                        mc.player.displayClientMessage(
                                Component.literal("§e[AutoStore] §cDisabled: same container opened twice within 5s"), false
                        );
                    return;
                }
                lastContainerTitle = title;
                lastContainerTime = now;
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    public void toggle() {
        if (!enabled && InventoryHelper.findMaterialBagSlot() == -1) {
            msg("§cNo Material Bag found in hotbar!");
            return;
        }
        enabled = !enabled;
        msg(enabled ? "§aON" : "§cOFF");
        if (enabled) {
            savedToggleUse = mc.options.toggleUse().get();
            if (savedToggleUse) {
                mc.options.toggleUse().set(false);
                mc.options.save();
            }
            nextCycleTime[0] = System.currentTimeMillis() + (cfg().delaySeconds * 1000L);
            InputHelper.holdRightClick(true);
        } else {
            restoreToggleUse();
            InputHelper.stopAll();
            state = State.IDLE;
            clickQueue.clear();
            tickCooldown = 0;
        }
    }

    // =========================
    // MAIN LOOP
    // =========================
    private void onTick(Minecraft client) {
        if (mc.player == null) return;

        if (enabled && state == State.IDLE && InventoryHelper.findMaterialBagSlot() == -1) {
            disableAndCleanup();
            msg("§cDisabled: Material Bag not found in hotbar");
            return;
        }

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
                if (System.currentTimeMillis() >= nextCycleTime[0]) {
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

    private void startCycle() {
        int bagSlot = InventoryHelper.findMaterialBagSlot();
        if (bagSlot == -1) {
            InputHelper.holdRightClick(true);
            nextCycleTime[0] = System.currentTimeMillis() + 5000;
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

    private void tryFinishOpen() {
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) {
            tickCooldown = 2;
            return;
        }

        if (!ensureDepositMode()) { tickCooldown = 6; return; }
        if (!ensureAmount64())    { tickCooldown = 6; return; }

        buildClickQueue();

        if (clickQueue.isEmpty()) {
            if (mc.player != null) mc.player.closeContainer();
            finishCycle();
        } else {
            state = State.DRAINING;
            tickCooldown = 1;
        }
    }

    private boolean ensureDepositMode() {
        if (mc.player == null) return true;
        var handler = mc.player.containerMenu;
        for (int i = 0; i < handler.slots.size(); i++) {
            var stack = handler.getSlot(i).getItem();
            if (stack.isEmpty() || !stack.getHoverName().getString().contains("Material Bag Mode")) continue;
            var lore = stack.get(DataComponents.LORE);
            if (lore == null) continue;
            for (var line : lore.lines()) {
                String text = line.getString();
                if (text.contains("Current Mode: Deposit")) return true;
                if (text.contains("Current Mode: Withdraw")) {
                    if (mc.gameMode != null)
                        mc.gameMode.handleInventoryMouseClick(handler.containerId, i, 0, ClickType.PICKUP, mc.player);
                    return false;
                }
            }
        }
        return true;
    }

    private boolean ensureAmount64() {
        if (mc.player == null) return true;
        var handler = mc.player.containerMenu;
        for (int i = 0; i < handler.slots.size(); i++) {
            var stack = handler.getSlot(i).getItem();
            if (stack.isEmpty() || !stack.getHoverName().getString().contains("Set Amount")) continue;
            var lore = stack.get(DataComponents.LORE);
            if (lore == null) continue;
            for (var line : lore.lines()) {
                String text = line.getString();
                if (text.contains("Current amount: 64")) return true;
                if (text.contains("Current amount: 1") || text.contains("Current amount: 8")) {
                    if (mc.gameMode != null)
                        mc.gameMode.handleInventoryMouseClick(handler.containerId, i, 0, ClickType.PICKUP, mc.player);
                    return false;
                }
            }
        }
        return true;
    }

    private void buildClickQueue() {
        clickQueue.clear();
        if (mc.player == null) return;
        var inv = mc.player.getInventory();
        var handler = mc.player.containerMenu;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            int fullStacks = stack.getCount() / 64;
            if (fullStacks == 0) continue;
            int guiSlot = findGuiSlotByName(stack.getHoverName().getString(), handler);
            if (guiSlot == -1) continue;
            for (int s = 0; s < fullStacks; s++) clickQueue.add(guiSlot);
        }
    }

    private void drainOneTick() {
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) {
            finishCycle();
            return;
        }
        if (clickQueue.isEmpty()) {
            state = State.RESCANNING;
            tickCooldown = 6;
            return;
        }
        if (mc.player != null && mc.gameMode != null) {
            mc.gameMode.handleInventoryMouseClick(
                    mc.player.containerMenu.containerId, clickQueue.poll(), 0, ClickType.PICKUP, mc.player
            );
        }
        tickCooldown = 5 + mc.player.getRandom().nextInt(4);
    }

    private void rescanAndContinue() {
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) {
            finishCycle();
            return;
        }
        buildClickQueue();
        if (clickQueue.isEmpty()) {
            if (mc.player != null) mc.player.closeContainer();
            finishCycle();
        } else {
            state = State.DRAINING;
            tickCooldown = 1;
        }
    }

    private int findGuiSlotByName(String itemName, net.minecraft.world.inventory.AbstractContainerMenu handler) {
        int bagSlotCount = handler.slots.size() - 36;
        for (int i = 0; i < bagSlotCount; i++) {
            var stack = handler.getSlot(i).getItem();
            if (!stack.isEmpty() && stack.getHoverName().getString().equals(itemName)) return i;
        }
        return -1;
    }

    private void finishCycle() {
        state = State.IDLE;
        clickQueue.clear();
        tickCooldown = 0;
        InventoryHelper.restoreSlot();
        nextCycleTime[0] = System.currentTimeMillis() + (cfg().delaySeconds * 1000L);
        InputHelper.holdRightClick(true);
    }

    private void restoreToggleUse() {
        if (savedToggleUse) {
            mc.options.toggleUse().set(true);
            mc.options.save();
            savedToggleUse = false;
        }
    }

    public void disableAndCleanup() {
        enabled = false;
        restoreToggleUse();
        InputHelper.stopAll();
        state = State.IDLE;
        clickQueue.clear();
        tickCooldown = 0;
    }

    private static ModConfig cfg() { return ModConfig.get(); }

    public void msg(String text) {
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal("§e[AutoStore] " + text), false);
    }
}