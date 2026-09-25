package fr.tropicube.lobby.gui;

import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.core.menu.NetworkMenuStyle;
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
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * VIP shop menu.
 *
 * The entries (grade, material, price, displayed name) are read from the section
 * {@code vip-shop.entries} section of the lobby config.yml.
 * The advantages are read in the language files:
 *   {@code lobby.vip-perks-<grade-key-lowercase-with-dashes>}
 */
public class VipShopGUI {

    public static final int HOME_GRADES_SLOT = 11;
    public static final int HOME_COSMETICS_SLOT = 15;
    public static final int HOME_CLOSE_SLOT = 26;
    public static final int GRADES_BACK_SLOT = 45;
    public static final int GRADES_CLOSE_SLOT = 53;
    public static final int CONFIRM_SLOT = 11;
    public static final int CONFIRM_CANCEL_SLOT = 15;
    public static final int CONFIRM_CLOSE_SLOT = 26;
    private static final int[] GRADE_SLOTS = {11, 13, 15};
    private static final int[] ACTIVE_SLOTS  = {20, 22, 24};
    private static final int[] SOON_SLOTS  = {29, 31, 33};

    public static final class Holder implements InventoryHolder {
        private final View view;
        private final String gradeKey;
        private final int price;
        private Inventory inventory;
        private Holder(View view) { this(view, null, 0); }
        private Holder(View view, String gradeKey, int price) {
            this.view = view;
            this.gradeKey = gradeKey;
            this.price = price;
        }
        public View view() { return view; }
        public String gradeKey() { return gradeKey; }
        public int price() { return price; }
        @Override public @NonNull Inventory getInventory() { return inventory; }
        private void setInventory(Inventory inventory)     { this.inventory = inventory; }
    }

    public static Inventory build(Player player, double balance, String currentGrade) {
        Holder holder = new Holder(View.HOME);
        Inventory inv = Bukkit.createInventory(holder, LangHelper.menuSize("vip-shop-home"),
                LangHelper.menuTitle(player, "vip-shop-home"));
        holder.setInventory(inv);
        NetworkMenuStyle.applyFrame(inv, player, LangHelper.menuFrame("vip-shop-home"));
        inv.setItem(4, new ItemBuilder(Material.GOLD_INGOT)
                .name(LangHelper.get(player, "lobby.shop-home-name"))
                .lore(LangHelper.get(player, "lobby.vip-banner-balance", formatCoins(balance))).glow(player).build());
        inv.setItem(HOME_GRADES_SLOT, new ItemBuilder(Material.NAME_TAG)
                .name(LangHelper.get(player, "lobby.shop-grades-tab"))
                .lore(LangHelper.get(player, "lobby.shop-grades-tab-lore")).build());
        inv.setItem(HOME_COSMETICS_SLOT, new ItemBuilder(Material.FEATHER).name(LangHelper.get(player, "cosmetics.wardrobe"))
                .lore(LangHelper.get(player, "cosmetics.wardrobe-action")).build());
        inv.setItem(HOME_CLOSE_SLOT, ItemBuilder.closeButton(player));
        return inv;
    }

    public static Inventory buildGrades(Player player, double balance, String currentGrade) {
        Holder holder = new Holder(View.GRADES);
        Inventory inv = Bukkit.createInventory(holder, LangHelper.menuSize("vip-shop-grades"),
                LangHelper.menuTitle(player, "vip-shop-grades"));
        holder.setInventory(inv);

        NetworkMenuStyle.applyFrame(inv, player, LangHelper.menuFrame("vip-shop-grades"));

        inv.setItem(4, new ItemBuilder(Material.GOLD_INGOT)
                .name(LangHelper.get(player, "lobby.vip-banner-name"))
                .lore(LangHelper.get(player, "lobby.vip-banner-lore"),
                      "",
                      LangHelper.get(player, "lobby.vip-banner-balance", formatCoins(balance)))
                .glow(player).build());

        List<ShopEntry> entries = loadEntries(player);

        for (int i = 0; i < entries.size() && i < GRADE_SLOTS.length; i++) {
            ShopEntry entry = entries.get(i);
            boolean owned = isGradeOwned(currentGrade, entry.gradeKey());

            int upgradePrice = getUpgradePrice(currentGrade, entry.gradeKey());
            String actionLore = owned
                    ? LangHelper.get(player, "lobby.vip-grade-owned")
                    : (balance >= upgradePrice
                            ? LangHelper.get(player, "lobby.vip-grade-buy")
                            : LangHelper.get(player, "lobby.vip-grade-no-funds"));

            ItemBuilder ib = new ItemBuilder(entry.material())
                    .name(entry.displayName() + " <gray>- <yellow>" + formatCoins(upgradePrice) + " <gold>⬡")
                    .lore(actionLore);
            if (owned) ib.glow(player);
            inv.setItem(GRADE_SLOTS[i], ib.build());

            String normalized = entry.gradeKey().toLowerCase().replace('_', '-');
            inv.setItem(ACTIVE_SLOTS[i], new ItemBuilder(Material.LIME_DYE)
                    .name(LangHelper.get(player, "lobby.shop-active-name"))
                    .lore(LangHelper.getList(player, "lobby.shop-active-" + normalized))
                    .build());
            inv.setItem(SOON_SLOTS[i], new ItemBuilder(Material.CLOCK)
                    .name(LangHelper.get(player, "lobby.shop-soon-name"))
                    .lore(LangHelper.getList(player, "lobby.shop-soon-" + normalized))
                    .build());
        }

        inv.setItem(GRADES_BACK_SLOT, ItemBuilder.backButton(player));
        inv.setItem(GRADES_CLOSE_SLOT, ItemBuilder.closeButton(player));

        return inv;
    }

    /** Builds a focused, explicit purchase checkpoint before any balance mutation. */
    public static Inventory buildConfirmation(Player player, String gradeKey, int price) {
        Holder holder = new Holder(View.CONFIRM, gradeKey, price);
        Inventory inventory = Bukkit.createInventory(holder, LangHelper.menuSize("vip-shop-confirm"),
                LangHelper.menuTitle(player, "vip-shop-confirm"));
        holder.setInventory(inventory);
        NetworkMenuStyle.applyFrame(inventory, player, LangHelper.menuFrame("vip-shop-confirm"));
        inventory.setItem(13, new ItemBuilder(Material.GOLD_INGOT)
                .name(LangHelper.get(player, "lobby.vip-confirm-details", getDisplayNameForGrade(gradeKey), formatCoins(price)))
                .lore(LangHelper.get(player, "lobby.vip-confirm-warning")).build());
        inventory.setItem(CONFIRM_SLOT, new ItemBuilder(Material.LIME_DYE)
                .name(LangHelper.get(player, "lobby.vip-confirm"))
                .lore(LangHelper.get(player, "lobby.vip-confirm-action")).build());
        inventory.setItem(CONFIRM_CANCEL_SLOT, new ItemBuilder(Material.RED_DYE)
                .name(LangHelper.get(player, "lobby.vip-cancel"))
                .lore(LangHelper.get(player, "lobby.vip-cancel-action")).build());
        inventory.setItem(CONFIRM_CLOSE_SLOT, ItemBuilder.closeButton(player));
        return inventory;
    }

    // Metadata consulted by the click manager.

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

    public static int getUpgradePrice(String currentGrade, String targetGrade) {
        return GradeUpgradePricing.difference(getPriceForGrade(currentGrade), getPriceForGrade(targetGrade));
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

    public static String formatCoins(double amount) {
        if (amount >= 1_000_000_000) return String.format(Locale.ROOT, "%.1fB", amount / 1_000_000_000.0);
        if (amount >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000) return String.format(Locale.ROOT, "%.1fK", amount / 1_000.0);
        return java.math.BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
    }

    private static String hardcodedKeyForSlot(int slot) {
        // Fallback used if the Lobby instance is not available, especially during tests.
        return switch (slot) {
            case 11 -> "VIP";
            case 13 -> "VIP_PLUS";
            case 15 -> "PREMIUM";
            default -> null;
        };
    }

    public record ShopEntry(String gradeKey, Material material, String displayName,
                            int price, List<String> advantages) {}
    public enum View { HOME, GRADES, CONFIRM }

    public static void validateConfiguration(TropicubeLobby lobby) {
        Set<String> keys = new HashSet<>();
        int previousPrice = -1;
        for (Map<?, ?> raw : lobby.getConfig().getMapList("vip-shop.entries")) {
            Object keyValue = raw.get("grade-key");
            Object priceValue = raw.get("price");
            if (!(keyValue instanceof String key) || key.isBlank() || !(priceValue instanceof Number number)) {
                throw new IllegalArgumentException("vip-shop.entries exige grade-key et price");
            }
            int price = number.intValue();
            if (!keys.add(key.toUpperCase())) throw new IllegalArgumentException("Grade boutique dupliqué : " + key);
            if (price <= previousPrice) throw new IllegalArgumentException("Les prix de grades doivent être strictement croissants");
            if (!lobby.getCore().getPermissionManager().getAllGrades().containsKey(key.toUpperCase())) {
                throw new IllegalArgumentException("Grade boutique inconnu dans Core : " + key);
            }
            previousPrice = price;
        }
    }
}
