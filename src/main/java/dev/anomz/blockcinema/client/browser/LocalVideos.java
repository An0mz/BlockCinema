package dev.anomz.blockcinema.client.browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvPageRules;
import net.neoforged.fml.loading.FMLPaths;

/**
 * The player's own video files: the ones in the {@code blockcinema-videos} folder of the game folder.
 * A TV only ever shares a file's name, and each player's game plays the file of that name from its own
 * folder, so nothing else on a computer can be reached and no file is sent to anyone.
 */
public final class LocalVideos {
    public static final String FOLDER = "blockcinema-videos";

    @Nullable
    private static LocalVideoServer server;

    private LocalVideos() {
    }

    /** The videos folder, made if it wasn't there. */
    public static Path folder() {
        Path folder = FMLPaths.GAMEDIR.get().resolve(FOLDER);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            BlockCinema.LOGGER.warn("Couldn't make the {} folder", FOLDER, e);
        }
        return folder;
    }

    public static List<String> list() {
        return list(folder());
    }

    static List<String> list(Path folder) {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile).map(file -> file.getFileName().toString())
                    .filter(TvPageRules::localFileName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** The file called {@code name} right in {@code folder}, or null if there is none. */
    @Nullable
    static Path file(Path folder, String name) {
        if (!TvPageRules.localFileName(name)) {
            return null;
        }
        try {
            Path file = folder.resolve(name);
            return folder.equals(file.getParent()) && Files.isRegularFile(file) ? file : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /** An address the TV browsers can play this player's file {@code name} from, or null if they don't have it. */
    @Nullable
    static String url(String name) {
        Path folder = FMLPaths.GAMEDIR.get().resolve(FOLDER);
        if (file(folder, name) == null) {
            return null;
        }
        if (server == null) {
            try {
                server = new LocalVideoServer(file -> file(folder, file));
            } catch (IOException e) {
                BlockCinema.LOGGER.error("Couldn't start serving local videos", e);
                return null;
            }
        }
        return server.url(name);
    }

    static void stop() {
        if (server != null) {
            server.close();
            server = null;
        }
    }
}
