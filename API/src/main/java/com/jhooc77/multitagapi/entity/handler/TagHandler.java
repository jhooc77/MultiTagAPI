package com.jhooc77.multitagapi.entity.handler;

import com.jhooc77.multitagapi.tag.Tag;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public interface TagHandler {
    void unInjectPacketListenerPlayer(Player player);

    void injectPacketListenerPlayer(Player player);

    void updateTag(Tag tag);

    void createTag(Tag tag);

    void removeTag(Tag tag);
}
