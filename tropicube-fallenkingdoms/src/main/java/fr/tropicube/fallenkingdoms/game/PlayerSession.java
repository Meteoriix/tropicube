package fr.tropicube.fallenkingdoms.game;

import java.util.UUID;

/** Mutable runtime data for one player; it never outlives its game session. */
public final class PlayerSession {
    private final UUID playerId;
    private final KingdomId kingdom;
    private PlayerLifeState state = PlayerLifeState.ACTIVE;
    private int deaths;
    private int eliminations;
    private int objectives;
    private String kitId;

    public PlayerSession(UUID playerId, KingdomId kingdom) {
        this.playerId = playerId;
        this.kingdom = kingdom;
    }

    public UUID playerId() { return playerId; }
    public KingdomId kingdom() { return kingdom; }
    public PlayerLifeState state() { return state; }
    public int deaths() { return deaths; }
    public int eliminations() { return eliminations; }
    public int objectives() { return objectives; }
    public String kitId() { return kitId; }
    public void kitId(String kitId) { this.kitId = kitId; }
    public void state(PlayerLifeState state) { this.state = state; }
    public void recordDeath() { deaths++; }
    public void recordElimination() { eliminations++; }
    public void recordObjective() { objectives++; }
    public boolean surviving() { return state == PlayerLifeState.ACTIVE || state == PlayerLifeState.RESPAWNING || state == PlayerLifeState.LAST_LIFE; }
}
