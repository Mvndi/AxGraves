package com.artillexstudios.axgraves.listeners;

import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.SpawnedGraves;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Set;

public class EntityInteractListener implements Listener {
    private static final Set<EntityType> ALLOWED_TYPES = Set.of(EntityType.MANNEQUIN, EntityType.INTERACTION);

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        event.setCancelled(handleInteract(event.getPlayer(), event.getRightClicked(), event.getHand()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        event.setCancelled(handleInteract(event.getPlayer(), event.getRightClicked(), event.getHand()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player))
            return;
        event.setCancelled(handleInteract(player, event.getEntity(), null));
    }

    private boolean handleInteract(Player player, Entity entity, EquipmentSlot hand) {
        if (!ALLOWED_TYPES.contains(entity.getType()))
            return false;

        for (Grave grave : SpawnedGraves.getGraves()) {
            if (grave.getInteraction() == null && grave.getMannequin() == null)
                continue;
            if (!grave.getInteraction().getUniqueId().equals(entity.getUniqueId()) && !grave.getMannequin().getUniqueId().equals(entity.getUniqueId()))
                continue;

            grave.interact(player, hand);
            return true;
        }

        return false;
    }
}
