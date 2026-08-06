package fr.tropicube.sheepwars.menu;

import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.sheepwars.player.PlayerClass;
import fr.tropicube.sheepwars.player.PlayerKit;
import fr.tropicube.sheepwars.sheep.SheepType;
import fr.tropicube.sheepwars.util.ItemBuilder;
import fr.tropicube.sheepwars.util.LangHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Interface de configuration d'une partie personnalisée : règles, moutons,
 * classes et kits autorisés. Les réglages sont réinitialisés entre les parties.
 */
public class GameSettingsMenu implements Listener {

    private enum Page { MAIN, SHEEP, KITS, CLASSES, OPTIONS, DROP_RATES }

    // Page de 54 cases : deux lignes internes de sept moutons, puis deux sur la troisième.
    private static final int[] SHEEP_SLOTS     = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29};
    // 27-slot kits page: DPS row 1, TANK row 2, SUPPORT row 3
    private static final int[] KIT_SLOTS_DPS     = {2, 4, 6};
    private static final int[] KIT_SLOTS_TANK    = {11, 13, 15};
    private static final int[] KIT_SLOTS_SUPPORT = {20, 22, 24};
    // 27-slot classes page: middle row centered (slots 11, 13, 15)
    private static final int[] CLASS_SLOTS = {11, 13, 15};

    private final TropicubeSheepwars plugin;
    public final NamespacedKey settingsMenuKey;
    private final Map<UUID, Page> openMenus  = new HashMap<>();
    private final EnumSet<SheepType>  disabledSheep = EnumSet.noneOf(SheepType.class);
    private final EnumSet<PlayerKit>  disabledKits  = EnumSet.noneOf(PlayerKit.class);
    private final EnumSet<PlayerClass>  disabledClasses = EnumSet.noneOf(PlayerClass.class);

    public GameSettingsMenu(TropicubeSheepwars plugin) {
        this.plugin = plugin;
        this.settingsMenuKey = new NamespacedKey(plugin, "settings_menu_item");
        load();
    }

    // Objet privé placé dans la barre rapide de l'hôte

    public ItemStack createSelectorItem(Player player) {
        return new ItemBuilder(Material.COMPARATOR)
                .name(LangHelper.component(player, "sw.settings-selector-name")
                        .decoration(TextDecoration.ITALIC, false))
                .lore(LangHelper.component(player, "sw.settings-selector-lore")
                        .decoration(TextDecoration.ITALIC, false))
                .persistentData(settingsMenuKey, PersistentDataType.BYTE, (byte) 1)
                .build();
    }

    public boolean isSelectorItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(settingsMenuKey, PersistentDataType.BYTE);
    }

    // ── Persistence ────────────────────────────────────────────────────────

    public void load() {
        disabledSheep.clear();
        disabledKits.clear();
        disabledClasses.clear();
        for (String s : plugin.getConfig().getStringList("force-settings.sheep-disabled"))
            try { disabledSheep.add(SheepType.valueOf(s.trim().toUpperCase(Locale.ROOT))); } catch (IllegalArgumentException ignored) {}
        for (String s : plugin.getConfig().getStringList("force-settings.kits-disabled"))
            try { disabledKits.add(PlayerKit.valueOf(s.trim().toUpperCase(Locale.ROOT))); } catch (IllegalArgumentException ignored) {}
        for (String s : plugin.getConfig().getStringList("force-settings.classes-disabled"))
            try { disabledClasses.add(PlayerClass.valueOf(s.trim().toUpperCase(Locale.ROOT))); } catch (IllegalArgumentException ignored) {}
    }

    private void save() {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("force-settings.sheep-disabled", disabledSheep.stream().map(Enum::name).toList());
        cfg.set("force-settings.kits-disabled",  disabledKits.stream().map(Enum::name).toList());
        cfg.set("force-settings.classes-disabled", disabledClasses.stream().map(Enum::name).toList());
        plugin.saveConfig();
    }

    // ── Public API ─────────────────────────────────────────────────────────

    public boolean isSheepEnabled(SheepType type) { return !disabledSheep.contains(type); }
    public boolean isKitEnabled(PlayerKit kit)     { return !disabledKits.contains(kit); }
    public boolean isClassEnabled(PlayerClass playerClass) { return !disabledClasses.contains(playerClass); }
    public boolean isMapVoteEnabled() { return plugin.getConfig().getBoolean("default-settings.map-vote-enabled", true); }

    // ── Menu opening ───────────────────────────────────────────────────────

    public void open(Player player) { openMain(player); }

    private void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(LangHelper.get(player, "sw.settings-main-title"), NamedTextColor.DARK_AQUA));
        inv.setItem(10, new ItemBuilder(Material.PINK_WOOL)
                .name(Component.text(LangHelper.get(player, "sw.settings-sheep-name"), NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text(LangHelper.get(player, "sw.settings-sheep-lore"), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .noTooltip().build());
        inv.setItem(12, new ItemBuilder(Material.BLAZE_POWDER)
                .name(Component.text(LangHelper.get(player, "sw.settings-classes-name"), NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text(LangHelper.get(player, "sw.settings-classes-lore"), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .noTooltip().build());
        inv.setItem(14, new ItemBuilder(Material.IRON_SWORD)
                .name(Component.text(LangHelper.get(player, "sw.settings-kits-name"), NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text(LangHelper.get(player, "sw.settings-kits-lore"), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .noTooltip().build());
        inv.setItem(16, new ItemBuilder(Material.COMPARATOR)
                .name(Component.text(LangHelper.get(player, "sw.settings-options-name"), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text(LangHelper.get(player, "sw.settings-options-lore"), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .noTooltip().build());

        // Launch / Cancel button
        inv.setItem(22, launchItem(player));

        player.openInventory(inv);
        openMenus.put(player.getUniqueId(), Page.MAIN);
    }

    private ItemStack launchItem(Player player) {
        var state = plugin.getGameManager().getState();
        boolean canStart = state == fr.tropicube.sheepwars.game.GameState.WAITING;
        boolean canCancel = state == fr.tropicube.sheepwars.game.GameState.STARTING;
        if (canStart) {
            return new ItemBuilder(Material.LIME_CONCRETE)
                    .name(Component.text(LangHelper.get(player, "sw.settings-launch-name"), NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false))
                    .lore(Component.text(LangHelper.get(player, "sw.settings-launch-lore"), NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false))
                    .noTooltip().build();
        } else if (canCancel) {
            return new ItemBuilder(Material.RED_CONCRETE)
                    .name(Component.text(LangHelper.get(player, "sw.settings-cancel-launch-name"), NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false))
                    .lore(Component.text(LangHelper.get(player, "sw.settings-cancel-launch-lore"), NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false))
                    .noTooltip().build();
        } else {
            return new ItemBuilder(Material.GRAY_CONCRETE)
                    .name(Component.text(LangHelper.get(player, "sw.settings-no-launch-name"), NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false))
                    .noTooltip().build();
        }
    }

    private void openSheepPage(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(LangHelper.get(player, "sw.settings-sheep-title"), NamedTextColor.LIGHT_PURPLE));
        SheepType[] types = SheepType.values();
        for (int i = 0; i < types.length && i < SHEEP_SLOTS.length; i++)
            inv.setItem(SHEEP_SLOTS[i], sheepToggleItem(player, types[i]));
        inv.setItem(47, dropRatesNavItem(player));
        inv.setItem(49, backItem(player));
        player.openInventory(inv);
        openMenus.put(player.getUniqueId(), Page.SHEEP);
    }

    private void openKitsPage(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(LangHelper.get(player, "sw.settings-kits-title"), NamedTextColor.RED));
        PlayerKit[] dps     = PlayerKit.getKitsForClass(PlayerClass.DPS);
        PlayerKit[] tank    = PlayerKit.getKitsForClass(PlayerClass.TANK);
        PlayerKit[] support = PlayerKit.getKitsForClass(PlayerClass.SUPPORT);
        for (int i = 0; i < dps.length && i < KIT_SLOTS_DPS.length; i++)
            inv.setItem(KIT_SLOTS_DPS[i], kitToggleItem(player, dps[i]));
        for (int i = 0; i < tank.length && i < KIT_SLOTS_TANK.length; i++)
            inv.setItem(KIT_SLOTS_TANK[i], kitToggleItem(player, tank[i]));
        for (int i = 0; i < support.length && i < KIT_SLOTS_SUPPORT.length; i++)
            inv.setItem(KIT_SLOTS_SUPPORT[i], kitToggleItem(player, support[i]));
        inv.setItem(26, backItem(player));
        player.openInventory(inv);
        openMenus.put(player.getUniqueId(), Page.KITS);
    }

    private void openClassesPage(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(LangHelper.get(player, "sw.settings-classes-title"), NamedTextColor.RED));
        // Ignore PlayerClass.NONE et affiche uniquement les classes jouables.
        PlayerClass[] allClasses = PlayerClass.values();
        for (int i = 0; i < CLASS_SLOTS.length && (i + 1) < allClasses.length; i++)
            inv.setItem(CLASS_SLOTS[i], classToggleItem(player, allClasses[i + 1]));
        inv.setItem(22, backItem(player));
        player.openInventory(inv);
        openMenus.put(player.getUniqueId(), Page.CLASSES);
    }

    private void openOptionsPage(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(LangHelper.get(player, "sw.settings-options-title"), NamedTextColor.YELLOW));
        FileConfiguration cfg = plugin.getConfig();

        // Row 1: min-players (0-2) | max-players (4-6)
        if (isConfigurable("min-players")) {
            int min = cfg.getInt("default-settings.min-players", 2);
            inv.setItem(0, minusItem());
            inv.setItem(1, displayItem(Material.OAK_SIGN, LangHelper.get(player, "sw.settings-min-players"), String.valueOf(min)));
            inv.setItem(2, plusItem());
        }
        if (isConfigurable("max-players")) {
            int max = cfg.getInt("default-settings.max-players", 16);
            inv.setItem(4, minusItem());
            inv.setItem(5, displayItem(Material.OAK_SIGN, LangHelper.get(player, "sw.settings-max-players"), String.valueOf(max)));
            inv.setItem(6, plusItem());
        }

        // Row 2: countdown (9-11) | game-duration (13-15)
        if (isConfigurable("countdown")) {
            int countdown = cfg.getInt("default-settings.countdown", 10);
            inv.setItem(9, minusItem());
            inv.setItem(10, displayItem(Material.CLOCK, LangHelper.get(player, "sw.settings-countdown-label"), countdown + "s"));
            inv.setItem(11, plusItem());
        }
        if (isConfigurable("game-duration")) {
            int duration = cfg.getInt("default-settings.game-duration", 600);
            inv.setItem(13, minusItem());
            inv.setItem(14, displayItem(Material.CLOCK, LangHelper.get(player, "sw.settings-duration-label"),
                    (duration / 60) + "m " + (duration % 60) + "s"));
            inv.setItem(15, plusItem());
        }

        // Troisième ligne : délai, kits aléatoires, démarrage, vote et retour.
        if (isConfigurable("sheep-give-delay")) {
            int delay = cfg.getInt("default-settings.sheep-give-delay", 10);
            List<Component> delayLore = new ArrayList<>();
            delayLore.add(Component.text(delay + "s", NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            if (delay < 5) {
                delayLore.add(Component.empty());
                delayLore.add(Component.text(LangHelper.get(player, "sw.settings-apocalypse-lore"), NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false));
            }
            inv.setItem(18, minusItem());
            inv.setItem(19, new ItemBuilder(Material.REPEATER)
                    .name(Component.text(LangHelper.get(player, "sw.settings-sheep-delay-label"), NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false))
                    .lore(delayLore)
                    .noTooltip().build());
            inv.setItem(20, plusItem());
        }
        if (isConfigurable("random-kits")) {
            inv.setItem(22, randomKitsItem(player, cfg.getBoolean("default-settings.random-kits", false)));
        }
        if (isConfigurable("auto-start")) {
            inv.setItem(23, autoStartItem(player, cfg.getBoolean("default-settings.auto-start", true)));
        }
        if (isConfigurable("map-vote-enabled")) {
            inv.setItem(24, mapVoteItem(player, cfg.getBoolean("default-settings.map-vote-enabled", true)));
        }
        inv.setItem(26, backItem(player));

        player.openInventory(inv);
        openMenus.put(player.getUniqueId(), Page.OPTIONS);
    }

    private void openDropRatesPage(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(LangHelper.get(player, "sw.settings-drop-rates-title"), NamedTextColor.LIGHT_PURPLE));
        SheepType[] types = SheepType.values();
        for (int i = 0; i < types.length; i++) {
            int row = i / 3;
            int groupInRow = i % 3;
            int base = row * 9 + groupInRow * 3;
            inv.setItem(base,     minusItem());
            inv.setItem(base + 1, dropRatesItem(player, types[i]));
            inv.setItem(base + 2, plusItem());
        }
        inv.setItem(53, backItem(player));
        player.openInventory(inv);
        openMenus.put(player.getUniqueId(), Page.DROP_RATES);
    }

    // ── Item builders ──────────────────────────────────────────────────────

    private ItemStack sheepToggleItem(Player player, SheepType type) {
        boolean enabled = isSheepEnabled(type);
        Material mat = enabled ? Material.valueOf(type.getWool().name() + "_WOOL") : Material.GRAY_WOOL;
        Component name = enabled
                ? Component.text(type.getDisplayName(), type.getTextColor())
                        .decoration(TextDecoration.ITALIC, false)
                : Component.text(type.getDisplayName(), NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.STRIKETHROUGH, true);
        return new ItemBuilder(mat)
                .name(name)
                .lore(
                    Component.text(type.getDescription(), NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    toggleLine(player, enabled)
                )
                .noTooltip().build();
    }

    private ItemStack dropRatesNavItem(Player player) {
        return new ItemBuilder(Material.COMPARATOR)
                .name(Component.text(LangHelper.get(player, "sw.settings-drop-rates-name"), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text(LangHelper.get(player, "sw.settings-drop-rates-lore"), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .noTooltip().build();
    }

    private ItemStack dropRatesItem(Player player, SheepType type) {
        boolean enabled = isSheepEnabled(type);
        Material mat = enabled ? Material.valueOf(type.getWool().name() + "_WOOL") : Material.GRAY_WOOL;
        int weight = plugin.getConfig().getInt("default-settings.sheep-probabilities." + type.getConfigKey(), 10);
        Component name = enabled
                ? Component.text(type.getDisplayName(), type.getTextColor()).decoration(TextDecoration.ITALIC, false)
                : Component.text(type.getDisplayName(), NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.STRIKETHROUGH, true);
        return new ItemBuilder(mat)
                .name(name)
                .lore(
                    Component.text(type.getDescription(), NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text(LangHelper.get(player, "sw.settings-weight-label", weight), NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)
                )
                .noTooltip().build();
    }

    private ItemStack kitToggleItem(Player player, PlayerKit kit) {
        boolean enabled = isKitEnabled(kit);
        Material mat = enabled ? kit.getIcon() : Material.GRAY_WOOL;
        Component name = enabled
                ? Component.text(kit.getDisplayName(), kit.getColor())
                        .decoration(TextDecoration.ITALIC, false)
                : Component.text(kit.getDisplayName(), NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.STRIKETHROUGH, true);
        return new ItemBuilder(mat)
                .name(name)
                .lore(
                    Component.text(kit.getDescription(), NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    toggleLine(player, enabled)
                )
                .noTooltip().build();
    }

    private ItemStack classToggleItem(Player player, PlayerClass playerClass) {
        boolean enabled = isClassEnabled(playerClass);
        Material mat = enabled ? playerClass.getIcon() : Material.GRAY_WOOL;
        Component name = enabled ? Component.text(playerClass.getDisplayName(), playerClass.getColor())
                                   .decoration(TextDecoration.ITALIC, false)
                : Component.text(playerClass.getDisplayName(), NamedTextColor.DARK_GRAY)
                  .decoration(TextDecoration.ITALIC, false)
                  .decoration(TextDecoration.STRIKETHROUGH, true);

        return new ItemBuilder(mat)
                .name(name)
                .lore(Component.text(playerClass.getDescription(), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        toggleLine(player, enabled)
                )
                .noTooltip().build();
    }

    private ItemStack displayItem(Material material, String label, String value) {
        return new ItemBuilder(material)
                .name(Component.text(label, NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text(value, NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false))
                .noTooltip().build();
    }

    private ItemStack minusItem() {
        return new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name(Component.text("−", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
                .noTooltip().build();
    }

    private ItemStack plusItem() {
        return new ItemBuilder(Material.GREEN_STAINED_GLASS_PANE)
                .name(Component.text("+", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))
                .noTooltip().build();
    }

    private ItemStack randomKitsItem(Player player, boolean enabled) {
        return new ItemBuilder(Material.ENDER_PEARL)
                .name(Component.text(LangHelper.get(player, "sw.settings-random-kits-name"), NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(
                    Component.text(LangHelper.get(player, "sw.settings-random-kits-lore1"), NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text(LangHelper.get(player, "sw.settings-random-kits-lore2"), NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    toggleLine(player, enabled)
                )
                .noTooltip().build();
    }

    private ItemStack autoStartItem(Player player, boolean enabled) {
        return new ItemBuilder(Material.CLOCK)
                .name(Component.text(LangHelper.get(player, "sw.settings-auto-start-name"), NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(
                        Component.text(LangHelper.get(player, "sw.settings-auto-start-lore1"), NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text(LangHelper.get(player, "sw.settings-auto-start-lore2"), NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        toggleLine(player, enabled)
                )
                .noTooltip().build();
    }

    private ItemStack mapVoteItem(Player player, boolean enabled) {
        return new ItemBuilder(Material.FILLED_MAP)
                .name(Component.text(LangHelper.get(player, "sw.settings-map-vote-name"), NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(
                        Component.text(LangHelper.get(player, "sw.settings-map-vote-lore1"), NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text(LangHelper.get(player, "sw.settings-map-vote-lore2"), NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        toggleLine(player, enabled)
                )
                .noTooltip().build();
    }

    private ItemStack backItem(Player player) {
        return new ItemBuilder(Material.ARROW)
                .name(Component.text(LangHelper.get(player, "sw.settings-back"), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .noTooltip().build();
    }

    private Component toggleLine(Player player, boolean enabled) {
        return enabled
                ? Component.text(LangHelper.get(player, "sw.settings-toggle-on"), NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
                : Component.text(LangHelper.get(player, "sw.settings-toggle-off"), NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false);
    }

    // ── Event handlers ─────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        Page page = openMenus.get(uuid);
        if (page == null) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        if (event.getClickedInventory() == null
                || event.getClickedInventory() == player.getInventory()) return;

        int slot = event.getRawSlot();
        switch (page) {
            case MAIN       -> handleMainClick(player, slot);
            case SHEEP      -> handleSheepClick(player, slot);
            case KITS       -> handleKitsClick(player, slot);
            case CLASSES    -> handleClassesClick(player, slot);
            case OPTIONS    -> handleOptionsClick(player, slot);
            case DROP_RATES -> handleDropRatesClick(player, slot, event.getClick());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    // ── Page click handlers ────────────────────────────────────────────────

    private void handleMainClick(Player player, int slot) {
        switch (slot) {
            case 10 -> openSheepPage(player);
            case 12 -> openClassesPage(player);
            case 14 -> openKitsPage(player);
            case 16 -> openOptionsPage(player);
            case 22 -> {
                player.closeInventory();
                var state = plugin.getGameManager().getState();
                if (state == fr.tropicube.sheepwars.game.GameState.WAITING) {
                    if (plugin.getGameManager().getGameMaps().isEmpty()) {
                        player.sendMessage(LangHelper.component(player, "sw.cmd-arena-not-ready"));
                        return;
                    }
                    if (!isMapVoteEnabled()) {
                        var selected = plugin.getGameManager().getSelectedMap();
                        if (selected == null || selected.isNotReady()) {
                            player.sendMessage(LangHelper.component(player, "sw.map-pick-required"));
                            return;
                        }
                    }
                    int min = plugin.getConfig().getInt("default-settings.min-players", 2);
                    int current = plugin.getGameManager().getPlayers().size();
                    if (current < min) {
                        player.sendMessage(LangHelper.component(player, "sw.not-enough-players", current, min));
                        return;
                    }
                    plugin.getGameManager().startCountdown();
                } else if (state == fr.tropicube.sheepwars.game.GameState.STARTING) {
                    plugin.getGameManager().cancelCountdown();
                }
            }
        }
    }

    private void handleSheepClick(Player player, int slot) {
        if (slot == 49) { openMain(player); return; }
        if (slot == 47) { openDropRatesPage(player); return; }
        SheepType[] types = SheepType.values();
        for (int i = 0; i < types.length && i < SHEEP_SLOTS.length; i++) {
            if (SHEEP_SLOTS[i] == slot) {
                if (disabledSheep.contains(types[i])) {
                    disabledSheep.remove(types[i]);
                } else {
                    long enabledCount = Arrays.stream(types).filter(this::isSheepEnabled).count();
                    if (enabledCount <= 1) return;
                    disabledSheep.add(types[i]);
                }
                save();
                plugin.getSheepManager().buildWeightCache();
                openSheepPage(player);
                return;
            }
        }
    }

    private void handleDropRatesClick(Player player, int slot, ClickType click) {
        if (slot == 53) { openSheepPage(player); return; }

        int row = slot / 9;
        int col = slot % 9;
        int colInGroup = col % 3;
        int groupInRow = col / 3;
        int sheepIndex = row * 3 + groupInRow;

        if (colInGroup == 1) return; // display item clicked

        SheepType[] types = SheepType.values();
        if (sheepIndex >= types.length) return;

        SheepType type = types[sheepIndex];
        String cfgKey = "default-settings.sheep-probabilities." + type.getConfigKey();
        int current = plugin.getConfig().getInt(cfgKey, 10);
        int delta = click.isRightClick() ? 5 : 1;

        if (colInGroup == 0) plugin.getConfig().set(cfgKey, Math.max(0, current - delta));
        else                 plugin.getConfig().set(cfgKey, Math.min(99, current + delta));

        plugin.saveConfig();
        plugin.getSheepManager().buildWeightCache();
        openDropRatesPage(player);
    }

    private void handleKitsClick(Player player, int slot) {
        if (slot == 26) { openMain(player); return; }
        PlayerKit kit = slotToKit(slot);
        if (kit != null) {
            if (disabledKits.contains(kit)) disabledKits.remove(kit);
            else disabledKits.add(kit);
            save();
            openKitsPage(player);
        }
    }

    private void handleClassesClick(Player player, int slot) {
        if (slot == 22) { openMain(player); return; }
        PlayerClass playerClass = slotToClass(slot);
        if (playerClass == null) return;  // clicked on filler or an unrecognized slot

        if (disabledClasses.contains(playerClass)) {
            disabledClasses.remove(playerClass);
            classToKits(playerClass).forEach(disabledKits::remove);
        } else {
            disabledClasses.add(playerClass);
            disabledKits.addAll(classToKits(playerClass));
        }
        save();
        openClassesPage(player);
    }

    private void handleOptionsClick(Player player, int slot) {
        if (slot == 26) { openMain(player); return; }
        FileConfiguration cfg = plugin.getConfig();
        int min       = cfg.getInt("default-settings.min-players", 2);
        int max       = cfg.getInt("default-settings.max-players", 16);
        int countdown = cfg.getInt("default-settings.countdown", 10);
        int duration  = cfg.getInt("default-settings.game-duration", 600);
        int delay     = cfg.getInt("default-settings.sheep-give-delay", 10);
        switch (slot) {
            case 0  -> cfg.set("default-settings.min-players", Math.max(1, min - 1));
            case 2  -> cfg.set("default-settings.min-players", Math.min(max, min + 1));
            case 4  -> cfg.set("default-settings.max-players", Math.max(min, max - 1));
            case 6  -> cfg.set("default-settings.max-players", Math.min(16, max + 1));
            case 9  -> cfg.set("default-settings.countdown", Math.max(5, countdown - 5));
            case 11 -> cfg.set("default-settings.countdown", Math.min(300, countdown + 5));
            case 13 -> cfg.set("default-settings.game-duration", Math.max(60, duration - 30));
            case 15 -> cfg.set("default-settings.game-duration", Math.min(3600, duration + 30));
            case 18 -> cfg.set("default-settings.sheep-give-delay", Math.max(1, delay - 1));
            case 20 -> cfg.set("default-settings.sheep-give-delay", Math.min(120, delay + 1));
            case 22 -> cfg.set("default-settings.random-kits", !cfg.getBoolean("default-settings.random-kits", false));
            case 23 -> {
                cfg.set("default-settings.auto-start", !cfg.getBoolean("default-settings.auto-start", true));
                plugin.getGameManager().evaluateAutoStart();
            }
            case 24 -> {
                boolean next = !cfg.getBoolean("default-settings.map-vote-enabled", true);
                cfg.set("default-settings.map-vote-enabled", next);
                if (!next) plugin.getMapVoteMenu().reset();
            }
            default -> { return; }
        }
        plugin.saveConfig();
        openOptionsPage(player);
    }

    private boolean isConfigurable(String shortKey) {
        return plugin.getConfig().contains("default-settings." + shortKey);
    }

    // ── Slot mapping ───────────────────────────────────────────────────────

    private PlayerKit slotToKit(int slot) {
        PlayerKit[] dps     = PlayerKit.getKitsForClass(PlayerClass.DPS);
        PlayerKit[] tank    = PlayerKit.getKitsForClass(PlayerClass.TANK);
        PlayerKit[] support = PlayerKit.getKitsForClass(PlayerClass.SUPPORT);
        for (int i = 0; i < dps.length && i < KIT_SLOTS_DPS.length; i++)
            if (KIT_SLOTS_DPS[i] == slot) return dps[i];
        for (int i = 0; i < tank.length && i < KIT_SLOTS_TANK.length; i++)
            if (KIT_SLOTS_TANK[i] == slot) return tank[i];
        for (int i = 0; i < support.length && i < KIT_SLOTS_SUPPORT.length; i++)
            if (KIT_SLOTS_SUPPORT[i] == slot) return support[i];
        return null;
    }

    private PlayerClass slotToClass(int slot) {
        PlayerClass[] allClasses = PlayerClass.values();
        for (int i = 0; i < CLASS_SLOTS.length && (i + 1) < allClasses.length; i++)
            if (CLASS_SLOTS[i] == slot) return allClasses[i + 1];
        return null;
    }

    // ── Class to Kits mapping ───────────────────────────────────────────────────────

    private List<PlayerKit> classToKits(PlayerClass playerClass) {
        List<PlayerKit> playerKits = new ArrayList<>();

        for (PlayerKit kit : PlayerKit.values()) {
            if(kit.getPlayerClass() == playerClass) playerKits.add(kit);
        }

        return playerKits;
    }
}
