package com.towsifkafi.statsave.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.towsifkafi.statsave.StatSave;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SaveCommand implements CommandExecutor, TabCompleter {

    private final StatSave plugin;

    public SaveCommand(StatSave plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
        if (!commandSender.hasPermission("statsave.admin")) {
            commandSender.sendMessage(color("&cYou do not have permission to use this command."));
            return true;
        }

        if (args.length == 0) {
            commandSender.sendMessage(color("&cInvalid usage. Use /statsave reload or /statsave save [all|offline]."));
            return true;
        }

        String subcommand = args[0];
        if (subcommand.equalsIgnoreCase("reload")) {
            plugin.reload();
            commandSender.sendMessage(color("&aStatSave configuration reloaded."));
            return true;
        } else if (subcommand.equalsIgnoreCase("save")) {
            if (args.length > 1 && args[1].equalsIgnoreCase("offline")) {
                int savedCount = 0;
                for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                    Player online = offlinePlayer.getPlayer();
                    if (online != null && online.isOnline()) {
                        plugin.playerUtil.saveData(online);
                        savedCount++;
                    }
                }
                commandSender.sendMessage(color(String.format(
                    "&aSaved data for %d online player(s). &eNote: Full inventory/state data cannot be retrieved for players who are currently offline.",
                    savedCount
                )));
                return true;
            } else {
                int count = 0;
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    plugin.playerUtil.saveData(onlinePlayer);
                    count++;
                }
                commandSender.sendMessage(color(String.format("&aSaved data for %d active player(s) to the database.", count)));
                return true;
            }
        } else {
            commandSender.sendMessage(color("&cInvalid subcommand. Use /statsave reload or /statsave save [all|offline]."));
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("statsave.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("reload", "save");
            return subcommands.stream()
                .filter(sub -> sub.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("save")) {
            List<String> options = Arrays.asList("all", "offline");
            return options.stream()
                .filter(opt -> opt.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
