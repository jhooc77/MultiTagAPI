package com.jhooc77.multitagapi.tag;

import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface Tag {
    LivingEntity getEntity();
    UUID getEntityUUID();

    void setTexts(String... texts);

    void setText(int line, @Nullable String text);

    void setTexts(@NotNull List<@Nullable String> texts);

    List<String> getTexts();

    @ApiStatus.Internal
    void setEntityId(int entityId);
}
