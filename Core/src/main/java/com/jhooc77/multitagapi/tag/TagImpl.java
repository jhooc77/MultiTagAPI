package com.jhooc77.multitagapi.tag;

import com.jhooc77.multitagapi.MultiTagAPI;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ExecutionException;

public class TagImpl implements Tag {

    protected List<String> texts = new ArrayList<>();

    protected LivingEntity entity;

    private final MultiTagAPI plugin;
    private final UUID uuid;
    private int entityId;


    public TagImpl(LivingEntity entity, MultiTagAPI plugin) {
        this.plugin = plugin;
        this.entity = entity;
        this.texts.add(entity.getName());
        this.entityId = entity.getEntityId();
        this.uuid = entity.getUniqueId();
    }

    @Override
    public LivingEntity getEntity() {
        if (this.entityId != this.entity.getEntityId()) {
            try {
                this.entity = (LivingEntity) plugin.getPlugin().getServer().getScheduler().callSyncMethod(plugin.getPlugin(), () -> plugin.getPlugin().getServer().getEntity(entity.getUniqueId())).get();
            } catch (InterruptedException | ExecutionException ex) {
                throw new RuntimeException(ex);
            }
        }
        return this.entity;
    }

    @Override
    public void setTexts(String... texts) {
        setTexts(Arrays.asList(texts));
    }

    @Override
    public void setText(int line, @Nullable String text) {
        if (texts.size() > line && texts.get(texts.size()-1-line).equals(text)) return;
        while (texts.size() <= line) {
            texts.add("");
        }
        texts.set(texts.size()-1-line, text);
        plugin.getTagHandler().updateTag(this);
    }

    @Override
    public void setTexts(@NotNull List<@Nullable String> texts) {
        List<String> texts1 = new ArrayList<>(texts);
        while (texts1.remove(null));
        Collections.reverse(texts1);
        if (this.texts.equals(texts1)) return;
        this.texts = texts1;
        plugin.getTagHandler().updateTag(this);
    }

    @Override
    public List<String> getTexts(){
        return Collections.unmodifiableList(this.texts);
    }

    @ApiStatus.Internal
    @Override
    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }

    @Override
    public UUID getEntityUUID() {
        return this.uuid;
    }
}
