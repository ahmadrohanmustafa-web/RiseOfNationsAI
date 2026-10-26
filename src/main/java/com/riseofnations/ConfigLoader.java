package com.riseofnations;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.riseofnations.core.config.RonSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads config/riseofnations.json. Never crashes the game:
 *  - missing file  -> writes the defaults
 *  - broken file   -> uses defaults and leaves the broken file alone so you can fix it
 *  - bad numbers   -> clamped to safe values (and logged)
 */
final class ConfigLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigLoader() { }

    static RonSettings load(Path file) {
        RonSettings settings = new RonSettings();
        if (Files.exists(file)) {
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                RonSettings parsed = GSON.fromJson(text, RonSettings.class);
                if (parsed != null) {
                    settings = parsed;
                }
            } catch (IOException | JsonParseException e) {
                RiseOfNationsMod.LOGGER.warn("Could not read {} ({}). Using defaults and NOT overwriting the file.", file, e.getMessage());
                return settings;
            }
        } else {
            write(file, settings);
        }
        for (String note : settings.sanitize()) {
            RiseOfNationsMod.LOGGER.warn("Config value adjusted: {}", note);
        }
        return settings;
    }

    private static void write(Path file, RonSettings settings) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(settings), StandardCharsets.UTF_8);
        } catch (IOException e) {
            RiseOfNationsMod.LOGGER.warn("Could not write default config {}: {}", file, e.getMessage());
        }
    }
}
