package com.artillexstudios.axgraves.api;

import com.artillexstudios.axgraves.utils.LimitUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AxGravesAPI {

    public static com.artillexstudios.axgraves.grave.Grave createNpcGrave(org.bukkit.Location location,
            org.bukkit.OfflinePlayer owner, String name,
            io.papermc.paper.datacomponent.item.ResolvableProfile appearance,
            java.util.List<org.bukkit.inventory.ItemStack> items, org.bukkit.inventory.ItemStack[] equipment) {
        var grave = new com.artillexstudios.axgraves.grave.Grave(location, owner, items, 0,
                System.currentTimeMillis(), equipment, name, appearance);
        com.artillexstudios.axgraves.grave.SpawnedGraves.addGrave(grave);
        return grave;
    }

    public static int getGraveLimit(@NotNull Player player) {
        return LimitUtils.getGraveLimit(player);
    }
}
