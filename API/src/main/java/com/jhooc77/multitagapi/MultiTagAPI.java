package com.jhooc77.multitagapi;

import com.jhooc77.multitagapi.entity.handler.TagHandler;
import com.jhooc77.multitagapi.tag.Tag;
import com.jhooc77.multitagapi.tag.TagDataContainer;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public interface MultiTagAPI {
    TagHandler getTagHandler();

    Tag createTag(LivingEntity entity);

    void removeTag(LivingEntity entity);

    @Nullable Tag getTag(LivingEntity entity);

    JavaPlugin getPlugin();

    TagDataContainer getTagDataContainer();

    Material getMaterial();

    void setMaterial(Material material);

    int getCustomModelData();

    void setCustomModelData(int customModelData);

    int getData();

    void setData(int data);
}
