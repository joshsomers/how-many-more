package com.howmanymore;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

// One skill's XP per hour over its most recent training time. Breaks between XP drops aren't counted.
class XpRate
{
	static final long WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(30);
	// A longer gap between drops is a break: neither its time nor the drop that ends it counts.
	static final long BREAK_MILLIS = TimeUnit.MINUTES.toMillis(5);
	// Less training than this gives too rough a rate to show.
	static final long MIN_MILLIS = TimeUnit.MINUTES.toMillis(2);

	private static final double MILLIS_PER_HOUR = TimeUnit.HOURS.toMillis(1);

	// Each counted gap between two drops, oldest first: {millis, xp gained at its end}.
	private final ArrayDeque<long[]> intervals = new ArrayDeque<>();
	private long totalMillis;
	private long totalXp;
	private long lastGainMillis;

	void gain(long nowMillis, int xp)
	{
		long gap = nowMillis - lastGainMillis;
		boolean counted = lastGainMillis != 0 && gap > 0 && gap <= BREAK_MILLIS;
		lastGainMillis = nowMillis;
		if (!counted)
		{
			return;
		}

		intervals.addLast(new long[]{gap, xp});
		totalMillis += gap;
		totalXp += xp;
		while (totalMillis - intervals.getFirst()[0] >= WINDOW_MILLIS)
		{
			long[] oldest = intervals.removeFirst();
			totalMillis -= oldest[0];
			totalXp -= oldest[1];
		}
	}

	// -1 until there's enough training to tell.
	double perHour()
	{
		if (totalMillis < MIN_MILLIS)
		{
			return -1;
		}
		return totalXp * MILLIS_PER_HOUR / totalMillis;
	}

	void reset()
	{
		intervals.clear();
		totalMillis = 0;
		totalXp = 0;
		lastGainMillis = 0;
	}
}
