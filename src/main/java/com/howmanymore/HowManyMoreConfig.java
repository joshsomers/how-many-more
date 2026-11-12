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
	String COUNT_DOWN_KEY = "countDown";
	String FINISH_TIME_KEY = "showFinishTime";

	enum CountDown
	{
		ACTIONS("Actions"),
		TIME("Time");

		private final String label;

		CountDown(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(keyName = "showHud", name = "Show HUD",
		description = "Show your goals and what's left on screen. Set goals in the How Many More sidebar panel",
		position = 0)
	default boolean showHud()
	{
		return true;
	}

	@ConfigItem(keyName = COUNT_DOWN_KEY, name = "Count down",
		description = "Count down the actions left, or the time left at your XP/hr over your last 30 minutes of training."
			+ " Breaks over 5 minutes aren't counted",
		position = 1)
	default CountDown countDown()
	{
		return CountDown.ACTIONS;
	}

	@ConfigItem(keyName = "showProgressBar", name = "Show progress bar",
		description = "Show a bar under each goal, from where you were when you set it to the goal",
		position = 2)
	default boolean showProgressBar()
	{
		return true;
	}

	@ConfigItem(keyName = "showXpLeft", name = "Show XP left",
		description = "Show the XP left as well as the actions or time left",
		position = 3)
	default boolean showXpLeft()
	{
		return false;
	}

	@ConfigItem(keyName = FINISH_TIME_KEY, name = "Show finish time",
		description = "Show the time of day you'll reach each goal at your recent XP/hr, when that's within a day",
		position = 4)
	default boolean showFinishTime()
	{
		return false;
	}

	@Range(max = 120)
	@Units(Units.MINUTES)
	@ConfigItem(keyName = "hideAfterIdle", name = "Hide when not training",
		description = "Hide a goal from the HUD after this many minutes without XP in its skill. 0 always shows it",
		position = 5)
	default int hideAfterIdle()
	{
		return 0;
	}

	@ConfigItem(keyName = "goalReachedNotification", name = "Goal reached notification",
		description = "Notify you when you reach a goal",
		position = 6)
	default Notification goalReachedNotification()
	{
		return Notification.ON;
	}

	@ConfigItem(keyName = "goalReachedSummary", name = "Goal reached summary",
		description = "Add the XP gained, training time and XP/hr to the goal reached chat message."
			+ " Breaks over 5 minutes aren't counted as training",
		position = 7)
	default boolean goalReachedSummary()
	{
		return true;
	}
}
