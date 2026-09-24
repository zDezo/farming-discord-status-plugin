package com.farmingdiscordstatus;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(FarmingDiscordStatusConfig.GROUP)
public interface FarmingDiscordStatusConfig extends Config
{
    String GROUP = "farmingdiscordstatus";

    @ConfigItem(
        keyName = "setupHint",
        name = "<html><table width='220' cellpadding='0' cellspacing='0'><tr>"
            + "<td><font color='#ff981f'><b>New here?</b><br>Click the Setup directions<br>checkbox below.</font></td>"
            + "<td width='22' align='center' valign='bottom'><font face='Dialog' color='#ff981f' size='+3'>&#8595;</font></td>"
            + "</tr></table></html>",
        description = "Tick Setup directions below to open the setup guide.",
        position = -2
    )
    default void setupHint()
    {
    }

    @ConfigItem(
        keyName = "setupDirections",
        name = "Setup directions — start here",
        description = "Open setup directions, the Link account button, and Send test ping. This checkbox resets after opening.",
        position = -1
    )
    default boolean setupDirections()
    {
        return false;
    }

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
