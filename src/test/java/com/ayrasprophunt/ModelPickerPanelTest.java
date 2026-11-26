/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasprophunt;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModelPickerPanelTest
{
	@Test public void typingAndPressingEnterSelectsTheMatchingModelWithoutADropdown() throws Exception
	{
		ModelCatalog catalog = new ModelCatalog();
		AtomicReference<ModelEntry> chosen = new AtomicReference<>();
		ModelPickerPanel[] panel = new ModelPickerPanel[1];
		CountDownLatch ready = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() ->
		{
			panel[0] = new ModelPickerPanel(catalog, chosen::set, catalog.defaultEntry());
			panel[0].addPropertyChangeListener("searchComplete", event ->
			{
				ModelCatalog.SearchResult result = (ModelCatalog.SearchResult) event.getNewValue();
				if (!result.entries.isEmpty() && result.entries.get(0).key.equals("NPC:7706:0") && result.total < 20) { ready.countDown(); }
			});
			PropHuntPickerPreview.find(panel[0], JTextField.class).setText("zuk");
		});
		assertTrue("The search should finish without blocking the UI thread", ready.await(10, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() ->
		{
			PropHuntPickerPreview.find(panel[0], JTextField.class).postActionEvent();
			panel[0].setSize(225, 700); PropHuntPickerPreview.layout(panel[0]);
			assertTrue(PropHuntPickerPreview.find(panel[0], JList.class).getScrollableTracksViewportWidth());
			panel[0].close();
		});
		assertEquals("NPC:7706:0", chosen.get().key);
	}
}
