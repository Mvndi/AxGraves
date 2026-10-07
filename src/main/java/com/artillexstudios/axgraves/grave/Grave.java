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
import com.artillexstudios.axapi.utils.logging.LogUtils;
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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mannequin;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
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
    private final @Nullable ResolvableProfile appearance;
    private final boolean npc;
    private volatile boolean hadItems;
    private final Inventory gui;
    private int storedXP;
    private volatile Mannequin entity;
    private volatile Interaction[] interactions;
    private final ItemStack[] equipmentSnapshot;
    private final float yaw;
    private Hologram hologram;
    private boolean removed = false;
    private boolean spawnFailureReported = false;

    public Grave(Location loc, @NotNull OfflinePlayer offlinePlayer, @NotNull List<ItemStack> items, int storedXP, long date, @Nullable ItemStack[] equipment) {
        this(loc, offlinePlayer, items, storedXP, date, equipment, null, null);
    }

    public Grave(Location loc, @NotNull OfflinePlayer offlinePlayer, @NotNull List<ItemStack> items, int storedXP,
            long date, @Nullable ItemStack[] equipment, @Nullable String displayName, @Nullable ResolvableProfile appearance) {
        this(loc, offlinePlayer, items, storedXP, date, equipment, displayName, appearance, !items.isEmpty());
    }

    public Grave(Location loc, @NotNull OfflinePlayer offlinePlayer, @NotNull List<ItemStack> items, int storedXP,
            long date, @Nullable ItemStack[] equipment, @Nullable String displayName, @Nullable ResolvableProfile appearance,
            boolean hadItems) {
        this.hadItems = hadItems;
        this.npc = displayName != null;
        this.appearance = appearance;
        items = new ArrayList<>(items);
        items.removeIf(it -> {
            if (it == null || it.getType().isAir() || it.getAmount() <= 0) return true;
            if (BlacklistUtils.isBlacklisted(it)) return true;
            return false;
        });
        items.replaceAll(ItemStack::clone); // clone all items

        this.location = LocationUtils.getCenterOf(loc, true, false);
        this.player = offlinePlayer;
        this.playerName = displayName != null ? displayName
                : offlinePlayer.getName() == null ? LANG.getString("unknown-player", "???") : offlinePlayer.getName();
        this.storedXP = storedXP;
        this.spawned = date;
        this.gui = Bukkit.createInventory(
                null,
                InventoryUtils.getRequiredRows(items.size()) * 9,
                StringUtils.formatToString(LANG.getString("gui-name").replace("%player%", npc ? net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(playerName) : playerName))
        );

        LocationUtils.clampLocation(location);

        Player pl = offlinePlayer.getPlayer();
        if (pl != null && !npc) {
            items = InventoryUtils.reorderInventory(pl.getInventory(), items);
            if (LANG.getBoolean("death-message.enabled", false)) {
                MESSAGEUTILS.sendLang(pl, "death-message.message", Map.of("%world%", LocationUtils.getWorldName(location.getWorld()), "%x%", "" + location.getBlockX(), "%y%", "" + location.getBlockY(), "%z%", "" + location.getBlockZ()));
            }
        }
        items.forEach(gui::addItem);

        this.yaw = CONFIG.getBoolean("rotate-head-360", true)
                ? location.getYaw()
                : LocationUtils.getNearestDirection(location.getYaw());
        this.equipmentSnapshot = new ItemStack[6];
        if (equipment != null) {
            for (int i = 0; i < Math.min(equipment.length, equipmentSnapshot.length); i++) {
                if (equipment[i] != null) equipmentSnapshot[i] = equipment[i].clone();
            }
        } else {
            // Older saves have no equipment snapshot; display armor from their stored items.
            for (ItemStack item : gui.getContents()) {
                if (item == null) continue;
                Material material = item.getType();
                int slot = Utils.isHelmet(material) ? 0 : Utils.isChestplate(material) ? 1
                        : Utils.isLeggings(material) ? 2 : Utils.isBoots(material) ? 3 : -1;
                if (slot >= 0 && equipmentSnapshot[slot] == null) equipmentSnapshot[slot] = item.clone();
            }
        }

        spawnMannequin();
        spawnInteractions();
        reportSpawnFailure();

        updateHologram();
    }

    private void spawnMannequin() {
        Location spawnLoc = location.clone().add(0, 0.5 + corpseVerticalOffset(), 0);
        entity = location.getWorld().spawn(spawnLoc, Mannequin.class, mannequin -> {
            mannequin.setPose(Pose.SLEEPING);
            mannequin.setProfile(appearance != null ? appearance : ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            mannequin.setInvulnerable(true);
            mannequin.setGravity(false);
            mannequin.setSilent(true);
            mannequin.setAI(false);
            mannequin.setCollidable(false);
            mannequin.setRotation(yaw, 0);
            mannequin.setPersistent(false);
            mannequin.setImmovable(true);

            updateEquipment(mannequin);
        });
    }

    private void spawnInteractions() {
        // Mannequin in Pose.SLEEPING is rendered lying flat, extending PERPENDICULAR to
        // the mannequin's forward yaw direction, from the entity position (one end of the body)
        // outward across ~1.8 blocks. The entity position is at one end of the body (head/feet).
        // The exact end (head vs feet) flips depending on the yaw axis (N/S vs E/W alignments),
        // which is why a simple ± offset only ever worked for two of the four cardinal directions.
        // We therefore compute a dynamic multiplier (-cos(2 * yawRad)) so the two 0.9-wide
        // Interaction boxes always cover the full lying body correctly for every yaw.
        double yawRad = Math.toRadians(yaw);
        double dx = Math.cos(yawRad);
        double dz = Math.sin(yawRad);
        double multiplier = -Math.cos(2 * yawRad);

        Location anchor = location.clone().add(0, 0.4 + corpseVerticalOffset(), 0);
        Location near = anchor.clone().add(dx * 0.45 * multiplier, 0, dz * 0.45 * multiplier);
        Location far = anchor.clone().add(dx * 1.35 * multiplier, 0, dz * 1.35 * multiplier);

        Interaction a = location.getWorld().spawn(near, Interaction.class, this::configureInteraction);
        Interaction b = location.getWorld().spawn(far, Interaction.class, this::configureInteraction);
        interactions = new Interaction[]{a, b};
    }

    private double corpseVerticalOffset() {
        if (!npc) return 0;
        double offset = CONFIG.getDouble("npc-corpse-vertical-offset", -1.0);
        return Double.isFinite(offset) ? Math.clamp(offset, -2.0, 2.0) : -1.0;
    }

    private void configureInteraction(Interaction i) {
        i.setInteractionWidth(0.9f);
        i.setInteractionHeight(0.4f);
        i.setResponsive(true);
        i.setInvulnerable(true);
        i.setPersistent(false);
        i.setSilent(true);
    }

    public void update() {
        Scheduler.get().runAt(location, this::updateInRegion);
    }

    private void updateInRegion() {
        if (removed || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;

        int items = countItems();
        if (items > 0) markHadItems();

        if (shouldDespawn(items)) {
            remove();
            return;
        }

        if (entity == null || !entity.isValid()) spawnMannequin();
        ensureInteractions();
        reportSpawnFailure();
        updateEquipment(entity);

        rotateCorpse();
    }

    private void ensureInteractions() {
        Interaction[] current = interactions;
        boolean valid = current != null && current.length == 2
                && current[0] != null && current[0].isValid()
                && current[1] != null && current[1].isValid();
        if (valid) return;
        removeInteractions(current);
        spawnInteractions();
    }

    private void removeInteractions(Interaction[] current) {
        if (current == null) return;
        for (Interaction interaction : current) {
            if (interaction != null) interaction.remove();
        }
    }

    private void rotateCorpse() {
        if (!CONFIG.getBoolean("auto-rotation.enabled", false)) return;
        Mannequin mannequin = entity;
        if (mannequin == null) return;
        Location current = mannequin.getLocation();
        current.setYaw(current.getYaw() + CONFIG.getFloat("auto-rotation.speed", 10f));
        mannequin.setRotation(current.getYaw(), 0);
    }

    private boolean shouldDespawn(int items) {
        int time = CONFIG.getInt("despawn-time-seconds", 180);
        boolean outOfTime = time * 1_000L <= (System.currentTimeMillis() - spawned);
        boolean despawn = CONFIG.getBoolean("despawn-when-empty", true);
        boolean empty = items == 0 && storedXP == 0;
        return (time != -1 && outOfTime) || (despawn && empty && (!npc || hadItems));
    }

    private void reportSpawnFailure() {
        boolean valid = entity != null && entity.isValid() && interactions != null
                && interactions.length == 2 && interactions[0].isValid() && interactions[1].isValid();
        if (!valid && !spawnFailureReported) {
            LogUtils.warn("Grave entities for {} at {} did not enter the world. Check entity spawn restrictions in other plugins. Mannequin valid: {}, interactions valid: {}",
                    playerName, location, entity != null && entity.isValid(),
                    interactions != null && interactions.length == 2
                            && interactions[0].isValid() && interactions[1].isValid());
        }
        spawnFailureReported = !valid;
    }

    private void updateEquipment(Mannequin mannequin) {
        // Only show equipment still present in the grave. Consume matching amounts so
        // identical items in both hands cannot display more than the grave actually holds.
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack item : gui.getContents()) {
            if (!isSlotEmpty(item)) remaining.add(item.clone());
        }
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND};
        EntityEquipment equipment = mannequin.getEquipment();
        for (int i = 0; i < slots.length; i++) {
            ItemStack display = null;
            ItemStack original = equipmentSnapshot[i];
            if (!isSlotEmpty(original)) {
                for (ItemStack item : remaining) {
                    if (item.getAmount() <= 0 || !item.isSimilar(original)) continue;
                    display = original.clone();
                    display.setAmount(Math.min(original.getAmount(), item.getAmount()));
                    item.setAmount(item.getAmount() - display.getAmount());
                    break;
                }
            }
            if (!java.util.Objects.equals(equipment.getItem(slots[i]), display == null
                    ? new ItemStack(Material.AIR) : display)) {
                equipment.setItem(slots[i], display);
            }
        }
    }

    public ItemStack[] getEquipmentSnapshot() {
        ItemStack[] copy = new ItemStack[equipmentSnapshot.length];
        for (int i = 0; i < copy.length; i++) {
            if (equipmentSnapshot[i] != null) copy[i] = equipmentSnapshot[i].clone();
        }
        return copy;
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
        return item.getType().isAir() || item.getAmount() <= 0;
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
            if (isSlotEmpty(it)) continue;
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

            if (entity != null) entity.remove();
            Interaction[] ixs = interactions;
            if (ixs != null) {
                for (Interaction ix : ixs) {
                    if (ix != null) ix.remove();
                }
            }
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

    public void markHadItems() {
        hadItems = true;
    }

    public boolean hadItems() {
        return hadItems;
    }

    public boolean isNpc() { return npc; }

    public @Nullable ResolvableProfile getAppearance() { return appearance; }

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

    public Mannequin getEntity() {
        return entity;
    }

    public Interaction[] getInteractions() {
        return interactions;
    }

    public Hologram getHologram() {
        return hologram;
    }

    public String getPlayerName() {
        return playerName;
    }
}
