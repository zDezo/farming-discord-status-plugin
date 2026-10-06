package com.farmingdiscordstatus;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;
import net.runelite.client.plugins.timetracking.farming.FarmingTracker;

/** Public Time Tracking records decoded with the bundled, versioned data table. */
@Singleton
public final class LocationTimerReader
{
    private static final JsonObject CATALOG = loadCatalog();
    private final ConfigManager configManager;
    private final FarmingTracker farmingTracker;
    private final Client client;

    @Inject
    public LocationTimerReader(ConfigManager configManager, FarmingTracker farmingTracker, Client client)
    {
        this.configManager = configManager;
        this.farmingTracker = farmingTracker;
        this.client = client;
    }

    private static JsonObject loadCatalog()
    {
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(
            LocationTimerReader.class.getResourceAsStream("patch-catalog.json")), StandardCharsets.UTF_8))
        {
            return new Gson().fromJson(reader, JsonObject.class);
        }
        catch (java.io.IOException exception)
        {
            throw new IllegalStateException("Cannot read bundled patch catalog", exception);
        }
    }

    public Map<String, List<Location>> collect()
    {
        String profile = configManager.getRSProfileKey();
        boolean leagues = client.getWorldType().contains(WorldType.SEASONAL)
            && !client.getWorldType().contains(WorldType.DEADMAN);
        return collect(key -> configManager.getRSProfileConfiguration(TimeTrackingConfig.CONFIG_GROUP, key),
            (rate, ticks, timestamp) -> farmingTracker.getTickTime(rate, ticks, timestamp, profile),
            Instant.now().getEpochSecond(), leagues);
    }

    interface TickClock { long at(int rate, int ticks, long timestamp); }

    static Map<String, List<Location>> collect(Function<String, String> records, TickClock clock, long now, boolean leagues)
    {
        Map<String, Map<String, List<Location>>> groups = new LinkedHashMap<>();
        for (JsonElement element : CATALOG.getAsJsonArray("patches"))
        {
            JsonObject patch = element.getAsJsonObject();
            String category = patch.get("category").getAsString();
            Location location = decode(patch, records.apply(patch.get("key").getAsString()), clock, now, leagues);
            groups.computeIfAbsent(category, key -> new LinkedHashMap<>())
                .computeIfAbsent(location.id, key -> new ArrayList<>()).add(location);
        }
        Map<String, List<Location>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<Location>>> category : groups.entrySet())
        {
            List<Location> locations = new ArrayList<>();
            for (List<Location> patches : category.getValue().values()) locations.add(aggregate(patches));
            result.put(category.getKey(), locations);
        }
        List<Location> birdhouses = new ArrayList<>();
        for (JsonElement element : CATALOG.getAsJsonArray("birdhouses"))
        {
            JsonObject house = element.getAsJsonObject();
            long[] record = record(records.apply(house.get("key").getAsString()));
            Location location = location(house, "UNKNOWN", 0);
            if (record != null && record[0] >= 0 && record[0] <= house.get("maxValue").getAsInt())
            {
                if (record[0] == 0) location = location(house, "EMPTY", 0);
                else if (record[0] % 3 != 0) location = location(house, "FILLING", 0);
                else
                {
                    long readyAt = record[1] + house.get("duration").getAsInt();
                    location = location(house, readyAt <= now ? "READY" : "GROWING", readyAt <= now ? 0 : readyAt);
                }
            }
            birdhouses.add(location);
        }
        result.put("BIRD_HOUSE", birdhouses);
        return result;
    }

    private static Location decode(JsonObject patch, String stored, TickClock clock, long now, boolean leagues)
    {
        long[] record = record(stored);
        JsonArray values = CATALOG.getAsJsonObject("decoders").getAsJsonArray(patch.get("implementation").getAsString());
        if (record == null || record[0] < 0 || record[0] >= values.size() || values.get((int) record[0]).isJsonNull())
            return location(patch, "UNKNOWN", 0);
        JsonArray decoded = values.get((int) record[0]).getAsJsonArray();
        String state = decoded.get(0).getAsString();
        if (!state.equals("GROWING") && !state.equals("HARVESTABLE")) return location(patch, state, 0);
        int rate = decoded.get(1).getAsInt();
        int ticks = decoded.get(2).getAsInt();
        if (leagues) rate /= 5;
        long readyAt = rate <= 0 || ticks <= 0 ? 0 : clock.at(rate, ticks, clock.at(rate, 0, record[1]));
        return location(patch, readyAt <= now ? "READY" : "GROWING", readyAt <= now ? 0 : readyAt);
    }

    private static long[] record(String stored)
    {
        if (stored == null) return null;
        String[] parts = stored.split(":", -1);
        if (parts.length != 2) return null;
        try
        {
            long timestamp = Long.parseLong(parts[1]);
            return timestamp > 0 ? new long[]{ Integer.parseInt(parts[0]), timestamp } : null;
        }
        catch (NumberFormatException ignored) { return null; }
    }

    private static Location location(JsonObject patch, String state, long readyAt)
    {
        return new Location(patch.get("locationId").getAsString(), patch.get("location").getAsString(), state, readyAt);
    }

    static Location aggregate(List<Location> patches)
    {
        // Wait for every growing patch at this location, ignoring empty or
        // unobserved patches. For example, north/south allotments share one ping.
        for (String state : Arrays.asList("GROWING", "READY", "DISEASED", "DEAD", "FILLING", "EMPTY", "UNKNOWN"))
        {
            if (patches.stream().anyMatch(patch -> patch.state.equals(state)))
            {
                long readyAt = state.equals("GROWING")
                    ? patches.stream().filter(patch -> patch.state.equals(state)).mapToLong(patch -> patch.readyAt).max().orElse(0) : 0;
                Location first = patches.get(0);
                return new Location(first.id, first.name, state, readyAt);
            }
        }
        throw new IllegalArgumentException("No patches at this location");
    }

    static final class Location
    {
        final String id;
        final String name;
        final String state;
        final long readyAt;
        Location(String id, String name, String state, long readyAt)
        {
            this.id = id; this.name = name; this.state = state; this.readyAt = readyAt;
        }
    }
}
