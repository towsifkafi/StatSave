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
    
    private static final Logger LOGGER = Bukkit.getLogger();

    public HikariPool(StatSave plugin) {
        hikari = new HikariDataSource();

        hikari.setDataSourceClassName(plugin.getConfig().getString("mysql.driver"));
        hikari.addDataSourceProperty("serverName", plugin.getConfig().getString("mysql.host"));
        hikari.addDataSourceProperty("port", plugin.getConfig().getInt("mysql.port"));
        hikari.addDataSourceProperty("databaseName", plugin.getConfig().getString("mysql.database"));
        hikari.addDataSourceProperty("user", plugin.getConfig().getString("mysql.username"));
        hikari.addDataSourceProperty("password", plugin.getConfig().getString("mysql.password"));

    }

    public void closeConnection() {
        hikari.close();
    }

    public Connection getConnection() {
        try {
            return hikari.getConnection();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void reconnect() {
        try {
            hikari.close();
            hikari.getConnection();
        } catch (Exception e) {
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
            LOGGER.log(Level.SEVERE, "Could not create table " + table, e);
            throw new RuntimeException("Could not create table " + table, e);
        }
    }

    
}
