package fr.tropicube.lobby.utils;

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
import java.util.stream.Collectors;

/**
 * Builder fluide pour créer des ItemStack destinés aux GUI.
 * Accepte des chaînes MiniMessage pour les noms et lores.
 */
public class ItemBuilder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ItemStack item;
    private final ItemMeta meta;

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

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }

    // ── Méthodes statiques pratiques ──────────────────────────────────────────

    /** Créer un séparateur (verre teinté sans nom). */
    public static ItemStack filler(Material glass) {
        return new ItemBuilder(glass).name(" ").build();
    }

    /** Item de fermeture (localisé). */
    public static ItemStack closeButton(Player player) {
        return new ItemBuilder(Material.BARRIER)
                .name(LangHelper.get(player, "lobby.close-button"))
                .lore(LangHelper.get(player, "lobby.close-button-lore"))
                .build();
    }

    /** Item de fermeture (fallback sans joueur). */
    public static ItemStack closeButton() {
        return new ItemBuilder(Material.BARRIER)
                .name("<red>✖ Fermer")
                .lore("<gray>Cliquez pour fermer ce menu.")
                .build();
    }

    /** Flèche retour (localisé). */
    public static ItemStack backButton(Player player) {
        return new ItemBuilder(Material.ARROW)
                .name(LangHelper.get(player, "lobby.back-button"))
                .lore(LangHelper.get(player, "lobby.back-button-lore"))
                .build();
    }

    /** Flèche retour (fallback sans joueur). */
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
