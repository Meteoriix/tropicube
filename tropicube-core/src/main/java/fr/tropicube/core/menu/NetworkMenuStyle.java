package fr.tropicube.core.menu;

import fr.tropicube.core.util.ComponentLines;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/** Shared visual primitives for every Core and Lobby inventory. */
public final class NetworkMenuStyle {
    public static final Material BACKGROUND = Material.GRAY_STAINED_GLASS_PANE;
    public static final Material ACCENT = Material.LIGHT_BLUE_STAINED_GLASS_PANE;

    private NetworkMenuStyle() { }

    public static void fill(Inventory inventory) {
        ItemStack filler = item(BACKGROUND, Component.text(" "));
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    /** Draws an accent top row over the neutral shared background. */
    public static void frame(Inventory inventory) {
        fill(inventory);
        ItemStack accent = item(ACCENT, Component.text(" "));
        for (int slot = 0; slot < Math.min(9, inventory.getSize()); slot++) inventory.setItem(slot, accent);
    }

    public static ItemStack item(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(clean(name));
            if (lore.length > 0) meta.lore(ComponentLines.splitAll(List.of(lore)).stream()
                    .map(NetworkMenuStyle::clean)
                    .toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        return item;
    }

    /** Builds a player head whose explicit menu label overrides Minecraft's generated head name. */
    public static ItemStack playerHead(Player player, Component name, Component... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setPlayerProfile(player.getPlayerProfile());
        meta.displayName(clean(name));
        if (lore.length > 0) meta.lore(ComponentLines.splitAll(List.of(lore)).stream()
                .map(NetworkMenuStyle::clean)
                .toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack close(Component name, Component lore) {
        return item(Material.BARRIER, name, lore);
    }

    public static ItemStack back(Component name, Component lore) {
        return item(Material.ARROW, name, lore);
    }

    public static ItemStack loading(Component name, Component lore) {
        return item(Material.CLOCK, name, lore);
    }

    public static ItemStack locked(Component name, Component... lore) {
        return item(Material.IRON_DOOR, name, lore);
    }

    private static Component clean(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
