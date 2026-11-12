package com.howmanymore;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.runelite.api.Skill;

// Saved as JSON in the RuneScape profile config, so keep field names stable.
@Data
@NoArgsConstructor
class Goal
{
	private String id;
	private Skill skill;
	private int targetXp;
	// XP when the goal was set, where the progress bar starts.
	private int startXp;
	// The methods in training order. Null in goals saved before stages; see migrate().
	private List<Stage> stages;
	// Training time since the goal was set, not counting breaks. -1 in goals set before this was tracked.
	private long trainedMillis = -1;
	private boolean reached;

	// The only method of goals saved before stages. Kept as a copy of the first stage, so older versions still read it.
	private String method;
	private double customXp;
	private String bonus;

	// Gives a goal saved before stages its one stage.
	void migrate()
	{
		if (stages == null || stages.isEmpty())
		{
			stages = new ArrayList<>();
			stages.add(new Stage(method, customXp, bonus, 0));
		}
	}

	// Copies the first stage into the old method fields, before saving.
	void syncOldFields()
	{
		Stage first = stages.get(0);
		method = first.getMethod();
		customXp = first.getCustomXp();
		bonus = first.getBonus();
	}

	Goal copy()
	{
		Goal goal = new Goal();
		goal.id = id;
		goal.skill = skill;
		goal.targetXp = targetXp;
		goal.startXp = startXp;
		goal.stages = new ArrayList<>(stages.size());
		for (Stage stage : stages)
		{
			goal.stages.add(stage.copy());
		}
		goal.trainedMillis = trainedMillis;
		goal.reached = reached;
		goal.method = method;
		goal.customXp = customXp;
		goal.bonus = bonus;
		return goal;
	}
}
