package com.artillexstudios.axgraves.listeners;

import com.artillexstudios.axgraves.AxGraves;
import com.artillexstudios.axgraves.api.AxGravesAPI;

import net.mvndicraft.llmnpcs.events.NpcDeathEvent;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class LlmNpcDeathListener implements Listener {
    private static final String FAILURE = "NPC corpse creation failed: ";

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(NpcDeathEvent event) {
        if (event.areDropsHandled()) return;
        NpcDeathEvent.Death death = event.getDeath();
        try {
            AxGravesAPI.createNpcGrave(
                    death.location(),
                    Bukkit.getOfflinePlayer(death.owner()),
                    death.name(),
                    death.appearance(),
                    death.drops(),
                    death.equipment());
            event.setDropsHandled(true);
        } catch (RuntimeException | LinkageError failure) {
            AxGraves.getInstance()
                    .getLogger()
                    .warning(FAILURE + failure.getClass().getSimpleName());
        }
    }
}
