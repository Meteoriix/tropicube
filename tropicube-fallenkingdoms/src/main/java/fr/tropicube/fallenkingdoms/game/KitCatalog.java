package fr.tropicube.fallenkingdoms.game;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads configurable kits and issues each selected kit once at match start. */
public final class KitCatalog {
    private static final java.util.Set<String> SUPPORTED_POTIONS = java.util.Set.of("SWIFTNESS", "STRONG_SWIFTNESS", "LONG_SWIFTNESS");
    private final Map<String, KitDefinition> definitions;
    private final String defaultKit;
    private KitCatalog(Map<String, KitDefinition> definitions, String defaultKit) {
        this.definitions = Map.copyOf(definitions); this.defaultKit = defaultKit;
    }
    public static KitCatalog load(FileConfiguration config) {
        ConfigurationSection root = config.getConfigurationSection("kits.definitions");
        if (root == null) throw new IllegalArgumentException("kits.definitions: section manquante");
        Map<String, KitDefinition> result = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled")) continue;
            Material icon = material(section.getString("icon"), "kits.definitions." + id + ".icon");
            List<KitDefinition.KitItem> items = section.getMapList("items").stream().map(item -> {
                Material material = material(String.valueOf(item.get("material")), "kits.definitions." + id + ".items.material");
                int amount = ((Number) (item.containsKey("amount") ? item.get("amount") : 1)).intValue();
                int slot = ((Number) (item.containsKey("slot") ? item.get("slot") : 0)).intValue();
                String potionType = item.containsKey("potion-type") ? String.valueOf(item.get("potion-type")).toUpperCase(Locale.ROOT) : null;
                if (amount < 1 || amount > 64 || slot < 0 || slot > 35)
                    throw new IllegalArgumentException("kits.definitions." + id + ".items: quantité ou emplacement invalide");
                if (potionType != null && (!SUPPORTED_POTIONS.contains(potionType)
                        || (material != Material.POTION && material != Material.SPLASH_POTION)))
                    throw new IllegalArgumentException("kits.definitions." + id + ".items.potion-type: potion invalide");
                return new KitDefinition.KitItem(material, amount, slot, potionType);
            }).toList();
            result.put(id, new KitDefinition(id, icon, items));
        }
        String defaultKit = config.getString("kits.default", "").toLowerCase(Locale.ROOT);
        if (!result.containsKey(defaultKit)) throw new IllegalArgumentException("kits.default: kit absent ou désactivé");
        return new KitCatalog(result, defaultKit);
    }
    public String defaultKit() { return defaultKit; }
    public Map<String, KitDefinition> definitions() { return definitions; }
    public void give(Player player, String kitId) {
        KitDefinition kit = definitions.getOrDefault(kitId, definitions.get(defaultKit));
        kit.items().forEach(item -> {
            ItemStack stack = new ItemStack(item.material(), item.amount());
            if (item.potionType() != null) stack.editMeta(PotionMeta.class,
                    meta -> meta.setBasePotionType(PotionType.valueOf(item.potionType())));
            player.getInventory().setItem(item.slot(), stack);
        });
    }
    private static Material material(String value, String path) {
        try { return Material.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (RuntimeException exception) { throw new IllegalArgumentException(path + ": matériau inconnu " + value, exception); }
    }
}
