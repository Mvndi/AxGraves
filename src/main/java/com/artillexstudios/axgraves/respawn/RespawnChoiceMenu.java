package com.artillexstudios.axgraves.respawn;

import com.artillexstudios.axgraves.AxGraves;
import com.artillexstudios.axgraves.api.RespawnChoiceAPI;
import com.artillexstudios.axgraves.utils.GraveLockUtils;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.mvndicraft.mvndiships.MvndiShips;
import net.mvndicraft.mvndiships.ship.Ship;
import net.mvndicraft.mvndiships.util.SiegeWarUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RespawnChoiceMenu implements Listener {
    private static final int NORMAL_SLOT = 2;
    private static final int SHIP_SLOT = 6;

    private static final int COMPASS_SLOT = 4;
    private static final NamespacedKey COMPASS_KEY = new NamespacedKey(AxGraves.getInstance(), "respawn_choice_compass");

    public static void giveCompassLater(Player player, long delayTicks) {
        player.getScheduler().runDelayed(AxGraves.getInstance(), task -> {
            if (!player.isOnline() || player.isDead())
                return;
            if (GraveLockUtils.getRemainingLockMillis(player) <= 0)
                return;

            giveCompass(player);
        }, null, delayTicks);
    }

    public static void giveCompass(Player player) {
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
            if (slot == -1)
                return;
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

    public static void offer(Player player) {
        RespawnChoiceHolder holder = new RespawnChoiceHolder();
        Inventory inv = Bukkit.createInventory(holder, 9, Component.text("Choose your respawn", NamedTextColor.DARK_RED));
        holder.setInventory(inv);

        inv.setItem(NORMAL_SLOT, buildItem(Material.RED_BED, "Respawn Normally", NamedTextColor.GREEN,
                List.of("Respawn at your town or siege spawn, as usual.")));

        Ship spawnShip = getEligibleSpawnShip(player);
        String denial = spawnShip == null ? "Your nation needs a docked spawn ship." : getSpawnShipDenial(player);
        if (denial == null) {
            String shipName = spawnShip.getName().isEmpty() ? "your spawn ship" : spawnShip.getName();
            inv.setItem(SHIP_SLOT, buildItem(Material.OAK_BOAT, "Respawn at Spawn Ship", NamedTextColor.AQUA,
                    List.of("Respawn aboard " + shipName + " instead.", "You still wait out the timer.")));
        } else {
            inv.setItem(SHIP_SLOT, buildItem(Material.BARRIER, "Respawn at Spawn Ship", NamedTextColor.RED,
                    List.of("Unavailable right now.", denial)));
        }

        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RespawnChoiceHolder))
            return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player))
            return;

        int slot = event.getRawSlot();
        if (slot == SHIP_SLOT) {
            if (getEligibleSpawnShip(player) == null) {
                player.sendMessage(Component.text("Your nation has no docked spawn ship to respawn at.", NamedTextColor.RED));
                return;
            }

            String denial = getSpawnShipDenial(player);
            if (denial != null) {
                player.sendMessage(Component.text(denial, NamedTextColor.RED));
                return;
            }

            RespawnChoiceAPI.setWantsShipRespawn(player, true);
            player.sendMessage(Component.text("You will respawn aboard your spawn ship.", NamedTextColor.AQUA));
            player.closeInventory();
        } else if (slot == NORMAL_SLOT) {
            RespawnChoiceAPI.setWantsShipRespawn(player, false);
            player.sendMessage(Component.text("You will respawn normally.", NamedTextColor.GREEN));
            player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof RespawnChoiceHolder)
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCompassUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isChoiceCompass(event.getItem()))
            return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)
            return;

        event.setCancelled(true);
        offer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        removeCompass(player);

        if (GraveLockUtils.getRemainingLockMillis(player) > 0)
            giveCompassLater(player, 5L);
    }

    @Nullable
    private static Ship getEligibleSpawnShip(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Towny") || !Bukkit.getPluginManager().isPluginEnabled("MvndiShips"))
            return null;

        try {
            Town town = TownyAPI.getInstance().getTown(player);
            if (town == null)
                return null;

            Nation nation = town.getNationOrNull();
            if (nation == null)
                return null;

            Ship ship = MvndiShips.getInstance().getShipManager().findNationSpawnShip(nation.getUUID());
            if (ship == null || !ship.isDocked())
                return null;

            return ship;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String getSpawnShipDenial(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Towny") || !Bukkit.getPluginManager().isPluginEnabled("MvndiShips"))
            return null;

        try {
            Town town = TownyAPI.getInstance().getTown(player);
            return SiegeWarUtil.getSpawnShipDenial(town == null ? null : town.getNationOrNull());
        } catch (Exception e) {
            return null;
        }
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
