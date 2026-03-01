package com.artillexstudios.axgraves.utils;

import static com.artillexstudios.axgraves.AxGraves.EXECUTOR;
import static com.artillexstudios.axgraves.AxGraves.LANG;
import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axapi.utils.logging.LogUtils;
import com.artillexstudios.axgraves.AxGraves;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public final class GraveLockUtils {
    private static NamespacedKey gravedKey = new NamespacedKey(AxGraves.getInstance(), "graved");
    private static NamespacedKey logoutKey = new NamespacedKey(AxGraves.getInstance(), "logged-out");

    // Track vanished/invisible players
    private static final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();

    public static void applyGraveLockState(Player player) {
        // Hide from all other players (Folia safe)
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                Bukkit.getRegionScheduler().execute(AxGraves.getInstance(), other.getLocation(), () -> {
                    other.hidePlayer(AxGraves.getInstance(), player);
                });
            }
        }
        Bukkit.getRegionScheduler().execute(AxGraves.getInstance(), player.getLocation(), () -> {
            player.setInvulnerable(true);
            player.setInvisible(true);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setCanPickupItems(false);
            vanishedPlayers.add(player.getUniqueId());
        });
    }

    public static void removeGraveLockState(Player player) {
        unsetGravedPlayer(player);
        unsetGravedLogoutPlayer(player);

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                Bukkit.getRegionScheduler().execute(AxGraves.getInstance(), other.getLocation(), () -> {
                    other.showPlayer(AxGraves.getInstance(), player);
                });
            }
        }
        Bukkit.getRegionScheduler().execute(AxGraves.getInstance(), player.getLocation(), () -> {
            player.setInvulnerable(false);
            player.setInvisible(false);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setCanPickupItems(true);
            vanishedPlayers.remove(player.getUniqueId());
        });
    }

    private static final long DEFAULT_MOVE_LOCK_SECONDS = 30L;
    private static final long REJOIN_PENDING_SENTINEL = -1L;
    private static ScheduledFuture<?> cleanupTask;

    private GraveLockUtils() {}

    public static boolean isLocked(Player player) {
        long now = System.currentTimeMillis();

        if (!isGravedPlayer(player)) {
            return false;
        }

        long storedValue = getGravedPlayer(player);
        if (storedValue == 0L) {
            unsetGravedPlayer(player);
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

        if (now - storedValue < getRespawnLockSeconds(player)) {
            return true;

        }

        realDeath(player);
        return true;
    }

    public static long getRemainingLockMillis(Player player) {
        if (!isGravedPlayer(player)) {
            return 0L;
        }

        long now = System.currentTimeMillis();
        long storedValue = getGravedPlayer(player);
        if (storedValue == 0L) {
            return 0L;
        }

        if (storedValue == REJOIN_PENDING_SENTINEL) {
            return getMoveTownLockMillis();
        }

        if (storedValue < 0L) {
            return Math.max(0L, (-storedValue) - now);
        }

        long remaining = getRespawnLockSeconds(player) - (now - storedValue);

        return Math.max(0L, remaining);
    }

    public static void showFalseDeathTitle(Player player) {
        long totalMillis = getRemainingLockMillis(player);
        if (totalMillis <= 1000L) {
            return;
        }
        long interval = 1000L;
        long fadeIn = 0L;
        long fadeOut = 0L;
        long stay = interval + 500L;
        int updates = (int) Math.ceil(totalMillis / (double) interval);
        for (int i = 0; i < updates; i++) {
            final int index = i;
            long delayTicks = Math.max(1, (index * interval) / 50); // Folia: delay ticks must be >= 1
            String sound;

            String lastSoundName = AxGraves.CONFIG.getString("grave-lock-sounds.last", "block.lever.click");
            String intervalSoundName = AxGraves.CONFIG.getString("grave-lock-sounds.interval", "block.lever.click");
            String tickSoundName = AxGraves.CONFIG.getString("grave-lock-sounds.tick", "block.lever.click");

            if (index == updates - 1) {
                sound = lastSoundName;
            } else if (index % 5 == 0) {
                sound = intervalSoundName;
            } else {
                sound = tickSoundName;
            }
            Bukkit.getRegionScheduler().runDelayed(AxGraves.getInstance(), player.getLocation(), (ignored) -> {
                long remainingMillis = getRemainingLockMillis(player);
                long remainingSeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
                String subtitle = StringUtils.formatToString(LANG
                        .getString("grave-lock.false-death-subtitle", "You will stand on your grave for %time% seconds")
                        .replace("%time%", String.valueOf(remainingSeconds)));
                player.showTitle(Title.title(
                        Component.text(StringUtils
                                .formatToString(LANG.getString("grave-lock.false-death-title", "You just died"))),
                        Component.text(subtitle), Title.Times.times(Duration.ofMillis(fadeIn), Duration.ofMillis(stay),
                                Duration.ofMillis(fadeOut))));
                // Send chat message as well
                if (index == 0) {

                    String chatMessage = StringUtils.formatToString(LANG
                            .getString("grave-lock.false-death-chat", "You must wait %time% seconds before respawning.")
                            .replace("%time%", String.valueOf(remainingSeconds)));

                    player.sendMessage(Component.text(chatMessage));
                }
                player.getWorld().playSound(player, sound, 1.0f, 1.0f);
            }, delayTicks);
        }
    }

    public static void onPlayerJoin(Player player) {
        if (getRemainingLockMillis(player) <= 0) {
            removeGraveLockState(player);
        } else if (isGravedLogoutPlayer(player)) {
            unsetGravedLogoutPlayer(player);

            applyGraveLockState(player);
            showFalseDeathTitle(player);
        }
    }

    public static void onPlayerQuit(Player player) {
        LogUtils.info("Player {} is logging out, checking for grave lock", player.getName());

        if (!isGravedPlayer(player)) {
            LogUtils.info("Player {} does not have an active grave lock, no action needed", player.getName());
            return;
        }

        setGravedLogoutPlayer(player);

        player.setHealth(0.0D);
    }

    public static void startLockExpiryChecker() {
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

    public static void stopLockExpiryChecker() {
        if (cleanupTask == null)
            return;
        cleanupTask.cancel(true);
        cleanupTask = null;
    }

    private static void cleanupExpiredLocks() {
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isGravedPlayer(player)) {
                continue;
            }

            long storedValue = getGravedPlayer(player);
            if (storedValue == 0L) {
                unsetGravedPlayer(player);
                continue;
            }

            if (storedValue == REJOIN_PENDING_SENTINEL) {

                if (player == null || !player.isOnline()) {
                    continue;
                }

                showFalseDeathTitle(player);
                long rejoinMillis = getMoveTownLockMillis();
                if (rejoinMillis <= 0L) {
                    realDeath(player);
                    continue;
                }

                long rejoinUnlockAt = now + rejoinMillis;
                setGravedPlayer(player, -rejoinUnlockAt);
                continue;
            }

            if (storedValue < 0L) {
                long rejoinUnlockAt = -storedValue;
                if (now < rejoinUnlockAt) {
                    continue;
                }

                if (player == null || !player.isOnline()) {
                    continue;
                }

                realDeath(player);
                continue;
            }

            if (player == null || !player.isOnline()) {
                continue;
            }

            if (now - storedValue < getRespawnLockSeconds(player)) {
                continue;
            }

            if (player == null || !player.isOnline()) {
                setGravedPlayer(player, REJOIN_PENDING_SENTINEL);
                continue;
            }

            realDeath(player);
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
            removeGraveLockState(player);

            if (!isGravedLogoutPlayer(player)) {
                player.setHealth(0.0D);
            } else {
                unsetGravedPlayer(player);
                unsetGravedLogoutPlayer(player);
            }
        });
        removeGraveLockState(player);
    }


    public static void setGravedPlayer(Player player, long value) {
        player.getPersistentDataContainer().set(gravedKey, PersistentDataType.LONG, value);
    }
    public static void setGravedPlayer(Player player) {
        setGravedPlayer(player, System.currentTimeMillis());
    }
    public static long getGravedPlayer(Player player) {
        return player.getPersistentDataContainer().getOrDefault(gravedKey, PersistentDataType.LONG, 0L);
    }
    public static void unsetGravedPlayer(Player player) {
        player.getPersistentDataContainer().remove(gravedKey);
    }
    public static boolean isGravedPlayer(Player player) {
        return player.getPersistentDataContainer().has(gravedKey);
    }

    public static void setGravedLogoutPlayer(Player player) {
        player.getPersistentDataContainer().set(logoutKey, PersistentDataType.BOOLEAN, true);
    }
    public static void unsetGravedLogoutPlayer(Player player) {
        player.getPersistentDataContainer().remove(logoutKey);
    }
    public static boolean isGravedLogoutPlayer(Player player) {
        return player.getPersistentDataContainer().has(logoutKey);
    }


    public static long getRespawnLockSeconds(Player player) {
        if (isSiegeActive(player)) {
            return getMoveSiegeLockMillis();
        } else if (isNearIsTownSpawn(player)) {
            return getMoveTownLockMillis();
        } else {
            return getMoveNormalLockMillis();
        }
    }

    public static long getMoveNormalLockMillis() {
        long seconds = AxGraves.CONFIG.getLong("normal-respawn-lock-seconds", DEFAULT_MOVE_LOCK_SECONDS);

        return Math.max(0L, seconds) * 1000L;
    }

    public static long getMoveSiegeLockMillis() {
        long seconds = AxGraves.CONFIG.getLong("siege-respawn-lock-seconds", DEFAULT_MOVE_LOCK_SECONDS);

        return Math.max(0L, seconds) * 1000L;
    }

    public static long getMoveTownLockMillis() {
        long seconds = AxGraves.CONFIG.getLong("town-respawn-lock-seconds", DEFAULT_MOVE_LOCK_SECONDS);
        return Math.max(0L, seconds) * 1000L;
    }

    private static boolean isNearIsTownSpawn(Player player) {
        if (Bukkit.getServer().getPluginManager().getPlugin("Towny") == null
                || !Bukkit.getServer().getPluginManager().isPluginEnabled("Towny")) {
            LogUtils.info("Towny plugin not found or not enabled.");
            return false;
        }

        return TownyUtils.isNearTownSpawn(player);
    }

    private static boolean isSiegeActive(Player player) {
        if (Bukkit.getServer().getPluginManager().getPlugin("SiegeWar") == null
                || !Bukkit.getServer().getPluginManager().isPluginEnabled("SiegeWar")) {
            LogUtils.info("SiegeWar plugin not found or not enabled.");
            return false;
        }

        return TownyUtils.isSiegeActive(player);
    }
}
