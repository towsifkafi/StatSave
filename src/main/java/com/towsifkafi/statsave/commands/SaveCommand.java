package com.towsifkafi.statsave.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.towsifkafi.statsave.StatSave;

public class SaveCommand implements CommandExecutor {

    private StatSave plugin;

    public SaveCommand(StatSave plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
        if (!commandSender.hasPermission("statsave.admin")) {
            commandSender.sendMessage(color("&cYou do not have permission to use this command."));
            return false;
        }

        if(args.length != 0) {
            String subcommand = args[0];
            if(subcommand.equalsIgnoreCase("reload")) {
                plugin.reload();
                commandSender.sendMessage(color("&aStatSave configuration reloaded."));
                return true;
            } else if(subcommand.equalsIgnoreCase("save")) {

                if(args.length > 1) {

                    String arg = args[1];

                    if(arg.equalsIgnoreCase("offline")) {
                        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                            plugin.playerUtil.saveData(offlinePlayer.getPlayer());
                        }
                
                        commandSender.sendMessage(color("&aAll offline player data has been saved to the database."));
                    }

                } else {
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        plugin.playerUtil.saveData(onlinePlayer);
                    }
            
                    commandSender.sendMessage(color("&aAll player data has been saved to the database."));
                }

            }  else commandSender.sendMessage(color("&cInvalid subcommand. Use /statsave reload or /statsave save."));
        } else commandSender.sendMessage(color("&cInvalid usage. Use /statsave reload or /statsave save."));
        
        return false;
    }

    public String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
