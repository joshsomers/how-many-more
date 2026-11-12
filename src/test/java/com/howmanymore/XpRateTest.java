package com.howmanymore;

import static org.junit.Assert.assertEquals;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class XpRateTest
{
	private static final long START = 1_000_000L;
	private static final long SECOND = TimeUnit.SECONDS.toMillis(1);
	private static final long MINUTE = TimeUnit.MINUTES.toMillis(1);

	@Test
	public void unknownWithoutTraining()
	{
		assertEquals(-1, new XpRate().perHour(), 0);
	}

	@Test
	public void unknownUnderTwoMinutes()
	{
		XpRate rate = new XpRate();
		// The first drop only starts the clock, so 20 drops are 1 minute 54 seconds of training.
		long time = train(rate, START, 6 * SECOND, 100, 20);
		assertEquals(-1, rate.perHour(), 0);

		train(rate, time + 6 * SECOND, 6 * SECOND, 100, 1);
		assertEquals(60_000, rate.perHour(), 0.001);
	}

	@Test
	public void steadyDrops()
	{
		XpRate rate = new XpRate();
		// 100 XP every 6 seconds is 60,000 an hour.
		train(rate, START, 6 * SECOND, 100, 50);
		assertEquals(60_000, rate.perHour(), 0.001);
	}

	@Test
	public void skipsBreaks()
	{
		XpRate rate = new XpRate();
		long time = train(rate, START, 6 * SECOND, 100, 30);
		// A 20 minute break, then the same pace again.
		train(rate, time + 20 * MINUTE, 6 * SECOND, 100, 30);
		assertEquals(60_000, rate.perHour(), 0.001);
	}

	@Test
	public void countsGapsUpToFiveMinutes()
	{
		XpRate rate = new XpRate();
		// 5,000 XP every 5 minutes, like a bank trip between loads.
		train(rate, START, 5 * MINUTE, 5_000, 3);
		assertEquals(60_000, rate.perHour(), 0.001);
	}

	@Test
	public void keepsTheLastThirtyMinutes()
	{
		XpRate rate = new XpRate();
		long time = train(rate, START, MINUTE, 500, 41);
		// 40 minutes at 30,000 an hour, then 30 minutes at 90,000 an hour.
		train(rate, time + MINUTE, MINUTE, 1_500, 30);
		assertEquals(90_000, rate.perHour(), 0.001);
	}

	@Test
	public void resetForgetsEverything()
	{
		XpRate rate = new XpRate();
		long time = train(rate, START, 6 * SECOND, 100, 50);
		rate.reset();
		assertEquals(-1, rate.perHour(), 0);

		// The first drop after a reset only starts the clock.
		train(rate, time + 6 * SECOND, 6 * SECOND, 100, 21);
		assertEquals(60_000, rate.perHour(), 0.001);
	}

	// Gains xp every step, starting at time; returns the time of the last drop.
	private static long train(XpRate rate, long time, long step, int xp, int drops)
	{
		long last = time;
		for (int i = 0; i < drops; i++)
		{
			last = time + i * step;
			rate.gain(last, xp);
		}
		return last;
	}
}
