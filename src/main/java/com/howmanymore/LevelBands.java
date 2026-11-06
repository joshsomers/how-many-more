package com.howmanymore;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Experience;

// Shades every other level on a goal's progress bar, so the level-ups still to come are easy to see.
final class LevelBands
{
	// Pixels a band must be wide; level-ups closer together than this share a band.
	private static final int MIN_WIDTH = 4;

	private LevelBands()
	{
	}

	// Paints the bands of a bar at x, y, right of fromX so they stay off its fill. They're worked out from the goal's
	// start, not the fill, so they stay put as you level.
	static void paint(Graphics g, Color shade, int startXp, int targetXp, int x, int y, int width, int height, int fromX)
	{
		List<Integer> edges = edges(startXp, targetXp, width);
		g.setColor(shade);
		// The bar starts plain, and each level-up kept switches between plain and shaded.
		for (int i = 0; i < edges.size(); i += 2)
		{
			int left = Math.max(edges.get(i), fromX);
			int right = i + 1 < edges.size() ? edges.get(i + 1) : width;
			if (right > left)
			{
				g.fillRect(x + left, y, right - left, height);
			}
		}
	}

	// Where each level-up between startXp and targetXp falls on a bar this wide. Any closer than MIN_WIDTH to the
	// last one kept, or to the bar's end, is left out.
	static List<Integer> edges(int startXp, int targetXp, int width)
	{
		List<Integer> edges = new ArrayList<>();
		if (targetXp <= startXp)
		{
			return edges;
		}
		int last = 0;
		for (int level = Experience.getLevelForXp(startXp) + 1; level <= Experience.MAX_VIRT_LEVEL; level++)
		{
			int xp = Experience.getXpForLevel(level);
			if (xp >= targetXp)
			{
				break;
			}
			int edge = (int) ((long) width * (xp - startXp) / (targetXp - startXp));
			if (edge - last >= MIN_WIDTH && width - edge >= MIN_WIDTH)
			{
				edges.add(edge);
				last = edge;
			}
		}
		return edges;
	}
}
