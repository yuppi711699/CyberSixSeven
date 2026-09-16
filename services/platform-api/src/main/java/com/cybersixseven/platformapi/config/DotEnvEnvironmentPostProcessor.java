package com.cybersixseven.platformapi.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Loads a {@code .env} file into the Spring {@link ConfigurableEnvironment}.
 *
 * <p>Does not override variables already present in the process environment. Searches {@code
 * user.dir} and its parents for {@code .env}.
 */
public final class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "dotenvFile";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        Path envFile = findDotEnv(Path.of("").toAbsolutePath());
        if (envFile == null) {
            return;
        }

        Map<String, Object> parsed = parseDotEnv(envFile);
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : parsed.entrySet()) {
            // Process env wins over .env (12-factor).
            if (System.getenv(entry.getKey()) == null) {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        if (values.isEmpty()) {
            return;
        }

        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, values));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    static Path findDotEnv(Path start) {
        Path dir = start.toAbsolutePath().normalize();
        while (dir != null) {
            Path candidate = dir.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    static Map<String, Object> parseDotEnv(Path envFile) {
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (String rawLine : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                if (key.isEmpty()) {
                    continue;
                }
                String value = stripQuotes(line.substring(eq + 1).trim());
                values.put(key, value);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read .env at " + envFile, ex);
        }
        return values;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
