package com.farmingdiscordstatus;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.time.Instant;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.timetracking.SummaryState;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;
import net.runelite.client.plugins.timetracking.farming.FarmingTracker;
import net.runelite.client.plugins.timetracking.hunter.BirdHouseTracker;
import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TimeTrackingCompatibilityTest
{
    private final Client client = mock(Client.class);
    private final ConfigManager configManager = mock(ConfigManager.class);
    private final OkHttpClient httpClient = mock(OkHttpClient.class);
    private final TimeTrackingConfig timeConfig = mock(TimeTrackingConfig.class);
    private final FarmingDiscordStatusPlugin plugin = new FarmingDiscordStatusPlugin();
    private Injector injector;

    @Before
    public void createIndependentPluginInjector()
    {
        when(configManager.getConfig(TimeTrackingConfig.class)).thenReturn(timeConfig);
        when(configManager.getConfig(FarmingDiscordStatusConfig.class))
            .thenReturn(mock(FarmingDiscordStatusConfig.class));
        injector = Guice.createInjector(new AbstractModule()
        {
            @Override protected void configure()
            {
                bind(Client.class).toInstance(client);
                bind(ClientThread.class).toInstance(mock(ClientThread.class));
                bind(ConfigManager.class).toInstance(configManager);
                bind(ItemManager.class).toInstance(mock(ItemManager.class));
                bind(Notifier.class).toInstance(mock(Notifier.class));
                bind(OkHttpClient.class).toInstance(httpClient);
                bind(Gson.class).toInstance(new Gson());
            }
        }, plugin);
        injector.injectMembers(plugin);
    }

    @Test
    public void readersInjectWithoutTimeTrackingPluginServices()
    {
        assertEquals(0, FarmingDiscordStatusPlugin.class.getAnnotationsByType(PluginDependency.class).length);
        assertSame(timeConfig, injector.getInstance(TimeTrackingConfig.class));
        assertNotNull(injector.getInstance(FarmingTracker.class));
        assertNotNull(injector.getInstance(BirdHouseTracker.class));
        assertNotNull(injector.getInstance(SplitTimerReader.class));
    }

    @Test
    public void statusRefreshReadsSavedBirdhousesAndDoesNotSendWhenUnlinked()
    {
        String prefix = TimeTrackingConfig.BIRD_HOUSE + ".";
        when(configManager.getRSProfileConfiguration(eq(TimeTrackingConfig.CONFIG_GROUP), startsWith(prefix)))
            .thenReturn("0:" + Instant.now().getEpochSecond());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        plugin.onGameTick(new GameTick());
        assertEquals(SummaryState.EMPTY, injector.getInstance(BirdHouseTracker.class).getSummary());
        verify(configManager, atLeastOnce()).getRSProfileConfiguration(eq(TimeTrackingConfig.CONFIG_GROUP), startsWith(prefix));
        verifyNoInteractions(httpClient);
    }

    @Test
    public void loginScreenDoesNotReadTimerRecordsOrSubmitStatus()
    {
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        clearInvocations(configManager);
        plugin.onGameTick(new GameTick());
        verify(configManager, never()).getRSProfileConfiguration(anyString(), anyString());
        verifyNoInteractions(httpClient);
    }
}
