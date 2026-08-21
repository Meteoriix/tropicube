package fr.tropicube.lobby.utils;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Fluid builder to create ItemStacks for GUIs.
 * Accepts MiniMessage strings for names and lore.
 */
public class ItemBuilder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ItemStack item;
    private final ItemMeta meta;
    private ResolvableProfile skullProfile;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(ItemStack item) {
        this.item = item;
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(Material material, int amount) {
        item = new ItemStack(material, amount);
        meta = item.getItemMeta();
    }

    public ItemBuilder name(String miniMessage) {
        meta.displayName(parse(miniMessage));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        meta.lore(Arrays.stream(lines)
                .map(this::parseLore)
                .collect(Collectors.toList()));
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        meta.lore(lines.stream().map(this::parseLore).collect(Collectors.toList()));
        return this;
    }

    public ItemBuilder glow() {
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public ItemBuilder hideFlags() {
        meta.addItemFlags(ItemFlag.values());
        return this;
    }

    public ItemBuilder customModelData(int data) {
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setFloats(List.of((float) data));
        meta.setCustomModelDataComponent(component);
        return this;
    }

    /** Assigns an already resolved player profile to a player head. */
    public ItemBuilder skullProfile(ResolvableProfile profile) {
        if (item.getType() != Material.PLAYER_HEAD) {
            throw new IllegalStateException("skullProfile nécessite un item PLAYER_HEAD");
        }
        skullProfile = Objects.requireNonNull(profile, "profile");
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        if (skullProfile != null) item.setData(DataComponentTypes.PROFILE, skullProfile);
        return item;
    }

    // ── Practical static methods ───────────────────── ─────────────────────

    /** Create a separator (unnamed tinted glass). */
    public static ItemStack filler(Material glass) {
        return new ItemBuilder(glass).name(" ").build();
    }

    /** Closing item (localized). */
    public static ItemStack closeButton(Player player) {
        return new ItemBuilder(Material.BARRIER)
                .name(LangHelper.get(player, "lobby.close-button"))
                .lore(LangHelper.get(player, "lobby.close-button-lore"))
                .build();
    }

    /** Closing item (fallback without player). */
    public static ItemStack closeButton() {
        return new ItemBuilder(Material.BARRIER)
                .name("<red>✖ Fermer")
                .lore("<gray>Cliquez pour fermer ce menu.")
                .build();
    }

    /** Back arrow (located). */
    public static ItemStack backButton(Player player) {
        return new ItemBuilder(Material.ARROW)
                .name(LangHelper.get(player, "lobby.back-button"))
                .lore(LangHelper.get(player, "lobby.back-button-lore"))
                .build();
    }

    /** Return arrow (fallback without player). */
    public static ItemStack backButton() {
        return new ItemBuilder(Material.ARROW)
                .name("<yellow>← Retour")
                .lore("<gray>Retourner au menu précédent.")
                .build();
    }

    private Component parse(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) return Component.empty();
        return MM.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    private Component parseLore(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) {
            return Component.empty().decoration(TextDecoration.ITALIC, false);
        }
        return MM.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }
}
