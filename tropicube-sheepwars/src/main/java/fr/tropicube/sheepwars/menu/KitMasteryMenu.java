package fr.tropicube.sheepwars.menu;

import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.sheepwars.competitive.KitMasteryBranch;
import fr.tropicube.sheepwars.player.PlayerKit;
import fr.tropicube.sheepwars.util.ItemBuilder;
import fr.tropicube.sheepwars.util.LangHelper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Two side-by-side, exclusive and reversible mastery paths for the selected kit. */
public final class KitMasteryMenu implements Listener {
    private final TropicubeSheepwars plugin;
    private final NamespacedKey branchKey;
    private final Set<UUID> viewers = new HashSet<>();

    public KitMasteryMenu(TropicubeSheepwars plugin) {
        this.plugin = plugin;
        this.branchKey = new NamespacedKey(plugin, "mastery_branch");
    }

    public void open(Player player) {
        PlayerKit kit = plugin.getPlayerDataManager().getKit(player.getUniqueId());
        if (kit == PlayerKit.NONE) {
            player.sendMessage(LangHelper.component(player, "sw.mastery-kit-required"));
            return;
        }
        var view = plugin.getProgressionService().mastery(player.getUniqueId(), kit);
        var inventory = Bukkit.createInventory(null, 9, LangHelper.component(player, "sw.mastery-title",
                view.level(), view.experience()));
        inventory.setItem(3, branchItem(player, kit, KitMasteryBranch.BRANCH_A, Material.LIME_DYE, view));
        inventory.setItem(5, branchItem(player, kit, KitMasteryBranch.BRANCH_B, Material.CYAN_DYE, view));
        player.openInventory(inventory);
        viewers.add(player.getUniqueId());
    }

    private org.bukkit.inventory.ItemStack branchItem(Player player, PlayerKit kit, KitMasteryBranch branch,
                                                       Material material,
                                                       fr.tropicube.sheepwars.competitive.SheepWarsProgressionService.MasteryView view) {
        String suffix = branch == KitMasteryBranch.BRANCH_A ? "a" : "b";
        var definition = plugin.getKitMasteryCatalog().branch(kit, branch);
        boolean unlocked = view.level() >= plugin.getKitMasteryCatalog().unlockLevel();
        return new ItemBuilder(material)
                .name(LangHelper.component(player, "sw.mastery-branch-" + suffix + "-name"))
                .lore(LangHelper.component(player, definition.descriptionKey()),
                        LangHelper.component(player, unlocked ? "sw.mastery-branch-lore" : "sw.mastery-locked",
                                plugin.getKitMasteryCatalog().unlockLevel()),
                        LangHelper.component(player, view.branch() == branch ? "sw.mastery-selected" : "sw.mastery-selectable"))
                .persistentData(branchKey, PersistentDataType.STRING, branch.name())
                .build();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !viewers.contains(player.getUniqueId())) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;
        String raw = event.getCurrentItem().getItemMeta().getPersistentDataContainer()
                .get(branchKey, PersistentDataType.STRING);
        if (raw == null) return;
        PlayerKit kit = plugin.getPlayerDataManager().getKit(player.getUniqueId());
        if (kit == PlayerKit.NONE) return;
        KitMasteryBranch branch = KitMasteryBranch.valueOf(raw);
        plugin.getProgressionService().selectBranch(player.getUniqueId(), kit, branch).thenAccept(result ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (result == fr.tropicube.sheepwars.competitive.SheepWarsProgressionService.BranchSelection.LOCKED) {
                        player.sendMessage(LangHelper.component(player, "sw.mastery-locked",
                                plugin.getKitMasteryCatalog().unlockLevel()));
                    } else {
                        player.sendMessage(LangHelper.component(player, "sw.mastery-branch-selected", branch.name()));
                        open(player);
                    }
                }));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) { viewers.remove(event.getPlayer().getUniqueId()); }
}
