package fr.tropicube.fallenkingdoms.listener;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.menu.NetworkMenuStyle;
import fr.tropicube.fallenkingdoms.TropicubeFallenKingdoms;
import fr.tropicube.fallenkingdoms.game.GameSession;
import fr.tropicube.fallenkingdoms.game.GameState;
import fr.tropicube.fallenkingdoms.game.KingdomId;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import fr.tropicube.language.PlaceholderValues;
import fr.tropicube.core.ui.UiReloadParticipant;
import org.bukkit.plugin.ServicePriority;

/** Localized waiting-room selectors for kingdoms and kits. */
public final class LobbyMenuListener implements Listener, UiReloadParticipant {
    private static final int[] CONTENT_SLOTS = {10, 12, 14, 16, 22};
    private final TropicubeFallenKingdoms plugin;
    private final GameSession session;
    private final NamespacedKey actionKey;
    private final Map<UUID, MenuType> openMenus = new HashMap<>();
    public LobbyMenuListener(TropicubeFallenKingdoms plugin, GameSession session) {
        this.plugin = plugin; this.session = session; this.actionKey = new NamespacedKey(plugin, "waiting_action");
        Bukkit.getServicesManager().register(UiReloadParticipant.class,this,plugin,ServicePriority.Normal);
    }
    public void unregister(){Bukkit.getServicesManager().unregister(UiReloadParticipant.class,this);}
    @EventHandler public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> prepareHotbar(event.getPlayer()));
    }
    @EventHandler public void interact(PlayerInteractEvent event) {
        String action = event.getItem() == null ? null : event.getItem().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;
        event.setCancelled(true);
        if (action.equals("team")) openTeams(event.getPlayer());
        else if (action.equals("kit")) openKits(event.getPlayer());
        else if (action.equals("map")) openMaps(event.getPlayer());
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        MenuType type = openMenus.get(player.getUniqueId());
        if (type == null) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        String action = item == null ? null : item.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;
        if (action.equals("filler")) return;
        if (action.equals("close")) { player.closeInventory(); return; }
        boolean selected = switch(type){case KIT->session.chooseKit(player.getUniqueId(),action);case TEAM->session.chooseKingdom(player.getUniqueId(),KingdomId.valueOf(action));case MAP->session.chooseMap(player.getUniqueId(),action);};
        if (selected) player.closeInventory();
    }
    @EventHandler public void close(InventoryCloseEvent event) { openMenus.remove(event.getPlayer().getUniqueId()); }

    private void prepareHotbar(Player player) {
        if (session.state() != GameState.WAITING && session.state() != GameState.COUNTDOWN) return;
        player.getInventory().setItem(0, item(Material.WHITE_BANNER, text(player, "fk.selector-team"), "team"));
        player.getInventory().setItem(4, item(Material.NAME_TAG, text(player, "fk.selector-kit"), "kit"));
        player.getInventory().setItem(8, item(Material.MAP, text(player, "fk.selector-map"), "map"));
        session.refreshHud(player);
    }
    private void openTeams(Player player) {
        Inventory inventory = menu(player, "fk.menu-team-title");
        int index = 0;
        for (KingdomId kingdom : session.availableKingdoms()) {
            if (index >= CONTENT_SLOTS.length) break;
            Material banner = Material.valueOf(kingdom.name() + "_BANNER");
            inventory.setItem(CONTENT_SLOTS[index++], item(banner,
                    text(player, "fk.team-" + kingdom.name().toLowerCase(java.util.Locale.ROOT)), kingdom.name(),
                    text(player, "fk.menu-select")));
        }
        openMenus.put(player.getUniqueId(), MenuType.TEAM); player.openInventory(inventory);
    }
    private void openKits(Player player) {
        Inventory inventory = menu(player, "fk.menu-kit-title");
        int index = 0;
        for (var kit : session.availableKits().values()) {
            if (index >= CONTENT_SLOTS.length) break;
            inventory.setItem(CONTENT_SLOTS[index++], item(kit.icon(), text(player, "fk.kit-" + kit.id()), kit.id(),
                    text(player, "fk.menu-select")));
        }
        openMenus.put(player.getUniqueId(), MenuType.KIT); player.openInventory(inventory);
    }
    private void openMaps(Player player) {
        Inventory inventory=menu(player,"fk.menu-map-title");int index=0;
        for(var map:session.availableMaps()){
            if(index>=CONTENT_SLOTS.length)break;
            inventory.setItem(CONTENT_SLOTS[index++],item(Material.MAP,text(player,map.displayNameKey()),map.id(),
                    text(player,"fk.map-votes",PlaceholderValues.of("votes",session.mapVotes(map.id()))),text(player,"fk.map-selected")));
        }
        openMenus.put(player.getUniqueId(),MenuType.MAP);player.openInventory(inventory);
    }
    private Inventory menu(Player player, String titleKey) {
        Inventory inventory = Bukkit.createInventory(null, 27, text(player, titleKey));
        NetworkMenuStyle.frame(inventory, player);
        inventory.setItem(26, item(Material.BARRIER, text(player, "fk.menu-close"), "close"));
        return inventory;
    }
    private ItemStack item(Material material, Component name, String action, Component... lore) {
        ItemStack item = NetworkMenuStyle.item(material, name, lore);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action));
        return item;
    }
    private Component text(Player player, String key) {
        return ((TropicubeCore) Bukkit.getPluginManager().getPlugin("TropicubeCore")).getLanguageManager().getComponent(player.getUniqueId(), key);
    }
    private Component text(Player player,String key,PlaceholderValues values){return ((TropicubeCore)Bukkit.getPluginManager().getPlugin("TropicubeCore")).getLanguageManager().getComponent(player.getUniqueId(),key,values);}
    public void refresh(Player player){
        prepareHotbar(player);MenuType type=openMenus.get(player.getUniqueId());
        if(type==MenuType.TEAM)openTeams(player);else if(type==MenuType.KIT)openKits(player);else if(type==MenuType.MAP)openMaps(player);
    }
    @Override public Runnable prepareReload(){return()->{};}
    @Override public void refreshViewers(){Bukkit.getOnlinePlayers().forEach(this::refresh);}
    private enum MenuType { TEAM, KIT, MAP }
}
