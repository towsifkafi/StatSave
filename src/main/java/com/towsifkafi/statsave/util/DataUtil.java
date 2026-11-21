package com.towsifkafi.statsave.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.towsifkafi.statsave.StatSave;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import me.clip.placeholderapi.PlaceholderAPI;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DataUtil {

    private final StatSave plugin;
    private final Gson gson = new Gson();

    public DataUtil(StatSave plugin) {
        this.plugin = plugin;
    }

    public String encodeToBase64(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    public String encodeItemArrayToSNBT(ItemStack[] items) {
        if (items == null || items.length == 0) {
            return "[]";
        }
        try {
            ReadWriteNBT nbt = NBT.itemStackArrayToNBT(items);
            return nbt != null ? nbt.toString() : "[]";
        } catch (Exception e) {
            plugin.getLogger().warning("Error serializing items to SNBT: " + e.getMessage());
            return "[]";
        }
    }

    public String encodeInventoryToSNBT(Player player) {
        if (player == null) return "[]";
        return encodeItemArrayToSNBT(player.getInventory().getContents());
    }

    public String encodeEnderChestToSNBT(Player player) {
        if (player == null) return "[]";
        return encodeItemArrayToSNBT(player.getEnderChest().getContents());
    }

    public String encodeArmorToSNBT(Player player) {
        if (player == null) return "[]";
        return encodeItemArrayToSNBT(player.getInventory().getArmorContents());
    }

    public String encodeInventoryToBase64(Player player) throws Exception {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeInt(contents.length);

            for (ItemStack item : contents) {
                dataOutput.writeObject(item);
            }

            return java.util.Base64.getEncoder().encodeToString(outputStream.toByteArray());
        }
    }
    
    public String encodeAchievementsToJSON(Player player) {
        Map<String, Boolean> achievements = new HashMap<>();

        try {
            Iterator<Advancement> advancements = Bukkit.advancementIterator();
            while (advancements.hasNext()) {
                Advancement advancement = advancements.next();
                AdvancementProgress progress = player.getAdvancementProgress(advancement);

                if (plugin.getConfig().getBoolean("achievements-options.strip-recipes")) {
                    if (advancement.getKey().getKey().contains("recipes")) continue;
                }

                if (plugin.getConfig().getBoolean("achievements-options.only-completed")) {
                    if (!progress.isDone()) continue;
                }

                achievements.put(advancement.getKey().getKey(), progress.isDone());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error encoding achievements for " + player.getName() + ": " + e.getMessage());
        }

        return gson.toJson(achievements);
    }

    public String encodeAchievementsToBase64(String jsonAchievements) {
        return encodeToBase64(jsonAchievements != null ? jsonAchievements.getBytes() : new byte[0]);
    }

    public String encodeEnderChestToBase64(Player player) throws Exception {
        Inventory enderChest = player.getEnderChest();
        ItemStack[] contents = enderChest.getContents();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeInt(contents.length);

            for (ItemStack item : contents) {
                dataOutput.writeObject(item);
            }

            return java.util.Base64.getEncoder().encodeToString(outputStream.toByteArray());
        }
    }

    public String encodeArmorToBase64(Player player) {
        PlayerInventory inventory = player.getInventory();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            ItemStack[] armor = inventory.getArmorContents();
            dataOutput.writeInt(armor.length);

            for (ItemStack item : armor) {
                dataOutput.writeObject(item);
            }

            return java.util.Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().warning("Error encoding armor for " + player.getName() + ": " + e.getMessage());
            return "";
        }
    }

    public String encodeStatsToJSON(Player player) {
        Map<String, Object> stats = new HashMap<>();

        for (Statistic statistic : Statistic.values()) {
            try {
                if (statistic.isSubstatistic()) {
                    Map<String, Integer> paramStats = new HashMap<>();
                    if (statistic.isBlock()) {
                        for (Material material : Material.values()) {
                            if (material.isBlock()) {
                                try {
                                    int value = player.getStatistic(statistic, material);
                                    if (plugin.getConfig().getBoolean("stats.strip-zero-values") && value == 0) continue;
                                    paramStats.put(material.name(), value);
                                } catch (IllegalArgumentException ignored) {
                                    // Skip unsupported statistic-material pairs
                                }
                            }
                        }
                    } else if (statistic.getType() == Statistic.Type.ITEM) {
                        for (Material material : Material.values()) {
                            if (material.isItem()) {
                                try {
                                    int value = player.getStatistic(statistic, material);
                                    if (plugin.getConfig().getBoolean("stats.strip-zero-values") && value == 0) continue;
                                    paramStats.put(material.name(), value);
                                } catch (IllegalArgumentException ignored) {
                                    // Skip unsupported statistic-item pairs
                                }
                            }
                        }
                    } else if (statistic.getType() == Statistic.Type.ENTITY) {
                        for (EntityType entityType : EntityType.values()) {
                            if (entityType.isSpawnable()) {
                                try {
                                    int value = player.getStatistic(statistic, entityType);
                                    if (plugin.getConfig().getBoolean("stats.strip-zero-values") && value == 0) continue;
                                    paramStats.put(entityType.name(), value);
                                } catch (IllegalArgumentException ignored) {
                                    // Skip unsupported statistic-entity pairs
                                }
                            }
                        }
                    }
                    if (!paramStats.isEmpty()) {
                        stats.put(statistic.name(), paramStats);
                    }
                } else {
                    int value = player.getStatistic(statistic);
                    if (plugin.getConfig().getBoolean("stats.strip-zero-values") && value == 0) continue;
                    stats.put(statistic.name(), value);
                }
            } catch (IllegalArgumentException ignored) {
                // Skip unsupported general statistics
            }
        }

        return gson.toJson(stats);
    }

    public String encodeStatsToBase64(String jsonStats) {
        return encodeToBase64(jsonStats != null ? jsonStats.getBytes() : new byte[0]);
    }

    public String encodeEffectsToJSON(Player player) {
        Collection<PotionEffect> effects = player.getActivePotionEffects();
        Map<String, Integer[]> effectMap = new HashMap<>();

        for (PotionEffect effect : effects) {
            effectMap.put(effect.getType().getName(), new Integer[]{effect.getDuration(), effect.getAmplifier()});
        }

        return gson.toJson(effectMap);
    }

    public String encodeEffectsToBase64(String jsonEffects) {
        return encodeToBase64(jsonEffects != null ? jsonEffects.getBytes() : new byte[0]);
    }

    public String encodePlayerXP(Player player) {
        JsonObject data = new JsonObject();
        data.addProperty("level", player.getLevel());
        data.addProperty("xp", player.getExp());
        return data.toString();
    }

    public String encodePlayerStats(Player player) {
        JsonObject data = new JsonObject();
        data.addProperty("hunger", player.getFoodLevel());
        data.addProperty("health", player.getHealth());
        data.addProperty("ping", player.getPing());
        return data.toString();
    }

    public String encodePlaceholdersToJSON(Player player) {
        Map<String, Object> placeholders = new HashMap<>();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return gson.toJson(placeholders);
        }

        for (String p : plugin.getConfig().getStringList("placeholderapi.placeholders")) {
            String placeholder = "%" + p + "%";
            String parsedPlaceholder = PlaceholderAPI.setPlaceholders(player, placeholder);
            if (placeholder.equals(parsedPlaceholder)) {
                placeholders.put(p, "");
            } else {
                placeholders.put(p, parsePlaceholderText(parsedPlaceholder));
            }
        }

        return gson.toJson(placeholders);
    }

    public Object parsePlaceholderText(String input) {
        if (input == null) {
            return null;
        }
        input = input.trim();
        if (input.equalsIgnoreCase("true") || input.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(input);
        }
        try {
            if (input.matches("^-?\\d+$")) {
                try {
                    return Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    return Long.parseLong(input);
                }
            }
            
            if (input.matches("^-?\\d*\\.\\d+$")) {
                return Double.parseDouble(input);
            }
        } catch (NumberFormatException ignored) {
            // If number parsing fails, return the original string
        }
        return input;
    }
}
