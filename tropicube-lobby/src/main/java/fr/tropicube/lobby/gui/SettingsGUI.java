package fr.tropicube.lobby.gui;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.network.PlayerPreferenceService;
import fr.tropicube.core.menu.NetworkMenuStyle;
import fr.tropicube.lobby.utils.ItemBuilder;
import fr.tropicube.lobby.utils.LangHelper;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** Lobby player settings, built from an asynchronously loaded settings snapshot. */
public final class SettingsGUI {
    private static final String LANGUAGE_HEAD_ID = "71786";

    public static final int LANGUAGE_SLOT = 10;
    public static final int PROFILE_VISIBILITY_SLOT = 11;
    public static final int MESSAGE_PRIVACY_SLOT = 12;
    public static final int GLOBAL_CHAT_SLOT = 13;
    public static final int VISIBILITY_SLOT = 14;
    public static final int AUTO_REPLAY_SLOT = 15;
    public static final int HINTS_SLOT = 16;
    public static final int EFFECTS_SLOT = 22;
    public static final int CLOSE_SLOT = 26;

    private SettingsGUI() { }

    public static Inventory build(Player player, int autoReplayRemaining,
                                  PlayerPreferenceService.Preferences preferences) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 27,
                LangHelper.component(player, "lobby.settings-title"));
        holder.inventory = inventory;
        NetworkMenuStyle.frame(inventory);
        inventory.setItem(LANGUAGE_SLOT, new ItemBuilder(languageIcon())
                .name(LangHelper.get(player, "lobby.settings-language-name"))
                .lore(LangHelper.get(player, "lobby.settings-language-lore")).build());
        inventory.setItem(PROFILE_VISIBILITY_SLOT, new ItemBuilder(Material.NAME_TAG)
                .name(LangHelper.get(player, "lobby.settings-profile-name"))
                .lore(LangHelper.get(player, "lobby.settings-profile-lore",
                                settingValue(player, preferences.profileVisibility().name())),
                        LangHelper.get(player, "lobby.settings-profile-help"), "",
                        LangHelper.get(player, "lobby.settings-change-action")).build());
        inventory.setItem(MESSAGE_PRIVACY_SLOT, new ItemBuilder(Material.WRITABLE_BOOK)
                .name(LangHelper.get(player, "lobby.settings-messages-name"))
                .lore(LangHelper.get(player, "lobby.settings-messages-lore",
                                settingValue(player, preferences.messagePrivacy().name())),
                        LangHelper.get(player, "lobby.settings-messages-help"), "",
                        LangHelper.get(player, "lobby.settings-change-action")).build());
        inventory.setItem(GLOBAL_CHAT_SLOT, new ItemBuilder(preferences.globalChatEnabled() ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(LangHelper.get(player, "lobby.settings-global-chat-name"))
                .lore(LangHelper.get(player, preferences.globalChatEnabled()
                                ? "lobby.settings-global-chat-on" : "lobby.settings-global-chat-off"),
                        LangHelper.get(player, "lobby.settings-global-chat-help")).build());
        boolean enabled = autoReplayRemaining >= 0;
        String stateKey = !enabled ? "lobby.settings-auto-replay-off"
                : autoReplayRemaining == 0 ? "lobby.settings-auto-replay-confirm"
                : "lobby.settings-auto-replay-on";
        inventory.setItem(AUTO_REPLAY_SLOT, new ItemBuilder(enabled ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(LangHelper.get(player, "lobby.settings-auto-replay-name"))
                .lore(LangHelper.get(player, stateKey, autoReplayRemaining),
                        LangHelper.get(player, "lobby.settings-auto-replay-help")).build());
        inventory.setItem(VISIBILITY_SLOT, new ItemBuilder(Material.ENDER_EYE)
                .name(LangHelper.get(player, "lobby.settings-visibility-name"))
                .lore(LangHelper.get(player, "lobby.settings-visibility-lore",
                                settingValue(player, preferences.lobbyVisibility().name())),
                        LangHelper.get(player, "lobby.settings-visibility-help"), "",
                        LangHelper.get(player, "lobby.settings-change-action"))
                .build());
        inventory.setItem(HINTS_SLOT, new ItemBuilder(preferences.contextualHelp() ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(LangHelper.get(player, "lobby.settings-hints-name"))
                .lore(LangHelper.get(player, preferences.contextualHelp()
                                ? "lobby.settings-hints-on" : "lobby.settings-hints-off"),
                        LangHelper.get(player, "lobby.settings-hints-help")).build());
        inventory.setItem(EFFECTS_SLOT, new ItemBuilder(preferences.lobbyEffectsEnabled() ? Material.FIREWORK_ROCKET : Material.GRAY_DYE)
                .name(LangHelper.get(player, "lobby.settings-effects-name"))
                .lore(LangHelper.get(player, preferences.lobbyEffectsEnabled()
                                ? "lobby.settings-effects-on" : "lobby.settings-effects-off"),
                        LangHelper.get(player, "lobby.settings-effects-help")).build());
        inventory.setItem(CLOSE_SLOT, ItemBuilder.closeButton(player));
        return inventory;
    }

    private static ItemStack languageIcon() {
        ItemStack fallback = new ItemStack(Material.PLAYER_HEAD);
        try {
            if (Bukkit.getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core) {
                HeadDatabaseAPI api = core.getHeadDatabaseManager().getHeadDatabaseAPI();
                ItemStack icon = api == null ? null : api.getItemHead(LANGUAGE_HEAD_ID);
                if (icon != null) return icon;
            }
        } catch (RuntimeException _) {
            // HeadDatabase is optional at runtime; the menu remains usable with its vanilla fallback.
        }
        return fallback;
    }

    static String settingValue(Player player, String rawValue) {
        String key = rawValue.toLowerCase(Locale.ROOT).replace('_', '-');
        return LangHelper.get(player, "lobby.settings-value-" + key);
    }

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}
