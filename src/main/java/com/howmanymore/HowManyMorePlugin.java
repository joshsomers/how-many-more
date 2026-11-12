package com.howmanymore;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Experience;
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
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

@Slf4j
@PluginDescriptor(
	name = "How Many More",
	description = "Counts down the laps, catches or other actions left to reach your skill goals, live on screen",
	tags = {"goal", "xp", "experience", "level", "skill", "calculator", "laps", "actions", "counter"}
)
public class HowManyMorePlugin extends Plugin
{
	private static final String GOALS_KEY = "goals";
	private static final long FINISH_WITHIN_MILLIS = TimeUnit.DAYS.toMillis(1);
	// The summary needs at least this much training to give a sensible XP/hr.
	private static final long SUMMARY_MIN_MILLIS = TimeUnit.MINUTES.toMillis(1);
	// Training time is saved this often, so closing the client without logging out loses little.
	private static final int SAVE_EVERY_TICKS = 100;

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
	private final XpRate[] rates = new XpRate[Skill.values().length];
	// Set when training time is added, until the goals are next saved.
	private boolean unsavedTraining;
	private int ticksSinceSave;

	{
		Arrays.setAll(rates, skill -> new XpRate());
	}

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
			// Rates are only touched on the client thread.
			resetRates();
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
		clientThread.invoke(() ->
		{
			if (unsavedTraining)
			{
				saveGoals();
			}
			goals.clear();
			statuses = Collections.emptyList();
			statsReady = false;
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (HowManyMoreConfig.GROUP.equals(event.getGroup())
			&& (HowManyMoreConfig.COUNT_DOWN_KEY.equals(event.getKey()) || HowManyMoreConfig.FINISH_TIME_KEY.equals(event.getKey())))
		{
			clientThread.invoke(this::refresh);
		}
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
			if (unsavedTraining)
			{
				saveGoals();
			}
			statsReady = false;
			Arrays.fill(lastDrop, 0);
			Arrays.fill(lastGainMillis, 0);
			resetRates();
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

		if (++ticksSinceSave >= SAVE_EVERY_TICKS)
		{
			ticksSinceSave = 0;
			if (unsavedTraining)
			{
				saveGoals();
			}
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

		long now = System.currentTimeMillis();
		long sinceGain = now - lastGainMillis[skill];
		if (lastGainMillis[skill] != 0 && sinceGain <= XpRate.BREAK_MILLIS)
		{
			addTraining(event.getSkill(), sinceGain);
		}
		lastGainMillis[skill] = now;
		// XP reads 0 before the login stats arrive, so don't count that jump as one action.
		if (previous > 0)
		{
			lastDrop[skill] = gained;
			rates[skill].gain(now, gained);
			announceNextStages(event.getSkill(), previous, event.getXp());
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
			goal.setStages(new ArrayList<>(Collections.singletonList(new Stage(method, customXp, bonus, 0))));
			goal.setTrainedMillis(0);
			goals.add(goal);
			saveGoals();
			refresh();
		});
	}

	void setStageMethod(String goalId, int index, String method, double customXp, @Nullable String bonus)
	{
		editStage(goalId, index, stage ->
		{
			stage.setMethod(method);
			stage.setCustomXp(customXp);
			stage.setBonus(bonus);
		});
	}

	void setStageEnd(String goalId, int index, int endLevel)
	{
		editStage(goalId, index, stage -> stage.setEndLevel(endLevel));
	}

	void removeStage(String goalId, int index)
	{
		clientThread.invoke(() ->
		{
			Goal goal = goal(goalId);
			if (goal != null && goal.getStages().size() > 1 && index < goal.getStages().size())
			{
				goal.getStages().remove(index);
				saveGoals();
				refresh();
			}
		});
	}

	// Adds a method after the last one. The last one then ends where the next method the Skill Calculator lists unlocks.
	void addStage(String goalId)
	{
		clientThread.invoke(() ->
		{
			Goal goal = goal(goalId);
			if (goal == null)
			{
				return;
			}

			List<Stage> stages = goal.getStages();
			Stage last = stages.get(stages.size() - 1);
			int startLevel = Math.max(Experience.getLevelForXp(xp[goal.getSkill().ordinal()]),
				stages.size() > 1 ? stages.get(stages.size() - 2).getEndLevel() : 1);
			int maxEnd = maxStageEnd(goal.getTargetXp());
			if (startLevel >= maxEnd)
			{
				return;
			}

			MethodCatalog.Method lastMethod = catalog.method(goal.getSkill(), last.getMethod());
			MethodCatalog.Method next = nextMethod(goal.getSkill(), lastMethod, startLevel, maxEnd);
			if (next != null)
			{
				last.setEndLevel(next.getLevel());
				stages.add(new Stage(next.getId(), 0, null, 0));
			}
			else
			{
				last.setEndLevel((startLevel + maxEnd + 1) / 2);
				stages.add(new Stage(last.getMethod(), last.getCustomXp(), last.getBonus(), 0));
			}
			saveGoals();
			refresh();
		});
	}

	// The highest level a stage before the last can end at: the last level below the goal.
	static int maxStageEnd(int targetXp)
	{
		return Experience.getLevelForXp(Math.max(0, targetXp - 1));
	}

	// The lowest-level method above the start that gives more XP each than the last one, up to the last stage's end.
	@Nullable
	private MethodCatalog.Method nextMethod(Skill skill, @Nullable MethodCatalog.Method after, int startLevel, int maxEnd)
	{
		int fromLevel = Math.max(startLevel, after == null ? 0 : after.getLevel());
		MethodCatalog.Method next = null;
		for (MethodCatalog.Method method : catalog.methods(skill))
		{
			if (method.getLevel() > fromLevel && method.getLevel() <= maxEnd
				&& (after == null || method.getXp() > after.getXp())
				&& (next == null || method.getLevel() < next.getLevel()))
			{
				next = method;
			}
		}
		return next;
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

	private void editStage(String goalId, int index, Consumer<Stage> edit)
	{
		clientThread.invoke(() ->
		{
			Goal goal = goal(goalId);
			if (goal != null && index < goal.getStages().size())
			{
				edit.accept(goal.getStages().get(index));
				saveGoals();
				refresh();
			}
		});
	}

	@Nullable
	private Goal goal(String goalId)
	{
		for (Goal goal : goals)
		{
			if (goal.getId().equals(goalId))
			{
				return goal;
			}
		}
		return null;
	}

	private void addTraining(Skill skill, long millis)
	{
		for (Goal goal : goals)
		{
			if (goal.getSkill() == skill && !goal.isReached() && goal.getTrainedMillis() >= 0)
			{
				goal.setTrainedMillis(goal.getTrainedMillis() + millis);
				unsavedTraining = true;
			}
		}
	}

	// Says in chat which method to switch to when a stage's end level is passed.
	private void announceNextStages(Skill skill, int previousXp, int currentXp)
	{
		for (Goal goal : goals)
		{
			List<Stage> stages = goal.getStages();
			if (goal.getSkill() != skill || goal.isReached() || stages.size() < 2 || currentXp >= goal.getTargetXp())
			{
				continue;
			}

			int[] ends = StagePlan.endXps(stages, goal.getTargetXp());
			int next = -1;
			for (int i = 0; i < ends.length - 1; i++)
			{
				if (previousXp < ends[i] && currentXp >= ends[i])
				{
					next = i + 1;
				}
			}
			if (next >= 0)
			{
				chat("Next method for " + GoalMath.targetLabel(skill, goal.getTargetXp()) + ": "
					+ methodLabel(skill, stages.get(next)) + ".");
			}
		}
	}

	private String methodLabel(Skill skill, Stage stage)
	{
		if (MethodCatalog.CUSTOM.equals(stage.getMethod()))
		{
			return GoalMath.formatXp(stage.getCustomXp()) + " XP each";
		}
		if (MethodCatalog.AUTO.equals(stage.getMethod()))
		{
			return "Auto";
		}
		MethodCatalog.Method method = catalog.method(skill, stage.getMethod());
		return method == null ? "a new method" : method.getName();
	}

	private void resetRates()
	{
		for (XpRate rate : rates)
		{
			rate.reset();
		}
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
			int current = xp[goal.getSkill().ordinal()];
			if (goal.isReached() || current < goal.getTargetXp())
			{
				continue;
			}

			goal.setReached(true);
			changed = true;
			String message = "Goal reached: " + GoalMath.targetLabel(goal.getSkill(), goal.getTargetXp()) + "!";
			notifier.notify(config.goalReachedNotification(), message);
			long trained = goal.getTrainedMillis();
			if (config.goalReachedSummary() && trained >= SUMMARY_MIN_MILLIS)
			{
				int gained = current - goal.getStartXp();
				message += " " + QuantityFormatter.quantityToStackSize(gained) + " XP in "
					+ GoalMath.formatDuration(trained) + " of training, "
					+ QuantityFormatter.quantityToStackSize(Math.round(gained * (double) TimeUnit.HOURS.toMillis(1) / trained))
					+ " XP/hr.";
			}
			chat(message);
		}

		if (changed)
		{
			saveGoals();
		}
	}

	private void chat(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append(message)
				.build())
			.build());
	}

	private void refresh()
	{
		long now = System.currentTimeMillis();
		List<GoalStatus> next = new ArrayList<>(goals.size());
		for (Goal goal : goals)
		{
			next.add(status(goal, now));
		}
		statuses = Collections.unmodifiableList(next);

		HowManyMorePanel goalPanel = panel;
		boolean ready = statsReady;
		if (goalPanel != null)
		{
			SwingUtilities.invokeLater(() -> goalPanel.update(next, ready));
		}
	}

	private GoalStatus status(Goal goal, long now)
	{
		Skill skill = goal.getSkill();
		List<Stage> stages = goal.getStages();
		int current = xp[skill.ordinal()];
		int[] ends = StagePlan.endXps(stages, goal.getTargetXp());
		int currentStage = StagePlan.current(ends, current);

		double[] xpEach = new double[stages.size()];
		String[] names = new String[stages.size()];
		for (int i = 0; i < stages.size(); i++)
		{
			Stage stage = stages.get(i);
			if (MethodCatalog.CUSTOM.equals(stage.getMethod()))
			{
				xpEach[i] = stage.getCustomXp();
			}
			else if (MethodCatalog.AUTO.equals(stage.getMethod()))
			{
				// The last drop only says something about the method being trained now.
				xpEach[i] = i == currentStage ? lastDrop[skill.ordinal()] : 0;
			}
			else
			{
				MethodCatalog.Method method = catalog.method(skill, stage.getMethod());
				if (method != null)
				{
					MethodCatalog.Bonus bonus = MethodCatalog.bonus(method, stage.getBonus());
					names[i] = method.getName();
					xpEach[i] = method.getXp() * (bonus == null ? 1 : bonus.getMultiplier());
				}
			}
		}

		int[] actions = StagePlan.actionsLeft(ends, xpEach, current);
		List<GoalStatus.StageStatus> stageStatuses = new ArrayList<>(stages.size());
		for (int i = 0; i < stages.size(); i++)
		{
			stageStatuses.add(new GoalStatus.StageStatus(names[i], xpEach[i], ends[i], actions[i], current >= ends[i]));
		}

		int xpLeft = GoalMath.xpLeft(current, goal.getTargetXp());
		double xpPerHour = rates[skill.ordinal()].perHour();
		long millisLeft = GoalMath.millisLeft(xpLeft, xpPerHour);
		long stageMillisLeft = GoalMath.millisLeft(GoalMath.xpLeft(current, ends[currentStage]), xpPerHour);
		long finishAt = !goal.isReached() && millisLeft >= 0 && millisLeft < FINISH_WITHIN_MILLIS ? now + millisLeft : -1;
		return new GoalStatus(goal.copy(), Collections.unmodifiableList(stageStatuses), currentStage, current, xpLeft,
			StagePlan.total(actions), GoalMath.progress(goal.getStartXp(), current, goal.getTargetXp()),
			config.countDown() == HowManyMoreConfig.CountDown.TIME, config.showFinishTime(), xpPerHour, millisLeft,
			stageMillisLeft, finishAt);
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
					goal.migrate();
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
		unsavedTraining = false;
		if (goals.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(HowManyMoreConfig.GROUP, GOALS_KEY);
			return;
		}
		for (Goal goal : goals)
		{
			goal.syncOldFields();
		}
		configManager.setRSProfileConfiguration(HowManyMoreConfig.GROUP, GOALS_KEY, gson.toJson(goals));
	}
}
