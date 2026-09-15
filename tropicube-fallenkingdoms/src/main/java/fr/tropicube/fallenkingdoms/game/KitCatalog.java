package fr.tropicube.fallenkingdoms.game;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

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
        String enabledOverride=System.getenv("FK_ENABLED_KITS");
        java.util.Set<String> enabledIds=enabledOverride==null||enabledOverride.isBlank()?null:
                java.util.Arrays.stream(enabledOverride.split(",")).map(value->value.trim().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled") || enabledIds!=null&&!enabledIds.contains(id.toLowerCase(Locale.ROOT))) continue;
            Material icon = material(section.getString("icon"), "kits.definitions." + id + ".icon");
            List<KitDefinition.KitItem> items = section.getMapList("items").stream().map(item -> {
                Material material = material(String.valueOf(item.get("material")), "kits.definitions." + id + ".items.material");
                int amount = ((Number) (item.containsKey("amount") ? item.get("amount") : 1)).intValue();
                int slot = ((Number) (item.containsKey("slot") ? item.get("slot") : 0)).intValue();
                String potionType = item.containsKey("potion-type") ? String.valueOf(item.get("potion-type")).toUpperCase(Locale.ROOT) : null;
                int damage = ((Number) (item.containsKey("damage") ? item.get("damage") : 0)).intValue();
                Map<String, Integer> enchantments = new HashMap<>();
                Object configuredEnchantments = item.get("enchantments");
                if (configuredEnchantments instanceof Map<?, ?> values) values.forEach((name, level) -> {
                    String enchantment = String.valueOf(name).toLowerCase(Locale.ROOT);
                    if (!enchantment.matches("[a-z0-9_]+") || !(level instanceof Number number) || number.intValue() < 1)
                        throw new IllegalArgumentException("kits.definitions." + id + ".items.enchantments: enchantement invalide " + name);
                    if (org.bukkit.Bukkit.getServer() != null
                            && org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchantment)) == null)
                        throw new IllegalArgumentException("kits.definitions." + id + ".items.enchantments: enchantement inconnu " + name);
                    enchantments.put(enchantment, number.intValue());
                });
                if (amount < 1 || amount > 64 || slot < 0 || slot > 35)
                    throw new IllegalArgumentException("kits.definitions." + id + ".items: quantité ou emplacement invalide");
                if (potionType != null && (!SUPPORTED_POTIONS.contains(potionType)
                        || (material != Material.POTION && material != Material.SPLASH_POTION)))
                    throw new IllegalArgumentException("kits.definitions." + id + ".items.potion-type: potion invalide");
                if (damage < 0 || damage > 32767)
                    throw new IllegalArgumentException("kits.definitions." + id + ".items.damage: durabilité invalide");
                return new KitDefinition.KitItem(material, amount, slot, potionType, damage, enchantments);
            }).toList();
            result.put(id, new KitDefinition(id, icon, items));
        }
        if(enabledIds!=null&&!result.keySet().containsAll(enabledIds))throw new IllegalArgumentException("FK_ENABLED_KITS contient un kit inconnu ou désactivé");
        String defaultKit = config.getString("kits.default", "").toLowerCase(Locale.ROOT);
        if (!result.containsKey(defaultKit) && enabledIds != null && !result.isEmpty()) defaultKit=result.keySet().iterator().next();
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
            if (item.damage() > 0) stack.editMeta(Damageable.class, meta -> meta.setDamage(item.damage()));
            item.enchantments().forEach((name, level) -> {
                Enchantment enchantment = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name));
                if (enchantment == null) throw new IllegalStateException("Enchantement validé absent du registre Paper: " + name);
                if (stack.getItemMeta() instanceof EnchantmentStorageMeta) stack.editMeta(EnchantmentStorageMeta.class,
                        meta -> meta.addStoredEnchant(enchantment, level, true));
                else stack.addUnsafeEnchantment(enchantment, level);
            });
            player.getInventory().setItem(item.slot(), stack);
        });
    }
    private static Material material(String value, String path) {
        try { return Material.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (RuntimeException exception) { throw new IllegalArgumentException(path + ": matériau inconnu " + value, exception); }
    }
}
