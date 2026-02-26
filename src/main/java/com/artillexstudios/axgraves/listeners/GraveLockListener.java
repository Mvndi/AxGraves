package com.artillexstudios.axgraves.listeners;

import com.artillexstudios.axgraves.utils.GraveLockUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GraveLockListener implements Listener {
    private static final long ACTION_MESSAGE_COOLDOWN_MS = 1_000L;
    private final Map<UUID, Long> lastActionMessage = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;

    public GraveLockListener(JavaPlugin plugin) {
        this.plugin = plugin;
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
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!GraveLockUtils.isLocked(player))
            return;

        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();

        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setCancelled(true);
            sendDeniedActionMessage(player, "movement");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        GraveLockUtils.applyPendingRespawnGamemode(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        lastActionMessage.remove(event.getPlayer().getUniqueId());
        GraveLockUtils.onPlayerJoin(event.getPlayer());
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
        player.sendMessage("Action denied: " + action + ". Time remaining: " + remainingSeconds + "s.");
    }
}
