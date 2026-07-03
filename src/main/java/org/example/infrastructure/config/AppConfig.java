package org.example.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record AppConfig(
        String nodeId,
        int p2pPort,
        int apiPort,
        List<String> bootstrapPeers,
        String dataDir,
        String logLevel,
        int maxPeers,
        int heartbeatIntervalMs
) {
    private static final String CONFIG_FILE = "blokceng.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new ParameterNamesModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static AppConfig defaults() {
        return new AppConfig(
                "node-" + System.currentTimeMillis(),
                8080,
                8000,
                List.of(),
                "data",
                "INFO",
                50,
                30000
        );
    }

    public static AppConfig load() {
        Path path = Path.of(CONFIG_FILE);
        if (!Files.exists(path)) {
            AppConfig defaults = defaults();
            defaults.save();
            return defaults;
        }
        try {
            return MAPPER.readValue(path.toFile(), AppConfig.class);
        } catch (IOException e) {
            System.err.println("Error loading config: " + e.getMessage());
            return defaults();
        }
    }

    public void save() {
        try {
            MAPPER.writeValue(Path.of(CONFIG_FILE).toFile(), this);
        } catch (IOException e) {
            System.err.println("Error saving config: " + e.getMessage());
        }
    }
}
