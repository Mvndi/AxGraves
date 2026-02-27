package com.artillexstudios.axgraves.listeners;

import com.artillexstudios.axapi.utils.logging.LogUtils;
import com.artillexstudios.axgraves.AxGraves;
import com.artillexstudios.axgraves.api.events.GravePreSpawnEvent;
import com.artillexstudios.axgraves.api.events.GraveSpawnEvent;
import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.SpawnedGraves;
import com.artillexstudios.axgraves.utils.ExperienceUtils;
import com.artillexstudios.axgraves.utils.GraveLockUtils;
import com.artillexstudios.axgraves.utils.TownyUtils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.artillexstudios.axgraves.AxGraves.CONFIG;

public class DeathListener implements Listener {
    private static List<String> disabledWorlds;
    private static List<String> blacklistedDeathCauses;
    private static boolean overrideKeepInventory;
    private static boolean overrideKeepLevel;
    private static boolean storeItems;
    private static boolean storeXP;
    private static float xpKeepPercentage;

    // Map pour stocker le message de mort original par joueur
    private static final java.util.Map<java.util.UUID, String> originalDeathMessages = new java.util.HashMap<>();

    public static void reload() {
        disabledWorlds = CONFIG.getStringList("disabled-worlds");
        blacklistedDeathCauses = CONFIG.getStringList("blacklisted-death-causes");
        overrideKeepInventory = CONFIG.getBoolean("override-keep-inventory", true);
        overrideKeepLevel = CONFIG.getBoolean("override-keep-level", true);
        storeItems = CONFIG.getBoolean("store-items", true);
        storeXP = CONFIG.getBoolean("store-xp", true);
        xpKeepPercentage = CONFIG.getFloat("xp-keep-percentage", 1f);
    }

    public DeathListener() {
        reload();

        String priority = CONFIG.getString("death-listener-priority", "MONITOR");
        EventPriority eventPriority;
        try {
            eventPriority = EventPriority.valueOf(priority);
        } catch (IllegalArgumentException ex) {
            LogUtils.error("invalid event priority: {} (defaulting to MONITOR)", priority);
            eventPriority = EventPriority.MONITOR;
        }

        EventExecutor executor = (listener, event) -> {
            if (listener instanceof DeathListener && event instanceof PlayerDeathEvent deathEvent) {
                onDeath(deathEvent);
            }
        };

        AxGraves.getInstance().getServer().getPluginManager().registerEvent(
                PlayerDeathEvent.class,
                this,
                eventPriority,
                executor,
                AxGraves.getInstance(),
                true);
    }

    public void onDeath(PlayerDeathEvent event) {
        boolean debug = AxGraves.isDebugMode();
        Player player = event.getEntity();
        Player killer = player.getKiller();

        boolean stayOnGrave = true;

        if (killer == null || !(killer instanceof Player)) {
            LogUtils.debug("[{}] killer is not a player", player.getName());
            stayOnGrave = false;
        } else if (isSiegeActive(player) && GraveLockUtils.getMoveSiegeLockMillis() <= 0) {
            LogUtils.debug("[{}] siege is active and move siege lock is disabled", player.getName());
            stayOnGrave = false;
        } else if (isNearIsTownSpawn(player) && GraveLockUtils.getMoveTownLockMillis() <= 0) {
            LogUtils.debug("[{}] near town spawn and move town lock is disabled", player.getName());
            stayOnGrave = false;
        } else if (GraveLockUtils.getMoveNormalLockMillis() <= 0) {
            LogUtils.debug("[{}] move normal lock is disabled", player.getName());
            stayOnGrave = false;
        } else {
            LogUtils.debug("[{}] killer: {}, siege active: {}, near town spawn: {}", player.getName(),
                    killer.getName(), isSiegeActive(player), isNearIsTownSpawn(player));
        }
        boolean isRealDeath = isRealDeath(player, debug);
        if (stayOnGrave) {
            if (!isRealDeath) {
                if (event.deathMessage() != null) {
                    originalDeathMessages.put(player.getUniqueId(), event.deathMessage().toString());
                }
            } else {
                event.deathMessage(null);
                String savedMessage = originalDeathMessages.remove(player.getUniqueId());
                if (savedMessage != null) {
                    Bukkit.getServer().sendMessage(net.kyori.adventure.text.Component.text(savedMessage));
                }
                return;
            }
            if (player.isInsideVehicle()) {
                player.leaveVehicle();
            }
            event.setCancelled(true);
            // Hide and protect player instead of spectator mode
            GraveLockUtils.applyGraveLockState(player);
            GraveLockUtils.showFalseDeathTitle(player);
            if (killer != null) {
                killer.sendMessage("you just killed " + player.getName());
            }
        }

        LogUtils.debug("[{}] spawning grave", player.getName());
        if (disabledWorlds.contains(player.getWorld().getName())) {
            LogUtils.debug("[{}] return: disabled world {}", player.getName(), player.getWorld().getName());
            return;
        }

        if (!player.hasPermission("axgraves.allowgraves")) {
            LogUtils.debug("[{}] return: missing permission axgraves.allowgraves", player.getName());
            return;
        }

        if (player.getLastDamageCause() != null
                && blacklistedDeathCauses.contains(player.getLastDamageCause().getCause().name())) {
            LogUtils.debug("[{}] return: blacklisted death cause {}", player.getName(),
                    player.getLastDamageCause().getCause().name());
            return;
        }

        Location location = player.getLocation();
        location.setY(findSafeY(location));

        if (stayOnGrave) {

            Boolean isSwiming = Math.floor(player.getEyeLocation().getY() - player.getLocation().getY()) < 0.5;
            LogUtils.debug("isSwiming ", player.isSwimming(), player.getEyeLocation().getY(),
                    player.getLocation().getY());
            Location playerLocation = location.clone();
            playerLocation.setX(Math.floor(playerLocation.getX()) + 0.5);
            playerLocation.setZ(Math.floor(playerLocation.getZ()) + 0.5);
            if (!CONFIG.getStringList("safe-blocks")
                    .contains(playerLocation.clone().add(0, 1, 0).getBlock().getType().name())) {
                playerLocation.setY(playerLocation.getY() - 1);
                LogUtils.debug("player has a block above");
            }
            if (isSwiming) {
                LogUtils.debug("player is low (swimming)");
                playerLocation.setY(playerLocation.getY() + 1);
            }

            Bukkit.getRegionScheduler().execute(AxGraves.getInstance(), playerLocation, () -> {
                player.teleportAsync(playerLocation);
            });
        }

        location.setY(location.clone().getY() - 1); // place the armor stand head in the block

        LogUtils.debug("[{}] location moved to {}", player.getName(), location.toString());

        final GravePreSpawnEvent gravePreSpawnEvent = new GravePreSpawnEvent(player, location);
        Bukkit.getPluginManager().callEvent(gravePreSpawnEvent);
        if (gravePreSpawnEvent.isCancelled()) {
            LogUtils.debug("[{}] return: GravePreSpawnEvent cancelled", player.getName());
            return;
        }

        LogUtils.debug("[{}] storeItems: {} - getKeepInventory: {} - overrideKeepInventory: {}", player.getName(),
                storeItems, event.getKeepInventory(), overrideKeepInventory);
        LogUtils.debug("[{}] storeXP: {} - getKeepLevel: {} - overrideKeepLevel: {}", player.getName(), storeXP,
                event.getKeepLevel(), overrideKeepLevel);

        List<ItemStack> drops = new ArrayList<>();
        if (storeItems) {
            boolean store = false;

            if (!event.getKeepInventory()) {
                store = true;
                drops = new ArrayList<>(event.getDrops());
            } else if (overrideKeepInventory) {
                store = true;
                drops = Arrays.asList(player.getInventory().getContents());
                player.getInventory().clear();
            }

            if (store) {
                event.getDrops().clear();
                if (stayOnGrave) {
                    player.getInventory().clear();
                }

            }
            LogUtils.debug("[{}] store: {} - drops size: {}", player.getName(), store, drops.size());
        }

        int xp = 0;
        if (storeXP) {
            boolean store = false;
            if (!event.getKeepLevel()) {
                store = true;
            } else if (overrideKeepLevel) {
                store = true;
                player.setLevel(0);
                player.setTotalExperience(0);
            }

            if (store) {
                xp = Math.round(ExperienceUtils.getExp(player) * xpKeepPercentage);
                event.setDroppedExp(0);
                if (stayOnGrave) {
                    player.setLevel(0);
                }

            }
            LogUtils.debug("[{}] store: {} - xp: {}", player.getName(), store, xp);
        }

        if (drops.isEmpty() && xp == 0) {
            LogUtils.debug("[{}] return: drops empty and xp is 0", player.getName());
            return;
        }
        Grave grave = new Grave(location, player, drops, xp, System.currentTimeMillis());
        SpawnedGraves.addGrave(grave);
        LogUtils.debug("[{}] created and added grave", player.getName());

        final GraveSpawnEvent graveSpawnEvent = new GraveSpawnEvent(player, grave);
        Bukkit.getPluginManager().callEvent(graveSpawnEvent);

    }

    private boolean isRealDeath(Player player, boolean debug) {
        File graveFile = new File(AxGraves.getInstance().getDataFolder(), "graved-players.yml");
        FileConfiguration gravedPlayers = YamlConfiguration.loadConfiguration(graveFile);

        String playerUuid = player.getUniqueId().toString();
        long currentTime = System.currentTimeMillis();

        if (!gravedPlayers.contains(playerUuid)) {
            gravedPlayers.set(playerUuid, currentTime);
            try {
                gravedPlayers.save(graveFile);
            } catch (IOException e) {
                LogUtils.error("Failed to save graved-players.yml", e);
            }
            return false;
        } else {
            gravedPlayers.set(playerUuid, null);
            try {
                gravedPlayers.save(graveFile);
            } catch (IOException e) {
                LogUtils.error("Failed to save graved-players.yml", e);
            }
            long deathTime = gravedPlayers.getLong(playerUuid);
            LogUtils.debug("[{}] death recorded at: {}", player.getName(), new java.util.Date(deathTime));
            return true;
        }
    }

    private double findSafeY(Location location) {
        if (location.getWorld() == null) {
            return location.getY();
        }

        double y = location.getY();
        int maxAttempts = 256;
        List<String> safeBlocks = CONFIG.getStringList("safe-blocks");
        int blockX = (int) Math.floor(location.getX());
        int blockZ = (int) Math.floor(location.getZ());
        int minHeight = location.getWorld().getMinHeight();
        int maxHeight = location.getWorld().getMaxHeight() - 1;

        boolean startInBlock = !location.getWorld().getBlockAt(blockX, (int) Math.floor(y), blockZ).isPassable();

        if (startInBlock) {
            while (y < maxHeight && maxAttempts-- > 0) {
                String blockType = location.getWorld().getBlockAt(blockX, (int) Math.floor(y), blockZ)
                        .getType().name();
                if (safeBlocks.contains(blockType)) {
                    return Math.floor(y);
                }
                y++;
            }
        } else {
            while (y > minHeight && maxAttempts-- > 0) {
                String blockType = location.getWorld().getBlockAt(blockX, (int) Math.floor(y), blockZ)
                        .getType().name();
                if (!safeBlocks.contains(blockType)) {
                    return Math.floor(y + 1);
                }
                y--;
            }

            return Math.floor(Math.max(minHeight, y));
        }

        return Math.floor(location.getY());
    }

    private boolean isSiegeActiveGlobal() {

        if (Bukkit.getServer().getPluginManager().getPlugin("SiegeWar") == null
                || !Bukkit.getServer().getPluginManager().isPluginEnabled("SiegeWar")) {
            LogUtils.info("SiegeWar plugin not found or not enabled.");
            return false;
        }
        return TownyUtils.isSiegeActiveGlobal();

    }

    private boolean isSiegeActive(Player player) {
        if (Bukkit.getServer().getPluginManager().getPlugin("SiegeWar") == null
                || !Bukkit.getServer().getPluginManager().isPluginEnabled("SiegeWar")) {
            LogUtils.info("SiegeWar plugin not found or not enabled.");
            return false;
        }
        return TownyUtils.isSiegeActive(player);
    }

    private boolean isNearIsTownSpawn(Player player) {
        if (Bukkit.getServer().getPluginManager().getPlugin("Towny") == null
                || !Bukkit.getServer().getPluginManager().isPluginEnabled("Towny")) {
            LogUtils.info("Towny plugin not found or not enabled.");
            return false;
        }
        return TownyUtils.isNearTownSpawn(player);
    }
}