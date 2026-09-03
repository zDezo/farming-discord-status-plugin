package com.farmingdiscordstatus;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(FarmingDiscordStatusConfig.GROUP)
public interface FarmingDiscordStatusConfig extends Config
{
    String GROUP = "farmingdiscordstatus";

    @ConfigItem(
        keyName = "readyPings",
        name = "Ready pings",
        description = "Ping when a farming category changes from growing to ready",
        position = 0
    )
    default boolean readyPings()
    {
        return true;
    }

    @ConfigItem(
        keyName = "sendTestPing",
        name = "Send test ping",
        description = "Send a test message through the linked Discord bot",
        position = 1
    )
    default boolean sendTestPing()
    {
        return false;
    }

    @ConfigItem(
        keyName = "linkCode",
        name = "Link code",
        description = "One-time code returned by the bot's /link command",
        position = 2
    )
    default String linkCode()
    {
        return "";
    }

    @ConfigItem(
        keyName = "linkAccount",
        name = "Link account",
        description = "Connect to Farming Status. This sends your character name, farming timer states, and ready times to farming-status-bot.onrender.com. See https://farming-status-bot.onrender.com/privacy",
        position = 3
    )
    default boolean linkAccount()
    {
        return false;
    }
}
