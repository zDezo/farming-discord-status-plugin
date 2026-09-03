package com.farmingdiscordstatus;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class FarmingDiscordStatusDevelopment
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(FarmingDiscordStatusPlugin.class);
        RuneLite.main(args);
    }
}
