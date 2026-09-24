package com.artillexstudios.axgraves.api;

import org.bukkit.entity.Player;

public final class RespawnChoiceAPI {

    private static volatile Provider provider;

    private RespawnChoiceAPI() {
    }

    public interface Provider {
        boolean open(Player player);
    }

    public static void setProvider(Provider provider) {
        RespawnChoiceAPI.provider = provider;
    }

    public static Provider getProvider() {
        return provider;
    }
}
