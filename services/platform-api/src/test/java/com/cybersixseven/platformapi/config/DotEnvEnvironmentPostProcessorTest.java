package com.cybersixseven.platformapi.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DotEnvEnvironmentPostProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void parseDotEnvReadsKeysSkipsCommentsAndHonorsQuotes() throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(
                envFile,
                """
                # comment
                CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:3001
                QUOTED="http://localhost:3000"
                EMPTY=

                export ALSO=ok
                """);

        Map<String, Object> values = DotEnvEnvironmentPostProcessor.parseDotEnv(envFile);

        assertEquals("http://localhost:3000,http://localhost:3001", values.get("CORS_ALLOWED_ORIGINS"));
        assertEquals("http://localhost:3000", values.get("QUOTED"));
        assertEquals("", values.get("EMPTY"));
        assertEquals("ok", values.get("ALSO"));
    }

    @Test
    void findDotEnvWalksParents() throws Exception {
        Path nested = Files.createDirectories(tempDir.resolve("a/b"));
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "CORS_ALLOWED_ORIGINS=http://localhost:3000\n");

        assertEquals(envFile, DotEnvEnvironmentPostProcessor.findDotEnv(nested));
    }
}
