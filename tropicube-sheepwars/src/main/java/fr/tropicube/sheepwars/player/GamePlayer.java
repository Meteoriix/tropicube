package fr.tropicube.sheepwars.player;

import fr.tropicube.sheepwars.game.GameTeam;
import org.bukkit.entity.Player;

import java.util.UUID;

/** État mutable d'un participant pendant une partie SheepWars. */
public class GamePlayer {

    private final UUID uuid;
    private GameTeam team;
    private boolean alive;
    private int kills;
    private int sheepThrown;
    private PlayerClass playerClass = PlayerClass.NONE;
    private PlayerKit kit = PlayerKit.NONE;

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
}
