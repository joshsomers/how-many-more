package com.howmanymore;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.ProgressBar;
import net.runelite.client.util.QuantityFormatter;

// One goal in the sidebar: what's left, a progress bar, and its methods in training order.
class GoalCard extends JPanel
{
	private static final int PROGRESS_STEPS = 1000;
	private static final Color PROGRESS_BACKGROUND = new Color(61, 56, 49);
	// Light, for the dark bar.
	private static final Color BAND_COLOR = new Color(255, 255, 255, 24);

	private final HowManyMorePlugin plugin;
	private final String goalId;
	private final Skill skill;
	private final JLabel countLabel = new JLabel();
	private final JLabel detailLabel = new JLabel();
	private final JLabel finishLabel = new JLabel();
	private final BandedProgressBar progressBar = new BandedProgressBar();
	private final JPanel stagesPanel = new JPanel(new DynamicGridLayout(0, 1, 0, 8));
	private final List<StageRow> rows = new ArrayList<>();
	private final JButton addStageButton = new JButton("Add next method");

	GoalCard(HowManyMorePlugin plugin, SkillIconManager skillIconManager, GoalStatus status)
	{
		this.plugin = plugin;
		Goal goal = status.getGoal();
		goalId = goal.getId();
		skill = goal.getSkill();
		setLayout(new DynamicGridLayout(0, 1, 0, 6));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(8, 8, 8, 8));

		JLabel title = new JLabel(status.getTargetLabel(), new ImageIcon(skillIconManager.getSkillImage(skill, true)),
			JLabel.LEFT);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setIconTextGap(6);

		JButton remove = new JButton("Remove");
		remove.setFont(FontManager.getRunescapeSmallFont());
		remove.setFocusable(false);
		remove.addActionListener(e -> plugin.removeGoal(goalId));

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.add(title, BorderLayout.CENTER);
		header.add(remove, BorderLayout.EAST);

		countLabel.setFont(FontManager.getRunescapeBoldFont());
		detailLabel.setFont(FontManager.getRunescapeSmallFont());
		detailLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		finishLabel.setFont(FontManager.getRunescapeSmallFont());
		finishLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		progressBar.setMaximumValue(PROGRESS_STEPS);
		progressBar.setBackground(PROGRESS_BACKGROUND);
		progressBar.setPreferredSize(new Dimension(0, 16));
		stagesPanel.setOpaque(false);
		addStageButton.setFont(FontManager.getRunescapeSmallFont());
		addStageButton.setFocusable(false);
		addStageButton.setToolTipText("Switch to another method at a level on the way");
		addStageButton.addActionListener(e -> plugin.addStage(goalId));

		add(header);
		add(countLabel);
		add(detailLabel);
		add(progressBar);
		add(stagesPanel);
		add(addStageButton);
		update(status);
	}

	void update(GoalStatus status)
	{
		if (status.isReached())
		{
			countLabel.setText("Goal reached!");
			countLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		}
		else if (status.showsTime())
		{
			countLabel.setText(GoalMath.formatDuration(status.getMillisLeft()) + " left");
			countLabel.setForeground(ColorScheme.BRAND_ORANGE);
		}
		else if (status.getActionsLeft() >= 0)
		{
			countLabel.setText(QuantityFormatter.formatNumber(status.getActionsLeft()) + " left");
			countLabel.setForeground(ColorScheme.BRAND_ORANGE);
		}
		else
		{
			countLabel.setText(status.getMissingCountReason());
			countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}

		String detail = QuantityFormatter.formatNumber(status.getXpLeft()) + " XP left";
		if (status.showsTime())
		{
			detail += ", " + QuantityFormatter.quantityToStackSize(Math.round(status.getXpPerHour())) + " XP/hr";
		}
		else if (status.getXpEach() > 0)
		{
			detail += ", " + GoalMath.formatXp(status.getXpEach()) + " XP each";
		}
		detailLabel.setText(detail);
		showFinish(status.isFinishShown() && status.getFinishAtMillis() >= 0
			? "Done at " + GoalMath.formatClock(status.getFinishAtMillis(), System.currentTimeMillis())
			: null);

		int targetLevel = GoalMath.targetLevel(status.getGoal().getTargetXp());
		progressBar.goal = status.getGoal();
		progressBar.setValue((int) Math.round(status.getProgress() * PROGRESS_STEPS));
		progressBar.setLeftLabel("Lvl " + Experience.getLevelForXp(status.getCurrentXp()));
		progressBar.setRightLabel(targetLevel > 0
			? "Lvl " + targetLevel
			: QuantityFormatter.quantityToStackSize(status.getGoal().getTargetXp()));
		progressBar.setCenterLabel((int) Math.floor(status.getProgress() * 100) + "%");
		progressBar.setForeground(status.isReached() ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.BRAND_ORANGE);

		// Rebuilt only when a method is added or removed, so an XP drop doesn't close an open dropdown.
		if (rows.size() != status.getStages().size())
		{
			rows.clear();
			stagesPanel.removeAll();
			for (int i = 0; i < status.getStages().size(); i++)
			{
				StageRow row = new StageRow(status.getGoal(), i);
				rows.add(row);
				stagesPanel.add(row);
			}
			stagesPanel.revalidate();
			stagesPanel.repaint();
		}
		for (StageRow row : rows)
		{
			row.update(status);
		}
		addStageButton.setEnabled(canAddStage(status));
	}

	// A label with no text still takes a grid gap, so the finish time is added and removed instead.
	private void showFinish(@Nullable String text)
	{
		if (text == null)
		{
			if (finishLabel.getParent() == this)
			{
				remove(finishLabel);
				revalidate();
			}
			return;
		}
		finishLabel.setText(text);
		if (finishLabel.getParent() != this)
		{
			add(finishLabel, getComponentZOrder(detailLabel) + 1);
			revalidate();
		}
	}

	// A new method needs at least one level between where the last one starts and the goal.
	private static boolean canAddStage(GoalStatus status)
	{
		List<Stage> stages = status.getGoal().getStages();
		int start = Experience.getLevelForXp(status.getCurrentXp());
		if (stages.size() > 1)
		{
			start = Math.max(start, stages.get(stages.size() - 2).getEndLevel());
		}
		return !status.isReached() && start < HowManyMorePlugin.maxStageEnd(status.getGoal().getTargetXp());
	}

	private static JLabel small(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	// One method: its picker, then where it ends and the actions left in it. A lone method shows just the picker.
	private class StageRow extends JPanel
	{
		private final int index;
		@Nullable
		private final JLabel stageCount;

		StageRow(Goal goal, int index)
		{
			this.index = index;
			setLayout(new DynamicGridLayout(0, 1, 0, 4));
			setOpaque(false);

			Stage stage = goal.getStages().get(index);
			MethodPicker picker = new MethodPicker(p -> plugin.setStageMethod(goalId, index, p.getMethodId(),
				p.getCustomXp(), p.getBonusId()));
			picker.setChoices(plugin.getCatalog().methods(skill), stage.getMethod(), stage.getCustomXp(), stage.getBonus());
			add(picker);

			int count = goal.getStages().size();
			if (count == 1)
			{
				stageCount = null;
				return;
			}

			JPanel end = new JPanel(new BorderLayout(4, 0));
			end.setOpaque(false);
			if (index == count - 1)
			{
				int level = GoalMath.targetLevel(goal.getTargetXp());
				end.add(small(level > 0 ? "To " + level : "To goal"), BorderLayout.CENTER);
			}
			else
			{
				int max = Math.max(2, HowManyMorePlugin.maxStageEnd(goal.getTargetXp()));
				int value = Math.max(2, Math.min(max, stage.getEndLevel()));
				JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, 2, max, 1));
				spinner.setToolTipText("The level to switch to the next method at");
				spinner.addChangeListener(e -> plugin.setStageEnd(goalId, index, (Integer) spinner.getValue()));
				end.add(small("To lvl"), BorderLayout.WEST);
				end.add(spinner, BorderLayout.CENTER);
			}

			stageCount = new JLabel();
			stageCount.setFont(FontManager.getRunescapeSmallFont());
			stageCount.setHorizontalAlignment(JLabel.RIGHT);

			JButton remove = new JButton("×");
			remove.setFocusable(false);
			remove.setToolTipText("Remove this method");
			remove.addActionListener(e -> plugin.removeStage(goalId, index));

			JPanel line = new JPanel(new BorderLayout(6, 0));
			line.setOpaque(false);
			line.add(end, BorderLayout.WEST);
			line.add(stageCount, BorderLayout.CENTER);
			line.add(remove, BorderLayout.EAST);
			add(line);
		}

		void update(GoalStatus status)
		{
			if (stageCount == null)
			{
				return;
			}

			GoalStatus.StageStatus stage = status.getStages().get(index);
			if (stage.isDone())
			{
				stageCount.setText("Done");
				stageCount.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
			}
			else if (stage.getActionsLeft() < 0)
			{
				stageCount.setText("?");
				stageCount.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
			else
			{
				stageCount.setText(QuantityFormatter.formatNumber(stage.getActionsLeft()));
				stageCount.setForeground(index == status.getCurrentStage() ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
			}
		}
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
