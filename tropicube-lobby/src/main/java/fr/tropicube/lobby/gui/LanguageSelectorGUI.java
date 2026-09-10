package fr.tropicube.lobby.gui;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.menu.NetworkMenuStyle;
import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.ItemBuilder;
import fr.tropicube.lobby.utils.LangHelper;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Player language selection menu.
 * Uses the tropicube-core LanguageManager to persist the choice.
 */
public class LanguageSelectorGUI {

    /** “Close” button slot. */
    public static final int CLOSE_SLOT = 26;

    private static final int SIZE = 27;

    // Slots centered on the 2nd row (middle row of a 27-square inventory)
    private static final int[] LANG_SLOTS = {10, 12, 14, 16};

    /** Entries loaded on first display; {@code null} means “uninitialized”. */
    private static volatile List<LanguageEntry> LANGUAGES = null;

    private static List<LanguageEntry> getLanguages() {
        if (LANGUAGES != null) return LANGUAGES;
        var lobby = TropicubeLobby.getInstance();
        var corePlugin = Bukkit.getPluginManager().getPlugin("TropicubeCore");
        if (lobby == null || !(corePlugin instanceof TropicubeCore core)) return Collections.emptyList();
        try {
            HeadDatabaseAPI hdbapi = core.getHeadDatabaseManager().getHeadDatabaseAPI();

            List<Map<?, ?>> raw = lobby.getConfig().getMapList("lang-selector.languages");
            List<LanguageEntry> entries = new ArrayList<>();
            for (Map<?, ?> m : raw) {
                String code        = (String) m.get("code");
                String headId      = (String) m.get("head-id");
                String displayName = (String) m.get("display-name");
                String lore = languageLore(m);
                if (code == null || headId == null) continue;
                ItemStack icon = hdbapi == null ? null : hdbapi.getItemHead(headId);
                if (icon == null) icon = new ItemStack(Material.PLAYER_HEAD);
                entries.add(new LanguageEntry(code, displayName != null ? displayName : code,
                        icon, lore));
            }
            if (!entries.isEmpty()) LANGUAGES = Collections.unmodifiableList(entries);
        } catch (Exception _) {}
        return LANGUAGES != null ? LANGUAGES : Collections.emptyList();
    }

    /**
     * Holder marker: allows the listener to reliably identify this inventory
     * via instanceof, without depending on the title or the GuiManager map alone.
     */
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public @NonNull Inventory getInventory() { return inventory; }

        private void setInventory(Inventory inventory) { this.inventory = inventory; }
    }

    public static Inventory build(Player player) {
        String currentLang = getCurrentLang(player);

        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, LangHelper.menuSize("language-selector"),
                LangHelper.menuTitle(player, "language-selector"));
        holder.setInventory(inv);

        NetworkMenuStyle.frame(inv, player);

        // Language icons
        List<LanguageEntry> langs = getLanguages();
        for (int i = 0; i < langs.size() && i < LANG_SLOTS.length; i++) {
            LanguageEntry lang = langs.get(i);
            boolean isCurrent = lang.code().equals(currentLang);

            String currentLabel = LangHelper.get(player, "lobby.lang-current");
            String changeLabel  = LangHelper.get(player, "lobby.lang-change");
            ItemBuilder ib = new ItemBuilder(lang.itemStack())
                    .name((isCurrent ? "<green>✔ " : "") + lang.displayName())
                    .lore(lang.lore(), "",
                          isCurrent ? currentLabel : changeLabel);
            if (isCurrent) ib.glow(player);
            inv.setItem(LANG_SLOTS[i], ib.build());
        }

        inv.setItem(CLOSE_SLOT, ItemBuilder.closeButton(player));
        return inv;
    }

    /** @return the language code corresponding to the clicked slot, or null if none. */
    public static String getLangForSlot(int slot) {
        List<LanguageEntry> langs = getLanguages();
        for (int i = 0; i < LANG_SLOTS.length; i++) {
            if (LANG_SLOTS[i] == slot && i < langs.size()) return langs.get(i).code();
        }
        return null;
    }

    private static String getCurrentLang(Player player) {
        return LangHelper.getPlayerLang(player.getUniqueId());
    }

    static String languageLore(Map<?, ?> entry) {
        Object configured = entry.get("lore");
        if (configured instanceof String lore) return lore;
        String firstLine = entry.get("lore1") instanceof String line ? line : "";
        String secondLine = entry.get("lore2") instanceof String line ? line : "";
        if (firstLine.isEmpty()) return secondLine;
        return secondLine.isEmpty() ? firstLine : firstLine + "<br>" + secondLine;
    }

    public record LanguageEntry(String code, String displayName, ItemStack itemStack, String lore) {}
}
