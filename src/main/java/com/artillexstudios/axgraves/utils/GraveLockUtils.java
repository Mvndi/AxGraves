package com.artillexstudios.axgraves.utils;

import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.utils.logging.LogUtils;
import com.artillexstudios.axgraves.AxGraves;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.artillexstudios.axgraves.AxGraves.EXECUTOR;

public final class GraveLockUtils {

    private static final String RESPAWN_STORAGE_FILE = "graved-players.yml";
    private static final long DEFAULT_MOVE_LOCK_SECONDS = 30L;
    private static final long REJOIN_PENDING_SENTINEL = -1L;
    private static final Object LOCK = new Object();
    private static final Set<UUID> pendingRespawnGamemode = ConcurrentHashMap.newKeySet();
    private static ScheduledFuture<?> cleanupTask;

    private GraveLockUtils() {
    }

    public static boolean isLocked(Player player) {
        synchronized (LOCK) {
            FileConfiguration gravedPlayers = loadStorage();
            String playerUuid = player.getUniqueId().toString();
            long now = System.currentTimeMillis();

            if (!gravedPlayers.contains(playerUuid)) {
                return false;
            }

            long storedValue = gravedPlayers.getLong(playerUuid, 0L);
            if (storedValue == 0L) {
                gravedPlayers.set(playerUuid, null);
                saveStorage(gravedPlayers);
                return false;
            }

            if (storedValue == REJOIN_PENDING_SENTINEL) {
                return true;
            }

            if (storedValue < 0L) {
                long rejoinUnlockAt = -storedValue;
                if (now < rejoinUnlockAt) {
                    return true;
                }

                realDeath(player);
                return true;
            }

            if (now - storedValue < getMoveLockMillis()) {
                return true;
            }

            realDeath(player);
            return true;
        }
    }

    public static long getRemainingLockMillis(Player player) {
        synchronized (LOCK) {
            FileConfiguration gravedPlayers = loadStorage();
            String playerUuid = player.getUniqueId().toString();
            long now = System.currentTimeMillis();

            if (!gravedPlayers.contains(playerUuid)) {
                return 0L;
            }

            long storedValue = gravedPlayers.getLong(playerUuid, 0L);
            if (storedValue == 0L) {
                return 0L;
            }

            if (storedValue == REJOIN_PENDING_SENTINEL) {
                return getMoveLockRejoinMillis();
            }

            if (storedValue < 0L) {
                return Math.max(0L, (-storedValue) - now);
            }

            long remaining = getMoveLockMillis() - (now - storedValue);
            return Math.max(0L, remaining);
        }
    }

    public static void showFalseDeathTitle(Player player) {
        long remainingSeconds = Math.max(1L, getRemainingLockMillis(player) / 1000L);
        player.showTitle(Title.title(
                Component.text("You just died"),
                Component.text("You will stand on your grave for " + remainingSeconds + " seconds"),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1000), Duration.ofMillis(200))));
    }

    public static void onPlayerJoin(Player player) {
        synchronized (LOCK) {
            FileConfiguration gravedPlayers = loadStorage();
            String playerUuid = player.getUniqueId().toString();

            if (!gravedPlayers.contains(playerUuid)) {
                return;
            }

            showFalseDeathTitle(player);

            long storedValue = gravedPlayers.getLong(playerUuid, 0L);
            if (storedValue == REJOIN_PENDING_SENTINEL) {
                long rejoinMillis = getMoveLockRejoinMillis();
                if (rejoinMillis <= 0L) {
                    realDeath(player);
                    return;
                }

                long rejoinUnlockAt = System.currentTimeMillis() + rejoinMillis;
                gravedPlayers.set(playerUuid, -rejoinUnlockAt);
                saveStorage(gravedPlayers);
                return;
            }

            if (storedValue >= 0L) {
                return;
            }

            long rejoinUnlockAt = -storedValue;
            if (System.currentTimeMillis() < rejoinUnlockAt) {
                return;
            }

            realDeath(player);
        }
    }

    public static void startLockExpiryChecker() {
        synchronized (LOCK) {
            if (cleanupTask != null)
                return;
            cleanupTask = EXECUTOR.scheduleAtFixedRate(() -> {
                try {
                    cleanupExpiredLocks();
                } catch (Exception exception) {
                    LogUtils.error("failed to cleanup expired grave locks", exception);
                }
            }, 1, 1, TimeUnit.SECONDS);
        }
    }

    public static void stopLockExpiryChecker() {
        synchronized (LOCK) {
            if (cleanupTask == null)
                return;
            cleanupTask.cancel(true);
            cleanupTask = null;
        }
    }

    public static void applyPendingRespawnGamemode(Player player) {
        UUID uuid = player.getUniqueId();
        if (!pendingRespawnGamemode.remove(uuid)) {
            return;
        }

        Scheduler.get().runAt(player.getLocation(), task -> {
            if (!player.isOnline()) {
                return;
            }
            player.setGameMode(getReturnGamemode());
        });
    }

    private static void cleanupExpiredLocks() {
        synchronized (LOCK) {
            FileConfiguration gravedPlayers = loadStorage();
            long now = System.currentTimeMillis();
            long lockMillis = getMoveLockMillis();
            boolean changed = false;

            for (String uuid : gravedPlayers.getKeys(false)) {
                long storedValue = gravedPlayers.getLong(uuid, 0L);
                if (storedValue == 0L) {
                    gravedPlayers.set(uuid, null);
                    changed = true;
                    continue;
                }

                if (storedValue == REJOIN_PENDING_SENTINEL) {
                    Player player = getOnlinePlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        continue;
                    }

                    long rejoinMillis = getMoveLockRejoinMillis();
                    if (rejoinMillis <= 0L) {
                        realDeath(player);
                        continue;
                    }

                    long rejoinUnlockAt = now + rejoinMillis;
                    gravedPlayers.set(uuid, -rejoinUnlockAt);
                    changed = true;
                    continue;
                }

                if (storedValue < 0L) {
                    long rejoinUnlockAt = -storedValue;
                    if (now < rejoinUnlockAt) {
                        continue;
                    }

                    Player player = getOnlinePlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        continue;
                    }

                    realDeath(player);
                    continue;
                }

                if (now - storedValue < lockMillis) {
                    continue;
                }

                Player player = getOnlinePlayer(uuid);
                if (player == null || !player.isOnline()) {
                    gravedPlayers.set(uuid, REJOIN_PENDING_SENTINEL);
                    changed = true;
                    continue;
                }

                realDeath(player);
            }

            if (changed) {
                saveStorage(gravedPlayers);
            }
        }
    }

    private static Player getOnlinePlayer(String uuidString) {
        try {
            UUID uuid = UUID.fromString(uuidString);
            return Bukkit.getPlayer(uuid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void realDeath(Player player) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }

        Scheduler.get().runAt(player.getLocation(), task -> {
            if (!player.isOnline() || player.isDead()) {
                return;
            }

            pendingRespawnGamemode.add(player.getUniqueId());
            player.setGameMode(getReturnGamemode());

            player.setHealth(0.0D);
        });
    }

    private static GameMode getReturnGamemode() {
        String gamemode = AxGraves.CONFIG.getString("respawn-return-gamemode", "SURVIVAL");
        try {
            return GameMode.valueOf(gamemode.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            LogUtils.warn("Invalid gamemode in config: {}", gamemode);
            return GameMode.SURVIVAL;
        }
    }

    private static FileConfiguration loadStorage() {
        return YamlConfiguration.loadConfiguration(
                new File(AxGraves.getInstance().getDataFolder(), RESPAWN_STORAGE_FILE));
    }

    private static void saveStorage(FileConfiguration config) {
        File file = new File(AxGraves.getInstance().getDataFolder(), RESPAWN_STORAGE_FILE);
        try {
            config.save(file);
        } catch (IOException exception) {
            LogUtils.error("Failed to save {}", RESPAWN_STORAGE_FILE, exception);
        }
    }

    private static long getMoveLockMillis() {
        long seconds = AxGraves.CONFIG.getLong("respawn-lock-seconds", DEFAULT_MOVE_LOCK_SECONDS);
        return Math.max(0L, seconds) * 1000L;
    }

    private static long getMoveLockRejoinMillis() {
        long seconds = AxGraves.CONFIG.getLong("respawn-rejoin-lock-seconds", DEFAULT_MOVE_LOCK_SECONDS);
        return Math.max(0L, seconds) * 1000L;
    }
}
