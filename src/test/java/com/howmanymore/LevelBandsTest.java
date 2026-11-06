package com.howmanymore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import net.runelite.api.Experience;
import org.junit.Test;

public class LevelBandsTest
{
	private static final int WIDTH = 160;

	@Test
	public void marksEachLevelUpOfAShortGoal()
	{
		// 81 is 30% and 82 is 63% of the way from 80 to 83.
		assertEquals(List.of(48, 101), LevelBands.edges(xp(80), xp(83), WIDTH));
	}

	@Test
	public void goalInsideOneLevelHasNoBands()
	{
		assertTrue(LevelBands.edges(xp(80) + 1_000, xp(81) - 1_000, WIDTH).isEmpty());
		assertTrue(LevelBands.edges(xp(83), xp(80), WIDTH).isEmpty());
	}

	@Test
	public void longGoalLeavesOutCrowdedLevelUps()
	{
		List<Integer> edges = LevelBands.edges(xp(60), xp(99), WIDTH);
		// Levels 61 to 98 are 38 level-ups, but the early ones are under a pixel apart.
		assertTrue(edges.size() < 38);
		int last = 0;
		for (int edge : edges)
		{
			assertTrue(edge - last >= 4);
			last = edge;
		}
		assertTrue(WIDTH - last >= 4);
		// 98 is far enough from 97 and the end to keep.
		assertEquals((int) ((long) WIDTH * (xp(98) - xp(60)) / (xp(99) - xp(60))), last);
	}

	@Test
	public void shadesEveryOtherLevelRightOfTheFill()
	{
		BufferedImage bar = paint(0);
		// Plain to 81, shaded from 81 to 82, then plain to 83.
		assertEquals(0, alpha(bar, 47));
		assertTrue(alpha(bar, 48) > 0);
		assertTrue(alpha(bar, 100) > 0);
		assertEquals(0, alpha(bar, 101));
		assertEquals(0, alpha(bar, WIDTH - 1));

		// A fill past 81 leaves the band's start alone, and the rest of the band where it was.
		BufferedImage filled = paint(50);
		assertEquals(0, alpha(filled, 49));
		assertTrue(alpha(filled, 50) > 0);
		assertEquals(0, alpha(filled, 101));
	}

	private static BufferedImage paint(int fill)
	{
		BufferedImage image = new BufferedImage(WIDTH, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		LevelBands.paint(g, Color.BLACK, xp(80), xp(83), 0, 0, WIDTH, 1, fill);
		g.dispose();
		return image;
	}

	private static int alpha(BufferedImage image, int x)
	{
		return image.getRGB(x, 0) >>> 24;
	}

	private static int xp(int level)
	{
		return Experience.getXpForLevel(level);
	}
}
