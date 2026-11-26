/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasprophunt;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

class PropHuntUi
{
	@Inject private ModelCatalog catalog;
	@Inject private ConfigManager configs;
	@Inject private ClientToolbar toolbar;
	private volatile long generation;
	private ModelPickerPanel panel;
	private NavigationButton navigation;

	void start(ModelEntry current)
	{
		long token = ++generation;
		SwingUtilities.invokeLater(() ->
		{
			if (token != generation) { return; }
			panel = new ModelPickerPanel(catalog, entry -> configs.setConfiguration(AyrasPropHuntConfig.GROUP, "model", entry == null ? "NONE" : entry.key), current);
			navigation = NavigationButton.builder().tooltip("Ayra's Prop Hunt")
				.icon(icon()).panel(panel).priority(8).build();
			toolbar.addNavigation(navigation);
		});
	}
	void selection(ModelEntry entry)
	{
		long token = generation;
		SwingUtilities.invokeLater(() -> { if (token == generation && panel != null) { panel.setSelected(entry); } });
	}
	void status(String text)
	{
		long token = generation;
		SwingUtilities.invokeLater(() -> { if (token == generation && panel != null) { panel.setStatus(text); } });
	}
	void stop()
	{
		++generation;
		SwingUtilities.invokeLater(() ->
		{
			if (navigation != null) { toolbar.removeNavigation(navigation); navigation = null; }
			if (panel != null) { panel.close(); panel = null; }
		});
	}
	private static BufferedImage icon()
	{
		try (InputStream stream = PropHuntUi.class.getResourceAsStream("icon.png"))
		{
			if (stream != null) { return ImageIO.read(stream); }
		}
		catch (Exception ignored) { }
		return new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
	}
}
