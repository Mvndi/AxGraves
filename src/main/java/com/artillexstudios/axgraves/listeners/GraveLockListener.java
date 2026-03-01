package com.artillexstudios.axgraves.listeners;

import com.artillexstudios.axgraves.utils.GraveLockUtils;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;

import io.papermc.paper.event.player.PlayerPickItemEvent;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.artillexstudios.axgraves.AxGraves.MESSAGEUTILS;

public class GraveLockListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Si l'attaquant est un joueur et qu'il est en GraveLock, on annule l'attaque
        Entity damager = event.getDamager();
        if (damager instanceof Player player) {
            if (GraveLockUtils.isLocked(player)) {
                event.setCancelled(true);
                sendDeniedActionMessage(player, "attack");
            }
        }
    }

    private static final long ACTION_MESSAGE_COOLDOWN_MS = 1_000L;
    private final Map<UUID, Long> lastActionMessage = new ConcurrentHashMap<>();

    public GraveLockListener(JavaPlugin plugin) {
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!GraveLockUtils.isLocked(player))
            return;

        event.setCancelled(true);
        sendDeniedActionMessage(player, "item-drop");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickupXp(PlayerPickupExperienceEvent event) {
        Player player = event.getPlayer();
        if (GraveLockUtils.isLocked(player)) {
            event.setCancelled(true);
            sendDeniedActionMessage(player, "item-pickup");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickupArrow(PlayerPickupArrowEvent event) {
        Player player = event.getPlayer();
        if (GraveLockUtils.isLocked(player)) {
            event.setCancelled(true);
            sendDeniedActionMessage(player, "item-pickup");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!GraveLockUtils.isLocked(player))
            return;

        event.setCancelled(true);
        sendDeniedActionMessage(player, "command");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!GraveLockUtils.isLocked(player))
            return;

        event.setCancelled(true);
        sendDeniedActionMessage(player, "interaction");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (!GraveLockUtils.isLocked(player))
            return;

        event.setCancelled(true);
        sendDeniedActionMessage(player, "inventory-click");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player))
            return;

        if (!GraveLockUtils.isLocked(player))
            return;

        event.setCancelled(true);
        sendDeniedActionMessage(player, "inventory-open");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(org.bukkit.event.player.PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        if (!GraveLockUtils.isLocked(player))
            return;

        event.setCancelled(true);
        sendDeniedActionMessage(player, "entity-interact");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!GraveLockUtils.isLocked(player))
            return;

        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();

        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setTo(from.clone().setDirection(to.getDirection())); // Garde la direction (tête), bloque la position
            sendDeniedActionMessage(player, "movement");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMountMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!GraveLockUtils.isLocked(player))
            return;

        if (player.isInsideVehicle()) {
            org.bukkit.Location from = event.getFrom();
            org.bukkit.Location to = event.getTo();

            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                event.setTo(from.clone().setDirection(to.getDirection()));
                sendDeniedActionMessage(player, "mount-movement");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!GraveLockUtils.isLocked(player))
            return;

        Entity entity = event.getRightClicked();
        // Empêche toute interaction avec une monture
        if (entity instanceof org.bukkit.entity.Vehicle) {
            event.setCancelled(true);
            sendDeniedActionMessage(player, "mount-interact");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        lastActionMessage.remove(event.getPlayer().getUniqueId());
        GraveLockUtils.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        GraveLockUtils.onPlayerQuit(event.getPlayer());
    }

    private void sendDeniedActionMessage(Player player, String action) {
        long now = System.currentTimeMillis();
        long lastMessage = lastActionMessage.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastMessage < ACTION_MESSAGE_COOLDOWN_MS) {
            return;
        }

        lastActionMessage.put(player.getUniqueId(), now);
        long remainingMillis = GraveLockUtils.getRemainingLockMillis(player);
        long remainingSeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
        MESSAGEUTILS.sendLang(player, "grave-lock.action-denied", Map.of(
                "%action%", action,
                "%time%", String.valueOf(remainingSeconds)));
    }
}
