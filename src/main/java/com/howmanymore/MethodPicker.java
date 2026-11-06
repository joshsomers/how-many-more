package com.howmanymore;

import java.awt.BorderLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import lombok.Value;
import net.runelite.client.ui.DynamicGridLayout;

// The method, bonus and custom XP choice for a goal, shared by the new-goal form and each goal card.
class MethodPicker extends JPanel
{
	@Value
	private static class MethodOption
	{
		String id;
		String label;
		@Nullable
		MethodCatalog.Method method;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@Value
	private static class BonusOption
	{
		@Nullable
		MethodCatalog.Bonus bonus;

		@Override
		public String toString()
		{
			if (bonus == null)
			{
				return "No bonus";
			}
			double multiplier = bonus.getMultiplier();
			return bonus.getName() + (multiplier >= 1 && multiplier < 1.5
				? " (+" + GoalMath.formatXp((multiplier - 1) * 100) + "%)"
				: " (x" + GoalMath.formatXp(multiplier) + ")");
		}
	}

	private static final MethodOption CUSTOM = new MethodOption(MethodCatalog.CUSTOM, "Custom XP each", null);
	private static final MethodOption AUTO = new MethodOption(MethodCatalog.AUTO, "Auto (size of last XP drop)", null);

	private final JComboBox<MethodOption> methodBox = new JComboBox<>();
	private final JComboBox<BonusOption> bonusBox = new JComboBox<>();
	private final JPanel customRow = new JPanel(new BorderLayout(6, 0));
	private final JTextField customField = new JTextField();
	@Nullable
	private final Consumer<MethodPicker> onChange;
	// Set while the choices are filled in, so that doesn't count as the player changing them.
	private boolean filling;

	MethodPicker(@Nullable Consumer<MethodPicker> onChange)
	{
		this.onChange = onChange;
		setLayout(new DynamicGridLayout(0, 1, 0, 4));
		setOpaque(false);

		// Without a prototype the box is as wide as its longest method name and pushes the panel wider.
		methodBox.setPrototypeDisplayValue(new MethodOption("", "Razor-backed Kebbit (49)", null));
		bonusBox.setPrototypeDisplayValue(new BonusOption(null));
		methodBox.setToolTipText("The method you're training with");
		bonusBox.setToolTipText("An XP bonus from the Skill Calculator, like an outfit or altar");
		customField.setToolTipText("The XP you get each time");

		customRow.setOpaque(false);
		customRow.add(new JLabel("XP each"), BorderLayout.WEST);
		customRow.add(customField, BorderLayout.CENTER);

		add(methodBox);

		methodBox.addActionListener(e ->
		{
			if (!filling)
			{
				showExtras(null);
				changed();
			}
		});
		bonusBox.addActionListener(e ->
		{
			if (!filling)
			{
				changed();
			}
		});
		customField.addActionListener(e -> changed());
		customField.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				changed();
			}
		});
	}

	void setChoices(List<MethodCatalog.Method> methods, @Nullable String methodId, double customXp, @Nullable String bonusId)
	{
		filling = true;
		methodBox.removeAllItems();
		MethodOption selected = null;
		for (MethodCatalog.Method method : methods)
		{
			MethodOption option = new MethodOption(method.getId(), method.getName() + " (" + method.getLevel() + ")", method);
			methodBox.addItem(option);
			if (method.getId().equals(methodId))
			{
				selected = option;
			}
		}
		methodBox.addItem(CUSTOM);
		methodBox.addItem(AUTO);

		if (CUSTOM.getId().equals(methodId))
		{
			selected = CUSTOM;
		}
		else if (AUTO.getId().equals(methodId) || methodId == null)
		{
			selected = AUTO;
		}
		else if (selected == null)
		{
			// Keep a saved method the Skill Calculator no longer lists, so it isn't silently replaced.
			selected = new MethodOption(methodId, "Pick a method", null);
			methodBox.insertItemAt(selected, 0);
		}
		methodBox.setSelectedItem(selected);
		customField.setText(customXp > 0 ? GoalMath.formatXp(customXp) : "");
		showExtras(bonusId);
		filling = false;
	}

	String getMethodId()
	{
		MethodOption option = (MethodOption) methodBox.getSelectedItem();
		return option == null ? MethodCatalog.AUTO : option.getId();
	}

	double getCustomXp()
	{
		return GoalMath.parseXpEach(customField.getText());
	}

	@Nullable
	String getBonusId()
	{
		BonusOption option = (BonusOption) bonusBox.getSelectedItem();
		return option == null || option.getBonus() == null || bonusBox.getParent() != this ? null : option.getBonus().getId();
	}

	// Shows the bonus box when the method has bonuses, and the XP field for a custom method.
	private void showExtras(@Nullable String bonusId)
	{
		boolean wasFilling = filling;
		filling = true;
		MethodOption option = (MethodOption) methodBox.getSelectedItem();
		MethodCatalog.Method method = option == null ? null : option.getMethod();

		bonusBox.removeAllItems();
		bonusBox.addItem(new BonusOption(null));
		if (method != null)
		{
			for (MethodCatalog.Bonus bonus : method.getBonuses())
			{
				BonusOption bonusOption = new BonusOption(bonus);
				bonusBox.addItem(bonusOption);
				if (bonus.getId().equals(bonusId))
				{
					bonusBox.setSelectedItem(bonusOption);
				}
			}
		}
		// Removed rather than hidden, because the grid layout still leaves room for hidden rows.
		remove(bonusBox);
		remove(customRow);
		if (method != null && !method.getBonuses().isEmpty())
		{
			add(bonusBox);
		}
		if (CUSTOM.equals(option))
		{
			add(customRow);
		}
		filling = wasFilling;
		revalidate();
		repaint();
	}

	private void changed()
	{
		if (onChange != null)
		{
			onChange.accept(this);
		}
	}
}
