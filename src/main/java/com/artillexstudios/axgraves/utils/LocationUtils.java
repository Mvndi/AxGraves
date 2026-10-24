package com.artillexstudios.axgraves.utils;

import com.artillexstudios.axapi.libs.boostedyaml.block.implementation.Section;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.OptionalDouble;
import org.jetbrains.annotations.NotNull;

import static com.artillexstudios.axgraves.AxGraves.CONFIG;

public class LocationUtils {

    @NotNull
    public static Location getCenterOf(@NotNull Location location, boolean keepYaw, boolean keepPitch) {
        Location loc = location.getBlock().getLocation().add(0.5, 0.5, 0.5);
        if (keepYaw) loc.setYaw(location.getYaw());
        if (keepPitch) loc.setPitch(location.getPitch());
        return loc;
    }

    public static OptionalDouble findSurfaceY(Block block) {
        double surface = Double.NEGATIVE_INFINITY;
        for (BoundingBox box : block.getCollisionShape().getBoundingBoxes()) {
            if (box.getMinX() <= 0.5 && box.getMaxX() >= 0.5
                    && box.getMinZ() <= 0.5 && box.getMaxZ() >= 0.5) {
                surface = Math.max(surface, box.getMaxY());
            }
        }
        return Double.isFinite(surface) ? OptionalDouble.of(block.getY() + surface) : OptionalDouble.empty();
    }

    public static int getNearestDirection(float x) {
        return Math.round(x / 90f) * 90;
    }

    public static void clampLocation(Location location) {
        Section section = CONFIG.getSection("spawn-height-limits." + location.getWorld().getName());
        double min, max;
        if (section != null) {
            min = section.getDouble("min");
            max = section.getDouble("max");
        } else {
            switch (location.getWorld().getEnvironment()) {
                case NETHER, THE_END -> {
                    min = 0;
                    max = 255;
                }
                default -> {
                    min = -64;
                    max = 319;
                }
            }
        }
        location.setY(Math.clamp(location.getY(), min, max));
    }

    @NotNull
    public static String getWorldName(World world) {
        if (world == null) return "---";
        return CONFIG.getString("world-name." + world.getName(), world.getName());
    }
}
