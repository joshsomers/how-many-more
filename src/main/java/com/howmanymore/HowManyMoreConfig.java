package com.howmanymore;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(HowManyMoreConfig.GROUP)
public interface HowManyMoreConfig extends Config
{
	// The plugin's old name. Saved goals and settings live under it, so it stays.
	String GROUP = "goal-counter";

	@ConfigItem(keyName = "showHud", name = "Show HUD",
		description = "Show your goals and the actions left on screen. Set goals in the How Many More sidebar panel",
		position = 0)
	default boolean showHud()
	{
		return true;
	}

	@ConfigItem(keyName = "showProgressBar", name = "Show progress bar",
		description = "Show a bar under each goal, from where you were when you set it to the goal",
		position = 1)
	default boolean showProgressBar()
	{
		return true;
	}

	@ConfigItem(keyName = "showXpLeft", name = "Show XP left",
		description = "Show the XP left as well as the actions left",
		position = 2)
	default boolean showXpLeft()
	{
		return false;
	}

	@Range(max = 120)
	@Units(Units.MINUTES)
	@ConfigItem(keyName = "hideAfterIdle", name = "Hide when not training",
		description = "Hide a goal from the HUD after this many minutes without XP in its skill. 0 always shows it",
		position = 3)
	default int hideAfterIdle()
	{
		return 0;
	}

	@ConfigItem(keyName = "goalReachedNotification", name = "Goal reached notification",
		description = "Notify you when you reach a goal",
		position = 4)
	default Notification goalReachedNotification()
	{
		return Notification.ON;
	}
}
