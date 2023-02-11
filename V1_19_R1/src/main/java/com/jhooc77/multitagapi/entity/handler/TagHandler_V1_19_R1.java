package com.jhooc77.multitagapi.entity.handler;

import com.jhooc77.multitagapi.MultiTagAPI;
import com.jhooc77.multitagapi.tag.Tag;
import com.jhooc77.multitagapi.tag.TagDataContainer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.PacketDataSerializer;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.network.PlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.EntityArmorStand;
import net.minecraft.world.entity.monster.EntitySilverfish;
import net.minecraft.world.entity.projectile.EntitySnowball;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_19_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class TagHandler_V1_19_R1 implements TagHandler {

    private ItemStack itemStack;

    private final static Field PacketPlayOutMount_b = Objects.requireNonNull(getPacketPlayOutMount_b());

    private static Field getPacketPlayOutMount_b() {
        try {
            Field field = PacketPlayOutMount.class.getDeclaredField("b");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return null;
    }

    private final MultiTagAPI plugin;
    private final TagDataContainer tagDataContainer;

    private final WeakHashMap<Tag, EntitySilverfish> mainLines = new WeakHashMap<>();

    private final WeakHashMap<Tag, List<EntityArmorStand>> textLines = new WeakHashMap<>();
    private final WeakHashMap<Tag, List<EntitySnowball>> spliters = new WeakHashMap<>();

    public TagHandler_V1_19_R1(MultiTagAPI plugin) {
        this.plugin = plugin;
        this.tagDataContainer = plugin.getTagDataContainer();
    }

    @Override
    public void unInjectPacketListenerPlayer(Player player) {
        Channel channel = ((CraftPlayer) player).getHandle().b.b.m;
        if (channel.pipeline().get("multiTag") == null) return;
        channel.pipeline().remove("multiTag");
    }

    @Override
    public void injectPacketListenerPlayer(Player player) {
        Channel channel = ((CraftPlayer) player).getHandle().b.b.m;
        if (channel.pipeline().get("multiTag") != null) return;
        channel.pipeline().addBefore("packet_handler", "multiTag", new ChannelDuplexHandler() {

            final PlayerConnection connection = ((CraftPlayer) player).getHandle().b;
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof PacketPlayOutSpawnEntity packet) {
                    tagDataContainer.setPlayerRender(player, packet.c(), true);
                    tagDataContainer.setEntityID(packet.c(), packet.b());
                    Tag tag = tagDataContainer.getTagFromEntityUUID(packet.c());
                    if (tag != null) {
                        tag.setEntityId(packet.b());
                        LivingEntity target = tag.getEntity();
                        if (!tag.getEntityUUID().equals(player.getUniqueId())) {
                            Location location = target.getEyeLocation();
                            double x = location.getX(); double y = location.getY();double z = location.getZ();
                            EntitySilverfish mainLine = mainLines.get(tag);
                            mainLine.o(x, y, z);
                            connection.a(new PacketPlayOutSpawnEntity(mainLine));
                            connection.a(new PacketPlayOutEntityMetadata(mainLine.ae(), mainLine.ai(), true));
                            PacketDataSerializer data = new PacketDataSerializer(Unpooled.buffer());
                            data.d(packet.b());
                            data.a(new int[]{mainLine.ae()});
                            PacketPlayOutMount mountPacket = new PacketPlayOutMount(data);
                            plugin.getPlugin().getServer().getScheduler().runTask(plugin.getPlugin(), () ->connection.a(mountPacket));
                            for (int i = 0; i < textLines.get(tag).size(); i++) {
                                EntityArmorStand textLine = textLines.get(tag).get(i);
                                textLine.o(x, y, z);
                                textLine.n(!((target instanceof Player p) && p.isSneaking()) && !target.isInvisible());
                                connection.a(new PacketPlayOutSpawnEntity(textLine));
                                connection.a(new PacketPlayOutEntityMetadata(textLine.ae(), textLine.ai(), true));

                                EntitySnowball spliter = spliters.get(tag).get(i);
                                spliter.o(x, y, z);
                                connection.a(new PacketPlayOutSpawnEntity(spliter));
                                connection.a(new PacketPlayOutEntityMetadata(spliter.ae(), spliter.ai(), true));

                                if (i == 0) {
                                    PacketPlayOutMount mount = new PacketPlayOutMount(mainLine);
                                    PacketPlayOutMount_b.set(mount, new int[]{spliter.ae()});
                                    connection.a(mount);
                                } else {
                                    PacketPlayOutMount mount = new PacketPlayOutMount(spliters.get(tag).get(i - 1));
                                    PacketPlayOutMount_b.set(mount, new int[]{spliter.ae(), textLines.get(tag).get(i - 1).ae()});
                                    connection.a(mount);
                                }
                                if (i == tag.getTexts().size()-1) {
                                    PacketPlayOutMount mount = new PacketPlayOutMount(spliter);
                                    PacketPlayOutMount_b.set(mount, new int[]{textLine.ae()});
                                    connection.a(mount);
                                }
                            }
                        }
                    }
                } else if (msg instanceof PacketPlayOutEntityDestroy packet) {
                    for (Integer integer : packet.b()) {
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
                                ints[i++] = mainLines.get(tag).ae();
                                for (EntityArmorStand line : textLines.get(tag)) {
                                    ints[i++] = line.ae();
                                }
                                for (EntitySnowball spliter : spliters.get(tag)) {
                                    ints[i++] = spliter.ae();
                                }
                                PacketPlayOutEntityDestroy destroyPacket = new PacketPlayOutEntityDestroy(ints);
                                connection.a(destroyPacket);
                            }
                        }
                    }
                } else if (msg instanceof PacketPlayOutEntityMetadata packet) {
                    UUID target = tagDataContainer.getEntityUUIDFromID(packet.c());
                    Tag tag = tagDataContainer.getTagFromEntityUUID(target);
                    if (tag != null && !tag.getEntityUUID().equals(player.getUniqueId())) {
                        List<DataWatcher.Item<?>> items = packet.b();
                        if (items != null) {
                            for (DataWatcher.Item<?> item : items) {
                                if (item.a().a() == 0) {
                                    byte data = (byte) item.b();
                                    if ((data & 0x02) == 0x02 || (data & 0x20) == 0x20) {
                                        for (EntityArmorStand textLine : textLines.get(tag)) {
                                            textLine.n(false);
                                            connection.a(new PacketPlayOutEntityMetadata(textLine.ae(), textLine.ai(), true));
                                        }
                                    } else {
                                        for (EntityArmorStand textLine : textLines.get(tag)) {
                                            textLine.n(textLine.Z()!=null);
                                            connection.a(new PacketPlayOutEntityMetadata(textLine.ae(), textLine.ai(), true));
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
        ints[i++] = mainLines.get(tag).ae();
        for (EntityArmorStand line : textLines.get(tag)) {
            ints[i++] = line.ae();
        }
        for (EntitySnowball spliter : spliters.get(tag)) {
            ints[i++] = spliter.ae();
        }
        PacketPlayOutEntityDestroy destroyPacket = new PacketPlayOutEntityDestroy(ints);
        for (Player player : plugin.getPlugin().getServer().getOnlinePlayers()) {
            if (!tagDataContainer.getRenderedEntity(player).contains(tag.getEntityUUID())) continue;
            ((CraftPlayer) player).getHandle().b.a(destroyPacket);
        }
    }

    @Override
    public void createTag(Tag tag) {
        LivingEntity target = tag.getEntity();
        Location location = target.getEyeLocation();
        tagDataContainer.setEntityTag(tag.getEntityUUID(), tag);
        EntitySilverfish mainLine = new EntitySilverfish(EntityTypes.aD, ((CraftWorld) target.getWorld()).getHandle());
        mainLine.o(location.getX(), location.getY(), location.getZ()); //setRawPosition
        mainLine.n(false); //setCustomNameVisible
        mainLine.m(true); //setInvulnerable
        mainLine.b(5, true); //setInvisible
        mainLine.e(true); //setNoGravity
        mainLine.d(true); //setSilent
        mainLines.put(tag, mainLine);
        List<EntityArmorStand> lines = new ArrayList<>();
        List<EntitySnowball> spliters = new ArrayList<>();
        List<PacketPlayOutSpawnEntity> spawnEntities = new ArrayList<>();
        List<PacketPlayOutEntityMetadata> updateEntities = new ArrayList<>();
        List<PacketPlayOutMount> mountPackets = new ArrayList<>();
        for (int i = 0; i < tag.getTexts().size(); i++) {
            String text = tag.getTexts().get(i);
            EntityArmorStand line = new EntityArmorStand(EntityTypes.d, ((CraftWorld) target.getWorld()).getHandle());
            line.o(location.getX(), location.getY(), location.getZ());
            line.t(true); //setMarker
            line.m(true); //setInvulnerable
            line.b(5, true); //setInvisible
            line.e(true); //setNoGravity
            line.Q = true; //NoGravity
            if (text.equals("")) {
                line.n(false); //setCustomNameVisible
            } else {
                line.n(!((target instanceof Player p) && p.isSneaking()) && !target.isInvisible()); //setCustomNameVisible
                line.b(CraftChatMessage.fromString(text)[0]); //setCustomName
            }
            lines.add(line);
            spawnEntities.add(new PacketPlayOutSpawnEntity(line));
            updateEntities.add(new PacketPlayOutEntityMetadata(line.ae(), line.ai(), true));

            EntitySnowball spliter = new EntitySnowball(EntityTypes.aJ, ((CraftWorld) target.getWorld()).getHandle());
            spliter.o(location.getX(), location.getY(), location.getZ());
            spliter.m(true); //setInvulnerable
            spliter.b(5, true); //setInvisible
            spliter.e(true); //setNoGravity
            spliter.d(true); //setSilent
            ItemStack item = createItemStack();
            spliter.a(item); //setItem
            spliters.add(spliter);
            spawnEntities.add(new PacketPlayOutSpawnEntity(spliter));
            updateEntities.add(new PacketPlayOutEntityMetadata(spliter.ae(), spliter.ai(), true));

            if (i == 0) {
                PacketPlayOutMount mount = new PacketPlayOutMount(mainLine);
                try {
                    PacketPlayOutMount_b.set(mount, new int[]{spliter.ae()});
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                mountPackets.add(mount);
            } else {
                PacketPlayOutMount mount = new PacketPlayOutMount(spliters.get(i -1));
                try {
                    PacketPlayOutMount_b.set(mount, new int[]{spliter.ae(), lines.get(i -1).ae()});
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                mountPackets.add(mount);
            }
            if (i == tag.getTexts().size()-1) {
                PacketPlayOutMount mount = new PacketPlayOutMount(spliter);
                try {
                    PacketPlayOutMount_b.set(mount, new int[]{line.ae()});
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                mountPackets.add(mount);
            }
        }
        textLines.put(tag, lines);
        this.spliters.put(tag, spliters);
        PacketPlayOutSpawnEntity spawnMainLine = new PacketPlayOutSpawnEntity(mainLine);
        PacketPlayOutEntityMetadata updateMainLine = new PacketPlayOutEntityMetadata(mainLine.ae(), mainLine.ai(), true);

        PacketDataSerializer data = new PacketDataSerializer(Unpooled.buffer());
        data.d(tag.getEntity().getEntityId());
        data.a(new int[]{mainLine.ae()});
        PacketPlayOutMount mountMainLine = new PacketPlayOutMount(data);

        for (Player player : plugin.getPlugin().getServer().getOnlinePlayers()) {
            if (!tagDataContainer.getRenderedEntity(player).contains(tag.getEntityUUID())) continue;
            PlayerConnection connection = ((CraftPlayer) player).getHandle().b;
            connection.a(spawnMainLine);
            for (PacketPlayOutSpawnEntity spawnTextLine : spawnEntities) {
                connection.a(spawnTextLine);
            }
            connection.a(updateMainLine);
            connection.a(mountMainLine);
            for (PacketPlayOutEntityMetadata updateTextLine : updateEntities) {
                connection.a(updateTextLine);
            }
            for (PacketPlayOutMount mountPacket : mountPackets) {
                connection.a(mountPacket);
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
                ints[a++] = textLine.ae();
                ints[a++] = spliter.ae();
            }
            destroyPacket = new PacketPlayOutEntityDestroy(ints);
        } else {
            destroyPacket = null;
        }
        List<PacketPlayOutSpawnEntity> spawnEntities = new ArrayList<>();
        List<PacketPlayOutEntityMetadata> updateEntities = new ArrayList<>();
        List<PacketPlayOutMount> mountPackets = new ArrayList<>();
        for(int i = 0; i < textLength; i++) {
            String text = texts.get(i);
            EntityArmorStand textLine;
            if (textLines.size() <= i) {
                textLine = new EntityArmorStand(EntityTypes.d, ((CraftWorld) target.getWorld()).getHandle()) {
                    {
                        o(location.getX(), location.getY(), location.getZ());
                        t(true);
                        m(true);
                        b(5, true);
                        e(true);
                        Q = true;
                        textLines.add(this);
                        spawnEntities.add(new PacketPlayOutSpawnEntity(this));
                    }
                };
                EntitySnowball spliter = new EntitySnowball(EntityTypes.aJ, ((CraftWorld) target.getWorld()).getHandle()) {
                    {
                        o(location.getX(), location.getY(), location.getZ());
                        m(true);
                        b(5, true);
                        e(true);
                        d(true);
                        ItemStack item = createItemStack();
                        a(item);
                        spliters.add(this);
                        spawnEntities.add(new PacketPlayOutSpawnEntity(this));
                        updateEntities.add(new PacketPlayOutEntityMetadata(this.ae(), this.ai(), true));
                    }
                };
                if (i == 0) {
                    PacketPlayOutMount mount = new PacketPlayOutMount(mainLines.get(tag));
                    try {
                        PacketPlayOutMount_b.set(mount, new int[]{spliter.ae()});
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    mountPackets.add(mount);
                } else {
                    PacketPlayOutMount mount = new PacketPlayOutMount(spliters.get(i -1));
                    try {
                        PacketPlayOutMount_b.set(mount, new int[]{spliter.ae(), textLines.get(i -1).ae()});
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    mountPackets.add(mount);
                }
                if (i == textLength-1) {
                    PacketPlayOutMount mount = new PacketPlayOutMount(spliter);
                    try {
                        PacketPlayOutMount_b.set(mount, new int[]{textLine.ae()});
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    mountPackets.add(mount);
                }
            } else {
                textLine = textLines.get(i);
            }
            if (textLine.Z() != null && text.equals(textLine.Z().getString())) continue;
            if (text.equals("")) {
                textLine.n(false);
                textLine.b((IChatBaseComponent) null);
            } else {
                textLine.n(!((target instanceof Player p) && p.isSneaking()) && !target.isInvisible());
                textLine.b(CraftChatMessage.fromString(text)[0]);
            }
            updateEntities.add(new PacketPlayOutEntityMetadata(textLine.ae(), textLine.ai(), true));
        }
        this.textLines.put(tag, textLines);
        this.spliters.put(tag, spliters);
        for (Player player : plugin.getPlugin().getServer().getOnlinePlayers()) {
            if (!tagDataContainer.getRenderedEntity(player).contains(tag.getEntityUUID())) continue;
            PlayerConnection connection = ((CraftPlayer) player).getHandle().b;
            if (destroyPacket != null) {
                connection.a(destroyPacket);
            }
            for (PacketPlayOutSpawnEntity spawnTextLine : spawnEntities) {
                connection.a(spawnTextLine);
            }
            for (PacketPlayOutEntityMetadata updateTextLine : updateEntities) {
                connection.a(updateTextLine);
            }
            for (PacketPlayOutMount mountPacket : mountPackets) {
                connection.a(mountPacket);
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
