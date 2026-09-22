package org.example.api.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeBootstrapperMavenLauncherTest {
    @TempDir Path temp;

    @Test
    void windowsPrefersProjectWrapperBeforeSystemMaven() throws Exception {
        Files.writeString(temp.resolve("mvnw.cmd"), "@echo off\r\n");
        List<List<String>> commands = RuntimeBootstrapper.developmentMavenCommands(temp, true);
        assertEquals(List.of("cmd.exe", "/d", "/c", "mvnw.cmd"), commands.get(0));
        assertEquals(List.of("cmd.exe", "/d", "/c", "mvn"), commands.get(1));
    }

    @Test
    void windowsFallsBackToSystemMavenWhenWrapperIsMissing() {
        List<List<String>> commands = RuntimeBootstrapper.developmentMavenCommands(temp, true);
        assertEquals(1, commands.size());
        assertEquals(List.of("cmd.exe", "/d", "/c", "mvn"), commands.get(0));
    }

    @Test
    void unixPrefersProjectWrapperBeforeSystemMaven() throws Exception {
        Path wrapper = temp.resolve("mvnw");
        Files.writeString(wrapper, "#!/bin/sh\n");
        List<List<String>> commands = RuntimeBootstrapper.developmentMavenCommands(temp, false);
        assertEquals(wrapper.toAbsolutePath().normalize().toString(), commands.get(0).get(0));
        assertEquals(List.of("mvn"), commands.get(1));
        assertTrue(commands.get(0).size() == 1);
    }
    @Test
    void developmentServerFingerprintChangesWhenReleaseRevisionChanges() throws Exception {
        Files.createDirectories(temp.resolve(".mvn"));
        Files.createDirectories(temp.resolve("server/src/main/java"));
        Files.createDirectories(temp.resolve("shared/src/main/java"));
        Files.writeString(temp.resolve("pom.xml"), "<project><version>${revision}</version></project>");
        Files.writeString(temp.resolve("server/pom.xml"), "<project/>");
        Files.writeString(temp.resolve("shared/pom.xml"), "<project/>");
        Files.writeString(temp.resolve("server/src/main/java/Server.java"), "class Server {}");
        Files.writeString(temp.resolve("shared/src/main/java/Shared.java"), "class Shared {}");
        Files.writeString(temp.resolve(".mvn/maven.config"), "-Drevision=1.0.0\n");

        String first = RuntimeBootstrapper.developmentServerFingerprint(temp);
        Files.writeString(temp.resolve(".mvn/maven.config"), "-Drevision=1.0.1\n");
        String second = RuntimeBootstrapper.developmentServerFingerprint(temp);

        assertTrue(!first.equals(second), "release revision must change the development backend cache identity");
    }

}
