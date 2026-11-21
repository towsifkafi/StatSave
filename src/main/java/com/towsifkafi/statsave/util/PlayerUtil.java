package com.towsifkafi.statsave.util;

import org.bson.Document;
import org.bukkit.entity.Player;

import static com.mongodb.client.model.Filters.eq;

import com.google.gson.JsonParser;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import com.towsifkafi.statsave.StatSave;
import com.towsifkafi.statsave.addon.Addon;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;

public class PlayerUtil {
    
    private final StatSave plugin;
    private final Map<String, Function<Player, String>> tables = new HashMap<>();

    public PlayerUtil(StatSave plugin) {
        this.plugin = plugin;
    }

    private void log(String message) {
        if (plugin.logger != null) {
            plugin.logger.log(message);
        } else if (plugin.getLogger() != null) {
            plugin.getLogger().info(message);
        }
    }

    private void log(Level level, String message) {
        if (plugin.logger != null) {
            plugin.logger.log(level, message);
        } else if (plugin.getLogger() != null) {
            plugin.getLogger().log(level, message);
        }
    }

    private void debug(String message) {
        if (plugin.logger != null) {
            plugin.logger.debug(message);
        }
    }

    public void loadEncoders() {
        tables.clear();
        String format = plugin.getConfig().getString("inventory-format", "SNBT");
        boolean isSNBT = "SNBT".equalsIgnoreCase(format);
        org.bukkit.plugin.PluginManager pm = plugin.getServer() != null ? plugin.getServer().getPluginManager() : org.bukkit.Bukkit.getPluginManager();
        boolean nbtInstalled = pm != null && pm.getPlugin("NBTAPI") != null;

        boolean allowItemSaving = !isSNBT || nbtInstalled;
        if (isSNBT && !nbtInstalled) {
            plugin.getLogger().warning("inventory-format is set to SNBT but NBTAPI plugin is not installed! Skipping inventory, enderchest, and armor saving.");
        }

        if (allowItemSaving) {
            if (plugin.getConfig().getBoolean("save-data.inventory", true)) {
                if (isSNBT) {
                    tables.put("inventory", plugin.dataUtil::encodeInventoryToSNBT);
                } else {
                    tables.put("inventory", t -> {
                        try {
                            return plugin.dataUtil.encodeInventoryToBase64(t);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Error encoding inventory for " + t.getName() + ": " + e.getMessage());
                        }
                        return "";
                    });
                }
            }
            if (plugin.getConfig().getBoolean("save-data.enderchest", true)) {
                if (isSNBT) {
                    tables.put("enderchest", plugin.dataUtil::encodeEnderChestToSNBT);
                } else {
                    tables.put("enderchest", t -> {
                        try {
                            return plugin.dataUtil.encodeEnderChestToBase64(t);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Error encoding enderchest for " + t.getName() + ": " + e.getMessage());
                        }
                        return "";
                    });
                }
            }
            if (plugin.getConfig().getBoolean("save-data.armor", true)) {
                if (isSNBT) {
                    tables.put("armor", plugin.dataUtil::encodeArmorToSNBT);
                } else {
                    tables.put("armor", plugin.dataUtil::encodeArmorToBase64);
                }
            }
        }
        if (plugin.getConfig().getBoolean("save-data.achievements", true)) {
            tables.put("achievements", plugin.dataUtil::encodeAchievementsToJSON);
        }
        if (plugin.getConfig().getBoolean("save-data.xp", true)) {
            tables.put("xp", plugin.dataUtil::encodePlayerXP);
        }
        if (plugin.getConfig().getBoolean("save-data.stats", true)) {
            tables.put("stats", plugin.dataUtil::encodeStatsToJSON);
        }
        if (plugin.getConfig().getBoolean("save-data.player_state", true)) {
            tables.put("player_state", plugin.dataUtil::encodePlayerStats);
        }
        if (plugin.getConfig().getBoolean("save-data.player_effects", true)) {
            tables.put("player_effects", plugin.dataUtil::encodeEffectsToJSON);
        }
        if (plugin.getConfig().getBoolean("placeholderapi.enabled", false) 
                && pm != null && pm.getPlugin("PlaceholderAPI") != null) {
            tables.put("placeholders", plugin.dataUtil::encodePlaceholdersToJSON);
        }
    }

    public Map<String, Function<Player, String>> getTables() {
        return Collections.unmodifiableMap(tables);
    }

    public String buildUpsertQuery(String fullTableName) {
        if (plugin.pool != null && plugin.pool.isPostgreSQL()) {
            return String.format(
                "INSERT INTO %s (player_uuid, username, data) VALUES (?, ?, ?) " +
                "ON CONFLICT (player_uuid) DO UPDATE SET username = EXCLUDED.username, data = EXCLUDED.data",
                fullTableName
            );
        } else if (plugin.pool != null && plugin.pool.isSQLite()) {
            return String.format(
                "INSERT INTO %s (player_uuid, username, data) VALUES (?, ?, ?) " +
                "ON CONFLICT (player_uuid) DO UPDATE SET username = excluded.username, data = excluded.data",
                fullTableName
            );
        } else {
            return String.format(
                "INSERT INTO %s (player_uuid, username, data) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE username = VALUES(username), data = VALUES(data)",
                fullTableName
            );
        }
    }

    public void saveData(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        // Ensure player snapshot is taken on the player's entity thread (Folia / Thread-safety)
        if (plugin.getScheduler().isEntityThread(player)) {
            captureAndPersist(player);
        } else {
            plugin.getScheduler().runTask(player, () -> captureAndPersist(player));
        }
    }

    private void captureAndPersist(Player player) {
        if (!player.isOnline()) {
            return;
        }

        String uuid = player.getUniqueId().toString();
        String username = player.getName();

        debug("Preparing data snapshot for " + username + " on entity thread...");
        Map<String, String> datas = new HashMap<>();
        for (Map.Entry<String, Function<Player, String>> entry : tables.entrySet()) {
            try {
                String val = entry.getValue().apply(player);
                if (val != null) {
                    datas.put(entry.getKey(), val);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error extracting " + entry.getKey() + " for " + username + ": " + e.getMessage());
            }
        }

        // Notify addons while on the entity thread
        if (plugin.getAddonLoader() != null) {
            for (Addon addon : plugin.getAddonLoader().getLoadedAddons()) {
                try {
                    addon.savePlayerData(player);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error executing addon " + addon.getClass().getSimpleName() + " for " + username + ": " + e.getMessage());
                }
            }
        }

        // Dispatch persistence asynchronously
        if (plugin.isUsingSQL()) {
            saveDataSQLAsync(uuid, username, datas);
        } else if ("mongodb".equalsIgnoreCase(plugin.getStorageMethod())) {
            saveDataMongoDBAsync(uuid, username, datas);
        }
    }

    private void saveDataSQLAsync(String uuid, String username, Map<String, String> datas) {
        if (plugin.pool == null) {
            log(Level.WARNING, "SQL connection pool is null. Skipping save for " + username);
            return;
        }

        plugin.getScheduler().runTaskAsynchronously(() -> {
            long startTime = System.currentTimeMillis();
            Connection conn = null;
            try {
                conn = plugin.pool.getConnection();
                if (conn == null) {
                    log(Level.SEVERE, "Could not acquire database connection for saving " + username);
                    return;
                }
                conn.setAutoCommit(false);

                String prefix = plugin.getConfig().getString("table-prefix", "");
                for (Map.Entry<String, String> entry : datas.entrySet()) {
                    String table = entry.getKey();
                    String data = entry.getValue();

                    String fullTableName = prefix + table;
                    String query = buildUpsertQuery(fullTableName);

                    try (PreparedStatement statement = conn.prepareStatement(query)) {
                        statement.setString(1, uuid);
                        statement.setString(2, username);
                        statement.setString(3, data);
                        statement.executeUpdate();
                    }
                }
                conn.commit();
                long endTime = System.currentTimeMillis();
                debug(String.format("Saved data for %s to SQL in %dms", username, (endTime - startTime)));
            } catch (SQLException e) {
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        plugin.getLogger().severe("Failed to rollback transaction: " + rollbackEx.getMessage());
                    }
                }
                log(Level.SEVERE, "SQL error while saving data for " + username + ": " + e.getMessage());
            } catch (Exception e) {
                log(Level.SEVERE, "Unexpected error while saving data for " + username + ": " + e.getMessage());
            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(true);
                    } catch (Exception ignored) {
                    }
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        log(Level.WARNING, "Error closing connection: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void saveDataMongoDBAsync(String uuid, String username, Map<String, String> datas) {
        MongoCollection<Document> collection = plugin.mongoCollection;
        if (collection == null) {
            log(Level.WARNING, "MongoDB collection is null. Skipping save for " + username);
            return;
        }

        Document obj = new Document();
        obj.put("uuid", uuid);
        obj.put("username", username);

        for (Map.Entry<String, String> entry : datas.entrySet()) {
            String table = entry.getKey();
            String data = entry.getValue();
            if (data == null) {
                continue;
            }
            if (StatSave.jsonTables.contains(table)) {
                try {
                    obj.put(table, Document.parse(data));
                } catch (Exception e) {
                    obj.put(table, data);
                }
            } else {
                obj.put(table, data);
            }
        }

        plugin.getScheduler().runTaskAsynchronously(() -> {
            long startTime = System.currentTimeMillis();
            try {
                collection.replaceOne(
                    eq("uuid", uuid),
                    obj,
                    new ReplaceOptions().upsert(true)
                );
                long endTime = System.currentTimeMillis();
                debug(String.format("Saved data for %s to MongoDB in %dms", username, (endTime - startTime)));
            } catch (Exception e) {
                log(Level.SEVERE, "Failed to save data to MongoDB for " + username + ": " + e.getMessage());
            }
        });
    }

    public Map<String, Object> getData(String playerName) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> data;

        if (plugin.isUsingSQL()) {
            data = getDataMySQL(playerName);
        } else if ("mongodb".equalsIgnoreCase(plugin.getStorageMethod())) {
            data = getDataMongoDB(playerName);
        } else {
            data = new HashMap<>();
            log(Level.WARNING, "Invalid storage method configured. No data retrieved for " + playerName);
        }

        long endTime = System.currentTimeMillis();
        debug(String.format("Retrieved data for %s in %dms", playerName, (endTime - startTime)));
        return data;
    }

    private Map<String, Object> getDataMySQL(String playerName) {
        Map<String, Object> dataMap = new HashMap<>();
        if (plugin.pool == null) {
            return dataMap;
        }

        String prefix = plugin.getConfig().getString("table-prefix", "");
        try (Connection conn = plugin.pool.getConnection()) {
            if (conn == null) {
                return dataMap;
            }
            for (String table : tables.keySet()) {
                String query = String.format("SELECT data FROM %s WHERE username = ?", prefix + table);
                try (PreparedStatement statement = conn.prepareStatement(query)) {
                    statement.setString(1, playerName);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            String data = result.getString("data");
                            if (data != null) {
                                if (table.equalsIgnoreCase("inventory") || table.equalsIgnoreCase("enderchest") || table.equalsIgnoreCase("armor")) {
                                    dataMap.put(table, data);
                                } else {
                                    dataMap.put(table, JsonParser.parseString(data));
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    log(Level.SEVERE, "SQL error while retrieving data from " + table + " for " + playerName + ": " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            log(Level.SEVERE, "SQL error while obtaining connection for " + playerName + ": " + e.getMessage());
        }
        return dataMap;
    }

    private Map<String, Object> getDataMongoDB(String playerName) {
        Map<String, Object> dataMap = new HashMap<>();
        MongoCollection<Document> collection = plugin.mongoCollection;
        if (collection == null) {
            return dataMap;
        }

        Document found = collection.find(eq("username", playerName)).first();
        if (found != null) {
            for (String table : tables.keySet()) {
                Object data = found.get(table);
                if (data != null) {
                    if (StatSave.jsonTables.contains(table) && data instanceof Document) {
                        dataMap.put(table, data);
                    } else {
                        dataMap.put(table, data);
                    }
                }
            }
        }
        return dataMap;
    }

}
