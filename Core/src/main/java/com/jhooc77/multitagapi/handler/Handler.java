package com.jhooc77.multitagapi.handler;

import com.jhooc77.multitagapi.MultiTagAPI;
import com.jhooc77.multitagapi.MultiTagAPIImpl;
import com.jhooc77.multitagapi.tag.TagDataContainer;
import com.jhooc77.multitagapi.tag.TagDataContainerImpl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;

public class Handler implements Listener {

    final MultiTagAPI plugin;

    public Handler(MultiTagAPI plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getTagDataContainer().setEntityID(event.getPlayer().getUniqueId(), event.getPlayer().getEntityId());
        plugin.getTagHandler().injectPacketListenerPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getTagHandler().unInjectPacketListenerPlayer(event.getPlayer());
    }
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        TagDataContainer container = plugin.getTagDataContainer();
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof LivingEntity) {
                container.setEntityID(entity.getUniqueId(), entity.getEntityId());
            }
        }
    }
}
