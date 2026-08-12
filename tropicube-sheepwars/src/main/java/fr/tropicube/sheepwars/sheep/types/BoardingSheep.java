package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;

/** Mobility sheep that transports its launcher to the target. */
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
