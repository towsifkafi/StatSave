package com.towsifkafi.statsave;

import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.towsifkafi.statsave.commands.SaveCommand;
import com.towsifkafi.statsave.database.HikariPool;
import com.towsifkafi.statsave.database.MongoDBLoader;
import com.towsifkafi.statsave.util.DataUtil;
import com.towsifkafi.statsave.util.PlayerUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Logger;

public final class StatSave extends JavaPlugin implements Listener {

    public Logger logger;

    public HikariPool pool;
    public MongoDBLoader mongoLoader;
    public DataUtil dataUtil;
    public PlayerUtil playerUtil;

    public Configuration config;

    private static final String[] TABLES = {
        "inventory", "enderchest", "armor", "achievements", "xp", "stats", "player_state", "player_effects"
    };

    public Connection connection;
    public MongoDatabase mongoDatabase;
    public MongoCollection<Document> mongoCollection;

    @Override
    public void onEnable() {
        logger = getLogger();
        loadConfig();

        Bukkit.getPluginManager().registerEvents(this, this);

        getCommand("statsave").setExecutor(new SaveCommand(this));

        String storageMethod = getConfig().getString("storage-method");

        mongoLoader = new MongoDBLoader(this);
        pool = new HikariPool(this);

        if(storageMethod.equalsIgnoreCase("mongodb")) {
            loadMongo();
        } else if(storageMethod.equalsIgnoreCase("mysql")) {
            connection = pool.getConnection();

            if (config.getBoolean("recreatetables")) {
                recreateTables();
            }

            for (String table : TABLES) {
                pool.createTable(table, "player_uuid VARCHAR(36) PRIMARY KEY, username VARCHAR(16), data LONGTEXT");
            }
        }

        dataUtil = new DataUtil(this);
        playerUtil = new PlayerUtil(this);
    }

    @Override
    public void onDisable() {
        Bukkit.getOnlinePlayers().forEach(playerUtil::saveData);
        pool.closeConnection();
    }

    private void loadConfig() {
        saveDefaultConfig();
        saveConfig();
        config = getConfig();
    }

    public void reload() {
        loadConfig();
        playerUtil.loadEncoders();
        if(mongoDatabase != null) {
            mongoLoader.close();
        }
        if(connection != null) {
            pool.closeConnection();
        }
        String storageMethod = getConfig().getString("storage-method");
        if(storageMethod.equalsIgnoreCase("mongodb")) {
            loadMongo();
        } else if(storageMethod.equalsIgnoreCase("mysql")) {
            pool = new HikariPool(this);
            connection = pool.getConnection();
        }
    }

    public void loadMongo() {
        mongoLoader.load();
        mongoDatabase = mongoLoader.getDatabase();
        mongoCollection = mongoLoader.mongoCollection;
    }

    private void recreateTables() {
        logger.info("Deleting tables...");
        try (PreparedStatement statement = connection.prepareStatement(
                "DROP TABLE IF EXISTS " + String.join(", ", TABLES))) {
            statement.execute();
        } catch (SQLException e) {
            logger.severe("Failed to drop tables: "+ e.toString());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        savePlayerData(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        savePlayerData(player);
    }

    public void savePlayerData(Player player) {
        playerUtil.saveData(player);
    }

}
