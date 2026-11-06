package com.howmanymore;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "How Many More",
	description = "Counts down the laps, catches or other actions left to reach your skill goals, live on screen",
	tags = {"goal", "xp", "experience", "level", "skill", "calculator", "laps", "actions", "counter"}
)
public class HowManyMorePlugin extends Plugin
{
	private static final String GOALS_KEY = "goals";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private HowManyMoreConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private GoalOverlay overlay;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SkillIconManager skillIconManager;

	@Inject
	private Notifier notifier;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Gson gson;

	// Goals are only changed on the client thread; the HUD and sidebar read the statuses snapshot.
	private final List<Goal> goals = new ArrayList<>();
	private final int[] xp = new int[Skill.values().length];
	private final int[] lastDrop = new int[Skill.values().length];
	private final long[] lastGainMillis = new long[Skill.values().length];

	// False until the first tick after login, when the login stats have arrived.
	@Getter
	private volatile boolean statsReady;

	@Getter
	private volatile MethodCatalog catalog = MethodCatalog.EMPTY;

	@Getter
	private volatile List<GoalStatus> statuses = Collections.emptyList();

	private HowManyMorePanel panel;
	private NavigationButton navigationButton;

	@Provides
	HowManyMoreConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HowManyMoreConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new HowManyMorePanel(this, skillIconManager);
		navigationButton = NavigationButton.builder()
			.tooltip("How Many More")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		overlayManager.add(overlay);

		clientThread.invoke(() ->
		{
			// Item-based method names need the game cache, which is loaded by the login screen.
			if (client.getGameState().getState() < GameState.LOGIN_SCREEN.getState())
			{
				return false;
			}

			catalog = MethodCatalog.load(itemManager);
			HowManyMorePanel goalPanel = panel;
			if (goalPanel != null)
			{
				SwingUtilities.invokeLater(goalPanel::catalogLoaded);
			}

			loadGoals();
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				readAllStats();
			}
			refresh();
			return true;
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navigationButton);
		navigationButton = null;
		panel = null;
		goals.clear();
		statuses = Collections.emptyList();
		statsReady = false;
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		clientThread.invoke(() ->
		{
			loadGoals();
			refresh();
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			statsReady = false;
			Arrays.fill(lastDrop, 0);
			Arrays.fill(lastGainMillis, 0);
			refresh();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!statsReady)
		{
			readAllStats();
			checkReached();
			refresh();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		int skill = event.getSkill().ordinal();
		int previous = xp[skill];
		int gained = event.getXp() - previous;
		xp[skill] = event.getXp();
		if (!statsReady || gained <= 0)
		{
			return;
		}

		lastGainMillis[skill] = System.currentTimeMillis();
		// XP reads 0 before the login stats arrive, so don't count that jump as one action.
		if (previous > 0)
		{
			lastDrop[skill] = gained;
		}
		checkReached();
		refresh();
	}

	long millisSinceGain(Skill skill)
	{
		long last = lastGainMillis[skill.ordinal()];
		return last == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - last;
	}

	// Safe to call from the sidebar: an int read is atomic and the value is refreshed every XP drop.
	int getXp(Skill skill)
	{
		return xp[skill.ordinal()];
	}

	void addGoal(Skill skill, int targetXp, String method, double customXp, @Nullable String bonus)
	{
		clientThread.invoke(() ->
		{
			Goal goal = new Goal();
			goal.setId(UUID.randomUUID().toString());
			goal.setSkill(skill);
			goal.setTargetXp(targetXp);
			goal.setStartXp(xp[skill.ordinal()]);
			goal.setMethod(method);
			goal.setCustomXp(customXp);
			goal.setBonus(bonus);
			goals.add(goal);
			saveGoals();
			refresh();
		});
	}

	void setMethod(String goalId, String method, double customXp, @Nullable String bonus)
	{
		clientThread.invoke(() ->
		{
			for (Goal goal : goals)
			{
				if (goal.getId().equals(goalId))
				{
					goal.setMethod(method);
					goal.setCustomXp(customXp);
					goal.setBonus(bonus);
				}
			}
			saveGoals();
			refresh();
		});
	}

	void removeGoal(String goalId)
	{
		clientThread.invoke(() ->
		{
			goals.removeIf(goal -> goal.getId().equals(goalId));
			saveGoals();
			refresh();
		});
	}

	private void readAllStats()
	{
		int[] experiences = client.getSkillExperiences();
		System.arraycopy(experiences, 0, xp, 0, Math.min(experiences.length, xp.length));
		statsReady = true;
	}

	private void checkReached()
	{
		boolean changed = false;
		for (Goal goal : goals)
		{
			if (goal.isReached() || xp[goal.getSkill().ordinal()] < goal.getTargetXp())
			{
				continue;
			}

			goal.setReached(true);
			changed = true;
			String message = "Goal reached: " + GoalMath.targetLabel(goal.getSkill(), goal.getTargetXp()) + "!";
			notifier.notify(config.goalReachedNotification(), message);
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.GAMEMESSAGE)
				.runeLiteFormattedMessage(new ChatMessageBuilder()
					.append(ChatColorType.HIGHLIGHT)
					.append(message)
					.build())
				.build());
		}

		if (changed)
		{
			saveGoals();
		}
	}

	private void refresh()
	{
		List<GoalStatus> next = new ArrayList<>(goals.size());
		for (Goal goal : goals)
		{
			next.add(status(goal));
		}
		statuses = Collections.unmodifiableList(next);

		HowManyMorePanel goalPanel = panel;
		boolean ready = statsReady;
		if (goalPanel != null)
		{
			SwingUtilities.invokeLater(() -> goalPanel.update(next, ready));
		}
	}

	private GoalStatus status(Goal goal)
	{
		Skill skill = goal.getSkill();
		String methodName = null;
		double xpEach = 0;
		if (MethodCatalog.CUSTOM.equals(goal.getMethod()))
		{
			xpEach = goal.getCustomXp();
		}
		else if (MethodCatalog.AUTO.equals(goal.getMethod()))
		{
			xpEach = lastDrop[skill.ordinal()];
		}
		else
		{
			MethodCatalog.Method method = catalog.method(skill, goal.getMethod());
			if (method != null)
			{
				MethodCatalog.Bonus bonus = MethodCatalog.bonus(method, goal.getBonus());
				methodName = method.getName();
				xpEach = method.getXp() * (bonus == null ? 1 : bonus.getMultiplier());
			}
		}

		int current = xp[skill.ordinal()];
		int xpLeft = GoalMath.xpLeft(current, goal.getTargetXp());
		return new GoalStatus(goal.copy(), methodName, xpEach, current, xpLeft, GoalMath.actionsLeft(xpLeft, xpEach),
			GoalMath.progress(goal.getStartXp(), current, goal.getTargetXp()));
	}

	private void loadGoals()
	{
		goals.clear();
		String json = configManager.getRSProfileConfiguration(HowManyMoreConfig.GROUP, GOALS_KEY);
		if (json == null)
		{
			return;
		}

		try
		{
			Goal[] saved = gson.fromJson(json, Goal[].class);
			if (saved == null)
			{
				return;
			}
			for (Goal goal : saved)
			{
				// A skill RuneLite no longer knows reads back as null.
				if (goal != null && goal.getId() != null && goal.getSkill() != null)
				{
					goals.add(goal);
				}
			}
		}
		catch (JsonParseException e)
		{
			log.warn("Couldn't read the saved goals", e);
		}
	}

	private void saveGoals()
	{
		if (goals.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(HowManyMoreConfig.GROUP, GOALS_KEY);
		}
		else
		{
			configManager.setRSProfileConfiguration(HowManyMoreConfig.GROUP, GOALS_KEY, gson.toJson(goals));
		}
	}
}
