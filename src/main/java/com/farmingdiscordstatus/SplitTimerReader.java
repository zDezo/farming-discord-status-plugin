package com.farmingdiscordstatus;

import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;
import net.runelite.client.plugins.timetracking.farming.FarmingTracker;

/** Reads individual public Time Tracking configuration records without internal API access. */
@Singleton
public class SplitTimerReader
{
    private static final String HESPORI_KEY = "5021." + VarbitID.FARMING_TRANSMIT_J;
    private static final String MUSHROOM_KEY = "13622." + VarbitID.FARMING_TRANSMIT_A;
    private static final String ANIMA_KEY = "4922." + VarbitID.FARMING_TRANSMIT_M;
    private static final String[] BELLADONNA_KEYS = {
        "5427." + VarbitID.FARMING_TRANSMIT_B,
        "12340." + VarbitID.FARMING_TRANSMIT_A
    };
    private static final String[] CACTUS_KEYS = {
        "13106." + VarbitID.FARMING_TRANSMIT_A,
        "4922." + VarbitID.FARMING_TRANSMIT_F
    };
    private static final String[] CORAL_KEYS = {
        "12581." + VarbitID.FARMING_TRANSMIT_A,
        "12581." + VarbitID.FARMING_TRANSMIT_B
    };
    private static final String[] SEAWEED_KEYS = {
        "15008." + VarbitID.FARMING_TRANSMIT_A,
        "15008." + VarbitID.FARMING_TRANSMIT_B
    };
    private static final String[] COMPOST_KEYS = {
        "10548." + VarbitID.FARMING_TRANSMIT_E,
        "11062." + VarbitID.FARMING_TRANSMIT_E,
        "6192." + VarbitID.FARMING_TRANSMIT_E,
        "12083." + VarbitID.FARMING_TRANSMIT_E,
        "6967." + VarbitID.FARMING_TRANSMIT_E,
        "14391." + VarbitID.FARMING_TRANSMIT_E,
        "4922." + VarbitID.FARMING_TRANSMIT_N,
        "13151." + VarbitID.FARMING_TRANSMIT_D
    };

    private final ConfigManager configManager;
    private final FarmingTracker farmingTracker;

    @Inject
    public SplitTimerReader(ConfigManager configManager, FarmingTracker farmingTracker)
    {
        this.configManager = configManager;
        this.farmingTracker = farmingTracker;
    }

    public Snapshot hespori()
    {
        Record record = record(HESPORI_KEY);
        if (record == null) return Snapshot.unknown();
        if (record.value >= 7 && record.value <= 8) return Snapshot.ready();
        if (record.value >= 4 && record.value <= 6)
        {
            return growing(record, 640, 4, record.value - 4);
        }
        return Snapshot.empty();
    }

    public Snapshot mushroom()
    {
        Record record = record(MUSHROOM_KEY);
        if (record == null) return Snapshot.unknown();
        if (between(record.value, 10, 15)) return Snapshot.ready();
        if (between(record.value, 4, 9)) return growing(record, 40, 7, record.value - 4);
        return Snapshot.empty();
    }

    public Snapshot anima()
    {
        Record record = record(ANIMA_KEY);
        if (record == null) return Snapshot.unknown();
        int stage;
        if (between(record.value, 8, 16)) stage = record.value - 8;
        else if (between(record.value, 17, 25)) stage = record.value - 17;
        else if (between(record.value, 26, 34)) stage = record.value - 26;
        else return Snapshot.empty();
        return growing(record, 640, 9, stage);
    }

    public Snapshot belladonna()
    {
        Aggregate aggregate = new Aggregate();
        for (String key : BELLADONNA_KEYS)
        {
            Record record = record(key);
            if (record == null) continue;
            if (record.value == 8) aggregate.add(Snapshot.ready());
            else if (between(record.value, 4, 7))
                aggregate.add(growing(record, 80, 5, record.value - 4));
            else aggregate.add(Snapshot.empty());
        }
        return aggregate.result();
    }

    public Snapshot cactus()
    {
        Aggregate aggregate = new Aggregate();
        for (String key : CACTUS_KEYS)
        {
            Record record = record(key);
            if (record == null) continue;
            if (between(record.value, 15, 18) || between(record.value, 39, 45))
                aggregate.add(Snapshot.ready());
            else if (between(record.value, 8, 14))
                aggregate.add(growing(record, 80, 8, record.value - 8));
            else if (record.value == 31) aggregate.add(growing(record, 80, 8, 7));
            else if (between(record.value, 32, 38))
                aggregate.add(growing(record, 10, 8, record.value - 32));
            else if (record.value == 58) aggregate.add(growing(record, 10, 8, 7));
            else aggregate.add(Snapshot.empty());
        }
        return aggregate.result();
    }

    public Snapshot coral()
    {
        Aggregate aggregate = new Aggregate();
        for (String key : CORAL_KEYS)
        {
            Record record = record(key);
            if (record == null) continue;
            int stage;
            if (between(record.value, 4, 8)) stage = record.value - 4;
            else if (between(record.value, 15, 19)) stage = record.value - 15;
            else if (between(record.value, 26, 30)) stage = record.value - 26;
            else
            {
                aggregate.add(Snapshot.empty());
                continue;
            }
            aggregate.add(growing(record, 40, 5, stage));
        }
        return aggregate.result();
    }

    public Snapshot seaweed()
    {
        Aggregate aggregate = new Aggregate();
        for (String key : SEAWEED_KEYS)
        {
            Record record = record(key);
            if (record == null) continue;
            if (record.value >= 8 && record.value <= 10) aggregate.add(Snapshot.ready());
            else if (record.value >= 4 && record.value <= 7)
                aggregate.add(growing(record, 10, 5, record.value - 4));
            else aggregate.add(Snapshot.empty());
        }
        return aggregate.result();
    }

    public Snapshot compost()
    {
        Aggregate aggregate = new Aggregate();
        for (int index = 0; index < COMPOST_KEYS.length; index++)
        {
            Record record = record(COMPOST_KEYS[index]);
            if (record == null) continue;
            boolean big = index == 6;
            if (isReadyCompost(record.value, big)) aggregate.add(Snapshot.ready());
            else if (record.value == 0) aggregate.add(Snapshot.empty());
            else if (compostGrowthStage(record.value, big) >= 0)
                aggregate.add(growing(record, 40, 3, compostGrowthStage(record.value, big)));
            else aggregate.add(new Snapshot("GROWING", 0));
        }
        return aggregate.result();
    }

    private Snapshot growing(Record record, int tickRate, int stages, int stage)
    {
        String profile = configManager.getRSProfileKey();
        long observedTick = farmingTracker.getTickTime(tickRate, 0, record.timestamp, profile);
        long readyAt = farmingTracker.getTickTime(tickRate, stages - 1 - stage,
            observedTick, profile);
        return readyAt <= Instant.now().getEpochSecond()
            ? Snapshot.ready() : new Snapshot("GROWING", readyAt);
    }

    private Record record(String key)
    {
        String stored = configManager.getRSProfileConfiguration(TimeTrackingConfig.CONFIG_GROUP, key);
        if (stored == null) return null;
        String[] parts = stored.split(":", 2);
        if (parts.length != 2) return null;
        try
        {
            return new Record(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }

    static boolean isReadyCompost(int value, boolean big)
    {
        if (between(value, 16, 30) || between(value, 48, 62)
            || between(value, 144, 158)) return true;
        if (big)
        {
            return value == 93 || value == 99 || value == 222
                || between(value, 78, 92) || between(value, 100, 114)
                || between(value, 176, 205) || between(value, 207, 221);
        }
        return value == 94 || value == 126 || between(value, 176, 190);
    }

    // Closed bins retain a growing-state record; predict completion using
    // the same 40-minute farming ticks and three stages as Time Tracking.
    static int compostGrowthStage(int value, boolean big)
    {
        if (between(value, 159, 160)) return value - 159;
        if (big)
        {
            if (between(value, 127, 128)) return value - 127;
            if (between(value, 97, 98)) return value - 97;
        }
        else
        {
            if (between(value, 31, 32)) return value - 31;
            if (between(value, 95, 96)) return value - 95;
        }
        return -1;
    }

    private static boolean between(int value, int minimum, int maximum)
    {
        return value >= minimum && value <= maximum;
    }

    private static final class Record
    {
        private final int value;
        private final long timestamp;

        private Record(int value, long timestamp)
        {
            this.value = value;
            this.timestamp = timestamp;
        }
    }

    private static final class Aggregate
    {
        private boolean known;
        private boolean ready;
        private boolean growing;
        private long soonest = Long.MAX_VALUE;

        private void add(Snapshot snapshot)
        {
            known = !"UNKNOWN".equals(snapshot.state);
            ready |= "READY".equals(snapshot.state);
            growing |= "GROWING".equals(snapshot.state);
            if (snapshot.readyAt > 0) soonest = Math.min(soonest, snapshot.readyAt);
        }

        private Snapshot result()
        {
            if (!known) return Snapshot.unknown();
            if (ready) return Snapshot.ready();
            if (growing) return new Snapshot("GROWING", soonest == Long.MAX_VALUE ? 0 : soonest);
            return Snapshot.empty();
        }
    }

    public static final class Snapshot
    {
        private final String state;
        private final long readyAt;

        private Snapshot(String state, long readyAt)
        {
            this.state = state;
            this.readyAt = readyAt;
        }

        private static Snapshot unknown() { return new Snapshot("UNKNOWN", 0); }
        private static Snapshot empty() { return new Snapshot("EMPTY", 0); }
        private static Snapshot ready() { return new Snapshot("READY", 0); }
        public String getState() { return state; }
        public long getReadyAt() { return readyAt; }
    }
}
