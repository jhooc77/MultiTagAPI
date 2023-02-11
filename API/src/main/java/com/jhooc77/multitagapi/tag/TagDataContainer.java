package com.jhooc77.multitagapi.tag;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public interface TagDataContainer {
    boolean isEntityRender(Player player, UUID other);

    void setPlayerRender(Player player, UUID target, boolean rendered);

    UUID getEntityUUIDFromID(int id);

    void setEntityID(UUID uuid, int id);

    Tag getTagFromEntityUUID(UUID uuid);

    void setEntityTag(UUID uuid, Tag tag);

    Set<UUID> getRenderedEntity(Player player);
}
