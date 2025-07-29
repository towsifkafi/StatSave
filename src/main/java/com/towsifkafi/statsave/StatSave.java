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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;

public final class StatSave extends JavaPlugin implements Listener {

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

    public String[] sqlBased = {"mysql", "sqlite", "postgresql"};

    public MongoDatabase mongoDatabase;
    public MongoCollection<Document> mongoCollection;

    @Override
    public void onEnable() {
        logger = new Logger(this);
        scheduler = UniversalScheduler.getScheduler(this);

        loadConfig();

        String start = new StringBuilder().append("\n")
        .append("")
        .toString();

        
        getCommand("statsave").setExecutor(new SaveCommand(this));

        loadDatabase();
        
        if(config.getBoolean("placeholderapi.enabled")) {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
                logger.log(Level.WARNING, "Placeholder saving is enabled but PlaceholderAPI is not installed.");
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
        pool.closeConnection();
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

        mongoLoader = new MongoDBLoader(this);
        pool = new HikariPool(this);

        if(storageMethod.equalsIgnoreCase("mongodb")) {
            loadMongo();
        } else if(isUsingSQL()) {
            if (config.getBoolean("recreatetables")) {
                recreateTables();
            }

            for (String table : TABLES) {
                String dataType = jsonTables.contains(table) ? "JSON" : "LONGTEXT";
                pool.createTable(
                    getConfig().getString("table-prefix")+table, 
                    "player_uuid VARCHAR(36) PRIMARY KEY, username VARCHAR(16), data "+dataType
                );
            }
        }
    }

    public boolean isUsingSQL() {
        for(String s : sqlBased) { 
            if(getStorageMethod().equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }
    public String getStorageMethod() {
        return getConfig().getString("storage-method");
    }

    public void reloadAddons() {
        // Unload existing addons
        addonLoader.unloadAddons();

        // Reload addons
        addonLoader.loadAddons();
        getLogger().info("Reloaded " + addonLoader.getLoadedAddons().size() + " addons.");
    }

    public void reload() {
        loadConfig();
        playerUtil.loadEncoders();

        if(mongoDatabase != null) mongoLoader.close();

        scheduler.runTaskAsynchronously(() -> {
            String storageMethod = getStorageMethod();
            if(storageMethod.equalsIgnoreCase("mongodb")) {
                loadMongo();
            } else if(isUsingSQL()) {
                pool = new HikariPool(this);
            }
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
        try (PreparedStatement statement = pool.getConnection().prepareStatement(
                "DROP TABLE IF EXISTS " + String.join(", ", TABLES))
            ) {
            statement.execute();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to drop tables: "+ e.toString());
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
