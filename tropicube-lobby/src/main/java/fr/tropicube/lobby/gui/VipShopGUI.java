package fr.tropicube.lobby.gui;

import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.ItemBuilder;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Menu boutique VIP.
 *
 * Les entrées (grade, material, prix, nom affiché) sont lues depuis la section
 * {@code vip-shop.entries} du config.yml du lobby.
 * Les avantages sont lus dans les fichiers de langue :
 *   {@code lobby.vip-perks-<grade-key-lowercase-with-dashes>}
 */
public class VipShopGUI {

    public static final int CLOSE_SLOT = 40;

    private static final int[] GRADE_SLOTS = {11, 22, 33};
    private static final int[] INFO_SLOTS  = {12, 23, 34};

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override public @NonNull Inventory getInventory() { return inventory; }
        private void setInventory(Inventory inventory)     { this.inventory = inventory; }
    }

    public static Inventory build(Player player, double balance, String currentGrade) {
        int size = 45;
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, size, LangHelper.component(player, "lobby.vip-shop-title"));
        holder.setInventory(inv);

        for (int i = 0; i < size; i++)
            inv.setItem(i, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

        inv.setItem(4, new ItemBuilder(Material.GOLD_INGOT)
                .name(LangHelper.get(player, "lobby.vip-banner-name"))
                .lore(LangHelper.get(player, "lobby.vip-banner-lore1"),
                      LangHelper.get(player, "lobby.vip-banner-lore2"),
                      "",
                      LangHelper.get(player, "lobby.vip-banner-balance", formatCoins((int) balance)))
                .glow().build());

        List<ShopEntry> entries = loadEntries(player);

        for (int i = 0; i < entries.size() && i < GRADE_SLOTS.length; i++) {
            ShopEntry entry = entries.get(i);
            boolean owned = isGradeOwned(currentGrade, entry.gradeKey());

            String actionLore = owned
                    ? LangHelper.get(player, "lobby.vip-grade-owned")
                    : (balance >= entry.price()
                            ? LangHelper.get(player, "lobby.vip-grade-buy")
                            : LangHelper.get(player, "lobby.vip-grade-no-funds"));

            ItemBuilder ib = new ItemBuilder(entry.material())
                    .name(entry.displayName() + " <gray>- <yellow>" + formatCoins(entry.price()) + " <gold>⬡")
                    .lore(actionLore);
            if (owned) ib.glow();
            inv.setItem(GRADE_SLOTS[i], ib.build());

            inv.setItem(INFO_SLOTS[i], new ItemBuilder(Material.PAPER)
                    .name(LangHelper.get(player, "lobby.vip-advantages-name", entry.displayName()))
                    .lore(entry.advantages())
                    .build());
        }

        inv.setItem(CLOSE_SLOT, ItemBuilder.closeButton(player));

        inv.setItem(39, new ItemBuilder(Material.MAP)
                .name(LangHelper.get(player, "lobby.vip-online-shop-name"))
                .lore(LangHelper.get(player, "lobby.vip-online-shop-lore1"),
                      LangHelper.get(player, "lobby.vip-online-shop-lore2"),
                      LangHelper.get(player, "lobby.vip-online-shop-lore3"))
                .build());

        return inv;
    }

    // Métadonnées consultées par le gestionnaire de clics.

    /** Returns the grade key at the given slot, or null if not a grade slot. */
    public static String getEntryForSlot(int slot) {
        TropicubeLobby lobby = TropicubeLobby.getInstance();
        if (lobby == null) return hardcodedKeyForSlot(slot);
        List<Map<?, ?>> raw = lobby.getConfig().getMapList("vip-shop.entries");
        for (int i = 0; i < GRADE_SLOTS.length && i < raw.size(); i++) {
            if (GRADE_SLOTS[i] == slot) return (String) raw.get(i).get("grade-key");
        }
        return null;
    }

    /** Returns the MiniMessage display name for a grade key, used in purchase confirmation messages. */
    public static String getDisplayNameForGrade(String gradeKey) {
        TropicubeLobby lobby = TropicubeLobby.getInstance();
        if (lobby == null) return gradeKey;
        for (Map<?, ?> m : lobby.getConfig().getMapList("vip-shop.entries")) {
            if (gradeKey.equalsIgnoreCase((String) m.get("grade-key"))) {
                String dn = (String) m.get("display-name");
                return dn != null ? dn : gradeKey;
            }
        }
        return gradeKey;
    }

    public static int getPriceForGrade(String gradeKey) {
        TropicubeLobby lobby = TropicubeLobby.getInstance();
        if (lobby == null) return -1;
        for (Map<?, ?> m : lobby.getConfig().getMapList("vip-shop.entries")) {
            if (gradeKey.equalsIgnoreCase((String) m.get("grade-key"))) {
                Number p = (Number) m.get("price");
                return p != null ? p.intValue() : -1;
            }
        }
        return -1;
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private static List<ShopEntry> loadEntries(Player player) {
        TropicubeLobby lobby = TropicubeLobby.getInstance();
        if (lobby == null) return Collections.emptyList();

        List<Map<?, ?>> raw = lobby.getConfig().getMapList("vip-shop.entries");
        List<ShopEntry> result = new ArrayList<>();
        for (Map<?, ?> m : raw) {
            String gradeKey     = (String) m.get("grade-key");
            String materialName = (String) m.get("material");
            String displayName  = (String) m.get("display-name");
            Number price        = (Number) m.get("price");
            if (gradeKey == null || price == null) continue;

            Material material = Material.PAPER;
            if (materialName != null) {
                try { material = Material.valueOf(materialName.toUpperCase()); }
                catch (IllegalArgumentException _) {}
            }

            String perksKey = "lobby.vip-perks-" + gradeKey.toLowerCase().replace('_', '-');
            List<String> advantages = LangHelper.getList(player, perksKey);

            result.add(new ShopEntry(
                    gradeKey,
                    material,
                    displayName != null ? displayName : gradeKey,
                    price.intValue(),
                    advantages
            ));
        }
        return result;
    }

    public static boolean isGradeOwned(String currentGrade, String targetGrade) {
        // Uses DB-consistent grade names (VIP_PLUS, not VIP+).
        List<String> order = List.of("JOUEUR", "VIP", "VIP_PLUS", "PREMIUM",
                                     "HELPER", "MODERATEUR", "ADMIN", "OWNER");
        int cur = order.indexOf(currentGrade.toUpperCase());
        int tgt = order.indexOf(targetGrade.toUpperCase());
        return cur >= 0 && tgt >= 0 && cur >= tgt;
    }

    public static String formatCoins(int amount) {
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000)     return String.format("%.1fK", amount / 1_000.0);
        return String.valueOf(amount);
    }

    private static String hardcodedKeyForSlot(int slot) {
        // Repli utilisé si l'instance Lobby n'est pas disponible, notamment pendant les tests.
        return switch (slot) {
            case 11 -> "VIP";
            case 22 -> "VIP_PLUS";
            case 33 -> "PREMIUM";
            default -> null;
        };
    }

    public record ShopEntry(String gradeKey, Material material, String displayName,
                            int price, List<String> advantages) {}
}
