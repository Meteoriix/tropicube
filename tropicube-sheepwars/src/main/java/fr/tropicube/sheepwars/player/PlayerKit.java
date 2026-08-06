package fr.tropicube.sheepwars.player;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.Arrays;

/** Kits optionnels modifiant les capacités d'un joueur pendant la partie. */
public enum PlayerKit {
    NONE(PlayerClass.NONE, "Aucun", Material.BARRIER, NamedTextColor.WHITE,
            "Aucun bonus accordé"),

    DPS_SWORD(PlayerClass.DPS, "Épéiste", Material.STONE_SWORD, NamedTextColor.RED,
            "Épée en pierre avec Tranchant I"),
    DPS_BOW(PlayerClass.DPS, "Archer", Material.BOW, NamedTextColor.YELLOW,
            "Arc avec Puissance I"),
    DPS_SHEEP(PlayerClass.DPS, "Berger de la Mort", Material.RED_WOOL, NamedTextColor.DARK_RED,
            "Vos moutons infligent 50 % de dégâts supplémentaires"),

    TANK_HEARTS(PlayerClass.TANK, "Colosse", Material.GOLDEN_APPLE, NamedTextColor.BLUE,
            "+4 cœurs maximum"),
    TANK_KNOCKBACK(PlayerClass.TANK, "Ancre", Material.IRON_BOOTS, NamedTextColor.DARK_BLUE,
            "-80 % de recul reçu"),
    TANK_FALL(PlayerClass.TANK, "Plume d'Acier", Material.FEATHER, NamedTextColor.AQUA,
            "-80 % de dégâts de chute"),

    SUPPORT_SHEEP(PlayerClass.SUPPORT, "Éleveur", Material.PINK_WOOL, NamedTextColor.GREEN,
            "Vos moutons ont 40 PV (au lieu de 16)"),
    SUPPORT_ARROWS(PlayerClass.SUPPORT, "Médic", Material.TIPPED_ARROW, NamedTextColor.LIGHT_PURPLE,
            "Vos flèches soignent les alliés touchés"),
    SUPPORT_JUMP(PlayerClass.SUPPORT, "Acrobate", Material.RABBIT_FOOT, NamedTextColor.DARK_GREEN,
            "Saut amélioré II permanent");

    private final PlayerClass playerClass;
    private final String displayName;
    private final Material icon;
    private final NamedTextColor color;
    private final String description;

    PlayerKit(PlayerClass playerClass, String displayName, Material icon, NamedTextColor color, String description) {
        this.playerClass = playerClass;
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
        this.description = description;
    }

    public PlayerClass getPlayerClass() { return playerClass; }
    public String getDisplayName() { return displayName; }
    public Material getIcon() { return icon; }
    public NamedTextColor getColor() { return color; }
    public String getDescription() { return description; }

    public static PlayerKit[] getKitsForClass(PlayerClass playerClass) {
        return Arrays.stream(values())
                .filter(k -> k.playerClass == playerClass)
                .toArray(PlayerKit[]::new);
    }
}
