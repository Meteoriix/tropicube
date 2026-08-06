package fr.tropicube.docker.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServerTemplateTest {

    @Test
    void minPortSetterPersistsItsValue() {
        ServerTemplate template = new ServerTemplate();

        template.setMinPort(25_600);

        assertEquals(25_600, template.getMinPort());
    }

    @Test
    void nullCollectionsAreNormalized() {
        ServerTemplate template = new ServerTemplate();

        template.setEnvironmentVariables(null);
        template.setVolumes(null);

        assertNotNull(template.getEnvironmentVariables());
        assertNotNull(template.getVolumes());
        assertTrue(template.getEnvironmentVariables().isEmpty());
        assertTrue(template.getVolumes().isEmpty());
    }

    @Test
    void validatesConsistentTemplate() {
        ServerTemplate template = validTemplate();
        template.setEnvironmentVariables(Map.of("TYPE", "PAPER"));
        template.setVolumes(List.of("./world:/data/world:ro"));

        assertDoesNotThrow(template::validate);
    }

    @Test
    void rejectsReversedPortRange() {
        ServerTemplate template = validTemplate();
        template.setMinPort(25_650);
        template.setMaxPort(25_600);

        IllegalStateException error = assertThrows(IllegalStateException.class, template::validate);

        assertTrue(error.getMessage().contains("minPort"));
    }

    @Test
    void rejectsNullEnvironmentValue() {
        ServerTemplate template = validTemplate();
        Map<String, String> environment = new java.util.HashMap<>();
        environment.put("TYPE", null);
        template.setEnvironmentVariables(environment);

        assertThrows(IllegalStateException.class, template::validate);
    }

    private static ServerTemplate validTemplate() {
        ServerTemplate template = new ServerTemplate();
        template.setId("lobby");
        template.setName("Lobby");
        template.setDockerImage("tropicube-lobby:latest");
        template.setServerType("LOBBY");
        template.setMinPort(25_600);
        template.setMaxPort(25_624);
        template.setMaxPlayers(100);
        template.setMinRam(1_024);
        template.setMaxRam(2_048);
        template.setMinInstances(1);
        template.setMaxInstances(10);
        return template;
    }
}
