package com.artillexstudios.axgraves.listeners;

import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.SpawnedGraves;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public class EntityInteractListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;
        if (!handleInteract(event.getPlayer(), interaction, event.getHand())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        if (!(event.getAttacked() instanceof Interaction interaction)) return;
        if (!handleInteract(event.getPlayer(), interaction, null)) return;
        event.setCancelled(true);
    }

    private boolean handleInteract(Player player, Interaction interaction, EquipmentSlot hand) {
        for (Grave grave : SpawnedGraves.getGraves()) {
            Interaction[] ixs = grave.getInteractions();
            if (ixs == null) continue;
            for (Interaction ix : ixs) {
                if (ix == null) continue;
                if (!ix.getUniqueId().equals(interaction.getUniqueId())) continue;
                grave.interact(player, hand);
                return true;
            }
        }
        return false;
    }
}
