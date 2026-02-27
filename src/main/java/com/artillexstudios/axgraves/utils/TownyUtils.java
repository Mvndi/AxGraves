package com.artillexstudios.axgraves.utils;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.artillexstudios.axapi.utils.logging.LogUtils;
import com.artillexstudios.axgraves.AxGraves;
import com.gmail.goosius.siegewar.SiegeWarAPI;
import com.gmail.goosius.siegewar.objects.BattleSession;
import com.palmergames.bukkit.towny.TownyAPI;

public class TownyUtils {
    public static boolean isNearTownSpawn(Player player) {
        double distanceValue = AxGraves.CONFIG.getLong("distance-to-town-spawn", 1000L);
        return getDistanceToSpawn(player, player.getLocation()) < distanceValue;
    }

    private static double getDistanceToSpawn(Player player, Location deathLocation) {
        Location spawnLocation = getTownSpawn(player);
        if (spawnLocation == null)
            return deathLocation.distance(player.getWorld().getSpawnLocation());

        double distance = deathLocation.distance(spawnLocation);

        LogUtils.debug("Distance from death location to town spawn: " + distance);
        return distance;
    }

    private static Location getTownSpawn(Player player) {
        try {
            Object resident = TownyAPI.getInstance().getResident(player);
            if (resident == null)
                return null;
            Object town = resident.getClass().getMethod("getTownOrNull").invoke(resident);
            if (town == null)
                return null;
            return (Location) town.getClass().getMethod("getSpawn").invoke(town);
        } catch (Exception e) {
            LogUtils.warn("Failed to get town spawn for player " + player.getName(), e);
            return null;
        }
    }

    public static boolean isSiegeActiveGlobal() {
        BattleSession battleSession = BattleSession.getBattleSession();
        boolean active = battleSession != null && battleSession.isActive();

        LogUtils.debug(
                "Checked SiegeWar battle session: " + (battleSession != null ? battleSession.toString() : "null")
                        + ", active: " + active);
        return active;
    }

    public static boolean isSiegeActive(Player player) {
        return SiegeWarAPI.hasSiege(player);
    }

}
