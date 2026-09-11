package fr.tropicube.core.ui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validated, hot-reloadable catalog of menu layouts and their safe action identifiers. */
public final class MenuTemplateRegistry implements UiReloadParticipant {
    private final JavaPlugin plugin;
    private volatile Map<String, Menu> menus;

    public MenuTemplateRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.menus = load();
    }

    public Menu menu(String id) {
        Menu menu = menus.get(id);
        if (menu == null) throw new IllegalArgumentException("Menu inconnu : " + id);
        return menu;
    }

    @Override
    public Runnable prepareReload() {
        Map<String, Menu> prepared = load();
        return () -> menus = prepared;
    }

    @Override
    public void refreshViewers() {
        // Menus are rebuilt on their next open; existing inventories keep their click context safely.
    }

    private Map<String, Menu> load() {
        File file = new File(plugin.getDataFolder(), "menus.yml");
        if (!file.exists()) plugin.saveResource("menus.yml", false);
        try { fr.tropicube.core.util.ConfigUpdater.update(plugin, "menus.yml", file); }
        catch (java.io.IOException error) { throw new IllegalStateException("Unable to update menus.yml", error); }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (removeLegacyProfileGuildsButton(yaml)) {
            try {
                yaml.save(file);
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Unable to migrate the legacy profile menu", error);
            }
        }
        if (yaml.getInt("version", -1) != 1) throw new IllegalArgumentException("menus.yml: version attendue=1");
        ConfigurationSection root = yaml.getConfigurationSection("menus");
        if (root == null || root.getKeys(false).isEmpty()) throw new IllegalArgumentException("menus.yml: menus manquants");
        Map<String, Menu> parsed = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection source = Objects.requireNonNull(root.getConfigurationSection(id));
            String title = require(source, "title-key", id);
            int rows = source.getInt("rows", -1);
            if (rows < 1 || rows > 6) throw new IllegalArgumentException("menus.yml: " + id + ".rows doit valoir 1..6");
            String frame = source.getString("frame", "network");
            if (!List.of("network", "neutral", "none").contains(frame)) {
                throw new IllegalArgumentException("menus.yml: " + id + ".frame doit valoir network, neutral ou none");
            }
            Map<String, Button> buttons = new LinkedHashMap<>();
            ConfigurationSection buttonRoot = source.getConfigurationSection("buttons");
            if (buttonRoot != null) for (String buttonId : buttonRoot.getKeys(false)) {
                ConfigurationSection button = Objects.requireNonNull(buttonRoot.getConfigurationSection(buttonId));
                int slot = button.getInt("slot", -1);
                if (slot < 0 || slot >= rows * 9 || buttons.values().stream().anyMatch(value -> value.slot() == slot)) {
                    throw new IllegalArgumentException("menus.yml: slot invalide ou dupliqué dans " + id + ":" + buttonId);
                }
                Material material = Material.matchMaterial(require(button, "material", id + "." + buttonId));
                if (material == null || material.isAir()) throw new IllegalArgumentException(
                        "menus.yml: matériau invalide dans " + id + ":" + buttonId);
                buttons.put(buttonId, new Button(slot, material, button.getString("name-key"),
                        button.getString("lore-key"), require(button, "action", id + "." + buttonId),
                        button.getBoolean("required"), Math.max(1, button.getInt("amount", 1)),
                        button.getBoolean("glow")));
            }
            Map<String, DynamicRegion> regions = new LinkedHashMap<>();
            ConfigurationSection regionRoot = source.getConfigurationSection("dynamic-regions");
            if (regionRoot != null) for (String regionId : regionRoot.getKeys(false)) {
                ConfigurationSection region = Objects.requireNonNull(regionRoot.getConfigurationSection(regionId));
                List<Integer> slots = region.getIntegerList("slots");
                // A dynamic region may deliberately replace a static loading or empty-state card.
                if (slots.isEmpty() || slots.stream().anyMatch(slot -> slot < 0 || slot >= rows * 9)
                        || slots.stream().distinct().count() != slots.size()) {
                    throw new IllegalArgumentException("menus.yml: slots invalides dans " + id + ".dynamic-regions." + regionId);
                }
                regions.put(regionId, new DynamicRegion(List.copyOf(slots),
                        require(region, "template-action", id + ".dynamic-regions." + regionId),
                        Math.max(1, region.getInt("preview-count", 1))));
            }
            parsed.put(id, new Menu(id, title, rows, frame, Map.copyOf(buttons), Map.copyOf(regions)));
        }
        return Map.copyOf(parsed);
    }

    /**
     * Removes the Guilds button from the pre-wardrobe profile layout.
     *
     * <p>That entry occupied slot 24 in persisted runtime UI bundles. The
     * wardrobe deliberately takes that slot now, so retaining it would make
     * the complete menu catalog invalid and prevent Core from starting.</p>
     *
     * @param yaml persisted menu configuration to migrate.
     * @return {@code true} when the configuration was changed.
     */
    static boolean removeLegacyProfileGuildsButton(YamlConfiguration yaml) {
        ConfigurationSection buttons = yaml.getConfigurationSection("menus.profile-home.buttons");
        if (buttons == null) return false;
        ConfigurationSection guilds = buttons.getConfigurationSection("guilds");
        if (guilds == null || guilds.getInt("slot", -1) != 24
                || !"open_guilds".equals(guilds.getString("action"))) return false;
        buttons.set("guilds", null);
        return true;
    }

    private static String require(ConfigurationSection section, String key, String owner) {
        String value = section.getString(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("menus.yml: " + owner + "." + key + " est requis");
        return value;
    }

    public record Menu(String id, String titleKey, int rows, String frame, Map<String, Button> buttons,
                       Map<String, DynamicRegion> dynamicRegions) {
        public Button button(String id) {
            Button button = buttons.get(id);
            if (button == null) throw new IllegalArgumentException("Bouton inconnu dans " + this.id + " : " + id);
            return button;
        }
        public DynamicRegion dynamicRegion(String id) {
            DynamicRegion region = dynamicRegions.get(id);
            if (region == null) throw new IllegalArgumentException("Région inconnue dans " + this.id + " : " + id);
            return region;
        }
    }
    public record Button(int slot, Material material, String nameKey, String loreKey, String action,
                         boolean required, int amount, boolean glow) { }
    public record DynamicRegion(List<Integer> slots, String templateAction, int previewCount) { }
}
