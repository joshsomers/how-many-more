package com.howmanymore;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

// The sidebar: a form to add a goal, then a card per goal. Only touched on the Swing thread.
class HowManyMorePanel extends PluginPanel
{
	private final HowManyMorePlugin plugin;
	private final SkillIconManager skillIconManager;

	private final JComboBox<Skill> skillBox = new JComboBox<>();
	private final JTextField targetField = new JTextField();
	private final MethodPicker newMethod = new MethodPicker(null);
	private final JLabel errorLabel = new JLabel();
	private final JPanel goalList = new JPanel(new DynamicGridLayout(0, 1, 0, 8));
	private final JLabel emptyLabel = new JLabel();
	private final Map<String, GoalCard> cards = new LinkedHashMap<>();

	private List<GoalStatus> statuses = Collections.emptyList();
	private boolean ready;

	HowManyMorePanel(HowManyMorePlugin plugin, SkillIconManager skillIconManager)
	{
		this.plugin = plugin;
		this.skillIconManager = skillIconManager;
		setLayout(new BorderLayout(0, 12));
		setBorder(new EmptyBorder(10, 10, 10, 10));

		add(buildForm(), BorderLayout.NORTH);

		JPanel goalsSection = new JPanel(new BorderLayout(0, 6));
		goalsSection.setOpaque(false);
		goalsSection.add(heading("Your goals"), BorderLayout.NORTH);
		goalList.setOpaque(false);
		goalsSection.add(goalList, BorderLayout.CENTER);
		add(goalsSection, BorderLayout.CENTER);

		emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		emptyLabel.setFont(FontManager.getRunescapeSmallFont());
		showGoals();
	}

	private JPanel buildForm()
	{
		// Older RuneLite still lists Overall as a skill. Matched by name, since Skill.OVERALL is going away.
		Skill[] skills = Arrays.stream(Skill.values())
			.filter(skill -> !"Overall".equals(skill.getName()))
			.sorted(Comparator.comparing(Skill::getName))
			.toArray(Skill[]::new);
		for (Skill skill : skills)
		{
			skillBox.addItem(skill);
		}
		skillBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused)
			{
				super.getListCellRendererComponent(list, value, index, selected, focused);
				if (value instanceof Skill)
				{
					Skill skill = (Skill) value;
					setText(skill.getName());
					setIcon(new ImageIcon(skillIconManager.getSkillImage(skill, true)));
				}
				return this;
			}
		});
		skillBox.setSelectedItem(Skill.AGILITY);
		skillBox.addActionListener(e -> resetNewMethod());

		targetField.putClientProperty("JTextField.placeholderText", "e.g. 70 or 1.5m");
		targetField.setToolTipText("A level (like 70) or an amount of XP (like 1,500,000 or 1.5m)");
		targetField.addActionListener(e -> addGoal());

		JButton addButton = new JButton("Add goal");
		addButton.addActionListener(e -> addGoal());

		errorLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		errorLabel.setFont(FontManager.getRunescapeSmallFont());

		JPanel form = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		form.setOpaque(false);
		form.add(heading("New goal"));
		form.add(skillBox);
		form.add(label("Target level or XP"));
		form.add(targetField);
		form.add(label("Method"));
		form.add(newMethod);
		form.add(addButton);
		form.add(errorLabel);
		resetNewMethod();
		return form;
	}

	// Called once the Skill Calculator's methods are read, so the pickers can list them.
	void catalogLoaded()
	{
		resetNewMethod();
		cards.clear();
		showGoals();
	}

	void update(List<GoalStatus> statuses, boolean ready)
	{
		boolean loggedIn = ready && !this.ready;
		this.statuses = statuses;
		this.ready = ready;
		if (loggedIn)
		{
			// The preselected method depends on the player's level, which is only known now.
			resetNewMethod();
		}
		showGoals();
	}

	private void showGoals()
	{
		List<String> ids = new ArrayList<>(statuses.size());
		for (GoalStatus status : statuses)
		{
			ids.add(status.getGoal().getId());
		}

		// Rebuild only when goals are added or removed, so an open method dropdown isn't closed by an XP drop.
		if (!ids.equals(new ArrayList<>(cards.keySet())))
		{
			cards.clear();
			goalList.removeAll();
			for (GoalStatus status : statuses)
			{
				GoalCard card = new GoalCard(plugin, skillIconManager, status);
				cards.put(status.getGoal().getId(), card);
				goalList.add(card);
			}
			if (statuses.isEmpty())
			{
				emptyLabel.setText(ready ? "No goals yet. Add one above." : "Log in to see your goals.");
				goalList.add(emptyLabel);
			}
			goalList.revalidate();
			goalList.repaint();
		}
		else if (statuses.isEmpty())
		{
			emptyLabel.setText(ready ? "No goals yet. Add one above." : "Log in to see your goals.");
		}

		for (GoalStatus status : statuses)
		{
			cards.get(status.getGoal().getId()).update(status);
		}
	}

	private void addGoal()
	{
		Skill skill = (Skill) skillBox.getSelectedItem();
		int targetXp = GoalMath.parseTarget(targetField.getText());
		String method = newMethod.getMethodId();
		double customXp = newMethod.getCustomXp();

		String error = null;
		if (skill == null)
		{
			error = "Pick a skill.";
		}
		else if (!plugin.isStatsReady())
		{
			error = "Log in to set goals.";
		}
		else if (targetXp < 0)
		{
			error = "Enter a level from 2 to 126, or an XP amount.";
		}
		else if (targetXp <= plugin.getXp(skill))
		{
			error = "You're already past that.";
		}
		else if (MethodCatalog.CUSTOM.equals(method) && customXp <= 0)
		{
			error = "Enter the XP you get each time.";
		}

		// A label with no text takes no room.
		errorLabel.setText(error);
		if (error != null)
		{
			return;
		}

		plugin.addGoal(skill, targetXp, method, customXp, newMethod.getBonusId());
		targetField.setText("");
	}

	// Lists the new skill's methods, preselecting the most advanced one the player can use.
	private void resetNewMethod()
	{
		Skill skill = (Skill) skillBox.getSelectedItem();
		if (skill == null)
		{
			return;
		}

		MethodCatalog catalog = plugin.getCatalog();
		int level = plugin.isStatsReady() ? Experience.getLevelForXp(plugin.getXp(skill)) : 1;
		MethodCatalog.Method best = catalog.best(skill, level);
		newMethod.setChoices(catalog.methods(skill), best == null ? MethodCatalog.AUTO : best.getId(), 0, null);
	}

	private static JLabel heading(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		return label;
	}

	private static JLabel label(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}
}
