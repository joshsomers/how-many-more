package com.howmanymore;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import net.runelite.api.Experience;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;
import net.runelite.client.util.QuantityFormatter;

class GoalOverlay extends OverlayPanel
{
	private static final int WIDTH = 170;
	private static final Color COUNT_COLOR = Color.YELLOW;
	private static final Color REACHED_COLOR = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color HINT_COLOR = Color.LIGHT_GRAY;
	// Dark, for RuneLite's light overlay bar.
	private static final Color BAND_COLOR = new Color(0, 0, 0, 45);
	private static final Color CLEAR = new Color(0, 0, 0, 0);

	private final HowManyMorePlugin plugin;
	private final HowManyMoreConfig config;
	private final SkillIconManager skillIconManager;

	@Inject
	GoalOverlay(HowManyMorePlugin plugin, HowManyMoreConfig config, SkillIconManager skillIconManager)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.skillIconManager = skillIconManager;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
		panelComponent.setGap(new Point(0, 4));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showHud() || !plugin.isStatsReady())
		{
			return null;
		}

		long hideAfter = TimeUnit.MINUTES.toMillis(config.hideAfterIdle());
		for (GoalStatus status : plugin.getStatuses())
		{
			if (hideAfter == 0 || plugin.millisSinceGain(status.getSkill()) <= hideAfter)
			{
				addGoal(status);
			}
		}

		if (panelComponent.getChildren().isEmpty())
		{
			return null;
		}
		return super.render(graphics);
	}

	private void addGoal(GoalStatus status)
	{
		LayoutableRenderableEntity text = SplitComponent.builder()
			.first(LineComponent.builder()
				.left(status.getMethodName() != null ? status.getMethodName() : status.getSkill().getName())
				.build())
			.second(countLine(status))
			.orientation(ComponentOrientation.VERTICAL)
			.build();

		if (config.showXpLeft() && !status.isReached())
		{
			text = SplitComponent.builder()
				.first(text)
				.second(LineComponent.builder()
					.left("XP left")
					.right(QuantityFormatter.formatNumber(status.getXpLeft()))
					.build())
				.orientation(ComponentOrientation.VERTICAL)
				.build();
		}

		panelComponent.getChildren().add(SplitComponent.builder()
			.first(new ImageComponent(skillIconManager.getSkillImage(status.getSkill(), true)))
			.second(text)
			.orientation(ComponentOrientation.HORIZONTAL)
			.gap(new Point(4, 0))
			.build());

		if (config.showProgressBar())
		{
			panelComponent.getChildren().add(new GoalBar(status));
		}
	}

	private static LineComponent countLine(GoalStatus status)
	{
		if (status.isReached())
		{
			return LineComponent.builder().left("Goal reached!").leftColor(REACHED_COLOR).build();
		}
		if (status.getActionsLeft() < 0)
		{
			return LineComponent.builder().left(status.getMissingCountReason()).leftColor(HINT_COLOR).build();
		}
		return LineComponent.builder()
			.left("To " + shortTarget(status.getGoal().getTargetXp()))
			.right(QuantityFormatter.formatNumber(status.getActionsLeft()))
			.rightColor(COUNT_COLOR)
			.build();
	}

	// RuneLite's overlay bar with level bands: the bar without its text, then the bands, then only the text.
	private static class GoalBar implements LayoutableRenderableEntity
	{
		private final GoalStatus status;
		private final Rectangle bounds = new Rectangle();
		private Point preferredLocation = new Point();
		private Dimension preferredSize = new Dimension();

		GoalBar(GoalStatus status)
		{
			this.status = status;
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			ProgressBarComponent bar = bar();
			bar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.TEXT_ONLY);
			bar.setCenterLabel("");
			if (status.isReached())
			{
				bar.setForegroundColor(REACHED_COLOR);
			}
			Dimension size = bar.render(graphics);

			// The fill's width, worked out as ProgressBarComponent does it.
			int fill = (int) (size.width * Math.min(1, status.getProgress() * 100 / 100));
			Goal goal = status.getGoal();
			LevelBands.paint(graphics, BAND_COLOR, goal.getStartXp(), goal.getTargetXp(), preferredLocation.x,
				preferredLocation.y, size.width, size.height, fill);

			ProgressBarComponent text = bar();
			text.setForegroundColor(CLEAR);
			text.setBackgroundColor(CLEAR);
			text.setLeftLabel("Lvl " + Experience.getLevelForXp(status.getCurrentXp()));
			int targetLevel = GoalMath.targetLevel(goal.getTargetXp());
			text.setRightLabel(targetLevel > 0 ? "Lvl " + targetLevel : shortTarget(goal.getTargetXp()));
			text.render(graphics);

			bounds.setLocation(preferredLocation);
			bounds.setSize(size);
			return size;
		}

		private ProgressBarComponent bar()
		{
			ProgressBarComponent bar = new ProgressBarComponent();
			bar.setMaximum(100);
			bar.setValue(status.getProgress() * 100);
			bar.setPreferredLocation(preferredLocation);
			bar.setPreferredSize(preferredSize);
			return bar;
		}

		@Override
		public void setPreferredLocation(Point preferredLocation)
		{
			this.preferredLocation = preferredLocation;
		}

		@Override
		public void setPreferredSize(Dimension preferredSize)
		{
			this.preferredSize = preferredSize;
		}

		@Override
		public Rectangle getBounds()
		{
			return bounds;
		}
	}

	// "70" for a level goal, "1.5M XP" for an XP goal.
	private static String shortTarget(int targetXp)
	{
		int level = GoalMath.targetLevel(targetXp);
		return level > 0 ? Integer.toString(level) : QuantityFormatter.quantityToStackSize(targetXp) + " XP";
	}
}
