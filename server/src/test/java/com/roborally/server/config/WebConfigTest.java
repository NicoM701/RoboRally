package com.roborally.server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class WebConfigTest {

    @Test
    @DisplayName("resolveFileResourceLocations keeps launch-order fallbacks stable")
    void resolveFileResourceLocations_keepsCandidateOrderStable() {
        Path baseDir = Path.of("/tmp/roborally/server");

        String[] locations = WebConfig.resolveFileResourceLocations(
                baseDir,
                "gameResources",
                "../gameResources",
                "../../gameResources");

        assertArrayEquals(new String[] {
                baseDir.resolve("gameResources").normalize().toUri().toString() + "/",
                baseDir.resolve("../gameResources").normalize().toUri().toString() + "/",
                baseDir.resolve("../../gameResources").normalize().toUri().toString() + "/"
        }, locations);
    }
}
