package com.jhooc77.multitagapi.entity.handler;

import io.netty.channel.Channel;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.network.PlayerConnection;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

public class V1_20_R1_Field {

    static final Field field;

    static {
        try {
            field = PlayerConnection.class.getDeclaredField("h");
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static Channel getChannel(Player player) {
        NetworkManager manager = null;
        try {
            manager = (NetworkManager) field.get(((CraftPlayer) player).getHandle().c);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return manager.m;
    }

}
