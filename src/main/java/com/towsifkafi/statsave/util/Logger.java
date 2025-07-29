package com.towsifkafi.statsave.util;

import java.util.logging.Level;

import org.bukkit.Bukkit;

import com.towsifkafi.statsave.StatSave;

import net.md_5.bungee.api.ChatColor;

public class Logger {

    private StatSave plugin;
    java.util.logging.Logger logger;

    public Logger(StatSave pl) {
        plugin = pl;
        logger = plugin.getLogger();
    }

    public void log(String message) {
        logger.log(Level.INFO, tr(message));
    }

    public void log(Level level, String message) {
        logger.log(level, tr(message));
    }

    public void log(Level level, String message, Throwable throwable) {
        logger.log(level, tr(message), throwable);
    }

    public void debug(String message) {
        if(plugin.getConfig().getBoolean("debug")) {
            logger.log(Level.INFO, "[DEBUG] " + tr(message));
        }
    }

    public void debug(Level level, String message) {
        if(plugin.getConfig().getBoolean("debug")) {
            logger.log(level, "[DEBUG] " + tr(message));
        }
    }

    public java.util.logging.Logger getLogger() {
        return logger;
    }

    public String tr(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

}
