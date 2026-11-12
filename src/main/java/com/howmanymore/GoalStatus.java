package com.howmanymore;

import java.util.List;
import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.api.Skill;

// A snapshot of one goal's progress, shared read-only with the HUD and the sidebar.
@Value
class GoalStatus
{
	@Value
	static class StageStatus
	{
		// The training method's name, or null for custom and auto methods.
		@Nullable
		String methodName;
		double xpEach;
		int endXp;
		// 0 once the stage is done, -1 when its XP each isn't known yet.
		int actionsLeft;
		boolean done;
	}

	// A copy, so the sidebar can read it off the client thread.
	Goal goal;
	List<StageStatus> stages;
	int currentStage;
	int currentXp;
	int xpLeft;
	// Across all stages; -1 when a stage still to go has no XP each yet.
	int actionsLeft;
	double progress;
	// The Count down and Show finish time settings.
	boolean timeMode;
	boolean finishShown;
	// -1 until there's enough training to tell.
	double xpPerHour;
	long millisLeft;
	long stageMillisLeft;
	// When the goal will be reached at the current XP/hr, or -1 if that's unknown or over a day away.
	long finishAtMillis;

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

	StageStatus getStage()
	{
		return stages.get(currentStage);
	}

	@Nullable
	String getMethodName()
	{
		return getStage().getMethodName();
	}

	double getXpEach()
	{
		return getStage().getXpEach();
	}

	// True while a later method is still to come, so the stage's end is shown as well as the goal.
	boolean hasNextStage()
	{
		return !isReached() && currentStage < stages.size() - 1;
	}

	// Time mode shows the actions left until the time is known.
	boolean showsTime()
	{
		return timeMode && millisLeft >= 0;
	}

	// Why the count is missing, when actionsLeft is -1 and no time is shown.
	String getMissingCountReason()
	{
		if (timeMode)
		{
			return "Train to estimate";
		}
		String method = goal.getStages().get(currentStage).getMethod();
		if (MethodCatalog.AUTO.equals(method))
		{
			return "Gain XP once to count";
		}
		if (MethodCatalog.CUSTOM.equals(method))
		{
			return "Set the XP each";
		}
		return "Pick a method";
	}
}
