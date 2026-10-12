package com.artillexstudios.axgraves.listeners;

import com.artillexstudios.axgraves.utils.GraveLockUtils;
import net.mvndicraft.mvndiequipment.event.throwingweapon.PlayerThrowWeaponEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ThrowingWeaponLockListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onThrow(PlayerThrowWeaponEvent event) {
        if (GraveLockUtils.isLocked(event.player)) {
            event.cancelled = true;
        }
    }
}
