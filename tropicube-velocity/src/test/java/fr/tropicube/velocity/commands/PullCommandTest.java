package fr.tropicube.velocity.commands;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PullCommandTest {

    @Test
    void suggestsAuthenticatedNamesWithCaseInsensitiveFilteringAndSorting() {
        assertEquals(List.of("Alpha", "alpine"), PullCommand.filterRealNameSuggestions(
                List.of("Zulu", "alpine", "Alpha"), "AL"));
    }

    @Test
    void neverLeaksTheNickedDisplayNameWhenGivenRealNames() {
        assertEquals(List.of("RealWolf"), PullCommand.filterRealNameSuggestions(
                List.of("RealWolf"), ""));
    }
}
