package fr.tropicube.lobby.gui;

import fr.tropicube.lobby.utils.ItemBuilder;
import fr.tropicube.core.menu.NetworkMenuStyle;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

/**
 * Menu for choosing the type of personalized game (Custom Game).
 * Each available template is displayed as a clickable option.
 * Accessible to players with {@code tropicube.lobby.customgame} permission.
 */
public class CustomGameTypeGUI {

    private static final int SIZE = 27;
    static final int PUBLIC_GAME_SLOT = 11;
    static final int PRIVATE_GAME_SLOT = 15;
    static final int CLOSE_SLOT = 26;

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;

        public boolean isCloseSlot(int slot)        { return slot == CLOSE_SLOT; }
        public boolean isPublicGameSlot(int slot)        { return slot == PUBLIC_GAME_SLOT; }
        public boolean isPrivateGameSlot(int slot)         { return slot == PRIVATE_GAME_SLOT; }

        @Override public @NonNull Inventory getInventory() { return inventory; }
        private void setInventory(Inventory inv)            { this.inventory = inv; }
    }

    public static Inventory build(Player player) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, LangHelper.menuSize("custom-game-type"),
                LangHelper.menuTitle(player, "custom-game-type"));
        holder.setInventory(inv);

        NetworkMenuStyle.applyFrame(inv, player, LangHelper.menuFrame("custom-game-type"));

        inv.setItem(PUBLIC_GAME_SLOT, buildPublicGameItem(player));
        inv.setItem(PRIVATE_GAME_SLOT, buildPrivateGameItem(player));

        inv.setItem(CLOSE_SLOT, ItemBuilder.closeButton(player));
        return inv;
    }

    private static ItemStack buildPublicGameItem(Player player) {
        return new ItemBuilder(new ItemStack(Material.PAPER))
                .name("<gold>" + LangHelper.get(player, "lobby.custom-game-privacy-public"))
                .lore(
                        LangHelper.get(player, "lobby.custom-game-public"),
                        "",
                        LangHelper.get(player, "lobby.custom-game-type-click")
                )
                .glow(player)
                .build();
    }

    private static ItemStack buildPrivateGameItem(Player player) {
        return new ItemBuilder(new ItemStack(Material.PAPER))
                .name("<gold>" + LangHelper.get(player, "lobby.custom-game-privacy-private"))
                .lore(
                        LangHelper.get(player, "lobby.custom-game-private"),
                        "",
                        LangHelper.get(player, "lobby.custom-game-type-click")
                )
                .glow(player)
                .build();
    }
}
