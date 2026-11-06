package com.howmanymore;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class HowManyMoreLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(HowManyMorePlugin.class);
		RuneLite.main(args);
	}
}
