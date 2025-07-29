package com.towsifkafi.statsave.addon;

import org.bukkit.entity.Player;

import com.towsifkafi.statsave.StatSave;

public interface Addon {
    void onEnable(StatSave plugin);
    void onDisable(StatSave plugin);
    void reload(StatSave plugin);
    String[] getTableSchema();
    void savePlayerData(Player player);
}
