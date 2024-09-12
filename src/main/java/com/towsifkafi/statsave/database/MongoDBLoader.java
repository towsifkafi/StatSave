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

    private StatSave plugin;
    private ConfigurationSection config;

    public MongoClient mongoClient;
    public MongoDatabase mongoDatabase;
    public MongoCollection<Document> mongoCollection;

    public MongoDBLoader(StatSave plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig().getConfigurationSection("mongodb");
    }

    public void load() {
        connect();
        mongoCollection = mongoDatabase.getCollection(config.getString("collection"));
        silenceLogger();
    }

    public void connect() {
        mongoClient = MongoClients.create(config.getString("connectionURI"));
        mongoDatabase = mongoClient.getDatabase(config.getString("database"));
    }

    public void silenceLogger() {
        System.setProperty("DEBUG.GO", "true");
        System.setProperty("BD.TRACE", "true");
        Logger.getLogger("org.mongodb.driver").setLevel(Level.WARNING);
    }

    public void close() {
        mongoClient.close();
    }

    public MongoDatabase getDatabase() {
        return mongoDatabase;
    }

    public MongoClient getMongoClient() {
        return mongoClient;
    }


}
