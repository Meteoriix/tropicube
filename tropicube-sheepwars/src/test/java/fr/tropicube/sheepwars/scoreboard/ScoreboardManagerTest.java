package fr.tropicube.sheepwars.scoreboard;

import fr.tropicube.sheepwars.game.GameTeam;
import fr.tropicube.sheepwars.player.GamePlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreboardManagerTest {

    @Test
    void tablistUsesOnlyTheVisibleNameAndTeamColor() {
        Component expected = Component.text("MaskedWolf", NamedTextColor.RED);

        assertEquals(expected, ScoreboardManager.teamColoredName("MaskedWolf", NamedTextColor.RED));
    }

    @Test
    void tablistColorFollowsWaitingRoomTeamChanges() {
        GamePlayer player = new GamePlayer(UUID.randomUUID());
        player.setTeam(GameTeam.RED);
        assertEquals(NamedTextColor.RED, ScoreboardManager.playerListColor(player));

        player.setTeam(GameTeam.BLUE);
        assertEquals(NamedTextColor.BLUE, ScoreboardManager.playerListColor(player));
    }

    @Test
    void blankScoreboardLinesContainAVisibleSpace() {
        assertEquals(Component.text(" "), ScoreboardManager.blankLine());
    }
}
