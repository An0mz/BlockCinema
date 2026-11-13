package dev.anomz.blockcinema.client;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvPageRules;
import dev.anomz.blockcinema.TvQueueEntry;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.client.browser.VideoLinks;
import net.neoforged.fml.loading.FMLPaths;

/**
 * The player's favorites and the last videos they put on a TV, latest first, kept on this computer in
 * {@code config/blockcinema/saved.json}. Jellyfin isn't kept: its library has its own.
 */
public final class SavedVideos {
    static final int MAX_HISTORY = 50;
    static final int MAX_FAVORITES = 200;
    private static SavedVideos instance;

    public record Entry(TvService service, String page, String title) {
    }

    private final Path file;
    private final List<Entry> favorites = new ArrayList<>();
    private final List<Entry> history = new ArrayList<>();
    private final Map<String, String> titles = new HashMap<>();
    private boolean loaded;

    SavedVideos(Path file) {
        this.file = file;
    }

    public static SavedVideos get() {
        if (instance == null) {
            instance = new SavedVideos(FMLPaths.CONFIGDIR.get().resolve(BlockCinema.MODID).resolve("saved.json"));
        }
        return instance;
    }

    public List<Entry> favorites() {
        load();
        return Collections.unmodifiableList(favorites);
    }

    public List<Entry> history() {
        load();
        return Collections.unmodifiableList(history);
    }

    public boolean isFavorite(String page) {
        return favorites().stream().anyMatch(entry -> entry.page().equals(page));
    }

    public void played(TvService service, String page) {
        if (service == TvService.JELLYFIN) {
            return;
        }
        load();
        add(history, new Entry(service, page, title(page)), MAX_HISTORY);
        save();
    }

    public void toggleFavorite(TvService service, String page) {
        if (service == TvService.JELLYFIN) {
            return;
        }
        load();
        Entry entry = new Entry(service, page, title(page));
        if (!favorites.removeIf(saved -> saved.page().equals(page))) {
            add(favorites, entry, MAX_FAVORITES);
        }
        save();
    }

    /** The page told us what its video is called. */
    public void rename(String page, String title) {
        load();
        titles.put(page, title);
        boolean changed = false;
        for (List<Entry> list : List.of(favorites, history)) {
            for (int i = 0; i < list.size(); i++) {
                Entry entry = list.get(i);
                if (entry.page().equals(page) && !entry.title().equals(title)) {
                    list.set(i, new Entry(entry.service(), page, title));
                    changed = true;
                }
            }
        }
        if (changed) {
            save();
        }
    }

    public void clearHistory() {
        load();
        history.clear();
        save();
    }

    private String title(String page) {
        if (titles.containsKey(page)) {
            return titles.get(page);
        }
        for (List<Entry> list : List.of(favorites, history)) {
            for (Entry entry : list) {
                if (entry.page().equals(page)) {
                    return entry.title();
                }
            }
        }
        return VideoLinks.label(page);
    }

    private static void add(List<Entry> list, Entry entry, int max) {
        list.removeIf(saved -> saved.page().equals(entry.page()));
        list.add(0, entry);
        if (list.size() > max) {
            list.remove(list.size() - 1);
        }
    }

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        JsonFile.read(file, "Couldn't read the saved favorites and history", json -> {
            read(json, "favorites", favorites);
            read(json, "history", history);
            return json;
        });
    }

    private static void read(JsonObject json, String name, List<Entry> into) {
        if (!json.has(name)) {
            return;
        }
        for (JsonElement element : json.getAsJsonArray(name)) {
            JsonObject saved = element.getAsJsonObject();
            String page = saved.get("page").getAsString();
            for (TvService service : TvService.values()) {
                if (service != TvService.JELLYFIN && service.id().equals(saved.get("service").getAsString())
                        && TvPageRules.valid(service, page)) {
                    into.add(new Entry(service, page, TvQueueEntry.cleanTitle(saved.get("title").getAsString())));
                }
            }
        }
    }

    private void save() {
        JsonObject json = new JsonObject();
        json.add("favorites", write(favorites));
        json.add("history", write(history));
        JsonFile.write(file, json, "Couldn't save the favorites and history");
    }

    private static JsonArray write(List<Entry> entries) {
        JsonArray array = new JsonArray();
        for (Entry entry : entries) {
            JsonObject saved = new JsonObject();
            saved.addProperty("service", entry.service().id());
            saved.addProperty("page", entry.page());
            saved.addProperty("title", entry.title());
            array.add(saved);
        }
        return array;
    }
}
