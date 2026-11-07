package dev.anomz.blockcinema.client;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.function.Function;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.anomz.blockcinema.BlockCinema;

/** Reads and writes the small JSON files this mod keeps in {@code config/blockcinema}. */
public final class JsonFile {
    private JsonFile() {
    }

    /** What {@code parse} makes of the saved object; empty if there is no file or it can't be read (logged). */
    public static <T> Optional<T> read(Path file, String problem, Function<JsonObject, T> parse) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return Optional.ofNullable(parse.apply(JsonParser.parseReader(reader).getAsJsonObject()));
        } catch (IOException | RuntimeException e) {
            BlockCinema.LOGGER.warn(problem, e);
            return Optional.empty();
        }
    }

    /** Writes through a temporary file, so a crash can't leave a half-written one. */
    public static void write(Path file, JsonObject json, String problem) {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temp, json.toString());
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            BlockCinema.LOGGER.error(problem, e);
        }
    }
}
