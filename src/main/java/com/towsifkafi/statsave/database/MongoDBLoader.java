package com.towsifkafi.statsave.database;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bson.Document;
import org.bukkit.configuration.ConfigurationSection;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.towsifkafi.statsave.StatSave;

public class MongoDBLoader {

    private final StatSave plugin;
    private ConfigurationSection config;

    public MongoClient mongoClient;
    public MongoDatabase mongoDatabase;
    public MongoCollection<Document> mongoCollection;

    public MongoDBLoader(StatSave plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig().getConfigurationSection("mongodb");
    }

    private void log(String message) {
        if (plugin.logger != null) {
            plugin.logger.log(message);
        } else if (plugin.getLogger() != null) {
            plugin.getLogger().info(message);
        }
    }

    private void log(Level level, String message, Throwable t) {
        if (plugin.logger != null) {
            plugin.logger.log(level, message, t);
        } else if (plugin.getLogger() != null) {
            plugin.getLogger().log(level, message, t);
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

    public void load() {
        this.config = plugin.getConfig().getConfigurationSection("mongodb");
        if (config == null) {
            log(Level.SEVERE, "MongoDB configuration section ('mongodb') is missing in config.yml!");
            return;
        }
        silenceLogger();
        connect();
        if (mongoDatabase != null) {
            String collectionName = config.getString("collection", "player_data");
            mongoCollection = mongoDatabase.getCollection(collectionName);
            log("Using MongoDB collection: " + collectionName);
        }
    }

    public void connect() {
        if (config == null) {
            return;
        }
        try {
            String uri = config.getString("connectionURI", "mongodb://localhost:27017");
            String databaseName = config.getString("database", "statsave");
            mongoClient = MongoClients.create(uri);
            mongoDatabase = mongoClient.getDatabase(databaseName);
            log("Connected to MongoDB database: " + databaseName);
        } catch (Exception e) {
            log(Level.SEVERE, "Failed to connect to MongoDB", e);
        }
    }

    public void silenceLogger() {
        try {
            Logger.getLogger("org.mongodb.driver").setLevel(Level.WARNING);
        } catch (Exception ignored) {
        }
    }

    public void close() {
        if (mongoClient != null) {
            try {
                mongoClient.close();
                debug("Closed MongoDB client connection.");
            } catch (Exception e) {
                log(Level.WARNING, "Error closing MongoDB client: " + e.getMessage());
            } finally {
                mongoClient = null;
                mongoDatabase = null;
                mongoCollection = null;
            }
        }
    }

    public MongoDatabase getDatabase() {
        return mongoDatabase;
    }

    public MongoClient getMongoClient() {
        return mongoClient;
    }

}
