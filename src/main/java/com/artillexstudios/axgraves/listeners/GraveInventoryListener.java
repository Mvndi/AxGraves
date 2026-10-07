package com.artillexstudios.axgraves.listeners;

import com.artillexstudios.axgraves.AxGraves;
import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.SpawnedGraves;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class GraveInventoryListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        Grave grave = grave(event.getView().getTopInventory());
        if (grave == null) return;
        if (grave.countItems() > 0 || insertsItem(event)) grave.markHadItems();
        event.getWhoClicked()
                .getScheduler()
                .run(AxGraves.getInstance(), task -> grave.update(), null);
    }

    private boolean insertsItem(InventoryClickEvent event) {
        boolean top = event.getClickedInventory() == event.getView().getTopInventory();
        return switch (event.getAction()) {
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR ->
                    top && present(event.getCursor());
            case MOVE_TO_OTHER_INVENTORY -> !top && present(event.getCurrentItem());
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> top && present(hotbarItem(event));
            default -> false;
        };
    }

    private ItemStack hotbarItem(InventoryClickEvent event) {
        int slot = event.getHotbarButton();
        return slot < 0
                ? event.getWhoClicked().getInventory().getItemInOffHand()
                : event.getWhoClicked().getInventory().getItem(slot);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        Grave grave = grave(event.getView().getTopInventory());
        if (grave == null) return;
        boolean inserted =
                event.getNewItems().entrySet().stream()
                        .anyMatch(
                                entry ->
                                        entry.getKey() < grave.getGui().getSize()
                                                && present(entry.getValue()));
        if (grave.countItems() > 0 || inserted) grave.markHadItems();
        event.getWhoClicked()
                .getScheduler()
                .run(AxGraves.getInstance(), task -> grave.update(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void close(InventoryCloseEvent event) {
        Grave grave = grave(event.getView().getTopInventory());
        if (grave == null) return;
        if (grave.countItems() > 0) grave.markHadItems();
        grave.update();
    }

    private Grave grave(Inventory inventory) {
        return SpawnedGraves.getGraves().stream()
                .filter(grave -> grave.getGui() == inventory)
                .findFirst()
                .orElse(null);
    }

    private boolean present(ItemStack item) {
        return item != null && !item.isEmpty();
    }
}
