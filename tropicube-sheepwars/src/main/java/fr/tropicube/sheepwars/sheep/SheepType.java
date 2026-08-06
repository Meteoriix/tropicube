package fr.tropicube.sheepwars.sheep;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.DyeColor;

/** Catalogue des moutons, de leur présentation et de leur pondération configurable. */
public enum SheepType {
    BOARDING("Abordage", DyeColor.WHITE, NamedTextColor.WHITE, "boarding",
            "Un mouton qui transporte son lanceur"),
    TNT("TNT", DyeColor.RED, NamedTextColor.RED, "tnt",
            "Un mouton très explosif"),
    DISTORT("Distortion", DyeColor.PURPLE, NamedTextColor.DARK_PURPLE, "distort",
            "Téléporte les blocs autour du point d'impact"),
    DARKNESS("Ténébreux", DyeColor.GRAY, NamedTextColor.DARK_GRAY, "darkness",
            "Ralentit et aveugle les ennemis touchés"),
    FIRE("Feu", DyeColor.ORANGE, NamedTextColor.GOLD, "fire",
            "Enflamme les ennemis touchés"),
    SWAP("Échange", DyeColor.YELLOW, NamedTextColor.YELLOW, "swap",
            "Échange votre position avec le joueur le plus proche (dash si personne)"),
    METEOR("Météore", DyeColor.BLUE, NamedTextColor.DARK_BLUE, "meteor",
            "Fait pleuvoir des météores"),
    SEARCHING("Tête Chercheuse", DyeColor.LIME, NamedTextColor.GREEN, "searching",
            "Poursuit le joueur le plus proche du point d'impact"),
    HEALING("Soin", DyeColor.PINK, NamedTextColor.LIGHT_PURPLE, "healing",
            "Soigne le lanceur et ses alliés dans un rayon de 5 blocs"),
    LIGHTNING("Foudre", DyeColor.LIGHT_BLUE, NamedTextColor.AQUA, "lightning",
            "Frappe la cible et enchaîne sur 3 joueurs proches"),
    GRAVITY("Gravité", DyeColor.MAGENTA, NamedTextColor.LIGHT_PURPLE, "gravity",
            "Aspire les joueurs proches puis les projette en l'air"),
    MECHA("Mécha", DyeColor.LIGHT_GRAY, NamedTextColor.GRAY, "mecha",
            "Déploie un golem tank qui attaque les ennemis"),
    STRENGTH("Force", DyeColor.CYAN, NamedTextColor.DARK_AQUA, "strength",
            "Augmente les dégâts infligés par le lanceur et ses alliés dans un rayon de 5 blocs"),
    POISON("Poison", DyeColor.GREEN, NamedTextColor.DARK_GREEN, "poison",
            "Pose une zone de poison comme un cocktail molotov"),
    FRAGMENTATION("Fragmentation", DyeColor.BLACK, NamedTextColor.DARK_GRAY, "fragmentation",
            "Explose en 5 bébés moutons qui font de petites explosions");

    private final String displayName;
    private final DyeColor wool;
    private final NamedTextColor textColor;
    private final String configKey;
    private final String description;

    SheepType(String displayName, DyeColor wool, NamedTextColor textColor, String configKey, String description) {
        this.displayName = displayName;
        this.wool = wool;
        this.textColor = textColor;
        this.configKey = configKey;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public DyeColor getWool() {
        return wool;
    }

    public NamedTextColor getTextColor() {
        return textColor;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getDescription() {
        return description;
    }
}
