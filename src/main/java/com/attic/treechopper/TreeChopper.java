package com.attic.treechopper; // Change if needed

import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

public final class TreeChopper extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig(); // Creates config.yml if it doesn't exist
        reloadConfiguration(); // Load custom config values

        getServer().getPluginManager().registerEvents(new TreeListener(this), this);
        getLogger().info("TreeChopper has been enabled!");
        if (getConfig().getBoolean("debug-mode", false)) {
            getLogger().warning("TreeChopper Debug Mode is enabled.");
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("TreeChopper has been disabled.");
    }

    public void reloadConfiguration() {
        reloadConfig();
        // You could add logic here to re-read config values into variables if needed frequently
        // For now, the listener will read directly from the config object.
    }

    public void logDebug(String message) {
        if (getConfig().getBoolean("debug-mode", false)) {
            getLogger().log(Level.INFO, "[Debug] " + message);
        }
    }
}