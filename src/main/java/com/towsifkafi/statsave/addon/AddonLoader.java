package com.towsifkafi.statsave.addon;

import com.towsifkafi.statsave.StatSave;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class AddonLoader {
    private final StatSave plugin;
    private final File addonsFolder;
    private final List<Addon> loadedAddons = new ArrayList<>();
    private final List<URLClassLoader> classLoaders = new ArrayList<>();

    public AddonLoader(StatSave plugin, File addonsFolder) {
        this.plugin = plugin;
        this.addonsFolder = addonsFolder;
    }

    public List<Addon> getLoadedAddons() {
        return new ArrayList<>(loadedAddons);
    }

    public void loadAddons() {
        File[] files = addonsFolder.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null) return;

        for (File file : files) {
            URLClassLoader classLoader = null;
            JarFile jarFile = null;
            try {
                URL jarUrl = file.toURI().toURL();
                classLoader = new URLClassLoader(new URL[]{jarUrl}, plugin.getClass().getClassLoader());
                jarFile = new JarFile(file);

                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class")) {
                        String className = entry.getName().replace("/", ".").replace(".class", "");
                        try {
                            Class<?> clazz = classLoader.loadClass(className);
                            if (Addon.class.isAssignableFrom(clazz) && !clazz.isInterface()) {
                                Addon addon = (Addon) clazz.getDeclaredConstructor().newInstance();
                                addon.onEnable(plugin);
                                String[] createTableSQL = addon.getTableSchema();

                                for(String sql : createTableSQL) {
                                    try (Connection conn = plugin.pool.getConnection();
                                        Statement stmt = conn.createStatement()) {
                                        stmt.execute(sql);
                                        plugin.getLogger().info("Created table for addon: " + clazz.getSimpleName());
                                    } catch (SQLException e) {
                                        plugin.getLogger().warning("Failed to create table for " + clazz.getSimpleName() + ": " + e.getMessage());
                                    }
                                }
                                
                                loadedAddons.add(addon);
                                classLoaders.add(classLoader);
                                plugin.getLogger().info("Loaded addon: " + clazz.getSimpleName());
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to load class " + className + ": " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load addon from " + file.getName() + ": " + e.getMessage());
            } finally {
                if (jarFile != null) {
                    try {
                        jarFile.close();
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to close JAR file " + file.getName() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    public void unloadAddons() {
        for (Addon addon : loadedAddons) {
            try {
                addon.onDisable(plugin);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to disable addon " + addon.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        loadedAddons.clear();

        for (URLClassLoader classLoader : classLoaders) {
            try {
                classLoader.close();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to close class loader: " + e.getMessage());
            }
        }
        classLoaders.clear();
    }
}