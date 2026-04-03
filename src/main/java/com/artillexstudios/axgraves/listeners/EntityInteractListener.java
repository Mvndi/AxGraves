package com.artillexstudios.axgraves.listeners;

import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.SpawnedGraves;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

public class EntityInteractListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Mannequin mannequin)) return;

        Player player = event.getPlayer();

        for (Grave grave : SpawnedGraves.getGraves()) {
            if (grave.getEntity() == null) continue;
            if (!grave.getEntity().getUniqueId().equals(mannequin.getUniqueId())) continue;

            event.setCancelled(true);
            grave.interact(player, event.getHand());
            return;
        }
    }
}
