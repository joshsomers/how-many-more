package com.howmanymore;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Experience;
import org.junit.Test;

public class StagePlanTest
{
	private static final int XP_60 = Experience.getXpForLevel(60);
	private static final int XP_70 = Experience.getXpForLevel(70);
	private static final int XP_80 = Experience.getXpForLevel(80);

	// Canifis to 60, Seers' to 70, Pollnivneach to the goal.
	private static final List<Stage> ROOFTOPS = Arrays.asList(
		new Stage("canifis", 0, null, 60),
		new Stage("seers", 0, null, 70),
		new Stage("pollnivneach", 0, null, 0));

	@Test
	public void stagesEndAtTheirLevelsAndTheLastAtTheGoal()
	{
		assertArrayEquals(new int[]{XP_60, XP_70, XP_80}, StagePlan.endXps(ROOFTOPS, XP_80));
	}

	@Test
	public void endsNeverGoBackwardsOrPastTheGoal()
	{
		List<Stage> stages = Arrays.asList(
			new Stage("a", 0, null, 70),
			new Stage("b", 0, null, 60),
			new Stage("c", 0, null, 90),
			new Stage("d", 0, null, 0));
		assertArrayEquals(new int[]{XP_70, XP_70, XP_80, XP_80}, StagePlan.endXps(stages, XP_80));
	}

	@Test
	public void countsEachStageFromTheCurrentXp()
	{
		int[] ends = StagePlan.endXps(ROOFTOPS, XP_80);
		int[] actions = StagePlan.actionsLeft(ends, new double[]{240, 570, 890}, XP_60 - 2_400);
		assertEquals(10, actions[0]);
		assertEquals(0, StagePlan.current(ends, XP_60 - 2_400));
		assertEquals(StagePlan.total(actions), actions[0] + actions[1] + actions[2]);
	}

	@Test
	public void skipsStagesAlreadyDone()
	{
		int[] ends = StagePlan.endXps(ROOFTOPS, XP_80);
		int[] actions = StagePlan.actionsLeft(ends, new double[]{240, 570, 890}, XP_70 - 5_700);
		assertEquals(0, actions[0]);
		assertEquals(10, actions[1]);
		assertEquals(1, StagePlan.current(ends, XP_70 - 5_700));
	}

	@Test
	public void carriesTheLastActionsExtraXpIntoTheNextStage()
	{
		List<Stage> stages = Arrays.asList(new Stage("a", 0, null, 2), new Stage("b", 0, null, 0));
		// Level 2 is at 83 XP. One 100 XP action passes it, so the next stage starts at 100, not 83.
		int[] actions = StagePlan.actionsLeft(StagePlan.endXps(stages, 200), new double[]{100, 10}, 0);
		assertArrayEquals(new int[]{1, 10}, actions);
	}

	@Test
	public void unknownXpEachHasNoTotal()
	{
		int[] ends = StagePlan.endXps(ROOFTOPS, XP_80);
		int[] actions = StagePlan.actionsLeft(ends, new double[]{240, 0, 890}, XP_60 - 2_400);
		assertEquals(10, actions[0]);
		assertEquals(-1, actions[1]);
		// The stage after an unknown one starts at that stage's end.
		assertEquals((int) Math.ceil((XP_80 - XP_70) / 890.0), actions[2]);
		assertEquals(-1, StagePlan.total(actions));
	}

	@Test
	public void reachedGoalIsOnTheLastStage()
	{
		int[] ends = StagePlan.endXps(ROOFTOPS, XP_80);
		assertEquals(2, StagePlan.current(ends, XP_80));
		assertEquals(0, StagePlan.total(StagePlan.actionsLeft(ends, new double[]{240, 570, 890}, XP_80)));
	}
}
