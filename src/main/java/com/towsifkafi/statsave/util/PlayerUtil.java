package com.towsifkafi.statsave.util;

import org.bson.Document;
import org.bukkit.entity.Player;

import static com.mongodb.client.model.Filters.eq;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mongodb.client.MongoCollection;
import com.towsifkafi.statsave.StatSave;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;

public class PlayerUtil {
    
    private StatSave plugin;

    private Map<String, Function<Player, String>> tables = new HashMap<>();

    public PlayerUtil(StatSave plugin) {
        this.plugin = plugin;
    }

    public void loadEncoders() {
        tables.clear();
        if(plugin.getConfig().getBoolean("save-data.inventory")) tables.put("inventory", t -> {
            try {
                return plugin.dataUtil.encodeInventoryToBase64(t);
            } catch (Exception e) { e.printStackTrace();}
            return null;
        });
        if(plugin.getConfig().getBoolean("save-data.enderchest")) tables.put("enderchest", t -> {
            try {
                return plugin.dataUtil.encodeEnderChestToBase64(t);
            } catch (Exception e) { e.printStackTrace();}
            return null;
        });
        if(plugin.getConfig().getBoolean("save-data.armor")) tables.put("armor", plugin.dataUtil::encodeArmorToBase64);
        if(plugin.getConfig().getBoolean("save-data.achievements")) tables.put("achievements", plugin.dataUtil::encodeAchievementsToJSON);
        if(plugin.getConfig().getBoolean("save-data.xp")) tables.put("xp", plugin.dataUtil::encodePlayerXP);
        if(plugin.getConfig().getBoolean("save-data.stats")) tables.put("stats", plugin.dataUtil::encodeStatsToJSON);
        if(plugin.getConfig().getBoolean("save-data.player_state")) tables.put("player_state", plugin.dataUtil::encodePlayerStats);
        if(plugin.getConfig().getBoolean("save-data.player_effects")) tables.put("player_effects", plugin.dataUtil::encodeEffectsToJSON);
        if(plugin.getConfig().getBoolean("placeholderapi.enabled") && plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) tables.put("placeholders", plugin.dataUtil::encodePlaceholdersToJSON);
    }

    private void saveDataMySQL(Player player) {

        String sqlType = plugin.getStorageMethod();

        Map<String, String> queries = new HashMap<>();
        Map<String, String> datas = new HashMap<>();

        plugin.logger.debug("Preparing data for " + player.getName() + " to be saved...");
        for (Map.Entry<String, Function<Player, String>> entry: tables.entrySet()) {
            String table = entry.getKey();

            String query;
            if (plugin.pool.isSQLite()) {
                query = String.format("INSERT OR REPLACE INTO %s (player_uuid, username, data) VALUES (?, ?, ?)", 
                plugin.config.getString("table-prefix")+table);
            } else {
                query = String.format(
                    "INSERT INTO %s (player_uuid, username, data) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE data = ?",
                    plugin.config.getString("table-prefix")+table);
            }

            String data = entry.getValue().apply(player);
            if (data != null) {
                queries.put(table, query);
                datas.put(table, data);
            }

        }

        plugin.logger.debug("Prepared statement queries for " + player.getName());
        plugin.getScheduler().runTaskAsynchronously(() -> {
            long startTime = System.currentTimeMillis();
            Connection conn = null;
            try {
                conn = plugin.pool.getConnection();
                conn.setAutoCommit(false);

                for (Map.Entry<String, String> entry: queries.entrySet()) {
                    String query = entry.getValue();
                    String data = datas.get(entry.getKey());
                    
                    try (PreparedStatement statement = conn.prepareStatement(query)) {
                        statement.setString(1, player.getUniqueId().toString());
                        statement.setString(2, player.getName());
                        statement.setString(3, data);
                        if (!plugin.pool.isSQLite()) statement.setString(4, data);

                        statement.addBatch();
                        int[] results = statement.executeBatch();
                        plugin.logger.debug("["+player.getName()+"]"+" --> [" + entry.getKey() + "]: " + Arrays.stream(results).sum());
                    }
                }
                conn.commit();
                long endTime = System.currentTimeMillis();
                plugin.logger.debug(String.format("Saved data for %s in %dms", player.getName(), (endTime - startTime)));
            } catch (SQLException e) {
                try {
                    if (conn != null) {
                        conn.rollback();
                    }
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().severe("Failed to rollback transaction: " + rollbackEx.getMessage());
                }
                throw new RuntimeException("SQL error while saving data: " + e);
                
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                if (conn != null) {
                    try {
                        plugin.logger.debug("Closing connection for " + player.getName());
                        conn.setAutoCommit(true);
                        conn.close();
                    } catch (SQLException e) {
                        throw new RuntimeException("Error closing connection: " + e);
                    }
                }
            }
        });
        
    }
    private void saveDataMongoDB(Player player) {
        MongoCollection<Document> collection = plugin.mongoCollection;

        Document obj = new Document();
        obj.put("uuid", player.getUniqueId().toString());
        obj.put("username", player.getName());


        for (Map.Entry<String, Function<Player, String>> entry: tables.entrySet()) {
            String table = entry.getKey();
            try {
                String data = entry.getValue().apply(player);
                if (data == null) {
                    return;
                }
                if(StatSave.jsonTables.contains(table)) {
                    obj.put(table, Document.parse(data));
                } else obj.put(table, data);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        plugin.getScheduler().runTaskAsynchronously(() -> {
            long startTime = System.currentTimeMillis();
            Document found = collection.find(eq("uuid", player.getUniqueId().toString())).first();
            if (found != null) {
                //collection.replaceOne(found, obj);
                plugin.logger.debug("Updating data for " + player.getName());
                collection.updateOne(found, new Document("$set", obj));

            } else {
                plugin.logger.debug("Inserting new data for " + player.getName());
                collection.insertOne(obj);
            }
            long endTime = System.currentTimeMillis();
            plugin.logger.debug(String.format("Saved data for %s in %dms", player.getName(), (endTime - startTime)));
        });

    }

    public void saveData(Player player) {
        //long startTime = System.currentTimeMillis();
        if(plugin.isUsingSQL()) {
            saveDataMySQL(player);
        } else if(plugin.getStorageMethod().equalsIgnoreCase("mongodb")) {
            saveDataMongoDB(player);
        }
        //long endTime = System.currentTimeMillis();
        //plugin.logger.debug(String.format("Saved data for %s in %dms", player.getName(), (endTime - startTime)));
    }

    public Map<String, Object> getData(String playerName) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> data;

        if (plugin.isUsingSQL()) {
            data = getDataMySQL(playerName);
        } else if (plugin.getStorageMethod().equalsIgnoreCase("mongodb")) {
            data = getDataMongoDB(playerName);
        } else {
            data = new HashMap<>();
            plugin.logger.log(Level.WARNING, "Invalid storage method configured. No data retrieved for " + playerName);
        }

        long endTime = System.currentTimeMillis();
        plugin.logger.debug(String.format("Retrieved data for %s in %dms", playerName, (endTime - startTime)));
        return data;
    }

    private Map<String, Object> getDataMySQL(String playerName) {
        Map<String, Object> dataMap = new HashMap<>();

        for (String table : tables.keySet()) {
            String query = String.format("SELECT data FROM %s WHERE username = ?", 
                plugin.config.getString("table-prefix")+table);
            try (Connection conn = plugin.pool.getConnection();
                PreparedStatement statement = conn.prepareStatement(query)) {
                statement.setString(1, playerName);
                ResultSet result = statement.executeQuery();
                
                if (result.next()) {
                    String data = result.getString("data");
                    if (data != null) {
                        if(table.equalsIgnoreCase("inventory") || table.equalsIgnoreCase("enderchest")  || table.equalsIgnoreCase("armor")) {
                            dataMap.put(table, data);
                        } else {
                            dataMap.put(table, JsonParser.parseString(data));
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.logger.log(Level.SEVERE, "SQL error while retrieving data from " + table + " for " + playerName, e);
                e.printStackTrace();
            }
        }
        return dataMap;
    }

    private Map<String, Object> getDataMongoDB(String playerName) {
        Map<String, Object> dataMap = new HashMap<>();
        MongoCollection<Document> collection = plugin.mongoCollection;

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
