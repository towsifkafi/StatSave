package com.towsifkafi.statsave.util;

import org.bson.Document;
import org.bukkit.entity.Player;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.MongoCollection;
import com.towsifkafi.statsave.StatSave;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class PlayerUtil {
    
    private StatSave plugin;

    private Map<String, Function<Player, String>> tables = new HashMap<>();

    private List<String> jsonTables = List.of("achievements", "xp", "stats", "player_state", "player_effects");

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
    }

    private void saveDataMySQL(Player player) {

        for (Map.Entry<String, Function<Player, String>> entry: tables.entrySet()) {
            String table = entry.getKey();

            String query = String.format(
                    "INSERT INTO %s (player_uuid, username, data) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE data = ?",
                    table);
            try (Connection conn = plugin.pool.getConnection();
                PreparedStatement statement = conn.prepareStatement(query)) {

                String data = entry.getValue().apply(player);
                if (data == null) {
                    return;
                }

                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, player.getName());
                statement.setString(3, data);
                statement.setString(4, data);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("SQL error while saving data to " + table, e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }
        
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
    
                if(jsonTables.contains(table)) {
                    obj.put(table, Document.parse(data));
                } else obj.put(table, data);
    
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }

        Document found = collection.find(eq("uuid", player.getUniqueId().toString())).first();
        if (found != null) {
            collection.updateOne(found, new Document("$set", obj));
        } else {
            collection.insertOne(obj);
        }

    }

    public void saveData(Player player) {
        long startTime = System.currentTimeMillis();
        if(plugin.getConfig().getString("storage-method").equalsIgnoreCase("mysql")) {
            saveDataMySQL(player);
        } else if(plugin.getConfig().getString("storage-method").equalsIgnoreCase("mongodb")) {
            saveDataMongoDB(player);
        }
        long endTime = System.currentTimeMillis();
        if(plugin.getConfig().getBoolean("debug")) plugin.logger.info(String.format("Saved data for %s in %dms", player.getName(), (endTime - startTime)));
    }

}
