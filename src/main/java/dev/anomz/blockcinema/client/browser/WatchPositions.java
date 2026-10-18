package dev.anomz.blockcinema.client.browser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.anomz.blockcinema.BlockCinema;

/**
 * Where the player stopped watching each video, so it resumes there next time. Kept on this computer in
 * {@code config/blockcinema/positions.json}, keyed by the TV page URL (which only holds video IDs or the
 * link the player typed). Jellyfin keeps its own positions on the server instead.
 */
public final class WatchPositions {
    /** The oldest entries are forgotten beyond this many. */
    static final int MAX_ENTRIES = 500;
    /** Stopping this close to the start doesn't count as watching. */
    static final double MIN_SECONDS = 10;
    /** Stopping this close to the end counts as finished, so it starts over next time. */
    static final double END_SECONDS = 30;

    private final Path file;
    private final LinkedHashMap<String, Double> positions = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
            return size() > MAX_ENTRIES;
        }
    };
    private boolean loaded;

    WatchPositions(Path file) {
        this.file = file;
    }

    /** Seconds to resume {@code key} at, or 0. */
    double get(String key) {
        load();
        Double seconds = positions.get(key);
        return seconds == null ? 0 : seconds;
    }

    /** Stopped at {@code time} of a video {@code length} seconds long (0 when live or unknown). */
    void remember(String key, double time, double length) {
        if (length <= 0) {
            return;
        }
        load();
        boolean changed;
        if (time < MIN_SECONDS || time > length - END_SECONDS) {
            changed = positions.remove(key) != null;
        } else {
            positions.put(key, Math.floor(time));
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            for (Map.Entry<String, JsonElement> entry : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                positions.put(entry.getKey(), entry.getValue().getAsDouble());
            }
        } catch (IOException | RuntimeException e) {
            BlockCinema.LOGGER.warn("Couldn't read the saved watch positions", e);
        }
    }

    private void save() {
        JsonObject json = new JsonObject();
        positions.forEach(json::addProperty);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                writer.write(json.toString());
            }
        } catch (IOException e) {
            BlockCinema.LOGGER.error("Couldn't save the watch positions", e);
        }
    }
}
