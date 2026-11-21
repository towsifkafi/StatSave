package com.towsifkafi.statsave.database;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

import com.towsifkafi.statsave.StatSave;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class HikariPool {

    private HikariDataSource hikari;
    private final StatSave plugin;
    private final File dataFolder;

    private boolean isSQL;
    private boolean isSQLite;
    private boolean isMySQL;
    private boolean isPostgreSQL;

    private String method;

    public HikariPool(StatSave pl) {
        this(pl, null);
    }

    public HikariPool(StatSave pl, File dataFolder) {
        this.plugin = pl;
        this.dataFolder = dataFolder;
        initPool();
    }

    private void initPool() {
        String storageMethod = plugin.getStorageMethod();
        this.method = storageMethod != null ? storageMethod.toLowerCase() : "";

        this.isMySQL = "mysql".equals(method);
        this.isSQLite = "sqlite".equals(method);
        this.isPostgreSQL = "postgresql".equals(method);
        this.isSQL = isMySQL || isSQLite || isPostgreSQL;

        if (!isSQL) {
            return;
        }

        HikariConfig config = new HikariConfig();

        if (isMySQL) {
            config.setPoolName("StatSaveMySQLPool");
            config.setDataSourceClassName(plugin.getConfig().getString("mysql.driver", "com.mysql.cj.jdbc.MysqlDataSource"));
            config.addDataSourceProperty("serverName", plugin.getConfig().getString("mysql.host", "127.0.0.1"));
            config.addDataSourceProperty("port", plugin.getConfig().getInt("mysql.port", 3306));
            config.addDataSourceProperty("databaseName", plugin.getConfig().getString("mysql.database", "database"));
            config.addDataSourceProperty("user", plugin.getConfig().getString("mysql.username", "user"));
            config.addDataSourceProperty("password", plugin.getConfig().getString("mysql.password", "pass"));
            config.setMaximumPoolSize(plugin.getConfig().getInt("mysql.pool-size", 10));
            config.setConnectionTimeout(30000);
        } else if (isPostgreSQL) {
            config.setPoolName("StatSavePostgresPool");
            config.setDataSourceClassName(plugin.getConfig().getString("postgresql.driver", "org.postgresql.ds.PGSimpleDataSource"));
            config.addDataSourceProperty("serverName", plugin.getConfig().getString("postgresql.host", "127.0.0.1"));
            config.addDataSourceProperty("port", plugin.getConfig().getInt("postgresql.port", 5432));
            config.addDataSourceProperty("databaseName", plugin.getConfig().getString("postgresql.database", "database"));
            config.addDataSourceProperty("user", plugin.getConfig().getString("postgresql.username", "user"));
            config.addDataSourceProperty("password", plugin.getConfig().getString("postgresql.password", "pass"));
            config.setMaximumPoolSize(plugin.getConfig().getInt("postgresql.pool-size", 10));
            config.setConnectionTimeout(30000);
        } else if (isSQLite) {
            config.setPoolName("StatSaveSQLitePool");
            File folder = this.dataFolder != null ? this.dataFolder : plugin.getDataFolder();
            if (folder != null && !folder.exists()) {
                folder.mkdirs();
            }
            File dbFile = new File(folder, plugin.getConfig().getString("sqlite.file", "database.db"));
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setMaximumPoolSize(1);
            config.setConnectionInitSql("PRAGMA journal_mode=WAL;");
            config.setConnectionTimeout(30000);
        }

        try {
            this.hikari = new HikariDataSource(config);
            log("Initialized database connection pool for: " + method);
        } catch (Exception e) {
            log(Level.SEVERE, "Failed to initialize HikariCP connection pool for " + method, e);
        }
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

    public boolean isSQL() {
        return isSQL;
    }

    public boolean isSQLite() {
        return isSQLite;
    }

    public boolean isMySQL() {
        return isMySQL;
    }

    public boolean isPostgreSQL() {
        return isPostgreSQL;
    }

    public String getMethod() {
        return method;
    }

    public void closeConnection() {
        if (hikari != null && !hikari.isClosed()) {
            try {
                hikari.close();
                debug("Closed database connection pool.");
            } catch (Exception e) {
                log(Level.WARNING, "Error closing connection pool: " + e.getMessage());
            } finally {
                hikari = null;
            }
        }
    }

    public Connection getConnection() {
        if (hikari == null || hikari.isClosed()) {
            log(Level.WARNING, "Database connection pool is not available.");
            return null;
        }
        try {
            return hikari.getConnection();
        } catch (Exception e) {
            log(Level.SEVERE, "Failed to obtain database connection", e);
        }
        return null;
    }

    public void reconnect() {
        log("Reconnecting database connection pool...");
        closeConnection();
        initPool();
    }

    public void createTable(String table, String columns) {
        String sql = String.format("CREATE TABLE IF NOT EXISTS %s (%s)", table, columns);
        Connection conn = getConnection();
        if (conn == null) {
            log(Level.SEVERE, "Could not create table " + table + ": connection unavailable");
            return;
        }
        try (Statement statement = conn.createStatement()) {
            statement.execute(sql);
            debug("Ensured table exists: " + table);
        } catch (SQLException e) {
            log(Level.SEVERE, "Could not create table " + table, e);
            throw new RuntimeException("Could not create table " + table, e);
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                log(Level.WARNING, "Failed to return connection to pool: " + e.getMessage());
            }
        }
    }

}
