package com.howmanymore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.client.plugins.skillcalculator.skills.AgilityAction;
import net.runelite.client.plugins.skillcalculator.skills.HunterAction;
import org.junit.Test;

public class MethodCatalogTest
{
	private static final MethodCatalog.Bonus GILDED = new MethodCatalog.Bonus("LIT_GILDED_ALTAR", "Lit Gilded Altar", 3.5);

	private static MethodCatalog catalog()
	{
		Map<Skill, List<MethodCatalog.Method>> methods = new EnumMap<>(Skill.class);
		methods.put(Skill.HUNTER, Arrays.asList(
			new MethodCatalog.Method("SPOTTED_KEBBIT", "Spotted Kebbit", 43, 104, Collections.emptyList()),
			new MethodCatalog.Method("RAZOR_BACKED_KEBBIT", "Razor-backed Kebbit", 49, 348, Collections.emptyList()),
			new MethodCatalog.Method("DASHING_KEBBIT", "Dashing Kebbit", 69, 156, Collections.emptyList())));
		methods.put(Skill.PRAYER, Collections.singletonList(
			new MethodCatalog.Method("DRAGON_BONES", "Dragon bones", 1, 72, Collections.singletonList(GILDED))));
		return new MethodCatalog(methods);
	}

	@Test
	public void preselectsTheMostAdvancedUsableMethod()
	{
		assertEquals("RAZOR_BACKED_KEBBIT", catalog().best(Skill.HUNTER, 55).getId());
		assertEquals("DASHING_KEBBIT", catalog().best(Skill.HUNTER, 80).getId());
		assertNull(catalog().best(Skill.HUNTER, 10));
		assertNull(catalog().best(Skill.ATTACK, 99));
	}

	@Test
	public void findsSavedMethodsAndBonuses()
	{
		MethodCatalog.Method bones = catalog().method(Skill.PRAYER, "DRAGON_BONES");
		assertEquals(72, bones.getXp(), 1e-9);
		assertEquals(GILDED, MethodCatalog.bonus(bones, "LIT_GILDED_ALTAR"));
		assertNull(MethodCatalog.bonus(bones, null));
		assertNull(catalog().method(Skill.PRAYER, "NOT_A_METHOD"));
		assertNull(catalog().method(Skill.HUNTER, MethodCatalog.AUTO));
	}

	// Guards against a RuneLite update changing the Skill Calculator data the counts rely on.
	@Test
	public void skillCalculatorHasTheExpectedMethods()
	{
		assertEquals("Razor-backed Kebbit", HunterAction.RAZOR_BACKED_KEBBIT.getName());
		assertEquals(348, HunterAction.RAZOR_BACKED_KEBBIT.getXp(), 1e-3);
		assertEquals("Canifis Rooftop", AgilityAction.CANIFIS_ROOFTOP.getName());
		assertEquals(240, AgilityAction.CANIFIS_ROOFTOP.getXp(), 1e-3);
	}
}
