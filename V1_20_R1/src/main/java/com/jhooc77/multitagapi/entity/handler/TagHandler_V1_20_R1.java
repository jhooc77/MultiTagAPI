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
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;

public class TagHandler_V1_20_R1 implements TagHandler {

    private ItemStack itemStack;

    private final MultiTagAPI plugin;
    private final TagDataContainer tagDataContainer;

    private final WeakHashMap<Tag, Display.TextDisplay> mainLines = new WeakHashMap<>();

    public TagHandler_V1_20_R1(MultiTagAPI plugin) {
        this.plugin = plugin;
        this.tagDataContainer = plugin.getTagDataContainer();
    }

    @Override
    public void unInjectPacketListenerPlayer(Player player) {
        Channel channel = V1_20_R1_Field.getChannel(player);
        if (channel.pipeline().get("multiTag") == null) return;
        channel.pipeline().remove("multiTag");
    }

    @Override
    public void injectPacketListenerPlayer(Player player) {
        Channel channel = V1_20_R1_Field.getChannel(player);
        if (channel.pipeline().get("multiTag") != null) return;
        channel.pipeline().addBefore("packet_handler", "multiTag", new ChannelDuplexHandler() {

            final ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof ClientboundBundlePacket packet) {
                    for (Packet<ClientGamePacketListener> p : packet.subPackets()) {
                        process(p, player, connection);
                    }
                } else {
                    process(msg, player, connection);
                }
                super.write(ctx, msg, promise);
            }
        });
    }

    private void process(Object msg, Player player, ServerGamePacketListenerImpl connection) {
        if (msg instanceof ClientboundAddEntityPacket packet) {
            tagDataContainer.setPlayerRender(player, packet.getUUID(), true);
            tagDataContainer.setEntityID(packet.getUUID(), packet.getId());
            Tag tag = tagDataContainer.getTagFromEntityUUID(packet.getUUID());
            if (tag != null) {
                tag.setEntityId(packet.getId());
                LivingEntity target = tag.getEntity();
                if (!tag.getEntityUUID().equals(player.getUniqueId())) {
                    Location location = target.getEyeLocation();
                    double x = location.getX();
                    double y = location.getY();
                    double z = location.getZ();
                    Display.TextDisplay mainLine = mainLines.get(tag);
                    mainLine.setPosRaw(x, y, z);
                    mainLine.setInvisible(!((target instanceof Player p) && p.isSneaking()) && !target.isInvisible());
                    connection.send(new ClientboundAddEntityPacket(mainLine));
                    connection.send(new ClientboundSetEntityDataPacket(mainLine.getId(), mainLine.getEntityData().getNonDefaultValues()));
                    FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer());
                    data.writeVarInt(packet.getId());
                    data.writeVarIntArray(new int[]{mainLine.getId()});
                    ClientboundSetPassengersPacket mountPacket = new ClientboundSetPassengersPacket(data);
                    connection.send(mountPacket);
                }
            }
        } else if (msg instanceof ClientboundAddEntityPacket packet) {
            if (!plugin.isOnlyPlayer()) {
                tagDataContainer.setPlayerRender(player, packet.getUUID(), true);
                tagDataContainer.setEntityID(packet.getUUID(), packet.getId());
                Tag tag = tagDataContainer.getTagFromEntityUUID(packet.getUUID());
                if (tag != null) {
                    tag.setEntityId(packet.getId());
                    LivingEntity target = tag.getEntity();
                    if (!tag.getEntityUUID().equals(player.getUniqueId())) {
                        Location location = target.getEyeLocation();
                        double x = location.getX();
                        double y = location.getY();
                        double z = location.getZ();
                        Display.TextDisplay mainLine = mainLines.get(tag);
                        mainLine.setPosRaw(x, y, z);
                        connection.send(new ClientboundAddEntityPacket(mainLine));
                        connection.send(new ClientboundSetEntityDataPacket(mainLine.getId(), mainLine.getEntityData().getNonDefaultValues()));
                        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer());
                        data.writeVarInt(packet.getId());
                        data.writeVarIntArray(new int[]{mainLine.getId()});
                        ClientboundSetPassengersPacket mountPacket = new ClientboundSetPassengersPacket(data);
                        connection.send(mountPacket);
                        mainLine.setInvisible(!((target instanceof Player p) && p.isSneaking()) && !target.isInvisible());
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
                        int[] ints = new int[size];
                        int i = 0;
                        ints[i++] = mainLines.get(tag).getId();
                        ClientboundRemoveEntitiesPacket destroyPacket = new ClientboundRemoveEntitiesPacket(ints);
                        connection.send(destroyPacket);
                    }
                }
            }
        } else if (msg instanceof ClientboundSetEntityDataPacket packet) {
            UUID target = tagDataContainer.getEntityUUIDFromID(packet.id());
            Tag tag = tagDataContainer.getTagFromEntityUUID(target);
            if (tag != null && !tag.getEntityUUID().equals(player.getUniqueId())) {
                List<SynchedEntityData.DataValue<?>> items = packet.packedItems();
                if (items != null) {
                    for (var item : items) {
                        if (item.id() == 0) {
                            byte data = (byte) item.value();
                            Display.TextDisplay mainLine = mainLines.get(tag);
                            if ((data & 0x02) == 0x02 || (data & 0x20) == 0x20) {
                                mainLine.setInvisible(true);
                                ClientboundSetEntityDataPacket updateMainLine = new ClientboundSetEntityDataPacket(mainLine.getId(), mainLine.getEntityData().getNonDefaultValues());
                                connection.send(updateMainLine);
                            } else {
                                mainLine.setInvisible(false);
                                ClientboundSetEntityDataPacket updateMainLine = new ClientboundSetEntityDataPacket(mainLine.getId(), mainLine.getEntityData().getNonDefaultValues());
                                connection.send(updateMainLine);
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void removeTag(Tag tag) {
        tagDataContainer.setEntityTag(tag.getEntityUUID(), null);
        int size = 1;
        int[] ints = new int[size];
        int i = 0;
        ints[i++] = mainLines.get(tag).getId();
        ClientboundRemoveEntitiesPacket destroyPacket = new ClientboundRemoveEntitiesPacket(ints);
        for (Player player : plugin.getPlugin().getServer().getOnlinePlayers()) {
            if (!tagDataContainer.getRenderedEntity(player).contains(tag.getEntityUUID())) continue;
            ((CraftPlayer) player).getHandle().connection.send(destroyPacket);
        }
    }

    private void setFlag(Display.TextDisplay handle, int flag, boolean set) {
        byte flagBits = handle.getFlags();
        if (set) {
            flagBits = (byte)(flagBits | flag);
        } else {
            flagBits = (byte)(flagBits & ~flag);
        }

        handle.setFlags(flagBits);
    }

    @Override
    public void createTag(Tag tag) {
        LivingEntity target = tag.getEntity();
        Location location = target.getEyeLocation();
        tagDataContainer.setEntityTag(tag.getEntityUUID(), tag);
        Display.TextDisplay mainLine = new Display.TextDisplay(EntityType.TEXT_DISPLAY, ((CraftWorld) target.getWorld()).getHandle());
        mainLine.setPosRaw(location.getX(), location.getY(), location.getZ()); //setRawPosition
        mainLine.setCustomNameVisible(true); //setCustomNameVisible
        mainLine.setInvulnerable(true); //setInvulnerable
        mainLine.setNoGravity(true); //setNoGravity
        mainLine.setSilent(true); //setSilent
        mainLine.setInvisible(false); //setInvisible
        mainLine.setBackgroundColor(0);
        mainLine.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        setFlag(mainLine, 4, false);
        mainLines.put(tag, mainLine);

        ClientboundAddEntityPacket spawnMainLine = new ClientboundAddEntityPacket(mainLine);
        ClientboundSetEntityDataPacket updateMainLine = new ClientboundSetEntityDataPacket(mainLine.getId(), mainLine.getEntityData().getNonDefaultValues());

        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer());
        data.writeVarInt(tag.getEntity().getEntityId());
        data.writeVarIntArray(new int[]{mainLine.getId()});
        ClientboundSetPassengersPacket mountMainLine = new ClientboundSetPassengersPacket(data);

        for (Player player : plugin.getPlugin().getServer().getOnlinePlayers()) {
            if (!tagDataContainer.getRenderedEntity(player).contains(tag.getEntityUUID())) continue;
            ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
            connection.send(spawnMainLine);
            connection.send(updateMainLine);
            connection.send(mountMainLine);
        }
    }

    @Override
    public void updateTag(Tag tag) {
        List<String> texts = tag.getTexts();

        Display.TextDisplay mainLine = mainLines.get(tag);
        StringBuilder sb = new StringBuilder();
        for (int i = texts.size(); i > 0; i--) {
            sb.append(texts.get(i-1)).append("\n");
        }
        mainLine.setText(CraftChatMessage.fromString(sb.toString(), true)[0]);
        ClientboundSetEntityDataPacket updateMainLine = new ClientboundSetEntityDataPacket(mainLine.getId(), mainLine.getEntityData().getNonDefaultValues());

        for (Player player : plugin.getPlugin().getServer().getOnlinePlayers()) {
            if (!tagDataContainer.getRenderedEntity(player).contains(tag.getEntityUUID())) continue;
            ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
            connection.send(updateMainLine);
        }


    }
}
