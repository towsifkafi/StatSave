package com.towsifkafi.statsave;

import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.towsifkafi.statsave.addon.AddonLoader;
import com.towsifkafi.statsave.commands.SaveCommand;
import com.towsifkafi.statsave.database.HikariPool;
import com.towsifkafi.statsave.database.MongoDBLoader;
import com.towsifkafi.statsave.util.DataUtil;
import com.towsifkafi.statsave.util.Logger;
import com.towsifkafi.statsave.util.PlayerUtil;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Level;

public class StatSave extends JavaPlugin implements Listener {

    public Logger logger;
    public TaskScheduler scheduler;

    public HikariPool pool;
    public MongoDBLoader mongoLoader;
    public DataUtil dataUtil;
    public PlayerUtil playerUtil;

    private AddonLoader addonLoader;

    public Configuration config;

    public static final String[] TABLES = {
        "inventory", "enderchest", "armor", "achievements", "xp", "stats", "player_state", 
        "player_effects", "placeholders"
    };

    public static List<String> jsonTables = List.of(
        "achievements", 
        "xp", 
        "stats", 
        "player_state",
        "player_effects", 
        "placeholders"
    );

    public final String[] sqlBased = {"mysql", "sqlite", "postgresql"};

    public MongoDatabase mongoDatabase;
    public MongoCollection<Document> mongoCollection;

    @Override
    public void onEnable() {
        logger = new Logger(this);
        scheduler = UniversalScheduler.getScheduler(this);

        loadConfig();

        SaveCommand saveCommand = new SaveCommand(this);
        if (getCommand("statsave") != null) {
            getCommand("statsave").setExecutor(saveCommand);
            getCommand("statsave").setTabCompleter(saveCommand);
        }

        loadDatabase();
        
        if (config.getBoolean("placeholderapi.enabled")) {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
                logger.log(Level.WARNING, "Placeholder saving is enabled but PlaceholderAPI is not installed.");
            }
        }

        if ("SNBT".equalsIgnoreCase(config.getString("inventory-format"))) {
            if (Bukkit.getPluginManager().getPlugin("NBTAPI") == null) {
                logger.log(Level.WARNING, "inventory-format is set to SNBT but the NBTAPI plugin is not installed. Skipping inventory, enderchest, and armor saving.");
            }
        }
        
        dataUtil = new DataUtil(this);
        playerUtil = new PlayerUtil(this);
        
        playerUtil.loadEncoders();

        // Create addons folder if it doesn't exist
        File addonsFolder = new File(getDataFolder(), "addons");
        if (!addonsFolder.exists()) {
            addonsFolder.mkdirs();
        }

        addonLoader = new AddonLoader(this, addonsFolder);
        addonLoader.loadAddons();
        getLogger().info("Loaded " + addonLoader.getLoadedAddons().size() + " addons.");
        
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (pool != null) {
            pool.closeConnection();
            pool = null;
        }
        if (mongoLoader != null) {
            mongoLoader.close();
            mongoLoader = null;
            mongoDatabase = null;
            mongoCollection = null;
        }
        if (addonLoader != null) {
            addonLoader.unloadAddons();
        }
    }

    public TaskScheduler getScheduler() {
        return scheduler;
    }

    private void loadConfig() {
        saveDefaultConfig();
        config = getConfig();
    }

    public void loadDatabase() {
        String storageMethod = getStorageMethod();

        if (storageMethod.equalsIgnoreCase("mongodb")) {
            if (mongoLoader == null) {
                mongoLoader = new MongoDBLoader(this);
            }
            loadMongo();
        } else if (isUsingSQL()) {
            pool = new HikariPool(this);
            if (config.getBoolean("recreatetables")) {
                recreateTables();
            }

            String prefix = getConfig().getString("table-prefix", "");
            for (String table : TABLES) {
                String dataType;
                if (jsonTables.contains(table)) {
                    dataType = pool.isSQLite() ? "TEXT" : "JSON";
                } else {
                    dataType = pool.isMySQL() ? "LONGTEXT" : "TEXT";
                }
                pool.createTable(
                    prefix + table, 
                    "player_uuid VARCHAR(36) PRIMARY KEY, username VARCHAR(16), data " + dataType
                );
            }
        }
    }

    public boolean isUsingSQL() {
        String current = getStorageMethod();
        if (current == null) {
            return false;
        }
        for (String s : sqlBased) { 
            if (current.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    public String getStorageMethod() {
        String method = getConfig().getString("storage-method");
        return method != null ? method : "";
    }

    public AddonLoader getAddonLoader() {
        return addonLoader;
    }

    public void reloadAddons() {
        if (addonLoader != null) {
            addonLoader.unloadAddons();
            addonLoader.loadAddons();
            getLogger().info("Reloaded " + addonLoader.getLoadedAddons().size() + " addons.");
        }
    }

    public void reload() {
        loadConfig();
        playerUtil.loadEncoders();

        // Safely close active connections before re-initializing
        if (pool != null) {
            pool.closeConnection();
            pool = null;
        }
        if (mongoLoader != null) {
            mongoLoader.close();
            mongoLoader = null;
            mongoDatabase = null;
            mongoCollection = null;
        }

        scheduler.runTaskAsynchronously(() -> {
            loadDatabase();
            reloadAddons();
        });
    }

    public void loadMongo() {
        mongoLoader.load();
        mongoDatabase = mongoLoader.getDatabase();
        mongoCollection = mongoLoader.mongoCollection;
    }

    private void recreateTables() {
        logger.log(Level.WARNING, "Deleting and Rebuilding tables...");
        if (pool == null) {
            return;
        }
        Connection conn = pool.getConnection();
        if (conn == null) {
            logger.log(Level.WARNING, "Failed to drop tables: database connection is null");
            return;
        }
        String prefix = getConfig().getString("table-prefix", "");
        try (Statement statement = conn.createStatement()) {
            for (String table : TABLES) {
                statement.execute("DROP TABLE IF EXISTS " + prefix + table);
            }
            logger.log(Level.INFO, "Tables successfully dropped.");
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to drop tables: " + e.getMessage());
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error closing connection: " + e.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        savePlayerData(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        savePlayerData(player);
    }

    public void savePlayerData(Player player) {
        playerUtil.saveData(player);
    }

}
