package fr.tropicube.sheepwars.menu;

import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.core.menu.NetworkMenuStyle;
import fr.tropicube.sheepwars.player.GamePlayer;
import fr.tropicube.sheepwars.util.LangHelper;
import fr.tropicube.sheepwars.player.PlayerClass;
import fr.tropicube.sheepwars.player.PlayerKit;
import fr.tropicube.sheepwars.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** Allows the player to select their class and kit before departure. */
public class ClassKitSelectionMenu implements Listener {

    private static final int[] CLASS_SLOTS = {1, 3, 5, 7};
    private static final int[] KIT_SLOTS   = {2, 4, 6};

    private final TropicubeSheepwars plugin;
    public final NamespacedKey classMenuKey;

    private final Set<UUID> classMenuOpen = new HashSet<>();
    private final Map<UUID, PlayerClass> kitMenuOpen = new HashMap<>();

    public ClassKitSelectionMenu(TropicubeSheepwars plugin) {
        this.plugin = plugin;
        this.classMenuKey = new NamespacedKey(plugin, "class_menu_item");
    }

    // Public item placed in the player's quickbar

    public ItemStack createSelectorItem(Player player) {
        return new ItemBuilder(Material.COMPASS)
                .name(LangHelper.component(player, "sw.selector-item-name")
                        .decoration(TextDecoration.ITALIC, false))
                .lore(LangHelper.component(player, "sw.selector-item-lore")
                        .decoration(TextDecoration.ITALIC, false))
                .persistentData(classMenuKey, PersistentDataType.BYTE, (byte) 1)
                .build();
    }

    public boolean isSelectorItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(classMenuKey, PersistentDataType.BYTE);
    }

    // ── Menu opening ───────────────────────────────────────────────────────

    public void openClassMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, LangHelper.menuSize("class-selector"),
                LangHelper.menuTitle(player, "class-selector"));
        NetworkMenuStyle.applyFrame(inv, player, LangHelper.menuFrame("class-selector"));

        GamePlayer gp = plugin.getGameManager().getPlayer(player);
        PlayerClass currentClass = gp != null ? gp.getPlayerClass() : plugin.getPlayerDataManager().getKit(player.getUniqueId()).getPlayerClass();

        PlayerClass[] classes = PlayerClass.values();
        for (int i = 0; i < classes.length; i++) {
            boolean enabled = plugin.getGameSettingsMenu().isClassEnabled(classes[i]);
            inv.setItem(CLASS_SLOTS[i], classItem(classes[i], classes[i] == currentClass, enabled, player.getUniqueId()));
        }

        // openInventory closes the previous menu and clears the associated tracking;
        // the recording of the new menu must therefore follow this call.
        player.openInventory(inv);
        classMenuOpen.add(player.getUniqueId());
    }

    private void openKitMenu(Player player, PlayerClass playerClass) {
        Inventory inv = Bukkit.createInventory(null, LangHelper.menuSize("kit-selector"),
                LangHelper.menuTitle(player, "kit-selector", className(player, playerClass)));
        NetworkMenuStyle.applyFrame(inv, player, LangHelper.menuFrame("kit-selector"));

        GamePlayer gp = plugin.getGameManager().getPlayer(player);
        PlayerKit currentKit = gp != null
                ? gp.getKit()
                : plugin.getPlayerDataManager().getKit(player.getUniqueId());

        PlayerKit[] kits = PlayerKit.getKitsForClass(playerClass);
        for (int i = 0; i < kits.length; i++) {
            boolean enabled = plugin.getGameSettingsMenu().isKitEnabled(kits[i]);
            inv.setItem(KIT_SLOTS[i], kitItem(kits[i], kits[i] == currentKit, enabled, player.getUniqueId()));
        }

        // Same order constraint: saves tracking after openInventory.
        player.openInventory(inv);
        kitMenuOpen.put(player.getUniqueId(), playerClass);
    }

    // ── Item builders ──────────────────────────────────────────────────────

    private ItemStack classItem(PlayerClass pc, boolean selected, boolean enabled, UUID uuid) {
        Component name;
        Material icon;

        if (!enabled) {
            icon = Material.GRAY_WOOL;
            name = Component.text(className(uuid, pc), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.STRIKETHROUGH, true);
        } else {
            icon = pc.getIcon();
            name = Component.text(className(uuid, pc), pc.getColor())
                    .decoration(TextDecoration.ITALIC, false);
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(classDescription(uuid, pc), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        if (!enabled) {
            lore.add(LangHelper.component(uuid, "sw.label-disabled")
                    .decoration(TextDecoration.ITALIC, false));
        } else if (selected) {
            lore.add(LangHelper.component(uuid, "sw.label-selected")
                    .decoration(TextDecoration.ITALIC, false));
        }

        return new ItemBuilder(icon)
                .name(name)
                .lore(lore)
                .noTooltip()
                .build();
    }

    private ItemStack kitItem(PlayerKit kit, boolean selected, boolean enabled, UUID uuid) {
        Component name;
        Material icon;

        if (!enabled) {
            icon = Material.GRAY_WOOL;
            name = Component.text(kitName(uuid, kit), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.STRIKETHROUGH, true);
        } else {
            icon = kit.getIcon();
            name = Component.text(kitName(uuid, kit), kit.getColor())
                    .decoration(TextDecoration.ITALIC, false);
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(kitDescription(uuid, kit), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        if (!enabled) {
            lore.add(LangHelper.component(uuid, "sw.label-disabled")
                    .decoration(TextDecoration.ITALIC, false));
        } else if (selected) {
            lore.add(LangHelper.component(uuid, "sw.label-selected")
                    .decoration(TextDecoration.ITALIC, false));
        }

        return new ItemBuilder(icon)
                .name(name)
                .lore(lore)
                .noTooltip()
                .build();
    }

    // ── Event handlers ─────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        if (!classMenuOpen.contains(uuid) && !kitMenuOpen.containsKey(uuid)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        if (event.getClickedInventory() == null
                || event.getClickedInventory() == player.getInventory()) return;

        int slot = event.getRawSlot();

        if (classMenuOpen.contains(uuid)) {
            PlayerClass selected = slotToClass(slot);
            if (selected == null) return;
            if (selected == PlayerClass.NONE) {
                GamePlayer gp = plugin.getGameManager().getPlayer(player);
                if (gp != null) {
                    gp.setKit(PlayerKit.NONE);
                    gp.setPlayerClass(PlayerClass.NONE);
                }
                plugin.getPlayerDataManager().updateKit(player.getUniqueId(), PlayerKit.NONE);
                player.sendMessage(LangHelper.component(player, "sw.kit-selected", kitName(uuid, PlayerKit.NONE)));
                player.closeInventory();
                return;
            }
            if (!plugin.getGameSettingsMenu().isClassEnabled(selected)) {
                player.sendMessage(LangHelper.component(player, "sw.kit-disabled"));
                return;
            }
            openKitMenu(player, selected);
            return;
        }

        if (kitMenuOpen.containsKey(uuid)) {
            PlayerClass pc = kitMenuOpen.get(uuid);
            PlayerKit[] kits = PlayerKit.getKitsForClass(pc);
            PlayerKit selected = slotToKit(slot, kits);
            if (selected != null) {
                if (!plugin.getGameSettingsMenu().isClassEnabled(pc)
                        || !plugin.getGameSettingsMenu().isKitEnabled(selected)) {
                    player.sendMessage(LangHelper.component(player, "sw.kit-disabled"));
                    return;
                }
                GamePlayer gp = plugin.getGameManager().getPlayer(player);
                if (!plugin.getGameManager().canSelectRole(gp, pc)) {
                    player.sendMessage(LangHelper.component(player, "sw.role-limit-reached"));
                    return;
                }
                if (gp != null) {
                    gp.setKit(selected);
                    gp.setPlayerClass(pc);
                }
                plugin.getPlayerDataManager().updateKit(player.getUniqueId(), selected);
                player.sendMessage(LangHelper.component(player, "sw.kit-selected", kitName(uuid, selected)));
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        classMenuOpen.remove(uuid);
        kitMenuOpen.remove(uuid);
    }

    // ── Slot mapping ───────────────────────────────────────────────────────

    private PlayerClass slotToClass(int slot) {
        PlayerClass[] classes = PlayerClass.values();
        for (int i = 0; i < CLASS_SLOTS.length && i < classes.length; i++) {
            if (CLASS_SLOTS[i] == slot) return classes[i];
        }
        return null;
    }

    private PlayerKit slotToKit(int slot, PlayerKit[] kits) {
        for (int i = 0; i < KIT_SLOTS.length && i < kits.length; i++) {
            if (KIT_SLOTS[i] == slot) return kits[i];
        }
        return null;
    }

    private String className(Player player, PlayerClass playerClass) {
        return className(player.getUniqueId(), playerClass);
    }

    private String className(UUID uuid, PlayerClass playerClass) {
        return LangHelper.get(uuid, "sw.catalog-class-" + playerClass.name().toLowerCase(Locale.ROOT) + "-name");
    }

    private String classDescription(UUID uuid, PlayerClass playerClass) {
        return LangHelper.get(uuid, "sw.catalog-class-" + playerClass.name().toLowerCase(Locale.ROOT) + "-description");
    }

    private String kitName(UUID uuid, PlayerKit kit) {
        return LangHelper.get(uuid, "sw.catalog-kit-" + kit.name().toLowerCase(Locale.ROOT) + "-name");
    }

    private String kitDescription(UUID uuid, PlayerKit kit) {
        return LangHelper.get(uuid, "sw.catalog-kit-" + kit.name().toLowerCase(Locale.ROOT) + "-description");
    }
}
