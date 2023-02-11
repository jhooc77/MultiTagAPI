package com.jhooc77.multitagapi.demo;

import com.jhooc77.multitagapi.MultiTagAPI;
import com.jhooc77.multitagapi.tag.Tag;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class DemoCommand extends Command {

    final private MultiTagAPI plugin;

    public DemoCommand(MultiTagAPI plugin) {
        super("multitagtest");
        this.setPermission("multitagapi.demo.command");
        this.plugin = plugin;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("/" + label + " [target] set [<text>]");
            sender.sendMessage("/" + label + " [target] clear");
            return true;
        }
        if (args.length > 1) {
            Entity target;
            try {
                UUID uuid = UUID.fromString(args[0]);
                target = plugin.getPlugin().getServer().getEntity(uuid);
            } catch (Exception e){
                target = plugin.getPlugin().getServer().getPlayer(args[0]);
            }
            if (target instanceof LivingEntity) {
                LivingEntity entity = (LivingEntity) target;
                Tag tag = plugin.getTag(entity);
                if (args[1].equalsIgnoreCase("set") && args.length > 2) {
                    if (tag == null) {
                        tag = plugin.createTag(entity);
                    }
                    List<String> st = Arrays.stream(args, 2, args.length).map(test -> test.replace("%name%", entity.getName()).replace("\"\"", "")).collect(Collectors.toList());
                    tag.setTexts(st);
                } else if (args[1].equalsIgnoreCase("clear")) {
                    if (tag != null) {
                        plugin.removeTag(entity);
                    }
                }
            }
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            Set<String> list = new HashSet<>(plugin.getPlugin().getServer().getOnlinePlayers().stream().map(p -> p.getName()).collect(Collectors.toList()));
            if (sender instanceof Player) {
                Player p = (Player) sender;
                Vector vector = p.getEyeLocation().getDirection().normalize();
                Location loc = p.getEyeLocation().clone();
                for(int i = 0; i < 10; i++) {
                    loc.add(vector);
                    for (Entity target : p.getWorld().getNearbyEntities(loc, 1, 1, 1)) {
                        if (target != p && target instanceof LivingEntity) {
                            list.add(target.getUniqueId().toString());
                        }
                    }
                }
            }
            return list.stream().filter(s -> s.startsWith(args[0])).collect(Collectors.toList());
        } else if (args.length == 2) {
            return Arrays.asList("set", "clear").stream().filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
