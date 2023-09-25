package com.jhooc77.multitagapi;

import com.jhooc77.multitagapi.demo.DemoCommand;
import com.jhooc77.multitagapi.demo.DemoHandler;
import com.jhooc77.multitagapi.entity.handler.*;
import com.jhooc77.multitagapi.handler.Handler;
import com.jhooc77.multitagapi.tag.Tag;
import com.jhooc77.multitagapi.tag.TagDataContainer;
import com.jhooc77.multitagapi.tag.TagDataContainerImpl;
import com.jhooc77.multitagapi.tag.TagImpl;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

public class MultiTagAPIImpl extends JavaPlugin implements MultiTagAPI {


    private static MultiTagAPI multiTagAPI;
    private TagHandler tagHandler;
    private TagDataContainer tagDataContainer;
    private JavaPlugin plugin;

    private Material material = Material.PAPER;
    private int customModelData = 101;
    private int data = 0;
    private boolean onlyPlayer;

    private void init(JavaPlugin plugin) {
        this.plugin = plugin;
        MultiTagAPIImpl.multiTagAPI = this;
        tagDataContainer = new TagDataContainerImpl(this);

        String version = plugin.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
        switch (version) {
            case "v1_12_R1":
                tagHandler = new TagHandler_V1_12_R1(this);
                break;
            case "v1_16_R3":
                tagHandler = new TagHandler_V1_16_R3(this);
                break;
            case "v1_17_R1":
                tagHandler = new TagHandler_V1_17_R1(this);
                break;
            case "v1_18_R1":
                tagHandler = new TagHandler_V1_18_R1(this);
                break;
            case "v1_18_R2":
                tagHandler = new TagHandler_V1_18_R2(this);
                break;
            case "v1_19_R1":
                tagHandler = new TagHandler_V1_19_R1(this);
                break;
            case "v1_19_R2":
                tagHandler = new TagHandler_V1_19_R2(this);
                break;
            case "v1_19_R3":
                tagHandler = new TagHandler_V1_19_R3(this);
                break;
            default:
                tagHandler = new TagHandler_V1_20_R1(this);
                break;
        }

        plugin.getServer().getPluginManager().registerEvents(new Handler(this), plugin);

    }

    @Override
    public void onEnable() {
        init(this);
        saveDefaultConfig();
        reloadConfig();
        material = Material.getMaterial(getConfig().getString("item.material"));
        customModelData = getConfig().getInt("item.customModelData");
        data = getConfig().getInt("item.data");
        onlyPlayer = getConfig().getBoolean("only-player");
        if (getConfig().getBoolean("demo")) {
            getServer().getPluginManager().registerEvents(new DemoHandler(this), this);
            try {
                final Field bukkitCommandMap = getServer().getClass().getDeclaredField("commandMap");
                bukkitCommandMap.setAccessible(true);
                CommandMap commandMap = (CommandMap) bukkitCommandMap.get(getServer());
                Command command = new DemoCommand(this);
                commandMap.register("multitagapi", command);
            } catch(Throwable e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public TagHandler getTagHandler() {
        return this.tagHandler;
    }

    @Override
    public Tag createTag(LivingEntity entity) {
        Tag tag = new TagImpl(entity, this);
        getTagHandler().createTag(tag);
        return tag;
    }

    @Override
    public void removeTag(LivingEntity entity) {
        Tag tag = tagDataContainer.getTagFromEntityUUID(entity.getUniqueId());
        if (tag != null) {
            getTagHandler().removeTag(tag);
        }
    }

    @Override
    public @Nullable Tag getTag(LivingEntity entity) {
        return tagDataContainer.getTagFromEntityUUID(entity.getUniqueId());
    }

    @Override
    public JavaPlugin getPlugin() {
        return plugin;
    }

    @Override
    public TagDataContainer getTagDataContainer() {
        return tagDataContainer;
    }

    public static MultiTagAPI register(JavaPlugin plugin) {
        if (multiTagAPI != null) {
            return multiTagAPI;
        }
        MultiTagAPIImpl instance = new MultiTagAPIImpl();
        instance.init(plugin);
        return instance;
    }

    public static MultiTagAPI getAPI() {
        if (multiTagAPI == null) {
            throw new RuntimeException("API not registered!");
        }
        return multiTagAPI;
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    @Override
    public void setMaterial(Material material) {
        this.material = material;
    }

    @Override
    public int getCustomModelData() {
        return customModelData;
    }

    @Override
    public void setCustomModelData(int customModelData) {
        this.customModelData = customModelData;
    }

    @Override
    public int getData() {
        return data;
    }

    @Override
    public void setData(int data) {
        this.data = data;
    }

    @Override
    public boolean isOnlyPlayer() {
        return onlyPlayer;
    }

    @Override
    public void setOnlyPlayer(boolean onlyPlayer) {
        this.onlyPlayer = onlyPlayer;
    }
}
