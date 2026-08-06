package fr.tropicube.sheepwars.game;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.DyeColor;

import java.util.concurrent.ThreadLocalRandom;

/** Équipes jouables et métadonnées visuelles associées. */
public enum GameTeam {
    RED("Rouge", NamedTextColor.RED, DyeColor.RED),
    BLUE("Bleu", NamedTextColor.BLUE, DyeColor.BLUE);

    private final String displayName;
    private final NamedTextColor color;
    private final DyeColor dyeColor;

    GameTeam(String displayName, NamedTextColor color, DyeColor dyeColor) {
        this.displayName = displayName;
        this.color = color;
        this.dyeColor = dyeColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public DyeColor getDyeColor() {
        return dyeColor;
    }

    public static GameTeam random() {
        GameTeam[] teams = values();
        return teams[ThreadLocalRandom.current().nextInt(teams.length)];
    }
}
