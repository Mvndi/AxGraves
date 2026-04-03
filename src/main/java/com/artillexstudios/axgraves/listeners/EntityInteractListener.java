package com.artillexstudios.axgraves.listeners;

import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.SpawnedGraves;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class EntityInteractListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Mannequin mannequin)) return;
        handleInteract(event.getPlayer(), mannequin, event.getHand());
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Mannequin mannequin)) return;
        handleInteract(event.getPlayer(), mannequin, event.getHand());
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof Mannequin mannequin)) return;
        handleInteract(player, mannequin, null);
        event.setCancelled(true);
    }

    private void handleInteract(Player player, Mannequin mannequin, org.bukkit.inventory.EquipmentSlot hand) {
        for (Grave grave : SpawnedGraves.getGraves()) {
            if (grave.getEntity() == null) continue;
            if (!grave.getEntity().getUniqueId().equals(mannequin.getUniqueId())) continue;

            grave.interact(player, hand);
            return;
        }
    }
}
