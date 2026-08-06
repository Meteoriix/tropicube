package fr.tropicube.sheepwars.player;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/** Classes de combat disponibles et bonus appliqué par chacune. */
public enum PlayerClass {
    NONE("Aucun", Material.BARRIER, NamedTextColor.WHITE,
            "Aucun bonus accordé"),
    DPS("DPS", Material.IRON_SWORD, NamedTextColor.RED,
            "Infligez plus de dégâts à vos ennemis"),
    TANK("Tank", Material.IRON_CHESTPLATE, NamedTextColor.BLUE,
            "Survivez plus longtemps sur le champ de bataille"),
    SUPPORT("Support", Material.BLAZE_POWDER, NamedTextColor.GREEN,
            "Renforcez l'équipe et gérez le terrain");

    private final String displayName;
    private final Material icon;
    private final NamedTextColor color;
    private final String description;

    PlayerClass(String displayName, Material icon, NamedTextColor color, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public Material getIcon() { return icon; }
    public NamedTextColor getColor() { return color; }
    public String getDescription() { return description; }
}
