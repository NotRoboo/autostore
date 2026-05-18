package com.roboo.autostore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("autostore.json");

    // AutoStore HUD
    public int delaySeconds = 1200;
    public int hudX = 10;
    public int hudY = 30;
    public boolean hudVisible = true;

    // Sprint HUD
    public int sprintHudX = 10;
    public int sprintHudY = 10;
    public boolean sprintHudVisible = true;

    // Right-click HUD
    public int useHudX = 10;
    public int useHudY = 20;
    public boolean useHudVisible = true;

    private static ModConfig instance;

    public static ModConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                instance = GSON.fromJson(json, ModConfig.class);
                return;
            } catch (IOException e) {
                System.err.println("[AutoStore] Failed to read config, using defaults: " + e.getMessage());
            }
        }
        instance = new ModConfig();
    }

    public static void save() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(instance));
        } catch (IOException e) {
            System.err.println("[AutoStore] Failed to save config: " + e.getMessage());
        }
    }
}