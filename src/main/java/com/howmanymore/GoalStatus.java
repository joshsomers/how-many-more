package com.howmanymore;

import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.api.Skill;

// A snapshot of one goal's progress, shared read-only with the HUD and the sidebar.
@Value
class GoalStatus
{
	// A copy, so the sidebar can read it off the client thread.
	Goal goal;
	// The training method's name, or null for custom and auto goals.
	@Nullable
	String methodName;
	double xpEach;
	int currentXp;
	int xpLeft;
	// -1 when the XP per action isn't known yet.
	int actionsLeft;
	double progress;

	Skill getSkill()
	{
		return goal.getSkill();
	}

	boolean isReached()
	{
		return goal.isReached();
	}

	String getTargetLabel()
	{
		return GoalMath.targetLabel(goal.getSkill(), goal.getTargetXp());
	}

	// Why the count is missing, when actionsLeft is -1.
	String getMissingCountReason()
	{
		if (MethodCatalog.AUTO.equals(goal.getMethod()))
		{
			return "Gain XP once to count";
		}
		if (MethodCatalog.CUSTOM.equals(goal.getMethod()))
		{
			return "Set the XP each";
		}
		return "Pick a method";
	}
}
