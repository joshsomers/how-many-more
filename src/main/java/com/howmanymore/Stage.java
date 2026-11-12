package com.howmanymore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// One method in a goal's training path. Saved as JSON inside the goal, so keep field names stable.
@Data
@NoArgsConstructor
@AllArgsConstructor
class Stage
{
	// A Skill Calculator action name, or MethodCatalog.CUSTOM / MethodCatalog.AUTO.
	private String method;
	private double customXp;
	// A Skill Calculator bonus name, or null.
	private String bonus;
	// The level this method is used up to. Ignored on the last stage, which runs to the goal.
	private int endLevel;

	Stage copy()
	{
		return new Stage(method, customXp, bonus, endLevel);
	}
}
