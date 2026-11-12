package com.howmanymore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;

public class GoalTest
{
	private final Gson gson = new Gson();

	@Test
	public void goalSavedBeforeStagesGetsOneStage()
	{
		Goal goal = gson.fromJson("{\"id\":\"a\",\"skill\":\"AGILITY\",\"targetXp\":737627,\"startXp\":273742,"
			+ "\"method\":\"SEERS\",\"customXp\":0.0,\"reached\":false}", Goal.class);
		goal.migrate();

		assertEquals(1, goal.getStages().size());
		assertEquals("SEERS", goal.getStages().get(0).getMethod());
		// Its training time wasn't tracked from the start, so it gets no summary.
		assertEquals(-1, goal.getTrainedMillis());
	}

	@Test
	public void savesTheFirstStageInTheOldFields()
	{
		Goal goal = new Goal();
		goal.setStages(new ArrayList<>(Arrays.asList(new Stage("CANIFIS", 0, null, 60), new Stage("SEERS", 0, null, 0))));
		goal.syncOldFields();

		Goal read = gson.fromJson(gson.toJson(goal), Goal.class);
		assertEquals("CANIFIS", read.getMethod());
		assertEquals(2, read.getStages().size());
		assertEquals(60, read.getStages().get(0).getEndLevel());
	}

	@Test
	public void copyDoesNotShareStages()
	{
		Goal goal = new Goal();
		goal.setStages(new ArrayList<>(Arrays.asList(new Stage("CANIFIS", 0, null, 60))));
		Goal copy = goal.copy();
		goal.getStages().get(0).setEndLevel(65);
		goal.getStages().add(new Stage("SEERS", 0, null, 0));

		assertEquals(60, copy.getStages().get(0).getEndLevel());
		assertTrue(copy.getStages().size() == 1);
	}
}
