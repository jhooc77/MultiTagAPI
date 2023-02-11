package com.jhooc77.multitagapi.demo;

import com.jhooc77.multitagapi.MultiTagAPI;
import com.jhooc77.multitagapi.MultiTagAPIImpl;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class DemoHandler implements Listener {


    private final MultiTagAPI plugin;

    public DemoHandler(MultiTagAPI plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.createTag(event.getPlayer());
    }
}
