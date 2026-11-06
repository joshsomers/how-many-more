package com.howmanymore;

import static org.junit.Assert.assertEquals;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import org.junit.Test;

public class GoalMathTest
{
	@Test
	public void countsRazorBackedKebbitsFrom50To70Hunter()
	{
		int xpLeft = GoalMath.xpLeft(101_333, 737_627);
		assertEquals(636_294, xpLeft);
		// 636,294 / 348 = 1,828.4, and a part catch still needs a whole catch.
		assertEquals(1_829, GoalMath.actionsLeft(xpLeft, 348));
	}

	@Test
	public void countsCanifisLapsFrom40To60Agility()
	{
		int xpLeft = GoalMath.xpLeft(Experience.getXpForLevel(40), Experience.getXpForLevel(60));
		assertEquals(986, GoalMath.actionsLeft(xpLeft, 240));
	}

	@Test
	public void exactMultipleNeedsNoExtraAction()
	{
		assertEquals(2, GoalMath.actionsLeft(480, 240));
		// Float XP like Varrock's 269.7 mustn't round 10 laps up to 11.
		assertEquals(10, GoalMath.actionsLeft(2_697, 269.7f));
		assertEquals(10, GoalMath.actionsLeft(2_697, Math.round(269.7f * 10) / 10.0));
	}

	@Test
	public void unknownXpEachHasNoCount()
	{
		assertEquals(-1, GoalMath.actionsLeft(1_000, 0));
	}

	@Test
	public void reachedGoalHasNothingLeft()
	{
		assertEquals(0, GoalMath.xpLeft(800_000, 737_627));
		assertEquals(0, GoalMath.actionsLeft(0, 348));
	}

	@Test
	public void progressRunsFromWhenTheGoalWasSet()
	{
		assertEquals(0, GoalMath.progress(1_000, 1_000, 3_000), 1e-9);
		assertEquals(0.5, GoalMath.progress(1_000, 2_000, 3_000), 1e-9);
		assertEquals(1, GoalMath.progress(1_000, 5_000, 3_000), 1e-9);
	}

	@Test
	public void parsesLevels()
	{
		assertEquals(737_627, GoalMath.parseTarget("70"));
		assertEquals(13_034_431, GoalMath.parseTarget(" 99 "));
		assertEquals(Experience.getXpForLevel(126), GoalMath.parseTarget("126"));
		assertEquals(-1, GoalMath.parseTarget("1"));
		assertEquals(-1, GoalMath.parseTarget("70.5"));
	}

	@Test
	public void parsesXpAmounts()
	{
		assertEquals(1_500_000, GoalMath.parseTarget("1.5m"));
		assertEquals(1_500_000, GoalMath.parseTarget("1,500,000"));
		assertEquals(500_000, GoalMath.parseTarget("500K"));
		assertEquals(200_000_000, GoalMath.parseTarget("200m"));
		assertEquals(-1, GoalMath.parseTarget("201m"));
		assertEquals(-1, GoalMath.parseTarget("lots"));
		assertEquals(-1, GoalMath.parseTarget(""));
		assertEquals(-1, GoalMath.parseTarget("-5"));
	}

	@Test
	public void labelsLevelAndXpTargets()
	{
		assertEquals(70, GoalMath.targetLevel(737_627));
		assertEquals(0, GoalMath.targetLevel(1_500_000));
		assertEquals("70 Hunter", GoalMath.targetLabel(Skill.HUNTER, 737_627));
		assertEquals("1.5M Agility XP", GoalMath.targetLabel(Skill.AGILITY, 1_500_000));
	}

	@Test
	public void formatsXpWithoutTrailingZeros()
	{
		assertEquals("348", GoalMath.formatXp(348));
		assertEquals("300", GoalMath.formatXp(300));
		assertEquals("269.7", GoalMath.formatXp(269.7));
		assertEquals("2.5", GoalMath.formatXp(2.5));
	}

	@Test
	public void parsesCustomXpEach()
	{
		assertEquals(348, GoalMath.parseXpEach("348"), 1e-9);
		assertEquals(1_234.5, GoalMath.parseXpEach(" 1,234.5 "), 1e-9);
		assertEquals(0, GoalMath.parseXpEach("abc"), 1e-9);
		assertEquals(0, GoalMath.parseXpEach("-4"), 1e-9);
	}
}
