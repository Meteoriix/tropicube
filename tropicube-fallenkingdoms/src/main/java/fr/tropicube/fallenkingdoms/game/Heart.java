package fr.tropicube.fallenkingdoms.game;

/** Pure heart damage rule; adapters decide how a Paper entity is represented. */
public final class Heart {
    private final KingdomId owner;
    private final double maximumHealth;
    private double health;
    private HeartState state = HeartState.PROTECTED;
    public Heart(KingdomId owner, double maximumHealth) {
        if (maximumHealth <= 0) throw new IllegalArgumentException("La vie d'un cœur doit être strictement positive.");
        this.owner = owner; this.maximumHealth = maximumHealth; this.health = maximumHealth;
    }
    public KingdomId owner() { return owner; }
    public double maximumHealth() { return maximumHealth; }
    public double health() { return health; }
    public HeartState state() { return state; }
    public void makeVulnerable() { if (state == HeartState.PROTECTED) state = HeartState.VULNERABLE; }
    public void destroy() { health = 0; state = HeartState.DESTROYED; }
    /** Returns actual damage, refusing allies, protected hearts and TNT direct damage. */
    public double damage(KingdomId attacker, double requested, boolean tnt) {
        if (state != HeartState.VULNERABLE || attacker == owner || tnt || requested <= 0) return 0;
        double applied = Math.min(health, requested); health -= applied;
        if (health == 0) state = HeartState.DESTROYED;
        return applied;
    }
}
