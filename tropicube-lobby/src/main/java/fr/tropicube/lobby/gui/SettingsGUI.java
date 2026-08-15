package fr.tropicube.lobby.gui;

import fr.tropicube.lobby.utils.ItemBuilder;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Lobby player settings, built from an asynchronously loaded settings snapshot. */
public final class SettingsGUI {
    public static final int LANGUAGE_SLOT = 11;
    public static final int AUTO_REPLAY_SLOT = 15;
    public static final int CLOSE_SLOT = 26;

    private SettingsGUI() { }

    public static Inventory build(Player player, int autoReplayRemaining) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 27,
                LangHelper.component(player, "lobby.settings-title"));
        holder.inventory = inventory;
        inventory.setItem(LANGUAGE_SLOT, new ItemBuilder(Material.PLAYER_HEAD)
                .name(LangHelper.get(player, "lobby.settings-language-name"))
                .lore(LangHelper.get(player, "lobby.settings-language-lore")).build());
        boolean enabled = autoReplayRemaining >= 0;
        String stateKey = !enabled ? "lobby.settings-auto-replay-off"
                : autoReplayRemaining == 0 ? "lobby.settings-auto-replay-confirm"
                : "lobby.settings-auto-replay-on";
        inventory.setItem(AUTO_REPLAY_SLOT, new ItemBuilder(enabled ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(LangHelper.get(player, "lobby.settings-auto-replay-name"))
                .lore(LangHelper.get(player, stateKey, autoReplayRemaining)).build());
        inventory.setItem(CLOSE_SLOT, ItemBuilder.closeButton(player));
        return inventory;
    }

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}
