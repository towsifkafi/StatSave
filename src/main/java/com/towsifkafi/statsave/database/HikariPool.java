package com.towsifkafi.statsave.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;

import com.towsifkafi.statsave.StatSave;
import com.zaxxer.hikari.HikariDataSource;

public class HikariPool {

    private HikariDataSource hikari;
    
    private StatSave plugin;

    private static boolean isSQL;
    private static boolean isSQLite;

    private static String method;

    public HikariPool(StatSave pl) {
        plugin = pl;

        hikari = new HikariDataSource();

        String storageMethod = plugin.getStorageMethod();

        String[] sqlBased = plugin.sqlBased;

        method = storageMethod;
        for(String s : sqlBased) {
            if(storageMethod.equalsIgnoreCase(s)) {
                isSQL = true;
                if(storageMethod.equalsIgnoreCase("sqlite")) {
                    isSQLite = true;
                }
                break;
            } else {
                isSQL = false;
            }
        }

        if(storageMethod.equalsIgnoreCase("mysql")) {
            hikari.setDataSourceClassName(plugin.getConfig().getString("mysql.driver"));
            hikari.addDataSourceProperty("serverName", plugin.getConfig().getString("mysql.host"));
            hikari.addDataSourceProperty("port", plugin.getConfig().getInt("mysql.port"));
            hikari.addDataSourceProperty("databaseName", plugin.getConfig().getString("mysql.database"));
            hikari.addDataSourceProperty("user", plugin.getConfig().getString("mysql.username"));
            hikari.addDataSourceProperty("password", plugin.getConfig().getString("mysql.password"));
            hikari.setMaximumPoolSize(50); // 50 Connections (including idle connections)
        } else if(storageMethod.equalsIgnoreCase("postgresql")) {
            hikari.setDataSourceClassName(plugin.getConfig().getString("postgresql.driver"));
            hikari.addDataSourceProperty("serverName", plugin.getConfig().getString("postgresql.host"));
            hikari.addDataSourceProperty("port", plugin.getConfig().getInt("postgresql.port"));
            hikari.addDataSourceProperty("databaseName", plugin.getConfig().getString("postgresql.database"));
            hikari.addDataSourceProperty("user", plugin.getConfig().getString("postgresql.username"));
            hikari.addDataSourceProperty("password", plugin.getConfig().getString("postgresql.password"));
            hikari.setMaximumPoolSize(50); // 50 Connections (including idle connections)
        } else if(isSQLite) {
            hikari.setPoolName("StatSaveSQLitePool");
            hikari.setDataSourceClassName(plugin.getConfig().getString("sqlite.driver"));
            hikari.addDataSourceProperty("url", "jdbc:sqlite:plugins/" + plugin.getName() + "/" + plugin.getConfig().getString("sqlite.file"));
            hikari.setMaxLifetime(60000); // 60 Sec
            hikari.setMaximumPoolSize(50); // 50 Connections (including idle connections)
        }

    }

    public boolean isSQL() {
        return isSQL;
    }

    public boolean isSQLite() {
        return isSQLite;
    }

    public void closeConnection() {
        hikari.close();
    }

    public Connection getConnection() {
        try {
            return hikari.getConnection();
        } catch (Exception e) {
            plugin.logger.log(Level.SEVERE, "Failed to connect to database", e);
            e.printStackTrace();
        }
        return null;
    }

    public void reconnect() {
        try {
            hikari.close();
            hikari.getConnection();
        } catch (Exception e) {
            plugin.logger.log(Level.SEVERE, "Failed to reconnect to database", e);
            e.printStackTrace();
        }
    }

    public static Connection getSConnection(HikariPool pool) {
        try {
            return pool.hikari.getConnection();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void createTable(String table, String columns) {
        String sql = String.format("CREATE TABLE IF NOT EXISTS %s (%s)", table, columns);
        try (Statement statement = getConnection().createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            plugin.logger.log(Level.SEVERE, "Could not create table " + table, e);
            throw new RuntimeException("Could not create table " + table, e);
        }
    }

    
}
