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
import java.util.Locale;

/** Shared visual primitives for every Core, Lobby, and mini-game inventory. */
public final class NetworkMenuStyle {
    public static final Material BACKGROUND = Material.GRAY_STAINED_GLASS_PANE;
    public static final Material ACCENT = Material.LIGHT_BLUE_STAINED_GLASS_PANE;

    private NetworkMenuStyle() { }

    public static void fill(Inventory inventory) {
        ItemStack filler = item(BACKGROUND, Component.text(" "));
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    /** Fills decorative background slots only when the viewer uses Java Edition. */
    public static void fill(Inventory inventory, Player player) {
        if (!isBedrockClient(player)) {
            fill(inventory);
        }
    }

    /** Draws an accent top row over the neutral shared background. */
    public static void frame(Inventory inventory) {
        fill(inventory);
        ItemStack accent = item(ACCENT, Component.text(" "));
        for (int slot = 0; slot < Math.min(9, inventory.getSize()); slot++) inventory.setItem(slot, accent);
    }

    /**
     * Draws the shared frame for Java clients and keeps Bedrock inventories sparse.
     * Geyser's touch interface makes a full grid of decorative panes visually noisy and
     * harder to navigate, while empty slots let actionable items remain immediately visible.
     */
    public static void frame(Inventory inventory, Player player) {
        if (!isBedrockClient(player)) {
            frame(inventory);
        }
    }

    /**
     * Applies a manifest-declared visual treatment.  Keeping the choice here
     * prevents a screen from silently drifting away from its declared layout.
     */
    public static void applyFrame(Inventory inventory, Player player, String frame) {
        switch (frame) {
            case "network" -> frame(inventory, player);
            case "neutral" -> fill(inventory, player);
            case "none" -> { /* Native inventories such as anvils own their surface. */ }
            default -> throw new IllegalArgumentException("Cadre de menu inconnu : " + frame);
        }
    }

    /** Returns whether this player is connected through Geyser/Floodgate. */
    public static boolean isBedrockClient(Player player) {
        return isBedrockClient(player.getName(), player.getClientBrandName());
    }

    static boolean isBedrockClient(String username, String clientBrand) {
        return username.startsWith(".")
                || clientBrand != null && clientBrand.toLowerCase(Locale.ROOT).contains("geyser");
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

    /**
     * Builds a player head whose explicit menu label overrides Minecraft's generated head name.
     * Bedrock receives a stable profile icon because its inventory cannot render an unregistered,
     * dynamically resolved skull texture.
     */
    public static ItemStack playerHead(Player player, Component name, Component... lore) {
        if (playerHeadMaterial(player.getName(), player.getClientBrandName()) == Material.NAME_TAG) {
            return item(Material.NAME_TAG, name, lore);
        }
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

    static Material playerHeadMaterial(String username, String clientBrand) {
        return isBedrockClient(username, clientBrand) ? Material.NAME_TAG : Material.PLAYER_HEAD;
    }

    /** Inserts a localized button label as a component, so its MiniMessage tags never become visible text. */
    public static fr.tropicube.language.PlaceholderValues actionPlaceholders(Component label) {
        return fr.tropicube.language.PlaceholderValues.builder().putComponent("action", label).build();
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
