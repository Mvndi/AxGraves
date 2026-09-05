package com.artillexstudios.axgraves.api;

import com.artillexstudios.axgraves.AxGraves;
import com.artillexstudios.axgraves.utils.GraveLockUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public final class RespawnChoiceAPI {
    private static final NamespacedKey RESPAWN_AT_SHIP_KEY = new NamespacedKey(AxGraves.getInstance(), "respawn_at_spawn_ship");

    private RespawnChoiceAPI() {
    }

    public static void setWantsShipRespawn(Player player, boolean wantsShipRespawn) {
        if (wantsShipRespawn) {
            player.getPersistentDataContainer().set(RESPAWN_AT_SHIP_KEY, PersistentDataType.BOOLEAN, true);
        } else {
            player.getPersistentDataContainer().remove(RESPAWN_AT_SHIP_KEY);
        }
    }

    public static boolean consumeWantsShipRespawn(Player player) {
        if (GraveLockUtils.isGravedPlayer(player))
            return false;

        boolean wants = player.getPersistentDataContainer().getOrDefault(RESPAWN_AT_SHIP_KEY, PersistentDataType.BOOLEAN, false);
        player.getPersistentDataContainer().remove(RESPAWN_AT_SHIP_KEY);
        return wants;
    }
}
