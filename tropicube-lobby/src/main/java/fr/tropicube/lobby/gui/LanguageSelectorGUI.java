package fr.tropicube.lobby.gui;

import fr.tropicube.core.TropicubeCore;
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
 * Menu de sélection de la langue du joueur.
 * Utilise le LanguageManager du tropicube-core pour persister le choix.
 */
public class LanguageSelectorGUI {

    /** Slot du bouton "fermer". */
    public static final int CLOSE_SLOT = 22;

    private static final int SIZE = 27;

    // Slots centrés sur la 2e ligne (rangée du milieu d'un inventaire 27 cases)
    private static final int[] LANG_SLOTS = {10, 12, 14, 16};

    /** Entrées chargées au premier affichage ; {@code null} signifie « non initialisé ». */
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
                Object raw1 = m.get("lore1"); String lore1 = raw1 instanceof String s1 ? s1 : "";
                Object raw2 = m.get("lore2"); String lore2 = raw2 instanceof String s2 ? s2 : "";
                if (code == null || headId == null) continue;
                ItemStack icon = hdbapi == null ? null : hdbapi.getItemHead(headId);
                if (icon == null) icon = new ItemStack(Material.PLAYER_HEAD);
                entries.add(new LanguageEntry(code, displayName != null ? displayName : code,
                        icon, lore1, lore2));
            }
            if (!entries.isEmpty()) LANGUAGES = Collections.unmodifiableList(entries);
        } catch (Exception _) {}
        return LANGUAGES != null ? LANGUAGES : Collections.emptyList();
    }

    /**
     * Holder marker : permet au listener d'identifier cet inventaire de façon fiable
     * via instanceof, sans dépendre du titre ni de la map GuiManager seule.
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
        Inventory inv = Bukkit.createInventory(holder, SIZE, LangHelper.component(player, "lobby.lang-selector-title"));
        holder.setInventory(inv);

        // Fond décoratif
        for (int i = 0; i < SIZE; i++)
            inv.setItem(i, new ItemBuilder(Material.LIGHT_BLUE_STAINED_GLASS_PANE).name(" ").build());

        // Icônes de langue
        List<LanguageEntry> langs = getLanguages();
        for (int i = 0; i < langs.size() && i < LANG_SLOTS.length; i++) {
            LanguageEntry lang = langs.get(i);
            boolean isCurrent = lang.code().equals(currentLang);

            String currentLabel = LangHelper.get(player, "lobby.lang-current");
            String changeLabel  = LangHelper.get(player, "lobby.lang-change");
            ItemBuilder ib = new ItemBuilder(lang.itemStack())
                    .name((isCurrent ? "<green>✔ " : "") + lang.displayName())
                    .lore(lang.lore1(), lang.lore2(), "",
                          isCurrent ? currentLabel : changeLabel);
            if (isCurrent) ib.glow();
            inv.setItem(LANG_SLOTS[i], ib.build());
        }

        inv.setItem(CLOSE_SLOT, ItemBuilder.closeButton(player));
        return inv;
    }

    /** @return le code de langue correspondant au slot cliqué, ou null si aucun. */
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

    public record LanguageEntry(String code, String displayName, ItemStack itemStack,
                                String lore1, String lore2) {}
}
