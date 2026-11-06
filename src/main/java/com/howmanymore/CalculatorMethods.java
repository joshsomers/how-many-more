package com.howmanymore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.skillcalculator.skills.AgilityAction;
import net.runelite.client.plugins.skillcalculator.skills.AgilityBonus;
import net.runelite.client.plugins.skillcalculator.skills.ConstructionAction;
import net.runelite.client.plugins.skillcalculator.skills.ConstructionBonus;
import net.runelite.client.plugins.skillcalculator.skills.CookingAction;
import net.runelite.client.plugins.skillcalculator.skills.CraftingAction;
import net.runelite.client.plugins.skillcalculator.skills.FarmingAction;
import net.runelite.client.plugins.skillcalculator.skills.FarmingBonus;
import net.runelite.client.plugins.skillcalculator.skills.FiremakingAction;
import net.runelite.client.plugins.skillcalculator.skills.FiremakingBonus;
import net.runelite.client.plugins.skillcalculator.skills.FishingAction;
import net.runelite.client.plugins.skillcalculator.skills.FishingBonus;
import net.runelite.client.plugins.skillcalculator.skills.FletchingAction;
import net.runelite.client.plugins.skillcalculator.skills.HerbloreAction;
import net.runelite.client.plugins.skillcalculator.skills.HunterAction;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.plugins.skillcalculator.skills.MiningAction;
import net.runelite.client.plugins.skillcalculator.skills.MiningBonus;
import net.runelite.client.plugins.skillcalculator.skills.PrayerAction;
import net.runelite.client.plugins.skillcalculator.skills.PrayerBonus;
import net.runelite.client.plugins.skillcalculator.skills.RunecraftAction;
import net.runelite.client.plugins.skillcalculator.skills.RunecraftBonus;
import net.runelite.client.plugins.skillcalculator.skills.SailingAction;
import net.runelite.client.plugins.skillcalculator.skills.SailingBonus;
import net.runelite.client.plugins.skillcalculator.skills.SkillAction;
import net.runelite.client.plugins.skillcalculator.skills.SkillBonus;
import net.runelite.client.plugins.skillcalculator.skills.SmithingAction;
import net.runelite.client.plugins.skillcalculator.skills.SmithingBonus;
import net.runelite.client.plugins.skillcalculator.skills.ThievingAction;
import net.runelite.client.plugins.skillcalculator.skills.WoodcuttingAction;
import net.runelite.client.plugins.skillcalculator.skills.WoodcuttingBonus;

// Every reference to the Skill Calculator lives here, so MethodCatalog can catch the error if an update moves it.
final class CalculatorMethods
{
	private CalculatorMethods()
	{
	}

	static Map<Skill, List<MethodCatalog.Method>> load(ItemManager itemManager)
	{
		Map<Skill, List<MethodCatalog.Method>> methods = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			SkillBonus[] bonuses = bonuses(skill);
			List<MethodCatalog.Method> skillMethods = new ArrayList<>();
			for (SkillAction action : actions(skill))
			{
				List<MethodCatalog.Bonus> applicable = new ArrayList<>();
				for (SkillBonus bonus : bonuses)
				{
					if (action.isBonusApplicable(bonus))
					{
						applicable.add(new MethodCatalog.Bonus(((Enum<?>) bonus).name(), bonus.getName(),
							Math.round(bonus.getValue() * 1e6) / 1e6));
					}
				}
				skillMethods.add(new MethodCatalog.Method(((Enum<?>) action).name(), action.getName(itemManager),
					action.getLevel(), Math.round(action.getXp() * 10) / 10.0, Collections.unmodifiableList(applicable)));
			}
			if (!skillMethods.isEmpty())
			{
				methods.put(skill, Collections.unmodifiableList(skillMethods));
			}
		}
		return methods;
	}

	private static SkillAction[] actions(Skill skill)
	{
		switch (skill)
		{
			case AGILITY:
				return AgilityAction.values();
			case CONSTRUCTION:
				return ConstructionAction.values();
			case COOKING:
				return CookingAction.values();
			case CRAFTING:
				return CraftingAction.values();
			case FARMING:
				return FarmingAction.values();
			case FIREMAKING:
				return FiremakingAction.values();
			case FISHING:
				return FishingAction.values();
			case FLETCHING:
				return FletchingAction.values();
			case HERBLORE:
				return HerbloreAction.values();
			case HUNTER:
				return HunterAction.values();
			case MAGIC:
				return MagicAction.values();
			case MINING:
				return MiningAction.values();
			case PRAYER:
				return PrayerAction.values();
			case RUNECRAFT:
				return RunecraftAction.values();
			case SAILING:
				return SailingAction.values();
			case SMITHING:
				return SmithingAction.values();
			case THIEVING:
				return ThievingAction.values();
			case WOODCUTTING:
				return WoodcuttingAction.values();
			default:
				return new SkillAction[0];
		}
	}

	private static SkillBonus[] bonuses(Skill skill)
	{
		switch (skill)
		{
			case AGILITY:
				return AgilityBonus.values();
			case CONSTRUCTION:
				return ConstructionBonus.values();
			case FARMING:
				return FarmingBonus.values();
			case FIREMAKING:
				return FiremakingBonus.values();
			case FISHING:
				return FishingBonus.values();
			case MINING:
				return MiningBonus.values();
			case PRAYER:
				return PrayerBonus.values();
			case RUNECRAFT:
				return RunecraftBonus.values();
			case SAILING:
				return SailingBonus.values();
			case SMITHING:
				return SmithingBonus.values();
			case WOODCUTTING:
				return WoodcuttingBonus.values();
			default:
				return new SkillBonus[0];
		}
	}
}
