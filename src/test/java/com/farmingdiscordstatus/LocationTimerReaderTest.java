package com.farmingdiscordstatus;

import com.google.gson.Gson;
import java.util.*;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocationTimerReaderTest
{
    private static final long OBSERVED = 120000;
    private static final LocationTimerReader READER = new LocationTimerReader(null, null, null, new Gson());
    private static Map<String, List<LocationTimerReader.Location>> read(Map<String, String> data, long now, boolean leagues)
    {
        return READER.collect(data::get,
            (rate, ticks, timestamp) -> timestamp - timestamp % (rate * 60L) + ticks * rate * 60L, now, leagues);
    }

    private static LocationTimerReader.Location find(Map<String, List<LocationTimerReader.Location>> result, String category, String id)
    {
        return result.get(category).stream().filter(location -> location.id.equals(id)).findFirst().orElseThrow();
    }

    @Test
    public void allotmentsWaitForBothPatchesAndKeepOtherLocationsIndependent()
    {
        Map<String, String> records = new HashMap<>();
        records.put("4922." + VarbitID.FARMING_TRANSMIT_C, "8:" + OBSERVED);
        records.put("4922." + VarbitID.FARMING_TRANSMIT_D, "9:" + OBSERVED);
        records.put("11062." + VarbitID.FARMING_TRANSMIT_A, "9:" + OBSERVED);
        Map<String, List<LocationTimerReader.Location>> result = read(records, OBSERVED + 601, false);
        LocationTimerReader.Location guild = find(result, "ALLOTMENT", "region-4922-allotment");
        assertEquals("GROWING", guild.state);
        assertEquals(OBSERVED + 1200, guild.readyAt);
        assertEquals("READY", find(result, "ALLOTMENT", "region-11062-allotment").state);
        assertEquals("READY", find(read(records, OBSERVED + 1200, false), "ALLOTMENT", guild.id).state);
    }

    @Test
    public void malformedMissingAndWeedRecordsDoNotBecomeReady()
    {
        for (String record : Arrays.asList("bad", "8:bad", "999:120000", "8:0", "8:120000:extra"))
        {
            assertEquals("UNKNOWN", find(read(Collections.singletonMap("4922." + VarbitID.FARMING_TRANSMIT_E, record),
                OBSERVED, false), "HERB", "region-4922-herb").state);
        }
        assertEquals("UNKNOWN", find(read(Collections.emptyMap(), OBSERVED, false), "HERB", "region-4922-herb").state);
        assertEquals("EMPTY", find(read(Collections.singletonMap("4922." + VarbitID.FARMING_TRANSMIT_E, "0:" + OBSERVED),
            OBSERVED + 999999, false), "HERB", "region-4922-herb").state);
    }

    @Test
    public void sickTreesNeverGeneratePredictedReadyTimers()
    {
        LocationTimerReader.Location tree = find(read(Collections.singletonMap("4922." + VarbitID.FARMING_TRANSMIT_G,
            "95:" + OBSERVED), OBSERVED + 999999, false), "TREE", "region-4922-tree");
        assertEquals("DISEASED", tree.state);
        assertEquals(0, tree.readyAt);
    }

    @Test
    public void leaguesAndFarmingTickOffsetsAreRespected()
    {
        Map<String, String> records = Collections.singletonMap("4922." + VarbitID.FARMING_TRANSMIT_E, "4:" + OBSERVED);
        assertEquals(OBSERVED + 4800, find(read(records, OBSERVED, false), "HERB", "region-4922-herb").readyAt);
        assertEquals(OBSERVED + 960, find(read(records, OBSERVED, true), "HERB", "region-4922-herb").readyAt);
        Map<String, List<LocationTimerReader.Location>> shifted = READER.collect(records::get,
            (rate, ticks, timestamp) -> timestamp + 120 - ((timestamp + 120) % (rate * 60L)) + ticks * rate * 60L - 120,
            OBSERVED, false);
        assertEquals(OBSERVED + 4680, find(shifted, "HERB", "region-4922-herb").readyAt);
    }

    @Test
    public void normalAndGuildClosedCompostBinsAreReady()
    {
        Map<String, String> records = new HashMap<>();
        records.put("11062." + VarbitID.FARMING_TRANSMIT_E, "126:" + OBSERVED);
        records.put("4922." + VarbitID.FARMING_TRANSMIT_N, "99:" + OBSERVED);
        Map<String, List<LocationTimerReader.Location>> result = read(records, OBSERVED, false);
        assertEquals("READY", find(result, "COMPOST", "region-11062-compost").state);
        assertEquals("READY", find(result, "COMPOST", "region-4922-big_compost").state);
    }

    @Test
    public void birdhousesNeedSeedsAndCompleteIndependentlyAfterFiftyMinutes()
    {
        Map<String, String> records = new HashMap<>();
        records.put("birdhouse.1626", "3:" + OBSERVED);
        records.put("birdhouse.1627", "3:" + (OBSERVED + 60));
        records.put("birdhouse.1628", "1:" + OBSERVED);
        records.put("birdhouse.1629", "0:" + OBSERVED);
        Map<String, List<LocationTimerReader.Location>> result = read(records, OBSERVED + 3000, false);
        assertEquals("READY", find(result, "BIRD_HOUSE", "birdhouse-meadow_north").state);
        assertEquals("GROWING", find(result, "BIRD_HOUSE", "birdhouse-meadow_south").state);
        assertEquals("FILLING", find(result, "BIRD_HOUSE", "birdhouse-valley_north").state);
        assertEquals("EMPTY", find(result, "BIRD_HOUSE", "birdhouse-valley_south").state);
    }

    @Test
    public void catalogIncludesEveryCategoryWithUniqueStableLocationIds()
    {
        Map<String, List<LocationTimerReader.Location>> result = read(Collections.emptyMap(), OBSERVED, false);
        assertEquals(17, result.size());
        for (List<LocationTimerReader.Location> locations : result.values())
        {
            Set<String> ids = new HashSet<>();
            assertTrue(locations.size() <= 40);
            for (LocationTimerReader.Location location : locations)
            {
                assertTrue(ids.add(location.id));
                assertTrue(location.name.length() <= 80);
                assertEquals("UNKNOWN", location.state);
            }
        }
        assertEquals("Farming Guild", find(result, "TREE", "region-4922-tree").name);
        assertEquals("Farming Guild (Redwood)", find(result, "TREE", "region-4922-redwood").name);
        String json = new Gson().toJson(result);
        assertTrue(json.contains("\"id\":\"region-4922-tree\""));
        assertTrue(json.length() < 40_000);
    }
}
