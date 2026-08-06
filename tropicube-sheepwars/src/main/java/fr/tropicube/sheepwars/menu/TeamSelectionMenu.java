package fr.tropicube.sheepwars.menu;

import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.sheepwars.player.GamePlayer;
import fr.tropicube.sheepwars.game.GameTeam;
import fr.tropicube.sheepwars.util.ItemBuilder;
import fr.tropicube.sheepwars.util.LangHelper;
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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Affiche les équipes disponibles et traite le choix du joueur. */
public class TeamSelectionMenu implements Listener {

    private static final int SLOT_RED  = 11;
    private static final int SLOT_BLUE = 15;

    private final TropicubeSheepwars plugin;
    public final NamespacedKey teamMenuKey;
    private final Set<UUID> openMenus = new HashSet<>();

    public TeamSelectionMenu(TropicubeSheepwars plugin) {
        this.plugin = plugin;
        this.teamMenuKey = new NamespacedKey(plugin, "team_menu_item");
    }

    // ── Hotbar item ──────────────────────────────────────────────────────────

    public ItemStack createSelectorItem(Player player) {
        GamePlayer gp = plugin.getGameManager().getPlayer(player);
        GameTeam team = gp != null ? gp.getTeam() : null;
        Material wool = team != null
                ? Material.valueOf(team.getDyeColor().name() + "_WOOL")
                : Material.WHITE_WOOL;

        return new ItemBuilder(wool)
                .name(LangHelper.component(player, "sw.team-selector-name")
                        .decoration(TextDecoration.ITALIC, false))
                .lore(LangHelper.component(player, "sw.team-selector-lore")
                        .decoration(TextDecoration.ITALIC, false))
                .persistentData(teamMenuKey, PersistentDataType.BYTE, (byte) 1)
                .build();
    }

    public boolean isSelectorItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(teamMenuKey, PersistentDataType.BYTE);
    }

    // ── Menu ────────────────────────────────────────────────────────────────

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27,
                LangHelper.component(player, "sw.team-menu-title"));

        int redCount  = plugin.getGameManager().getTeamPlayers(GameTeam.RED).size();
        int blueCount = plugin.getGameManager().getTeamPlayers(GameTeam.BLUE).size();
        GamePlayer gp = plugin.getGameManager().getPlayer(player);
        GameTeam currentTeam = gp != null ? gp.getTeam() : null;

        inv.setItem(SLOT_RED,  teamItem(player, GameTeam.RED,  redCount,  currentTeam == GameTeam.RED));
        inv.setItem(SLOT_BLUE, teamItem(player, GameTeam.BLUE, blueCount, currentTeam == GameTeam.BLUE));

        player.openInventory(inv);
        openMenus.add(player.getUniqueId());
    }

    private ItemStack teamItem(Player player, GameTeam team, int count, boolean selected) {
        Material wool = Material.valueOf(team.getDyeColor().name() + "_WOOL");
        Component name = Component.text(team.getDisplayName(), team.getColor())
                .decoration(TextDecoration.ITALIC, false);

        Component memberLine = Component.text(
                LangHelper.get(player, "sw.team-members", count),
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);

        Component statusLine = selected
                ? LangHelper.component(player, "sw.label-selected").decoration(TextDecoration.ITALIC, false)
                : LangHelper.component(player, "sw.team-click-join").decoration(TextDecoration.ITALIC, false);

        return new ItemBuilder(wool)
                .name(name)
                .lore(memberLine, Component.empty(), statusLine)
                .noTooltip()
                .build();
    }

    // ── Events ──────────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openMenus.contains(player.getUniqueId())) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() == player.getInventory()) return;

        int slot = event.getRawSlot();
        GameTeam chosen = switch (slot) {
            case SLOT_RED  -> GameTeam.RED;
            case SLOT_BLUE -> GameTeam.BLUE;
            default -> null;
        };
        if (chosen == null) return;

        GamePlayer gp = plugin.getGameManager().getPlayer(player);
        if (gp == null) return;

        int blueCount = plugin.getGameManager().getTeamPlayers(GameTeam.BLUE).size();
        int redCount = plugin.getGameManager().getTeamPlayers(GameTeam.RED).size();

        if (gp.getTeam() == chosen) {
            player.closeInventory();
            return;
        }

        if (gp.getTeam() == GameTeam.BLUE) blueCount--;
        if (gp.getTeam() == GameTeam.RED) redCount--;
        if (chosen == GameTeam.BLUE) blueCount++;
        if (chosen == GameTeam.RED) redCount++;

        if (Math.abs(blueCount - redCount) <= 1) {
            gp.setTeam(chosen);
            player.sendMessage(LangHelper.component(player, "sw.team-joined", chosen.getDisplayName()));
            // Actualise l'objet de sélection d'équipe dans la barre rapide.
            player.getInventory().setItem(0, createSelectorItem(player));
        } else {
            player.sendMessage(LangHelper.component(player, "sw.team-unbalanced", chosen.getDisplayName()));
        }

        player.closeInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }
}
