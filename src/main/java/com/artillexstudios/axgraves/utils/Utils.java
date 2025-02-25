package com.artillexstudios.axgraves.utils;

import com.artillexstudios.axapi.nms.wrapper.ServerPlayerWrapper;
import com.artillexstudios.axapi.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import static com.artillexstudios.axgraves.AxGraves.CONFIG;
import java.util.function.Consumer;

public class Utils {
    private static final ItemStack GRAVE_ITEM = make(new ItemStack(Material.STICK), item -> {
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("mvndicraft", "gravestone"));
        item.setItemMeta(meta);
    });

    @NotNull
    public static ItemStack getPlayerHead(@NotNull OfflinePlayer player) {
        ItemBuilder builder = ItemBuilder.create(Material.PLAYER_HEAD);

        String texture = null;
        if (CONFIG.getBoolean("custom-grave-skull.enabled", false)) {
            texture = CONFIG.getString("custom-grave-skull.base64");
        } else if (player.getPlayer() != null) {
            ServerPlayerWrapper wrapper = ServerPlayerWrapper.wrap(player);
            texture = wrapper.textures().texture();
        }

        if (texture != null) builder.setTextureValue(texture);

        return builder.get();
    }

    @NotNull
    public static ItemStack getGraveItem() {
        return GRAVE_ITEM.clone();
    }

    public static <T> T make(T object, Consumer<? super T> consumer) {
        consumer.accept(object);
        return object;
    }
}
