package com.howlstudio.tebex;

import java.nio.file.*;
import java.io.*;

public class TebexConfig {
    private static final String CONFIG_FILE = "tebex-config.txt";
    private String secretKey = "";
    private int pollInterval = 90; // seconds, overridden by API
    private final Path dataDir;

    public TebexConfig(Path dataDir) {
        this.dataDir = dataDir;
        load();
    }

    private void load() {
        Path file = dataDir.resolve(CONFIG_FILE);
        if (!Files.exists(file)) {
            save(); // write defaults
            return;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                line = line.trim();
                if (line.startsWith("secret=")) secretKey = line.substring(7).trim();
                if (line.startsWith("poll_interval=")) {
                    try { pollInterval = Integer.parseInt(line.substring(14).trim()); }
                    catch (NumberFormatException ignored) {}
                }
            }
        } catch (IOException e) {
            System.err.println("[TebexConnect] Failed to read config: " + e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(dataDir);
            Path file = dataDir.resolve(CONFIG_FILE);
            String content = "# TebexConnect Configuration\n" +
                "# Get your secret key from your Tebex dashboard: Game Server Settings\n" +
                "secret=" + secretKey + "\n" +
                "poll_interval=" + pollInterval + "\n";
            Files.writeString(file, content);
        } catch (IOException e) {
            System.err.println("[TebexConnect] Failed to write config: " + e.getMessage());
        }
    }

    public String getSecretKey() { return secretKey; }
    public int getPollInterval() { return pollInterval; }
    public void setPollInterval(int s) { this.pollInterval = s; }
    public boolean isConfigured() { return !secretKey.isBlank(); }
}
