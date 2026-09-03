package com.farmingdiscordstatus;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.timetracking.SummaryState;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.client.plugins.timetracking.TimeTrackingPlugin;
import net.runelite.client.plugins.timetracking.farming.FarmingTracker;
import net.runelite.http.api.RuneLiteAPI;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@PluginDescriptor(
    name = "Farming Discord Status",
    description = "Sends farming timer states to a private Discord dashboard and ready alerts",
    tags = {"farming", "discord", "timers", "notifications"}
)
@PluginDependency(TimeTrackingPlugin.class)
public class FarmingDiscordStatusPlugin extends Plugin
{
    private static final String API_TOKEN_KEY = "apiToken";
    private static final String BACKEND_URL = "https://farming-status-bot.onrender.com/";
    private static final int UPDATE_TICKS = 50;
    private static final Tab[] DISPLAY_TABS = {
        Tab.HERB, Tab.TREE, Tab.FRUIT_TREE, Tab.ALLOTMENT, Tab.FLOWER,
        Tab.BUSH, Tab.HOPS, Tab.GRAPE
    };

    @Inject private Client client;
    @Inject private FarmingTracker farmingTracker;
    @Inject private SplitTimerReader splitTimerReader;
    @Inject private FarmingDiscordStatusConfig config;
    @Inject private ConfigManager configManager;
    @Inject private OkHttpClient httpClient;
    @Inject private Gson gson;

    private int ticksUntilUpdate;
    private boolean requestInFlight;

    @Provides
    FarmingDiscordStatusConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(FarmingDiscordStatusConfig.class);
    }

    @Override
    protected void startUp()
    {
        configManager.unsetConfiguration(FarmingDiscordStatusConfig.GROUP, "webhookUrl");
        configManager.unsetConfiguration(FarmingDiscordStatusConfig.GROUP, "discordUserId");
        configManager.unsetConfiguration(FarmingDiscordStatusConfig.GROUP, "dashboardMessageId");
        configManager.unsetConfiguration(FarmingDiscordStatusConfig.GROUP, "alertMessageId");
        configManager.unsetConfiguration(FarmingDiscordStatusConfig.GROUP, "previousStates");
        configManager.unsetConfiguration(FarmingDiscordStatusConfig.GROUP, "backendUrl");
        ticksUntilUpdate = 1;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!FarmingDiscordStatusConfig.GROUP.equals(event.getGroup())) return;
        ticksUntilUpdate = 1;
        if ("sendTestPing".equals(event.getKey()) && Boolean.parseBoolean(event.getNewValue()))
        {
            HttpUrl backend = backendUrl();
            String token = stored(API_TOKEN_KEY);
            if (backend != null && token != null && !token.isEmpty())
            {
                callBackend(backend, "api/test", "POST", new LinkedHashMap<>(), token, false);
            }
            configManager.setConfiguration(FarmingDiscordStatusConfig.GROUP, "sendTestPing", false);
        }
        if ("linkAccount".equals(event.getKey()) && Boolean.parseBoolean(event.getNewValue()))
        {
            linkAccount();
            configManager.setConfiguration(FarmingDiscordStatusConfig.GROUP, "linkAccount", false);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (client.getGameState() != GameState.LOGGED_IN || requestInFlight) return;
        if (--ticksUntilUpdate > 0) return;
        ticksUntilUpdate = UPDATE_TICKS;
        farmingTracker.loadCompletionTimes();
        List<Category> categories = collectCategories();
        HttpUrl backend = backendUrl();
        String token = stored(API_TOKEN_KEY);
        if (backend != null && token != null && !token.isEmpty())
        {
            callBackend(backend, "api/status", "PUT", backendPayload(categories), token, true);
            return;
        }
    }

    private void linkAccount()
    {
        HttpUrl backend = backendUrl();
        String code = config.linkCode().trim();
        if (backend == null || code.isEmpty()) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        callBackend(backend, "api/link", "POST", payload, null, false);
    }

    private Map<String, Object> backendPayload(List<Category> categories)
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Category category : categories)
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", category.key);
            row.put("name", category.name);
            row.put("state", category.state);
            row.put("readyAt", category.readyAt);
            rows.add(row);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("player", client.getLocalPlayer() == null ? "Unknown player" : client.getLocalPlayer().getName());
        payload.put("updatedAt", System.currentTimeMillis());
        payload.put("readyPings", config.readyPings());
        payload.put("categories", rows);
        return payload;
    }

    private void callBackend(HttpUrl backend, String path, String method, Map<String, Object> payload,
        String token, boolean updateRequest)
    {
        HttpUrl.Builder url = backend.newBuilder();
        for (String segment : path.split("/")) url.addPathSegment(segment);
        Request.Builder request = new Request.Builder().url(url.build());
        if (token != null) request.header("Authorization", "Bearer " + token);
        RequestBody body = jsonBody(payload);
        if ("PUT".equals(method)) request.put(body); else request.post(body);
        if (updateRequest) requestInFlight = true;
        httpClient.newCall(request.build()).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException exception)
            {
                if (updateRequest) requestInFlight = false;
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException
            {
                try (Response ignored = response)
                {
                    if (response.code() == 401 && token != null)
                    {
                        configManager.unsetConfiguration(FarmingDiscordStatusConfig.GROUP, API_TOKEN_KEY);
                    }
                    if (token == null && response.isSuccessful() && response.body() != null)
                    {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> result = gson.fromJson(response.body().charStream(), Map.class);
                        if (result.get("token") != null)
                        {
                            store(API_TOKEN_KEY, result.get("token").toString());
                            configManager.setConfiguration(FarmingDiscordStatusConfig.GROUP, "linkCode", "");
                            ticksUntilUpdate = 1;
                        }
                    }
                }
                finally
                {
                    if (updateRequest) requestInFlight = false;
                }
            }
        });
    }

    private List<Category> collectCategories()
    {
        long now = Instant.now().getEpochSecond();
        List<Category> categories = new ArrayList<>();
        for (Tab tab : DISPLAY_TABS)
        {
            SummaryState summary = farmingTracker.getSummary(tab);
            long completion = farmingTracker.getCompletionTime(tab);
            String state;
            if (summary == SummaryState.COMPLETED || completion == 0) state = "READY";
            else if (summary == SummaryState.EMPTY) state = "EMPTY";
            else if (summary == SummaryState.UNKNOWN) state = "UNKNOWN";
            else state = "GROWING";
            categories.add(new Category(tab.name(), displayName(tab),
                state, completion > now ? completion : 0));
        }
        addSplitCategory(categories, "MUSHROOM", "Mushroom", splitTimerReader.mushroom());
        addSplitCategory(categories, "ANIMA", "Anima", splitTimerReader.anima());
        addSplitCategory(categories, "BELLADONNA", "Belladonna", splitTimerReader.belladonna());
        addSplitCategory(categories, "CACTUS", "Cactus", splitTimerReader.cactus());
        addSplitCategory(categories, "CORAL", "Coral", splitTimerReader.coral());
        SplitTimerReader.Snapshot hespori = splitTimerReader.hespori();
        addSplitCategory(categories, "HESPORI", "Hespori", hespori);
        SplitTimerReader.Snapshot seaweed = splitTimerReader.seaweed();
        addSplitCategory(categories, "SEAWEED", "Seaweed", seaweed);
        SplitTimerReader.Snapshot compost = splitTimerReader.compost();
        addSplitCategory(categories, "COMPOST", "Compost", compost);
        return categories;
    }

    private static void addSplitCategory(List<Category> categories, String key, String name,
        SplitTimerReader.Snapshot snapshot)
    {
        categories.add(new Category(key, name, snapshot.getState(), snapshot.getReadyAt()));
    }

    private RequestBody jsonBody(Map<String, Object> payload)
    {
        return RequestBody.create(RuneLiteAPI.JSON, gson.toJson(payload));
    }

    private HttpUrl backendUrl()
    {
        return HttpUrl.parse(BACKEND_URL);
    }

    private String stored(String key)
    {
        return configManager.getConfiguration(FarmingDiscordStatusConfig.GROUP, key);
    }

    private void store(String key, String value)
    {
        configManager.setConfiguration(FarmingDiscordStatusConfig.GROUP, key, value);
    }

    private static String displayName(Tab tab)
    {
        switch (tab)
        {
            case HERB: return "Herbs";
            case TREE: return "Trees";
            case FRUIT_TREE: return "Fruit trees";
            case ALLOTMENT: return "Allotments";
            case FLOWER: return "Flowers";
            case BUSH: return "Bushes";
            case HOPS: return "Hops";
            case GRAPE: return "Grapes";
            case SPECIAL: return "Special patches";
            default: return tab.getName();
        }
    }

    private static final class Category
    {
        private final String key;
        private final String name;
        private final String state;
        private final long readyAt;

        private Category(String key, String name, String state, long readyAt)
        {
            this.key = key;
            this.name = name;
            this.state = state;
            this.readyAt = readyAt;
        }

    }
}
