package com.howmanymore;

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
	// A Skill Calculator action name, or MethodCatalog.CUSTOM / MethodCatalog.AUTO.
	private String method;
	private double customXp;
	// A Skill Calculator bonus name, or null.
	private String bonus;
	private boolean reached;

	Goal copy()
	{
		Goal goal = new Goal();
		goal.id = id;
		goal.skill = skill;
		goal.targetXp = targetXp;
		goal.startXp = startXp;
		goal.method = method;
		goal.customXp = customXp;
		goal.bonus = bonus;
		goal.reached = reached;
		return goal;
	}
}
