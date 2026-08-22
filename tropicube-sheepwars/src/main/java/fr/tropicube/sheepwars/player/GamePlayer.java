package fr.tropicube.sheepwars.player;

import fr.tropicube.sheepwars.game.GameTeam;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.List;
import java.util.UUID;

/** Mutable state of a participant during a SheepWars game. */
public class GamePlayer {

    private final UUID uuid;
    private GameTeam team;
    private boolean alive;
    private int kills;
    private int sheepThrown;
    private PlayerClass playerClass = PlayerClass.NONE;
    private PlayerKit kit = PlayerKit.NONE;
    private boolean connected = true;
    private Location disconnectLocation;
    private ItemStack[] disconnectInventory = new ItemStack[0];
    private double disconnectHealth = 20.0;
    private int disconnectFoodLevel = 20;
    private float disconnectSaturation = 5.0F;
    private List<PotionEffect> disconnectEffects = List.of();

    public GamePlayer(UUID uuid) {
        this.uuid = uuid;
        this.alive = true;
        this.kills = 0;
        this.sheepThrown = 0;
    }

    public UUID getUuid() { return uuid; }

    public Player getBukkitPlayer() {
        return org.bukkit.Bukkit.getPlayer(uuid);
    }

    public GameTeam getTeam() { return team; }
    public void setTeam(GameTeam team) { this.team = team; }

    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }

    public int getKills() { return kills; }
    public void addKill() { this.kills++; }

    public int getSheepThrown() { return sheepThrown; }
    public void addSheepThrown() { this.sheepThrown++; }

    public PlayerKit getKit() { return kit; }
    public void setKit(PlayerKit kit) { this.kit = kit; }

    public PlayerClass getPlayerClass() { return playerClass; }
    public void setPlayerClass(PlayerClass playerClass) { this.playerClass = playerClass; }

    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }
    public Location getDisconnectLocation() { return disconnectLocation == null ? null : disconnectLocation.clone(); }
    public void setDisconnectLocation(Location location) {
        this.disconnectLocation = location == null ? null : location.clone();
    }

    /** Captures the mutable combat state that must survive a short ranked disconnection. */
    public void captureDisconnectState(Player player) {
        disconnectInventory = cloneItems(player.getInventory().getContents());
        disconnectHealth = player.getHealth();
        disconnectFoodLevel = player.getFoodLevel();
        disconnectSaturation = player.getSaturation();
        disconnectEffects = List.copyOf(player.getActivePotionEffects());
    }

    /** Restores the exact pre-disconnection state after the generic player reset. */
    public void restoreDisconnectState(Player player) {
        player.getInventory().setContents(cloneItems(disconnectInventory));
        var maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double maximum = maxHealth == null ? 20.0 : maxHealth.getValue();
        player.setHealth(Math.min(maximum, Math.max(1.0, disconnectHealth)));
        player.setFoodLevel(disconnectFoodLevel);
        player.setSaturation(disconnectSaturation);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        disconnectEffects.forEach(player::addPotionEffect);
    }

    private static ItemStack[] cloneItems(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            copy[index] = source[index] == null ? null : source[index].clone();
        }
        return copy;
    }
}
