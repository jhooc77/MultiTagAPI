package com.jhooc77.multitagapi.entity.handler;

import com.jhooc77.multitagapi.MultiTagAPI;
import com.jhooc77.multitagapi.tag.Tag;
import com.jhooc77.multitagapi.tag.TagDataContainer;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.server.v1_16_R3.*;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_16_R3.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;

public class TagHandler_V1_16_R3 implements TagHandler {

    private ItemStack itemStack;

    private final MultiTagAPI plugin;
    private final TagDataContainer tagDataContainer;

    private final WeakHashMap<Tag, EntitySilverfish> mainLines = new WeakHashMap<>();

    private final WeakHashMap<Tag, List<EntityArmorStand>> textLines = new WeakHashMap<>();
    private final WeakHashMap<Tag, List<EntitySnowball>> spliters = new WeakHashMap<>();

    public TagHandler_V1_16_R3(MultiTagAPI plugin) {
        this.plugin = plugin;
        this.tagDataContainer = plugin.getTagDataContainer();
    }

    @Override
    public void unInjectPacketListenerPlayer(Player player) {
        Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;
        if (channel.pipeline().get("multiTag") == null) return;
        channel.pipeline().remove("multiTag");
    }

    @Override
    public void injectPacketListenerPlayer(Player player) {
        Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;
        if (channel.pipeline().get("multiTag") != null) return;
        channel.pipeline().addBefore("packet_handler", "multiTag", new ChannelDuplexHandler() {

            final PlayerConnection connection = ((CraftPlayer) player).getHandle().playerConnection;
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof PacketPlayOutSpawnEntity || msg instanceof PacketPlayOutSpawnEntityLiving) {
                    PacketDataSerializer dataSerializer = new PacketDataSerializer(Unpooled.buffer());
                    if (msg instanceof PacketPlayOutSpawnEntity packet) {
                        packet.b(dataSerializer);
                    } else {
                        PacketPlayOutSpawnEntityLiving packet = (PacketPlayOutSpawnEntityLiving) msg;
                        packet.b(dataSerializer);
                    }
                    int id = dataSerializer.i();
                    UUID uuid = dataSerializer.k();
                    tagDataContainer.setPlayerRender(player, uuid, true);
                    tagDataContainer.setEntityID(uuid, id);
                    Tag tag = tagDataContainer.getTagFromEntityUUID(uuid);
                    if (tag != null) {
                        tag.setEntityId(id);
                        LivingEntity target = tag.getEntity();
                        if (!tag.getEntityUUID().equals(player.getUniqueId())) {
                            Location location = target.getEyeLocation();
                            double x = location.getX(); double y = location.getY();double z = location.getZ();
                            EntitySilverfish mainLine = mainLines.get(tag);
                            mainLine.setPositionRaw(x, y, z);
                            connection.sendPacket(new PacketPlayOutSpawnEntityLiving(mainLine));
                            connection.sendPacket(new PacketPlayOutEntityMetadata(mainLine.getId(), mainLine.getDataWatcher(), true));
                            PacketDataSerializer data = new PacketDataSerializer(Unpooled.buffer());
                            data.d(id);
                            data.a(new int[]{mainLine.getId()});
                            PacketPlayOutMount mountPacket = new PacketPlayOutMount();
                            mountPacket.a(data);
                            plugin.getPlugin().getServer().getScheduler().runTask(plugin.getPlugin(), () ->connection.sendPacket(mountPacket));
                            for (int i = 0; i < textLines.get(tag).size(); i++) {
                                EntityArmorStand textLine = textLines.get(tag).get(i);
                                textLine.setPositionRaw(x, y, z);
                                textLine.setCustomNameVisible(!((target instanceof Player p) && p.isSneaking()) && !target.isInvisible());
                                connection.sendPacket(new PacketPlayOutSpawnEntity(textLine));
                                connection.sendPacket(new PacketPlayOutEntityMetadata(textLine.getId(), textLine.getDataWatcher(), true));

                                EntitySnowball spliter = spliters.get(tag).get(i);
                                spliter.setPositionRaw(x, y, z);
                                connection.sendPacket(new PacketPlayOutSpawnEntity(spliter));
                                connection.sendPacket(new PacketPlayOutEntityMetadata(spliter.getId(), spliter.getDataWatcher(), true));

                                if (i == 0) {
                                    PacketDataSerializer buf = new PacketDataSerializer(Unpooled.buffer());
                                    buf.d(mainLine.getId());
                                    buf.a(new int[]{spliter.getId()});
                                    PacketPlayOutMount mount = new PacketPlayOutMount();
                                    mount.a(buf);
                                    connection.sendPacket(mount);
                                } else {
                                    PacketDataSerializer buf = new PacketDataSerializer(Unpooled.buffer());
                                    buf.d(spliters.get(tag).get(i - 1).getId());
                                    buf.a(new int[]{spliter.getId(), textLines.get(tag).get(i - 1).getId()});
                                    PacketPlayOutMount mount = new PacketPlayOutMount();
                                    mount.a(buf);
                                    connection.sendPacket(mount);
                                }
                                if (i == tag.getTexts().size()-1) {
                                    PacketDataSerializer buf = new PacketDataSerializer(Unpooled.buffer());
                                    buf.d(spliter.getId());
                                    buf.a(new int[]{textLine.getId()});
                                    PacketPlayOutMount mount = new PacketPlayOutMount();
                                    mount.a(buf);
                                    connection.sendPacket(mount);
                                }
                            }
                        }
                    }
                } else if (msg instanceof PacketPlayOutEntityDestroy packet) {
                    PacketDataSerializer dataSerializer = new PacketDataSerializer(Unpooled.buffer());
                    packet.b(dataSerializer);
                    int[] intList = new int[dataSerializer.i()];
                    for (int i = 0; i < intList.length; i++) {
                        intList[i] = dataSerializer.i();
                    }
                    for (Integer integer : intList) {
                        UUID target = tagDataContainer.getEntityUUIDFromID(integer);
                        tagDataContainer.setPlayerRender(player, target, false);
                        if (target != null) {
                            Tag tag = tagDataContainer.getTagFromEntityUUID(target);
                            if (tag != null && !tag.getEntityUUID().equals(player.getUniqueId())) {
                                int size = 1;
                                size += textLines.get(tag).size();
                                size += spliters.get(tag).size();
                                int[] ints = new int[size];
                                int i = 0;
                                ints[i++] = mainLines.get(tag).getId();
                                for (EntityArmorStand line : textLines.get(tag)) {
                                    ints[i++] = line.getId();
                                }
                                for (EntitySnowball spliter : spliters.get(tag)) {
                                    ints[i++] = spliter.getId();
                                }
                                PacketPlayOutEntityDestroy destroyPacket = new PacketPlayOutEntityDestroy(ints);
                                connection.sendPacket(destroyPacket);
                            }
                        }
                    }
                } else if (msg instanceof PacketPlayOutEntityMetadata packet) {
                    PacketDataSerializer dataSerializer = new PacketDataSerializer(Unpooled.buffer());
                    packet.b(dataSerializer);
                    int id = dataSerializer.i();
                    List<DataWatcher.Item<?>> datas = DataWatcher.a(dataSerializer);
                    UUID target = tagDataContainer.getEntityUUIDFromID(id);
                    Tag tag = tagDataContainer.getTagFromEntityUUID(target);
                    if (tag != null && !tag.getEntityUUID().equals(player.getUniqueId())) {
                        List<DataWatcher.Item<?>> items = datas;
                        if (items != null) {
                            for (DataWatcher.Item<?> item : items) {
                                if (item.a().a() == 0) {
                                    byte data = (byte) item.b();
                                    if ((data & 0x02) == 0x02 || (data & 0x20) == 0x20) {
                                        for (EntityArmorStand textLine : textLines.get(tag)) {
                                            textLine.setCustomNameVisible(false);
                                            connection.sendPacket(new PacketPlayOutEntityMetadata(textLine.getId(), textLine.getDataWatcher(), true));
                                        }
                                    } else {
                                        for (EntityArmorStand textLine : textLines.get(tag)) {
                                            textLine.setCustomNameVisible(textLine.getCustomName()!=null);
                                            connection.sendPacket(new PacketPlayOutEntityMetadata(textLine.getId(), textLine.getDataWatcher(), true));
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
                super.write(ctx, msg, promise);
            }
        });
    }

    @Override
    public void removeTag(Tag tag) {
        tagDataContainer.setEntityTag(tag.getEntityUUID(), null);
        int size = 1;
        size += textLines.get(tag).size();
        size += spliters.get(tag).size();
        int[] ints = new int[size];
        int i = 0;
        ints[i++] = mainLines.get(tag).getId();
        for (EntityArmorStand line : textLines.get(tag)) {
            ints[i++] = line.getId();
        }
        for (EntitySnowball spliter : spliters.get(tag)) {
            ints[i++] = spliter.getId();
        }
        PacketPlayOutEntityDestroy destroyPacket = new PacketPlayOutEntityDestroy(ints);
        for (Player player : plugin.getPlugin().getServer().getOnlinePlayers()) {
            if (!tagDataContainer.getRenderedEntity(player).contains(tag.getEntityUUID())) continue;
            ((CraftPlayer) player).getHandle().playerConnection.sendPacket(destroyPacket);
        }
    }

    @Override
    public void createTag(Tag tag) {
        LivingEntity target = tag.getEntity();
        Location location = target.getEyeLocation();
        tagDataContainer.setEntityTag(tag.getEntityUUID(), tag);
        EntitySilverfish mainLine = new EntitySilverfish(EntityTypes.SILVERFISH, ((CraftWorld) target.getWorld()).getHandle());
        mainLine.setPositionRaw(location.getX(), location.getY(), location.getZ()); //setRawPosition
        mainLine.setCustomNameVisible(false); //setCustomNameVisible
        mainLine.setInvulnerable(true); //setInvulnerable
        mainLine.setFlag(5, true); //setInvisible
        mainLine.setNoGravity(true); //setNoGravity
        mainLine.setSilent(true); //setSilent
        mainLines.put(tag, mainLine);
        List<EntityArmorStand> lines = new ArrayList<>();
        List<EntitySnowball> spliters = new ArrayList<>();
        List<Packet<?>> spawnEntities = new ArrayList<>();
        List<PacketPlayOutEntityMetadata> updateEntities = new ArrayList<>();
        List<PacketPlayOutMount> mountPackets = new ArrayList<>();
        for (int i = 0; i < tag.getTexts().size(); i++) {
            String text = tag.getTexts().get(i);
            EntityArmorStand line = new EntityArmorStand(EntityTypes.ARMOR_STAND, ((CraftWorld) target.getWorld()).getHandle());
            line.setPositionRaw(location.getX(), location.getY(), location.getZ());
            line.setMarker(true); //setMarker
            line.setInvulnerable(true); //setInvulnerable
            line.setFlag(5, true); //setInvisible
            line.setNoGravity(true); //setNoGravity
            if (text.equals("")) {
                line.setCustomNameVisible(false); //setCustomNameVisible
            } else {
                line.setCustomNameVisible(!((target instanceof Player p) && p.isSneaking()) && !target.isInvisible()); //setCustomNameVisible
                line.setCustomName(CraftChatMessage.fromString(text)[0]); //setCustomName
            }
            lines.add(line);
            spawnEntities.add(new PacketPlayOutSpawnEntity(line));
            updateEntities.add(new PacketPlayOutEntityMetadata(line.getId(), line.getDataWatcher(), true));

            EntitySnowball spliter = new EntitySnowball(EntityTypes.SNOWBALL, ((CraftWorld) target.getWorld()).getHandle());
            spliter.setPositionRaw(location.getX(), location.getY(), location.getZ());
            spliter.setInvulnerable(true); //setInvulnerable
            spliter.setFlag(5, true); //setInvisible
            spliter.setNoGravity(true); //setNoGravity
            spliter.setSilent(true); //setSilent
            ItemStack item = createItemStack();
            spliter.setItem(item); //setItem
            spliters.add(spliter);
            spawnEntities.add(new PacketPlayOutSpawnEntity(spliter));
            updateEntities.add(new PacketPlayOutEntityMetadata(spliter.getId(), spliter.getDataWatcher(), true));

            if (i == 0) {
                PacketDataSerializer buf = new PacketDataSerializer(Unpooled.buffer());
                buf.d(mainLine.getId());
                buf.a(new int[]{spliter.getId()});
                PacketPlayOutMount mount = new PacketPlayOutMount();
                try {
                    mount.a(buf);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                mountPackets.add(mount);
            } else {
                PacketDataSerializer buf = new PacketDataSerializer(Unpooled.buffer());
                buf.d(spliters.get(i -1).getId());
                buf.a(new int[]{spliter.getId(), lines.get(i -1).getId()});
                PacketPlayOutMount mount = new PacketPlayOutMount();
                try {
                    mount.a(buf);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                mountPackets.add(mount);
            }
            if (i == tag.getTexts().size()-1) {
                PacketDataSerializer buf = new PacketDataSerializer(Unpooled.buffer());
                buf.d(spliter.getId());
                buf.a(new int[]{line.getId()});
                PacketPlayOutMount mount = new PacketPlayOutMount();
                try {
                    mount.a(buf);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                mountPackets.add(mount);
            }
        }
        textLines.put(tag, lines);
        this.spliters.put(tag, spliters);
        PacketPlayOutSpawnEntityLiving spawnMainLine = new PacketPlayOutSpawnEntityLiving(mainLine);
        PacketPlayOutEntityMetadata updateMainLine = new PacketPlayOutEntityMetadata(mainLine.getId(), mainLine.getDataWatcher(), true);

        PacketDataSerializer data = new PacketDataSerializer(Unpooled.buffer());
        data.d(tag.getEntity().getEntityId());
        data.a(new int[]{mainLine.getId()});
        PacketPlayOutMount mountMainLine = new PacketPlayOutMount();
        try {
            mountMainLine.a(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (Player player : plugin.getPlugin().getServer().getOnlinePlayers()) {
            if (!tagDataContainer.getRenderedEntity(player).contains(tag.getEntityUUID())) continue;
            PlayerConnection connection = ((CraftPlayer) player).getHandle().playerConnection;
            connection.sendPacket(spawnMainLine);
            for (Packet<?> spawnTextLine : spawnEntities) {
                connection.sendPacket(spawnTextLine);
            }
            connection.sendPacket(updateMainLine);
            connection.sendPacket(mountMainLine);
            for (PacketPlayOutEntityMetadata updateTextLine : updateEntities) {
                connection.sendPacket(updateTextLine);
            }
            for (PacketPlayOutMount mountPacket : mountPackets) {
                connection.sendPacket(mountPacket);
            }
        }
    }

    @Override
    public void updateTag(Tag tag) {
        List<String> texts = tag.getTexts();
        LivingEntity target = tag.getEntity();
        Location location = target.getEyeLocation();
        List<EntityArmorStand> textLines = this.textLines.get(tag);
        List<EntitySnowball> spliters = this.spliters.get(tag);
        int textLength = texts.size();
        int lineLength = textLines.size();
        PacketPlayOutEntityDestroy destroyPacket;
        if (lineLength > textLength) {
            int[] ints = new int[(lineLength-textLength)*2];
            int a = 0;
            for(int i = textLength; i < lineLength; i++) {
                EntityArmorStand textLine = textLines.remove(textLength);
                EntitySnowball spliter = spliters.remove(textLength);
                ints[a++] = textLine.getId();
                ints[a++] = spliter.getId();
            }
            destroyPacket = new PacketPlayOutEntityDestroy(ints);
        } else {
            destroyPacket = null;
        }
        List<Packet<?>> spawnEntities = new ArrayList<>();
        List<PacketPlayOutEntityMetadata> updateEntities = new ArrayList<>();
        List<PacketPlayOutMount> mountPackets = new ArrayList<>();
        for(int i = 0; i < textLength; i++) {
            String text = texts.get(i);
            EntityArmorStand textLine;
            if (textLines.size() <= i) {
                textLine = new EntityArmorStand(EntityTypes.ARMOR_STAND, ((CraftWorld) target.getWorld()).getHandle()) {
                    {
                        setPositionRaw(location.getX(), location.getY(), location.getZ());
                        setMarker(true);
                        setInvulnerable(true);
                        setFlag(5, true);
                        setNoGravity(true);
                        textLines.add(this);
                        spawnEntities.add(new PacketPlayOutSpawnEntity(this));
                    }
                };
                EntitySnowball spliter = new EntitySnowball(EntityTypes.SNOWBALL, ((CraftWorld) target.getWorld()).getHandle()) {
                    {
                        setPositionRaw(location.getX(), location.getY(), location.getZ());
                        setInvulnerable(true);
                        setFlag(5, true);
                        setNoGravity(true);
                        setSilent(true);
                        ItemStack item = createItemStack();
                        setItem(item);
                        spliters.add(this);
                        spawnEntities.add(new PacketPlayOutSpawnEntity(this));
                        updateEntities.add(new PacketPlayOutEntityMetadata(this.getId(), this.getDataWatcher(), true));
                    }
                };
                if (i == 0) {
                    PacketDataSerializer buf = new PacketDataSerializer(Unpooled.buffer());
                    buf.d(mainLines.get(tag).getId());
                    buf.a(new int[]{spliter.getId()});
                    PacketPlayOutMount mount = new PacketPlayOutMount();
                    try {
                        mount.a(buf);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    mountPackets.add(mount);
                } else {
                    PacketDataSerializer buf = new PacketDataSerializer(Unpooled.buffer());
                    buf.d(spliters.get(i -1).getId());
                    buf.a(new int[]{spliter.getId(), textLines.get(i -1).getId()});
                    PacketPlayOutMount mount = new PacketPlayOutMount();
                    try {
                        mount.a(buf);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    mountPackets.add(mount);
                }
                if (i == textLength-1) {
                    PacketDataSerializer buf = new PacketDataSerializer(Unpooled.buffer());
                    buf.d(spliter.getId());
                    buf.a(new int[]{textLine.getId()});
                    PacketPlayOutMount mount = new PacketPlayOutMount();
                    try {
                        mount.a(buf);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    mountPackets.add(mount);
                }
            } else {
                textLine = textLines.get(i);
            }
            if (textLine.getCustomName() != null && text.equals(textLine.getCustomName().getString())) continue;
            if (text.equals("")) {
                textLine.setCustomNameVisible(false);
                textLine.setCustomName((IChatBaseComponent) null);
            } else {
                textLine.setCustomNameVisible(!((target instanceof Player p) && p.isSneaking()) && !target.isInvisible());
                textLine.setCustomName(CraftChatMessage.fromString(text)[0]);
            }
            updateEntities.add(new PacketPlayOutEntityMetadata(textLine.getId(), textLine.getDataWatcher(), true));
        }
        this.textLines.put(tag, textLines);
        this.spliters.put(tag, spliters);
        for (Player player : plugin.getPlugin().getServer().getOnlinePlayers()) {
            if (!tagDataContainer.getRenderedEntity(player).contains(tag.getEntityUUID())) continue;
            PlayerConnection connection = ((CraftPlayer) player).getHandle().playerConnection;
            if (destroyPacket != null) {
                connection.sendPacket(destroyPacket);
            }
            for (Packet<?> spawnTextLine : spawnEntities) {
                connection.sendPacket(spawnTextLine);
            }
            for (PacketPlayOutEntityMetadata updateTextLine : updateEntities) {
                connection.sendPacket(updateTextLine);
            }
            for (PacketPlayOutMount mountPacket : mountPackets) {
                connection.sendPacket(mountPacket);
            }
        }

    }

    private ItemStack createItemStack() {
        if (this.itemStack == null) {
            org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(plugin.getMaterial(), 1, (short) plugin.getData());
            ItemMeta meta = item.getItemMeta();
            meta.setCustomModelData(plugin.getCustomModelData());
            item.setItemMeta(meta);
            this.itemStack = CraftItemStack.asNMSCopy(item);
        }
        return this.itemStack;
    }
}
