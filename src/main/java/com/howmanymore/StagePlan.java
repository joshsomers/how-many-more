package com.howmanymore;

import java.util.List;
import net.runelite.api.Experience;

// Where each of a goal's stages ends, and the actions left in each.
final class StagePlan
{
	// XP per action is shown in tenths in game; this absorbs float noise like 269.7f -> 269.70001.
	private static final double EPSILON = 1e-6;

	private StagePlan()
	{
	}

	// The XP each stage ends at. Ends never go backwards or past the goal; the last stage ends at the goal.
	static int[] endXps(List<Stage> stages, int targetXp)
	{
		int[] ends = new int[stages.size()];
		int previous = 0;
		for (int i = 0; i < ends.length; i++)
		{
			int end = i == ends.length - 1 ? targetXp : xpForLevel(stages.get(i).getEndLevel());
			ends[i] = Math.max(previous, Math.min(targetXp, end));
			previous = ends[i];
		}
		return ends;
	}

	/**
	 * The actions left in each stage: 0 when it's done, -1 when its XP each isn't known. An action that goes past a
	 * stage's end carries its extra XP into the next stage.
	 */
	static int[] actionsLeft(int[] endXps, double[] xpEach, int currentXp)
	{
		int[] actions = new int[endXps.length];
		double xp = currentXp;
		for (int i = 0; i < endXps.length; i++)
		{
			if (xp >= endXps[i])
			{
				continue;
			}
			if (xpEach[i] <= 0)
			{
				actions[i] = -1;
				xp = endXps[i];
				continue;
			}
			actions[i] = (int) Math.ceil((endXps[i] - xp) / xpEach[i] - EPSILON);
			xp += actions[i] * xpEach[i];
		}
		return actions;
	}

	// The stage being trained now: the first that isn't done, or the last once the goal is reached.
	static int current(int[] endXps, int currentXp)
	{
		for (int i = 0; i < endXps.length; i++)
		{
			if (currentXp < endXps[i])
			{
				return i;
			}
		}
		return endXps.length - 1;
	}

	// The actions left across all stages, or -1 if any stage still to go has no count.
	static int total(int[] actions)
	{
		int total = 0;
		for (int count : actions)
		{
			if (count < 0)
			{
				return -1;
			}
			total += count;
		}
		return total;
	}

	private static int xpForLevel(int level)
	{
		return Experience.getXpForLevel(Math.max(1, Math.min(Experience.MAX_VIRT_LEVEL, level)));
	}
}
