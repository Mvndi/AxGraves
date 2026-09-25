package com.artillexstudios.axgraves.respawn;

import com.artillexstudios.axgraves.AxGraves;
import com.artillexstudios.axgraves.api.RespawnChoiceAPI;
import com.artillexstudios.axgraves.utils.GraveLockUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RespawnChoiceMenu implements Listener {

    private static final int COMPASS_SLOT = 4;
    private static final NamespacedKey COMPASS_KEY = new NamespacedKey(AxGraves.getInstance(), "respawn_choice_compass");

    private static final Set<UUID> opening = ConcurrentHashMap.newKeySet();

    public static void giveCompassLater(Player player, long delayTicks) {
        if (RespawnChoiceAPI.getProvider() == null)
            return;

        player.getScheduler().runDelayed(AxGraves.getInstance(), task -> {
            if (!player.isOnline() || player.isDead())
                return;
            if (GraveLockUtils.getRemainingLockMillis(player) <= 0)
                return;

            giveCompass(player);
        }, null, delayTicks);
    }

    public static void giveCompass(Player player) {
        if (RespawnChoiceAPI.getProvider() == null)
            return;

        removeCompass(player);

        ItemStack compass = buildItem(Material.COMPASS, "Choose your respawn", NamedTextColor.AQUA,
                List.of("Right-click to pick where you respawn.", "Disappears when you respawn."));
        ItemMeta meta = compass.getItemMeta();
        meta.getPersistentDataContainer().set(COMPASS_KEY, PersistentDataType.BOOLEAN, true);
        compass.setItemMeta(meta);

        PlayerInventory inv = player.getInventory();
        int slot = COMPASS_SLOT;
        if (inv.getItem(slot) != null) {
            slot = inv.firstEmpty();
            if (slot == -1) {
                offer(player);
                return;
            }
        }

        inv.setItem(slot, compass);
        if (slot < 9)
            inv.setHeldItemSlot(slot);
    }

    public static void removeCompass(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (isChoiceCompass(inv.getItem(slot)))
                inv.setItem(slot, null);
        }
    }

    public static void removeCompassOnDeath(Player player, PlayerDeathEvent event) {
        event.getDrops().removeIf(RespawnChoiceMenu::isChoiceCompass);
        removeCompass(player);
    }

    public static boolean isChoiceCompass(@Nullable ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta())
            return false;

        return item.getItemMeta().getPersistentDataContainer().has(COMPASS_KEY, PersistentDataType.BOOLEAN);
    }

    public static boolean isOpening(Player player) {
        return opening.contains(player.getUniqueId());
    }

    public static void offer(Player player) {
        RespawnChoiceAPI.Provider provider = RespawnChoiceAPI.getProvider();
        if (provider == null)
            return;

        opening.add(player.getUniqueId());
        try {
            if (!provider.open(player))
                player.sendMessage(Component.text("You have nowhere else to respawn right now.", NamedTextColor.RED));
        } catch (Exception e) {
            AxGraves.getInstance().getLogger().warning("Respawn choice provider failed to open: " + e);
        } finally {
            opening.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCompassUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isChoiceCompass(event.getItem()))
            return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)
            return;

        event.setCancelled(true);
        if (GraveLockUtils.getRemainingLockMillis(event.getPlayer()) <= 0) {
            removeCompass(event.getPlayer());
            return;
        }
        offer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        removeCompass(player);

        if (GraveLockUtils.getRemainingLockMillis(player) > 0)
            giveCompassLater(player, 5L);
    }

    private static ItemStack buildItem(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream()
                .map(line -> Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                .toList());
        item.setItemMeta(meta);
        return item;
    }
}
