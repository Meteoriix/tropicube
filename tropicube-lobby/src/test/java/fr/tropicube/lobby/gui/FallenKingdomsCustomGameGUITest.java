package fr.tropicube.lobby.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FallenKingdomsCustomGameGUITest {
    @Test void defaultsMatchThePublicProfileAndNeverDisableEveryKit() {
        var holder = new FallenKingdomsCustomGameGUI.Holder("fallenkingdoms", true);
        String defaults = holder.encodedOptions();
        assertTrue(defaults.contains("FK_PVP_AT_SECONDS=900"));
        assertTrue(defaults.contains("FK_FORCE_END_AT_SECONDS=5400"));
        assertTrue(defaults.contains("FK_ENABLED_KITS=miner,farmer,scout,enchanter"));

        holder.cycle(28, false);
        holder.cycle(29, false);
        holder.cycle(30, false);
        holder.cycle(31, false);
        assertTrue(holder.encodedOptions().contains("FK_ENABLED_KITS=enchanter"));
    }

    @Test void timelineControlsKeepStrictlyOrderedValues() {
        var holder = new FallenKingdomsCustomGameGUI.Holder("fallenkingdoms", false);
        holder.cycle(15, false);
        String encoded = holder.encodedOptions();
        assertTrue(encoded.contains("FK_SUDDEN_DEATH_AT_SECONDS=2700"));
        assertTrue(encoded.contains("FK_FORCE_END_AT_SECONDS=5400"));
    }
}
