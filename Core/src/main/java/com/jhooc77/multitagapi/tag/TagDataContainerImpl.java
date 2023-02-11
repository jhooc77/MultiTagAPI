package com.jhooc77.multitagapi.tag;

import com.jhooc77.multitagapi.MultiTagAPI;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

import java.util.*;

@ApiStatus.Internal
public class TagDataContainerImpl implements TagDataContainer {

    private final WeakHashMap<Player, HashMap<UUID, Boolean>> RENDERED_DATA = new WeakHashMap<>();
    private final Map<Integer, UUID> ID_DATA = new HashMap<>();
    private final HashMap<UUID, Tag> TAG_DATA = new HashMap<>();

    private final MultiTagAPI plugin;

    public TagDataContainerImpl(MultiTagAPI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isEntityRender(Player player, UUID other) {
        return RENDERED_DATA.getOrDefault(player, new HashMap<>()).getOrDefault(other, false);
    }

    @Override
    public void setPlayerRender(Player player, UUID target, boolean rendered) {
        HashMap<UUID, Boolean> playerData = RENDERED_DATA.getOrDefault(player, new HashMap<>());
        playerData.put(target, rendered);
        RENDERED_DATA.put(player, playerData);
    }

    @Override
    public UUID getEntityUUIDFromID(int id) {
        return ID_DATA.getOrDefault(id, null);
    }

    @Override
    public void setEntityID(UUID uuid, int id) {
        ID_DATA.put(id, uuid);
    }

    @Override
    public Tag getTagFromEntityUUID(UUID uuid) {
        return TAG_DATA.getOrDefault(uuid, null);
    }

    @Override
    public void setEntityTag(UUID uuid, Tag tag) {
        if (tag == null) {
            TAG_DATA.remove(uuid);
        } else if (!TAG_DATA.containsKey(uuid)){
            TAG_DATA.put(uuid, tag);
        } else {
            plugin.getTagHandler().removeTag(TAG_DATA.get(uuid));
            TAG_DATA.put(uuid, tag);
        }
    }

    @Override
    public Set<UUID> getRenderedEntity(Player player) {
        return RENDERED_DATA.getOrDefault(player, new HashMap<>()).keySet();
    }
}
