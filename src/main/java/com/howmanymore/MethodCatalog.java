package com.howmanymore;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;

/**
 * The training methods for each skill and their XP per action, read from RuneLite's Skill Calculator so the values
 * stay current with RuneLite updates.
 */
@Slf4j
final class MethodCatalog
{
	static final String CUSTOM = "custom";
	static final String AUTO = "auto";

	static final MethodCatalog EMPTY = new MethodCatalog(new EnumMap<>(Skill.class));

	@Value
	static class Method
	{
		String id;
		String name;
		int level;
		double xp;
		List<Bonus> bonuses;
	}

	@Value
	static class Bonus
	{
		String id;
		String name;
		double multiplier;
	}

	private final Map<Skill, List<Method>> methods;

	MethodCatalog(Map<Skill, List<Method>> methods)
	{
		this.methods = methods;
	}

	// Must run on the client thread, because item-based method names come from item definitions.
	static MethodCatalog load(ItemManager itemManager)
	{
		try
		{
			return new MethodCatalog(CalculatorMethods.load(itemManager));
		}
		catch (LinkageError | RuntimeException e)
		{
			// The Skill Calculator isn't RuneLite API, so an update can move it; custom and auto goals still work.
			log.warn("Couldn't read the Skill Calculator's training methods", e);
			return EMPTY;
		}
	}

	List<Method> methods(Skill skill)
	{
		return methods.getOrDefault(skill, Collections.emptyList());
	}

	@Nullable
	Method method(Skill skill, @Nullable String id)
	{
		for (Method method : methods(skill))
		{
			if (method.getId().equals(id))
			{
				return method;
			}
		}
		return null;
	}

	@Nullable
	static Bonus bonus(Method method, @Nullable String id)
	{
		for (Bonus bonus : method.getBonuses())
		{
			if (bonus.getId().equals(id))
			{
				return bonus;
			}
		}
		return null;
	}

	// The most advanced method at or below the player's level, to preselect for a new goal.
	@Nullable
	Method best(Skill skill, int level)
	{
		Method best = null;
		for (Method method : methods(skill))
		{
			if (method.getLevel() <= level && (best == null || method.getLevel() >= best.getLevel()))
			{
				best = method;
			}
		}
		return best;
	}
}
