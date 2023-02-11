package com.jhooc77.multitagapi.entity.handler;

import com.jhooc77.multitagapi.MultiTagAPI;
import com.jhooc77.multitagapi.tag.Tag;
import com.jhooc77.multitagapi.tag.TagDataContainer;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_18_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_18_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_18_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_18_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class TagHandler_V1_18_R1 implements TagHandler {

    private ItemStack itemStack;

    private final MultiTagAPI plugin;
    private final TagDataContainer tagDataContainer;

    private final WeakHashMap<Tag, Silverfish> mainLines = new WeakHashMap<>();

    private final WeakHashMap<Tag, List<ArmorStand>> textLines = new WeakHashMap<>();
    private final WeakHashMap<Tag, List<Snowball>> spliters = new WeakHashMap<>();

    public TagHandler_V1_18_R1(MultiTagAPI plugin) {
        this.plugin = plugin;
        this.tagDataContainer = plugin.getTagDataContainer();
    }

    @Override
    public void unInjectPacketListenerPlayer(Player player) {
        Channel channel = ((CraftPlayer) player).getHandle().connection.connection.channel;
        if (channel.pipeline().get("multiTag") == null) return;
        channel.pipeline().remove("multiTag");
    }

    @Override
    public void injectPacketListenerPlayer(Player player) {
        Channel channel = ((CraftPlayer) player).getHandle().connection.connection.channel;
        if (channel.pipeline().get("multiTag") != null) return;
        channel.pipeline().addBefore("packet_handler", "multiTag", new ChannelDuplexHandler() {

            final ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof ClientboundAddEntityPacket packet) {
                    tagDataContainer.setPlayerRender(player, packet.getUUID(), true);
                    tagDataContainer.setEntityID(packet.getUUID(), packet.getId());
                    Tag tag = tagDataContainer.getTagFromEntityUUID(packet.getUUID());
                    if (tag != null) {
                        tag.setEntityId(packet.getId());
                        LivingEntity target = tag.getEntity();
                        if (!tag.getEntityUUID().equals(player.getUniqueId())) {
                            Location location = target.getEyeLocation();
                            double x = location.getX(); double y = location.getY();double z = location.getZ();
                            Silverfish mainLine = mainLines.get(tag);
                            mainLine.setPosRaw(x, y, z);
                            connection.send(new ClientboundAddEntityPacket(mainLine));
                            connection.send(new ClientboundSetEntityDataPacket(mainLine.getId(), mainLine.getEntityData(), true));
                            FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer());
                            data.writeVarInt(packet.getId());
                            data.writeVarIntArray(new int[]{mainLine.getId()});
                            ClientboundSetPassengersPacket mountPacket = new ClientboundSetPassengersPacket(data);
                            plugin.getPlugin().getServer().getScheduler().runTask(plugin.getPlugin(), () ->connection.send(mountPacket));
                            for (int i = 0; i < textLines.get(tag).size(); i++) {
                                ArmorStand textLine = textLines.get(tag).get(i);
                                textLine.setPosRaw(x, y, z);
                                textLine.setCustomNameVisible(!((target instanceof Player p) && p.isSneaking()) && !target.isInvisible());
                                connection.send(new ClientboundAddEntityPacket(textLine));
                                connection.send(new ClientboundSetEntityDataPacket(textLine.getId(), textLine.getEntityData(), true));

                                Snowball spliter = spliters.get(tag).get(i);
                                spliter.setPosRaw(x, y, z);
                                connection.send(new ClientboundAddEntityPacket(spliter));
                                connection.send(new ClientboundSetEntityDataPacket(spliter.getId(), spliter.getEntityData(), true));

                                if (i == 0) {
                                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                                    buf.writeVarInt(mainLine.getId());
                                    buf.writeVarIntArray(new int[]{spliter.getId()});
                                    ClientboundSetPassengersPacket mount = new ClientboundSetPassengersPacket(buf);
                                    connection.send(mount);
                                } else {
                                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                                    buf.writeVarInt(spliters.get(tag).get(i - 1).getId());
                                    buf.writeVarIntArray(new int[]{spliter.getId(), textLines.get(tag).get(i - 1).getId()});
                                    ClientboundSetPassengersPacket mount = new ClientboundSetPassengersPacket(buf);
                                    connection.send(mount);
                                }
                                if (i == tag.getTexts().size()-1) {
                                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                                    buf.writeVarInt(spliter.getId());
                                    buf.writeVarIntArray(new int[]{textLine.getId()});
                                    ClientboundSetPassengersPacket mount = new ClientboundSetPassengersPacket(buf);
                                    connection.send(mount);
                                }
                            }
                        }
                    }
                } else if (msg instanceof ClientboundRemoveEntitiesPacket packet) {
                    for (Integer integer : packet.getEntityIds()) {
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
                                for (ArmorStand line : textLines.get(tag)) {
                                    ints[i++] = line.getId();
                                }
                                for (Snowball spliter : spliters.get(tag)) {
                                    ints[i++] = spliter.getId();
                                }
                                ClientboundRemoveEntitiesPacket destroyPacket = new ClientboundRemoveEntitiesPacket(ints);
                                connection.send(destroyPacket);
                            }
                        }
                    }
                } else if (msg instanceof ClientboundSetEntityDataPacket packet) {
                    UUID target = tagDataContainer.getEntityUUIDFromID(packet.getId());
                    Tag tag = tagDataContainer.getTagFromEntityUUID(target);
                    if (tag != null && !tag.getEntityUUID().equals(player.getUniqueId())) {
                        List<SynchedEntityData.DataItem<?>> items = packet.getUnpackedData();
                        if (items != null) {
                            for (SynchedEntityData.DataItem<?> item : items) {
                                if (item.getAccessor().getId() == 0) {
                                    byte data = (byte) item.getValue();
                                    if ((data & 0x02) == 0x02 || (data & 0x20) == 0x20) {
                                        for (ArmorStand textLine : textLines.get(tag)) {
                                            textLine.setCustomNameVisible(false);
                                            connection.send(new ClientboundSetEntityDataPacket(textLine.getId(), textLine.getEntityData(), true));
                                        }
                                    } else {
                                        for (ArmorStand textLine : textLines.get(tag)) {
                                            textLine.setCustomNameVisible(textLine.getCustomName()!=null);
                                            connection.send(new ClientboundSetEntityDataPacket(textLine.getId(), textLine.getEntityData(), true));
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
        for (ArmorStand line : textLines.get(tag)) {
            ints[i++] = line.getId();
        }
        for (Snowball spliter : spliters.get(tag)) {
            ints[i++] = spliter.getId();
        }
        ClientboundRemoveEntitiesPacket destroyPacket = new ClientboundRemoveEntitiesPacket(ints);
        for (Player player : plugin.getPlugin().getServer().getOnlinePlayers()) {
            if (!tagDataContainer.getRenderedEntity(player).contains(tag.getEntityUUID())) continue;
            ((CraftPlayer) player).getHandle().connection.send(destroyPacket);
        }
    }

    @Override
    public void createTag(Tag tag) {
        LivingEntity target = tag.getEntity();
        Location location = target.getEyeLocation();
        tagDataContainer.setEntityTag(tag.getEntityUUID(), tag);
        Silverfish mainLine = new Silverfish(EntityType.SILVERFISH, ((CraftWorld) target.getWorld()).getHandle());
        mainLine.setPosRaw(location.getX(), location.getY(), location.getZ()); //setRawPosition
        mainLine.setCustomNameVisible(false); //setCustomNameVisible
        mainLine.setInvulnerable(true); //setInvulnerable
        mainLine.setSharedFlag(5, true); //setInvisible
        mainLine.setNoGravity(true); //setNoGravity
        mainLine.setSilent(true); //setSilent
        mainLines.put(tag, mainLine);
        List<ArmorStand> lines = new ArrayList<>();
        List<Snowball> spliters = new ArrayList<>();
        List<ClientboundAddEntityPacket> spawnEntities = new ArrayList<>();
        List<ClientboundSetEntityDataPacket> updateEntities = new ArrayList<>();
        List<ClientboundSetPassengersPacket> mountPackets = new ArrayList<>();
        for (int i = 0; i < tag.getTexts().size(); i++) {
            String text = tag.getTexts().get(i);
            ArmorStand line = new ArmorStand(EntityType.ARMOR_STAND, ((CraftWorld) target.getWorld()).getHandle());
            line.setPosRaw(location.getX(), location.getY(), location.getZ());
            line.setMarker(true); //setMarker
            line.setInvulnerable(true); //setInvulnerable
            line.setSharedFlag(5, true); //setInvisible
            line.setNoGravity(true); //setNoGravity
            line.noPhysics = true; //NoGravity
            if (text.equals("")) {
                line.setCustomNameVisible(false); //setCustomNameVisible
            } else {
                line.setCustomNameVisible(!((target instanceof Player p) && p.isSneaking()) && !target.isInvisible()); //setCustomNameVisible
                line.setCustomName(CraftChatMessage.fromString(text)[0]); //setCustomName
            }
            lines.add(line);
            spawnEntities.add(new ClientboundAddEntityPacket(line));
            updateEntities.add(new ClientboundSetEntityDataPacket(line.getId(), line.getEntityData(), true));

            Snowball spliter = new Snowball(EntityType.SNOWBALL, ((CraftWorld) target.getWorld()).getHandle());
            spliter.setPosRaw(location.getX(), location.getY(), location.getZ());
            spliter.setInvulnerable(true); //setInvulnerable
            spliter.setSharedFlag(5, true); //setInvisible
            spliter.setNoGravity(true); //setNoGravity
            spliter.setSilent(true); //setSilent
            ItemStack item = createItemStack();
            spliter.setItem(item); //setItem
            spliters.add(spliter);
            spawnEntities.add(new ClientboundAddEntityPacket(spliter));
            updateEntities.add(new ClientboundSetEntityDataPacket(spliter.getId(), spliter.getEntityData(), true));

            if (i == 0) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeVarInt(mainLine.getId());
                buf.writeVarIntArray(new int[]{spliter.getId()});
                ClientboundSetPassengersPacket mount = new ClientboundSetPassengersPacket(buf);
                mountPackets.add(mount);
            } else {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeVarInt(spliters.get(i -1).getId());
                buf.writeVarIntArray(new int[]{spliter.getId(), lines.get(i -1).getId()});
                ClientboundSetPassengersPacket mount = new ClientboundSetPassengersPacket(buf);
                mountPackets.add(mount);
            }
            if (i == tag.getTexts().size()-1) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeVarInt(spliter.getId());
                buf.writeVarIntArray(new int[]{line.getId()});
                ClientboundSetPassengersPacket mount = new ClientboundSetPassengersPacket(buf);
                mountPackets.add(mount);
            }
        }
        textLines.put(tag, lines);
        this.spliters.put(tag, spliters);
        ClientboundAddEntityPacket spawnMainLine = new ClientboundAddEntityPacket(mainLine);
        ClientboundSetEntityDataPacket updateMainLine = new ClientboundSetEntityDataPacket(mainLine.getId(), mainLine.getEntityData(), true);

        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer());
        data.writeVarInt(tag.getEntity().getEntityId());
        data.writeVarIntArray(new int[]{mainLine.getId()});
        ClientboundSetPassengersPacket mountMainLine = new ClientboundSetPassengersPacket(data);

        for (Player player : plugin.getPlugin().getServer().getOnlinePlayers()) {
            if (!tagDataContainer.getRenderedEntity(player).contains(tag.getEntityUUID())) continue;
            ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
            connection.send(spawnMainLine);
            for (ClientboundAddEntityPacket spawnTextLine : spawnEntities) {
                connection.send(spawnTextLine);
            }
            connection.send(updateMainLine);
            connection.send(mountMainLine);
            for (ClientboundSetEntityDataPacket updateTextLine : updateEntities) {
                connection.send(updateTextLine);
            }
            for (ClientboundSetPassengersPacket mountPacket : mountPackets) {
                connection.send(mountPacket);
            }
        }
    }

    @Override
    public void updateTag(Tag tag) {
        List<String> texts = tag.getTexts();
        LivingEntity target = tag.getEntity();
        Location location = target.getEyeLocation();
        List<ArmorStand> textLines = this.textLines.get(tag);
        List<Snowball> spliters = this.spliters.get(tag);
        int textLength = texts.size();
        int lineLength = textLines.size();
        ClientboundRemoveEntitiesPacket destroyPacket;
        if (lineLength > textLength) {
            int[] ints = new int[(lineLength-textLength)*2];
            int a = 0;
            for(int i = textLength; i < lineLength; i++) {
                ArmorStand textLine = textLines.remove(textLength);
                Snowball spliter = spliters.remove(textLength);
                ints[a++] = textLine.getId();
                ints[a++] = spliter.getId();
            }
            destroyPacket = new ClientboundRemoveEntitiesPacket(ints);
        } else {
            destroyPacket = null;
        }
        List<ClientboundAddEntityPacket> spawnEntities = new ArrayList<>();
        List<ClientboundSetEntityDataPacket> updateEntities = new ArrayList<>();
        List<ClientboundSetPassengersPacket> mountPackets = new ArrayList<>();
        for(int i = 0; i < textLength; i++) {
            String text = texts.get(i);
            ArmorStand textLine;
            if (textLines.size() <= i) {
                textLine = new ArmorStand(EntityType.ARMOR_STAND, ((CraftWorld) target.getWorld()).getHandle()) {
                    {
                        setPosRaw(location.getX(), location.getY(), location.getZ());
                        setMarker(true);
                        setInvulnerable(true);
                        setSharedFlag(5, true);
                        setNoGravity(true);
                        noPhysics = true;
                        textLines.add(this);
                        spawnEntities.add(new ClientboundAddEntityPacket(this));
                    }
                };
                Snowball spliter = new Snowball(EntityType.SNOWBALL, ((CraftWorld) target.getWorld()).getHandle()) {
                    {
                        setPosRaw(location.getX(), location.getY(), location.getZ());
                        setInvulnerable(true);
                        setSharedFlag(5, true);
                        setNoGravity(true);
                        setSilent(true);
                        ItemStack item = createItemStack();
                        setItem(item);
                        spliters.add(this);
                        spawnEntities.add(new ClientboundAddEntityPacket(this));
                        updateEntities.add(new ClientboundSetEntityDataPacket(this.getId(), this.getEntityData(), true));
                    }
                };
                if (i == 0) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeVarInt(mainLines.get(tag).getId());
                    buf.writeVarIntArray(new int[]{spliter.getId()});
                    ClientboundSetPassengersPacket mount = new ClientboundSetPassengersPacket(buf);
                    mountPackets.add(mount);
                } else {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeVarInt(spliters.get(i -1).getId());
                    buf.writeVarIntArray(new int[]{spliter.getId(), textLines.get(i -1).getId()});
                    ClientboundSetPassengersPacket mount = new ClientboundSetPassengersPacket(buf);
                    mountPackets.add(mount);
                }
                if (i == textLength-1) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeVarInt(spliter.getId());
                    buf.writeVarIntArray(new int[]{textLine.getId()});
                    ClientboundSetPassengersPacket mount = new ClientboundSetPassengersPacket(buf);
                    mountPackets.add(mount);
                }
            } else {
                textLine = textLines.get(i);
            }
            if (textLine.getCustomName() != null && text.equals(textLine.getCustomName().getString())) continue;
            if (text.equals("")) {
                textLine.setCustomNameVisible(false);
                textLine.setCustomName((Component) null);
            } else {
                textLine.setCustomNameVisible(!((target instanceof Player p) && p.isSneaking()) && !target.isInvisible());
                textLine.setCustomName(CraftChatMessage.fromString(text)[0]);
            }
            updateEntities.add(new ClientboundSetEntityDataPacket(textLine.getId(), textLine.getEntityData(), true));
        }
        this.textLines.put(tag, textLines);
        this.spliters.put(tag, spliters);
        for (Player player : plugin.getPlugin().getServer().getOnlinePlayers()) {
            if (!tagDataContainer.getRenderedEntity(player).contains(tag.getEntityUUID())) continue;
            ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
            if (destroyPacket != null) {
                connection.send(destroyPacket);
            }
            for (ClientboundAddEntityPacket spawnTextLine : spawnEntities) {
                connection.send(spawnTextLine);
            }
            for (ClientboundSetEntityDataPacket updateTextLine : updateEntities) {
                connection.send(updateTextLine);
            }
            for (ClientboundSetPassengersPacket mountPacket : mountPackets) {
                connection.send(mountPacket);
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
