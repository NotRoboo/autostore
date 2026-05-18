package com.roboo.autostore;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;

public class Commands {

    private static final Minecraft mc = Minecraft.getInstance();

    public static void register(AutoStoreClient mod) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("mbag")

                        .then(ClientCommandManager.literal("toggle")
                                .executes(ctx -> {
                                    if (!AutoStoreClient.enabled && InventoryHelper.findMaterialBagSlot() == -1) {
                                        mod.msg("§cNo Material Bag found in hotbar! Place one in your hotbar and try again.");
                                        return 1;
                                    }
                                    mod.toggle();
                                    return 1;
                                })
                        )

                        .then(ClientCommandManager.literal("timer")
                                .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(5, 6000))
                                        .executes(ctx -> {
                                            cfg().delaySeconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                            ModConfig.save();
                                            mod.msg("§fTimer set to §e" + cfg().delaySeconds + "s");
                                            return 1;
                                        })
                                )
                        )

                        // === Main HUD ===
                        .then(ClientCommandManager.literal("hud")
                                .then(ClientCommandManager.literal("toggle")
                                        .executes(ctx -> {
                                            cfg().hudVisible = !cfg().hudVisible;
                                            ModConfig.save();
                                            mod.msg("§fHUD " + (cfg().hudVisible ? "§avisible" : "§chidden"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("pos")
                                        .then(ClientCommandManager.argument("x", IntegerArgumentType.integer(0, 1920))
                                                .then(ClientCommandManager.argument("y", IntegerArgumentType.integer(0, 1080))
                                                        .executes(ctx -> {
                                                            cfg().hudX = IntegerArgumentType.getInteger(ctx, "x");
                                                            cfg().hudY = IntegerArgumentType.getInteger(ctx, "y");
                                                            ModConfig.save();
                                                            mod.msg("§fHUD moved to §e" + cfg().hudX + ", " + cfg().hudY);
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )

                        // === Sprint HUD ===
                        .then(ClientCommandManager.literal("sprint")
                                .then(ClientCommandManager.literal("toggle")
                                        .executes(ctx -> {
                                            cfg().sprintHudVisible = !cfg().sprintHudVisible;
                                            ModConfig.save();
                                            mod.msg("§fSprint HUD " + (cfg().sprintHudVisible ? "§avisible" : "§chidden"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("pos")
                                        .then(ClientCommandManager.argument("x", IntegerArgumentType.integer(0, 1920))
                                                .then(ClientCommandManager.argument("y", IntegerArgumentType.integer(0, 1080))
                                                        .executes(ctx -> {
                                                            cfg().sprintHudX = IntegerArgumentType.getInteger(ctx, "x");
                                                            cfg().sprintHudY = IntegerArgumentType.getInteger(ctx, "y");
                                                            ModConfig.save();
                                                            mod.msg("§fSprint HUD moved to §e" + cfg().sprintHudX + ", " + cfg().sprintHudY);
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )

                        // === Right Click HUD ===
                        .then(ClientCommandManager.literal("rightclick")
                                .then(ClientCommandManager.literal("toggle")
                                        .executes(ctx -> {
                                            cfg().useHudVisible = !cfg().useHudVisible;
                                            ModConfig.save();
                                            mod.msg("§fRight Click HUD " + (cfg().useHudVisible ? "§avisible" : "§chidden"));
                                            return 1;
                                        })
                                )
                                .then(ClientCommandManager.literal("pos")
                                        .then(ClientCommandManager.argument("x", IntegerArgumentType.integer(0, 1920))
                                                .then(ClientCommandManager.argument("y", IntegerArgumentType.integer(0, 1080))
                                                        .executes(ctx -> {
                                                            cfg().useHudX = IntegerArgumentType.getInteger(ctx, "x");
                                                            cfg().useHudY = IntegerArgumentType.getInteger(ctx, "y");
                                                            ModConfig.save();
                                                            mod.msg("§fRight Click HUD moved to §e" + cfg().useHudX + ", " + cfg().useHudY);
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static ModConfig cfg() {
        return ModConfig.get();
    }
}