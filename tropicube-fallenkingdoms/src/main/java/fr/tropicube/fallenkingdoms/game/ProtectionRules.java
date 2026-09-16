package fr.tropicube.fallenkingdoms.game;

/** Stateless authorization table shared by block, entity and movement listeners. */
public final class ProtectionRules {
    public boolean allowsPvp(GameState state, Territory territory, boolean allies) {
        return !allies && (state == GameState.ASSAULT || state == GameState.SUDDEN_DEATH || (state == GameState.PVP && territory == Territory.COMMON));
    }
    public boolean allowsEnemyBaseEntry(GameState state) { return state == GameState.ASSAULT || state == GameState.SUDDEN_DEATH; }
    public boolean allowsHeartDamage(GameState state) { return state == GameState.ASSAULT; }
    public boolean allowsManualBuild(Territory territory, boolean owner) { return territory == Territory.COMMON || owner; }
    public boolean allowsPlacement(GameState state, Territory territory, boolean tnt, boolean forbidden) {
        if (forbidden || territory == Territory.OUTSIDE) return false;
        if (territory == Territory.OWN_BASE || territory == Territory.COMMON) return true;
        return territory == Territory.ENEMY_BASE && tnt
                && (state == GameState.ASSAULT || state == GameState.SUDDEN_DEATH);
    }
    public enum Territory { OWN_BASE, ENEMY_BASE, COMMON, OUTSIDE }
}
