package com.howmanymore;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Experience;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.ProgressBar;
import net.runelite.client.util.QuantityFormatter;

// One goal in the sidebar: what's left, a progress bar, and its method.
class GoalCard extends JPanel
{
	private static final int PROGRESS_STEPS = 1000;
	private static final Color PROGRESS_BACKGROUND = new Color(61, 56, 49);
	// Light, for the dark bar.
	private static final Color BAND_COLOR = new Color(255, 255, 255, 24);

	private final JLabel countLabel = new JLabel();
	private final JLabel detailLabel = new JLabel();
	private final BandedProgressBar progressBar = new BandedProgressBar();

	GoalCard(HowManyMorePlugin plugin, SkillIconManager skillIconManager, GoalStatus status)
	{
		Goal goal = status.getGoal();
		setLayout(new DynamicGridLayout(0, 1, 0, 6));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(8, 8, 8, 8));

		JLabel title = new JLabel(status.getTargetLabel(), new ImageIcon(skillIconManager.getSkillImage(goal.getSkill(), true)),
			JLabel.LEFT);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setIconTextGap(6);

		JButton remove = new JButton("Remove");
		remove.setFont(FontManager.getRunescapeSmallFont());
		remove.setFocusable(false);
		remove.addActionListener(e -> plugin.removeGoal(goal.getId()));

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.add(title, BorderLayout.CENTER);
		header.add(remove, BorderLayout.EAST);

		countLabel.setFont(FontManager.getRunescapeBoldFont());
		detailLabel.setFont(FontManager.getRunescapeSmallFont());
		detailLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		progressBar.setMaximumValue(PROGRESS_STEPS);
		progressBar.setBackground(PROGRESS_BACKGROUND);
		progressBar.setPreferredSize(new Dimension(0, 16));

		MethodPicker methodPicker = new MethodPicker(picker -> plugin.setMethod(goal.getId(), picker.getMethodId(),
			picker.getCustomXp(), picker.getBonusId()));
		methodPicker.setChoices(plugin.getCatalog().methods(goal.getSkill()), goal.getMethod(), goal.getCustomXp(),
			goal.getBonus());

		add(header);
		add(countLabel);
		add(detailLabel);
		add(progressBar);
		add(methodPicker);
		update(status);
	}

	void update(GoalStatus status)
	{
		if (status.isReached())
		{
			countLabel.setText("Goal reached!");
			countLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		}
		else if (status.getActionsLeft() < 0)
		{
			countLabel.setText(status.getMissingCountReason());
			countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}
		else
		{
			countLabel.setText(QuantityFormatter.formatNumber(status.getActionsLeft()) + " left");
			countLabel.setForeground(ColorScheme.BRAND_ORANGE);
		}

		String detail = QuantityFormatter.formatNumber(status.getXpLeft()) + " XP left";
		if (status.getXpEach() > 0)
		{
			detail += ", " + GoalMath.formatXp(status.getXpEach()) + " XP each";
		}
		detailLabel.setText(detail);

		int targetLevel = GoalMath.targetLevel(status.getGoal().getTargetXp());
		progressBar.goal = status.getGoal();
		progressBar.setValue((int) Math.round(status.getProgress() * PROGRESS_STEPS));
		progressBar.setLeftLabel("Lvl " + Experience.getLevelForXp(status.getCurrentXp()));
		progressBar.setRightLabel(targetLevel > 0
			? "Lvl " + targetLevel
			: QuantityFormatter.quantityToStackSize(status.getGoal().getTargetXp()));
		progressBar.setCenterLabel((int) Math.floor(status.getProgress() * 100) + "%");
		progressBar.setForeground(status.isReached() ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.BRAND_ORANGE);
	}

	// RuneLite's progress bar with level bands under its fill and labels.
	private static class BandedProgressBar extends ProgressBar
	{
		private Goal goal;

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			if (goal != null)
			{
				// The fill is painted over the bands, so they only show on the part still to go.
				LevelBands.paint(g, BAND_COLOR, goal.getStartXp(), goal.getTargetXp(), 0, 0, getWidth(), getHeight(), 0);
			}
		}
	}
}
