package com.howmanymore;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.util.QuantityFormatter;

final class GoalMath
{
	// XP per action is shown in tenths in game; this absorbs float noise like 269.7f -> 269.70001.
	private static final double EPSILON = 1e-6;

	private GoalMath()
	{
	}

	static int xpLeft(int currentXp, int targetXp)
	{
		return Math.max(0, targetXp - currentXp);
	}

	// -1 when the XP per action isn't known yet.
	static int actionsLeft(int xpLeft, double xpEach)
	{
		if (xpEach <= 0)
		{
			return -1;
		}
		return (int) Math.ceil(xpLeft / xpEach - EPSILON);
	}

	// -1 when the XP per hour isn't known yet.
	static long millisLeft(int xpLeft, double xpPerHour)
	{
		if (xpPerHour <= 0)
		{
			return -1;
		}
		return (long) Math.ceil(xpLeft / xpPerHour * TimeUnit.HOURS.toMillis(1));
	}

	// "<1m", "45m", "3h 20m" or "2d 4h", rounded up to the minute.
	static String formatDuration(long millis)
	{
		long minutes = (long) Math.ceil(millis / (double) TimeUnit.MINUTES.toMillis(1));
		if (minutes < 1)
		{
			return "<1m";
		}
		if (minutes < 60)
		{
			return minutes + "m";
		}
		long hours = minutes / 60;
		if (hours < 24)
		{
			return minutes % 60 == 0 ? hours + "h" : hours + "h " + minutes % 60 + "m";
		}
		return hours % 24 == 0 ? hours / 24 + "d" : hours / 24 + "d " + hours % 24 + "h";
	}

	// The time of day in the player's own format, like "9:40 PM", with the weekday when it isn't today.
	static String formatClock(long atMillis, long nowMillis)
	{
		return formatClock(atMillis, nowMillis, ZoneId.systemDefault(), Locale.getDefault());
	}

	static String formatClock(long atMillis, long nowMillis, ZoneId zone, Locale locale)
	{
		ZonedDateTime at = Instant.ofEpochMilli(atMillis).atZone(zone);
		String time = at.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale));
		if (at.toLocalDate().equals(Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()))
		{
			return time;
		}
		return at.format(DateTimeFormatter.ofPattern("EEE", locale)) + " " + time;
	}

	static double progress(int startXp, int currentXp, int targetXp)
	{
		if (targetXp <= startXp)
		{
			return 1;
		}
		double progress = (double) (currentXp - startXp) / (targetXp - startXp);
		return Math.max(0, Math.min(1, progress));
	}

	// The level whose XP is exactly targetXp, or 0 for a plain XP target.
	static int targetLevel(int targetXp)
	{
		for (int level = 2; level <= Experience.MAX_VIRT_LEVEL; level++)
		{
			int xp = Experience.getXpForLevel(level);
			if (xp == targetXp)
			{
				return level;
			}
			if (xp > targetXp)
			{
				break;
			}
		}
		return 0;
	}

	static String targetLabel(Skill skill, int targetXp)
	{
		int level = targetLevel(targetXp);
		return level > 0
			? level + " " + skill.getName()
			: QuantityFormatter.quantityToStackSize(targetXp) + " " + skill.getName() + " XP";
	}

	/**
	 * Reads a goal typed as a level ("70") or an XP amount ("1,500,000", "1.5m", "500k").
	 * Numbers up to the max virtual level are levels. Returns the target XP, or -1 if the text isn't a valid goal.
	 */
	static int parseTarget(String text)
	{
		if (text == null)
		{
			return -1;
		}

		String value = text.trim().toLowerCase(Locale.ROOT).replace(",", "").replace(" ", "");
		double multiplier = 1;
		if (value.endsWith("k"))
		{
			multiplier = 1_000;
		}
		else if (value.endsWith("m"))
		{
			multiplier = 1_000_000;
		}
		if (multiplier > 1)
		{
			value = value.substring(0, value.length() - 1);
		}

		double number;
		try
		{
			number = Double.parseDouble(value);
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
		if (Double.isNaN(number) || number <= 0)
		{
			return -1;
		}

		if (multiplier == 1 && number <= Experience.MAX_VIRT_LEVEL)
		{
			if (number != Math.floor(number) || number < 2)
			{
				return -1;
			}
			return Experience.getXpForLevel((int) number);
		}

		double xp = Math.round(number * multiplier);
		return xp > Experience.MAX_SKILL_XP ? -1 : (int) xp;
	}

	// The XP each typed for a custom goal, or 0 if it isn't a positive number.
	static double parseXpEach(String text)
	{
		try
		{
			double xp = Double.parseDouble(text.trim().replace(",", ""));
			return Double.isNaN(xp) || Double.isInfinite(xp) || xp <= 0 ? 0 : xp;
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	// "348" or "269.7": up to two decimals, without trailing zeros.
	static String formatXp(double xp)
	{
		String text = String.format(Locale.ROOT, "%.2f", xp);
		text = text.replaceAll("0+$", "");
		return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
	}
}
