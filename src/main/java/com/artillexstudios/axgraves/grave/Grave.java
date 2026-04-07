package com.artillexstudios.axgraves.grave;

import com.artillexstudios.axapi.hologram.Hologram;
import com.artillexstudios.axapi.hologram.HologramType;
import com.artillexstudios.axapi.hologram.HologramTypes;
import com.artillexstudios.axapi.hologram.page.HologramPage;
import com.artillexstudios.axapi.libs.boostedyaml.block.implementation.Section;
import com.artillexstudios.axapi.packetentity.meta.entity.DisplayMeta;
import com.artillexstudios.axapi.packetentity.meta.entity.TextDisplayMeta;
import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axgraves.api.events.GraveInteractEvent;
import com.artillexstudios.axgraves.api.events.GraveOpenEvent;
import com.artillexstudios.axgraves.utils.BlacklistUtils;
import com.artillexstudios.axgraves.utils.ExperienceUtils;
import com.artillexstudios.axgraves.utils.InventoryUtils;
import com.artillexstudios.axgraves.utils.LocationUtils;
import com.artillexstudios.axgraves.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.*;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.artillexstudios.axgraves.AxGraves.CONFIG;
import static com.artillexstudios.axgraves.AxGraves.LANG;
import static com.artillexstudios.axgraves.AxGraves.MESSAGEUTILS;

public class Grave {
    private static final Vector ZERO_VECTOR = new Vector(0, 0, 0);
    private final long spawned;
    private final Location location;
    private final OfflinePlayer player;
    private final String playerName;
    private final Inventory gui;
    private int storedXP;
    private final Mannequin mannequin;
    private final Interaction interaction;
    private Hologram hologram;
    private boolean removed = false;

    public Grave(Location loc, @NotNull OfflinePlayer offlinePlayer, @NotNull List<ItemStack> items, int storedXP, long date, @Nullable ItemStack[] equipment) {
        items = new ArrayList<>(items);
        items.removeIf(it -> {
            if (it == null) return true;
            if (BlacklistUtils.isBlacklisted(it)) return true;
            return false;
        });
        items.replaceAll(ItemStack::clone); // clone all items

        this.location = LocationUtils.getCenterOf(loc, true, false);
        this.player = offlinePlayer;
        this.playerName = offlinePlayer.getName() == null ? LANG.getString("unknown-player", "???") : offlinePlayer.getName();
        this.storedXP = storedXP;
        this.spawned = date;
        this.gui = Bukkit.createInventory(
                null,
                InventoryUtils.getRequiredRows(items.size()) * 9,
                StringUtils.formatToString(LANG.getString("gui-name").replace("%player%", playerName))
        );

        LocationUtils.clampLocation(location);

        Player pl = offlinePlayer.getPlayer();
        if (pl != null) {
            items = InventoryUtils.reorderInventory(pl.getInventory(), items);
            if (LANG.getBoolean("death-message.enabled", false)) {
                MESSAGEUTILS.sendLang(pl, "death-message.message", Map.of("%world%", LocationUtils.getWorldName(location.getWorld()), "%x%", "" + location.getBlockX(), "%y%", "" + location.getBlockY(), "%z%", "" + location.getBlockZ()));
            }
        }
        items.forEach(gui::addItem);

        float yaw = CONFIG.getBoolean("rotate-head-360", true)
                ? location.getYaw()
                : LocationUtils.getNearestDirection(location.getYaw());

        Location spawnLoc = location.clone().add(0, 0.5, 0);

        float headYaw = 90 - yaw;
        double rad = Math.toRadians(headYaw);
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);
        Location interactionLoc = spawnLoc.clone().add(dx, 0, dz);

        interaction = location.getWorld().spawn(interactionLoc, Interaction.class, interaction1 -> {
            interaction1.setInteractionWidth(1.0f);
            interaction1.setInteractionHeight(0.2f);
            interaction1.setRotation(yaw, 0);
            interaction1.setGravity(true);
            interaction1.setPersistent(false);
        });
        mannequin = location.getWorld().spawn(spawnLoc, Mannequin.class, mannequin1 -> {
            mannequin1.setPose(Pose.SLEEPING);
            mannequin1.setProfile(ResolvableProfile.resolvableProfile(offlinePlayer.getPlayerProfile()));
            mannequin1.setInvulnerable(true);
            mannequin1.setGravity(false);
            mannequin1.setSilent(true);
            mannequin1.setAI(false);
            mannequin1.setCollidable(false);
            mannequin1.setRotation(yaw, 0);
            mannequin1.setPersistent(false);

            if (equipment != null) {
                org.bukkit.inventory.EntityEquipment eq = mannequin1.getEquipment();
                if (equipment.length > 0 && equipment[0] != null) eq.setHelmet(equipment[0].clone());
                if (equipment.length > 1 && equipment[1] != null) eq.setChestplate(equipment[1].clone());
                if (equipment.length > 2 && equipment[2] != null) eq.setLeggings(equipment[2].clone());
                if (equipment.length > 3 && equipment[3] != null) eq.setBoots(equipment[3].clone());
                if (equipment.length > 4 && equipment[4] != null) eq.setItemInMainHand(equipment[4].clone());
                if (equipment.length > 5 && equipment[5] != null) eq.setItemInOffHand(equipment[5].clone());
            }
        });

        updateHologram();
    }

    public void update() {
        int items = countItems();

        int time = CONFIG.getInt("despawn-time-seconds", 180);
        boolean outOfTime = time * 1_000L <= (System.currentTimeMillis() - spawned);
        boolean despawn = CONFIG.getBoolean("despawn-when-empty", true);
        boolean empty = items == 0 && storedXP == 0;
        if ((time != -1 && outOfTime) || (despawn && empty)) {
            Scheduler.get().runAt(location, this::remove);
            return;
        }

        if (CONFIG.getBoolean("auto-rotation.enabled", false)) {
            Location loc = mannequin.getLocation();
            loc.setYaw(loc.getYaw() + CONFIG.getFloat("auto-rotation.speed", 10f));
            mannequin.setRotation(loc.getYaw(), 0);
        }
    }

    public void interact(@NotNull Player opener, @Nullable EquipmentSlot slot) {
        if (CONFIG.getBoolean("interact-only-own", false) && !opener.getUniqueId().equals(player.getUniqueId()) && !opener.hasPermission("axgraves.admin")) {
            MESSAGEUTILS.sendLang(opener, "interact.not-your-grave");
            return;
        }

        final GraveInteractEvent graveInteractEvent = new GraveInteractEvent(opener, this);
        Bukkit.getPluginManager().callEvent(graveInteractEvent);
        if (graveInteractEvent.isCancelled()) return;

        if (this.storedXP != 0) {
            ExperienceUtils.changeExp(opener, this.storedXP);
            this.storedXP = 0;
        }

        if (slot != null && slot.equals(EquipmentSlot.HAND) && opener.isSneaking()) {
            if (opener.getGameMode() == GameMode.SPECTATOR) return;
            if (!CONFIG.getBoolean("enable-instant-pickup", true)) return;
            if (CONFIG.getBoolean("instant-pickup-only-own", false) && !opener.getUniqueId().equals(player.getUniqueId())) return;

            PlayerInventory inventory = opener.getInventory();
            for (ItemStack it : gui.getContents()) {
                if (it == null) continue;

                if (CONFIG.getBoolean("auto-equip-armor", true)) {
                    Material material = it.getType();
                    if (isSlotEmpty(inventory.getHelmet()) && Utils.isHelmet(material)) {
                        inventory.setHelmet(it);
                        it.setAmount(0);
                        continue;
                    }

                    if (isSlotEmpty(inventory.getChestplate()) && Utils.isChestplate(material)) {
                        inventory.setChestplate(it);
                        it.setAmount(0);
                        continue;
                    }

                    if (isSlotEmpty(inventory.getLeggings()) && Utils.isLeggings(material)) {
                        inventory.setLeggings(it);
                        it.setAmount(0);
                        continue;
                    }

                    if (isSlotEmpty(inventory.getBoots()) && Utils.isBoots(material)) {
                        inventory.setBoots(it);
                        it.setAmount(0);
                        continue;
                    }
                }

                final Collection<ItemStack> ar = inventory.addItem(it).values();
                if (ar.isEmpty()) {
                    it.setAmount(0);
                    continue;
                }

                it.setAmount(ar.iterator().next().getAmount());
            }

            update();
            return;
        }

        final GraveOpenEvent graveOpenEvent = new GraveOpenEvent(opener, this);
        Bukkit.getPluginManager().callEvent(graveOpenEvent);
        if (graveOpenEvent.isCancelled()) return;

        opener.openInventory(gui);
    }

    private boolean isSlotEmpty(ItemStack item) {
        if (item == null) return true;
        return item.getType().isAir();
    }

    public void updateHologram() {
        if (hologram != null) hologram.remove();

        List<String> lines = LANG.getStringList("hologram");

        double hologramHeight = CONFIG.getFloat("hologram-height", 0.75f) + 1;
        hologram = new Hologram(location.clone().add(0, getNewHeight(hologramHeight, lines.size(), 0.3f), 0));

        HologramPage<String, HologramType<String>> page = hologram.createPage(HologramTypes.TEXT);
        page.getParameters().withParameter(Grave.class, this);

        Section section = CONFIG.getSection("holograms");
        page.setEntityMetaHandler(m -> {
            TextDisplayMeta meta = (TextDisplayMeta) m;
            meta.seeThrough(section.getBoolean("see-through"));
            meta.shadow(section.getBoolean("shadow", true));
            meta.alignment(TextDisplayMeta.Alignment.valueOf(section.getString("alignment").toUpperCase()));
            meta.backgroundColor(Integer.parseInt(section.getString("background-color"), 16));
            meta.lineWidth(1000);
            meta.billboardConstrain(DisplayMeta.BillboardConstrain.valueOf(section.getString("billboard").toUpperCase()));
        });

        page.setContent(String.join("<reset><br>", lines));
        page.spawn();
    }

    private static double getNewHeight(double y, int lines, float lineHeight) {
        return y - lineHeight * (lines - 1) + 0.25;
    }

    public int countItems() {
        int am = 0;
        for (ItemStack it : gui.getContents()) {
            if (it == null) continue;
            am++;
        }
        return am;
    }

    public void remove() {
        if (removed) return;
        removed = true;

        Runnable runnable = () -> {
            SpawnedGraves.removeGrave(this);
            removeInventory();

            if (interaction != null) interaction.remove();
            if (mannequin != null) mannequin.remove();
            if (hologram != null) hologram.remove();
        };

        if (Scheduler.get().isOwnedByCurrentRegion(location)) runnable.run();
        else Scheduler.get().runAt(location, runnable);
    }

    public void removeInventory() {
        closeInventory(null);

        if (CONFIG.getBoolean("drop-items", true)) {
            for (ItemStack it : gui.getContents()) {
                if (it == null) continue;
                final Item item = location.getWorld().dropItem(location.clone(), it);
                if (CONFIG.getBoolean("dropped-item-velocity", true)) continue;
                item.setVelocity(ZERO_VECTOR);
            }
        }

        if (storedXP == 0) return;
        final ExperienceOrb exp = (ExperienceOrb) location.getWorld().spawnEntity(location, EntityType.EXPERIENCE_ORB);
        exp.setExperience(storedXP);
    }

    public void closeInventory(@Nullable HumanEntity closeFor) {
        Scheduler.get().executeAt(location, () -> {
            if (closeFor != null) {
                closeFor.closeInventory();
                return;
            }
            List<HumanEntity> viewers = new ArrayList<>(gui.getViewers());
            for (HumanEntity viewer : viewers) {
                viewer.closeInventory();
            }
        });
    }

    public Location getLocation() {
        return location;
    }

    public OfflinePlayer getPlayer() {
        return player;
    }

    public long getSpawned() {
        return spawned;
    }

    public Inventory getGui() {
        return gui;
    }

    public int getStoredXP() {
        return storedXP;
    }

    public Interaction getInteraction() {
        return interaction;
    }

    public Mannequin getMannequin() {
        return mannequin;
    }

    public Hologram getHologram() {
        return hologram;
    }

    public String getPlayerName() {
        return playerName;
    }
}
