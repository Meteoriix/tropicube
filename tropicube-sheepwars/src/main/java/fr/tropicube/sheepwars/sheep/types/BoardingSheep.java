package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;

/** Mouton de mobilité qui transporte son lanceur vers la cible. */
public class BoardingSheep extends AbstractSheep {

    public BoardingSheep() {
        super(SheepType.BOARDING);
    }

    @Override
    public void onLaunch(Player thrower, Sheep sheep) {
        sheep.addPassenger(thrower);
    }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        return true;
    }

    @Override
    public boolean hasCountdown() { return false; }
}
