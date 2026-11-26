/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasprophunt;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/** Renders the actual searchable sidebar without needing a logged-in account. */
public final class PropHuntPickerPreview
{
	public static void main(String[] args) throws Exception
	{
		UIManager.put("Panel.background", new Color(37, 37, 42));
		UIManager.put("Label.foreground", new Color(225, 225, 230));
		UIManager.put("TextField.background", new Color(25, 25, 30));
		UIManager.put("TextField.foreground", Color.WHITE);
		UIManager.put("TextField.caretForeground", Color.WHITE);
		ModelCatalog catalog = new ModelCatalog();
		String[] queries = {"zuk", "bench", "chicken"};
		BufferedImage output = new BufferedImage(3 * 240 + 8, 792, BufferedImage.TYPE_INT_ARGB);
		Graphics2D image = output.createGraphics();
		image.setColor(new Color(20, 20, 25)); image.fillRect(0, 0, output.getWidth(), output.getHeight());
		for (int i = 0; i < queries.length; i++)
		{
			String query = queries[i];
			int expectedMatches = catalog.search(query, null, 250).total;
			CountDownLatch ready = new CountDownLatch(1);
			ModelPickerPanel[] holder = new ModelPickerPanel[1];
			SwingUtilities.invokeAndWait(() ->
			{
				ModelEntry entry = catalog.search(query, null, 1).entries.get(0);
				ModelPickerPanel panel = new ModelPickerPanel(catalog, ignored -> { }, entry);
				holder[0] = panel;
				panel.addPropertyChangeListener("searchComplete", event -> { if (((ModelCatalog.SearchResult) event.getNewValue()).total == expectedMatches) { ready.countDown(); } });
				find(panel, JTextField.class).setText(query);
			});
			if (!ready.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Search timed out: " + query); }
			int x = 8 + i * 240;
			SwingUtilities.invokeAndWait(() ->
			{
				ModelPickerPanel panel = holder[0];
				panel.setSize(232, 776); layout(panel);
				Graphics2D surface = (Graphics2D) image.create(x, 8, 232, 776);
				panel.printAll(surface); surface.dispose(); panel.close();
			});
		}
		image.dispose();
		ImageIO.write(output, "png", new File("build/prop-picker-preview.png"));
		System.out.println("Rendered actual sidebar searches to build/prop-picker-preview.png");
	}
	static void layout(Container parent) { parent.doLayout(); for (Component child : parent.getComponents()) { if (child instanceof Container) { layout((Container) child); } } }
	static <T> T find(Container parent, Class<T> type)
	{
		for (Component child : parent.getComponents())
		{
			if (type.isInstance(child)) { return type.cast(child); }
			if (child instanceof Container) { T found = find((Container) child, type); if (found != null) { return found; } }
		}
		return null;
	}
}
