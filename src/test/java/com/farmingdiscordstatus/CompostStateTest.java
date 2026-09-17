package com.farmingdiscordstatus;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompostStateTest
{
    @Test
    public void finishedClosedBinsAreReadyBeforeOpening()
    {
        // Actual saved records behind the reported mismatch: normal
        // supercompost is 126, and Farming Guild supercompost is 99.
        assertTrue(SplitTimerReader.isReadyCompost(126, false));
        assertTrue(SplitTimerReader.isReadyCompost(99, true));
        assertTrue(SplitTimerReader.isReadyCompost(94, false));
        assertTrue(SplitTimerReader.isReadyCompost(93, true));
        assertTrue(SplitTimerReader.isReadyCompost(222, true));
    }

    @Test
    public void unfinishedBinsHavePredictableStagesAndAreNotImmediatelyReady()
    {
        assertFalse(SplitTimerReader.isReadyCompost(95, false));
        assertEquals(0, SplitTimerReader.compostGrowthStage(95, false));
        assertFalse(SplitTimerReader.isReadyCompost(98, true));
        assertEquals(1, SplitTimerReader.compostGrowthStage(98, true));
        assertEquals(-1, SplitTimerReader.compostGrowthStage(33, false));
        assertFalse(SplitTimerReader.isReadyCompost(0, false));
    }

    @Test
    public void openedUltraCompostStillReportsReady()
    {
        assertTrue(SplitTimerReader.isReadyCompost(190, false));
        assertTrue(SplitTimerReader.isReadyCompost(205, true));
        assertFalse(SplitTimerReader.isReadyCompost(205, false));
    }
}
