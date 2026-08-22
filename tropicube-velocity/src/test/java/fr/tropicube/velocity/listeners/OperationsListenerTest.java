package fr.tropicube.velocity.listeners;

import fr.tropicube.docker.model.ServerTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperationsListenerTest {
    @Test
    void listsEnabledGameTypesOnceAndExcludesLobbyAndMaintenance() {
        assertEquals(List.of("FALLEN_KINGDOMS", "SHEEPWARS"), OperationsListener.availableGameTypes(List.of(
                template("LOBBY", true, false),
                template("SHEEPWARS", true, false),
                template("SHEEPWARS", true, false),
                template("FALLEN_KINGDOMS", true, false),
                template("BEDWARS", false, false),
                template("SURVIVAL", true, true))));
    }

    private static ServerTemplate template(String type, boolean enabled, boolean maintenance) {
        ServerTemplate template = new ServerTemplate();
        template.setServerType(type);
        template.setEnabled(enabled);
        template.setMaintenanceMode(maintenance);
        return template;
    }
}
