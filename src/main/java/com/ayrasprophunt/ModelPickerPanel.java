/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasprophunt;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/** Search is debounced and runs off the UI thread. Selection never blocks gameplay. */
public final class ModelPickerPanel extends PluginPanel
{
	private final ModelCatalog catalog;
	private final Consumer<ModelEntry> choose;
	private final JTextField search = new JTextField();
	private final DefaultListModel<ModelEntry> results = new DefaultListModel<>();
	private final JList<ModelEntry> list = new JList<ModelEntry>(results)
	{
		@Override public boolean getScrollableTracksViewportWidth() { return true; }
	};
	private final JLabel count = new JLabel();
	private final JLabel selected = new JLabel();
	private final JLabel status = new JLabel("Choose a result to become that model.");
	private final JButton apply = new JButton("Become selected model");
	private final Timer debounce;
	private SwingWorker<ModelCatalog.SearchResult, Void> worker;
	private ModelKind kind;
	private long generation;
	private boolean closed;

	public ModelPickerPanel(ModelCatalog catalog, Consumer<ModelEntry> choose, ModelEntry current)
	{
		super(false);
		this.catalog = catalog;
		this.choose = choose;
		setLayout(new BorderLayout(0, 10));
		setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		JPanel header = new JPanel();
		header.setOpaque(false);
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		JLabel title = new JLabel("<html>AYRA'S CHARACTER<br>TRANSMOG</html>");
		title.setForeground(new Color(245, 202, 145));
		title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
		header.add(title);
		header.add(new JLabel(String.format("%,d game models", catalog.size())));
		header.add(spacer(12));
		header.add(new JLabel("Search models"));
		search.setToolTipText("Search by name or catalog number. Try Zuk, bench, or chicken.");
		search.getAccessibleContext().setAccessibleName("Search game models");
		search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		header.add(search);
		header.add(spacer(4));
		JLabel hint = new JLabel("Zuk · bench · chicken · model ID");
		hint.setFont(hint.getFont().deriveFont(10f));
		header.add(hint);
		header.add(spacer(10));
		JPanel filters = new JPanel(new GridLayout(1, 5, 2, 0));
		filters.setOpaque(false);
		ButtonGroup group = new ButtonGroup();
		String[] labels = {"All", "NPC", "Props", "Items", "Raw"};
		for (int i = 0; i < labels.length; i++)
		{
			final ModelKind filter = i == 0 ? null : ModelKind.values()[i - 1];
			JToggleButton button = new JToggleButton(labels[i], i == 0);
			button.setMargin(new java.awt.Insets(5, 1, 5, 1));
			button.setFont(button.getFont().deriveFont(10f));
			button.addActionListener(event -> { kind = filter; startSearch(); });
			group.add(button); filters.add(button);
		}
		header.add(filters);
		header.add(spacer(8));
		count.setFont(count.getFont().deriveFont(11f));
		header.add(count);
		for (Component component : header.getComponents()) { if (component instanceof javax.swing.JComponent) { ((javax.swing.JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT); } }
		add(header, BorderLayout.NORTH);

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new EntryRenderer());
		list.setFixedCellHeight(49);
		list.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		list.getAccessibleContext().setAccessibleName("Matching game models");
		list.addListSelectionListener(event -> apply.setEnabled(list.getSelectedValue() != null));
		list.addMouseListener(new MouseAdapter()
		{
			@Override public void mouseClicked(MouseEvent event) { if (event.getClickCount() == 2) { applySelection(); } }
		});
		JScrollPane scroll = new JScrollPane(list);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setPreferredSize(new Dimension(0, 340));
		scroll.setBorder(BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR));
		add(scroll, BorderLayout.CENTER);
		JPanel footer = new JPanel();
		footer.setOpaque(false);
		footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
		selected.setForeground(new Color(245, 202, 145));
		selected.setFont(selected.getFont().deriveFont(Font.BOLD, 11f));
		footer.add(selected);
		status.setFont(status.getFont().deriveFont(10f));
		footer.add(status);
		footer.add(spacer(10));
		apply.setEnabled(false);
		apply.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		apply.addActionListener(event -> applySelection());
		footer.add(apply);
		JButton reset = new JButton("Return to your normal character");
		reset.setFont(reset.getFont().deriveFont(10f));
		reset.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		reset.addActionListener(event -> choose.accept(null));
		footer.add(reset);
		for (Component component : footer.getComponents()) { if (component instanceof javax.swing.JComponent) { ((javax.swing.JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT); } }
		add(footer, BorderLayout.SOUTH);
		debounce = new Timer(150, event -> startSearch());
		debounce.setRepeats(false);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent event) { debounce.restart(); }
			@Override public void removeUpdate(DocumentEvent event) { debounce.restart(); }
			@Override public void changedUpdate(DocumentEvent event) { debounce.restart(); }
		});
		search.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "results");
		search.getActionMap().put("results", new AbstractAction()
		{
			@Override public void actionPerformed(ActionEvent event) { if (!results.isEmpty()) { list.setSelectedIndex(0); list.requestFocusInWindow(); } }
		});
		AbstractAction applyAction = new AbstractAction()
		{
			@Override public void actionPerformed(ActionEvent event) { applySelection(); }
		};
		list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "become");
		list.getActionMap().put("become", applyAction);
		search.addActionListener(event -> { if (list.getSelectedValue() == null && !results.isEmpty()) { list.setSelectedIndex(0); } applySelection(); });
		setSelected(current);
		startSearch();
	}

	private static Component spacer(int height) { return javax.swing.Box.createVerticalStrut(height); }
	private void applySelection() { ModelEntry entry = list.getSelectedValue(); if (entry != null && !closed) { choose.accept(entry); } }
	public void setSelected(ModelEntry entry)
	{
		selected.setText(entry == null ? "Normal character" : "You: " + entry.name);
		selected.setToolTipText(entry == null ? null : entry.toString());
	}
	public void setStatus(String text) { status.setText(text); status.setToolTipText(text); }
	public void focusSearch() { search.requestFocusInWindow(); }

	private void startSearch()
	{
		if (closed) { return; }
		long token = ++generation;
		if (worker != null) { worker.cancel(true); }
		String query = search.getText();
		ModelKind filter = kind;
		count.setText("Searching…");
		worker = new SwingWorker<ModelCatalog.SearchResult, Void>()
		{
			@Override protected ModelCatalog.SearchResult doInBackground() { return catalog.search(query, filter, 250); }
			@Override protected void done()
			{
				if (closed || token != generation || isCancelled()) { return; }
				try
				{
					ModelCatalog.SearchResult found = get();
					results.clear();
					for (ModelEntry entry : found.entries) { results.addElement(entry); }
					count.setText(String.format("%,d matches · %,d shown", found.total, found.entries.size()));
					if (found.total == 0) { count.setText("No models found. Try another name."); }
					else if (!results.isEmpty()) { list.setSelectedIndex(0); }
					ModelPickerPanel.this.firePropertyChange("searchComplete", null, found);
				}
				catch (CancellationException ignored) { }
				catch (Exception ex) { count.setText("Search failed. Try again."); }
			}
		};
		worker.execute();
	}

	public void close()
	{
		closed = true; ++generation; debounce.stop();
		if (worker != null) { worker.cancel(true); }
	}

	private static final class EntryRenderer extends JPanel implements ListCellRenderer<ModelEntry>
	{
		private final JLabel name = new JLabel();
		private final JLabel detail = new JLabel();
		EntryRenderer()
		{
			setLayout(new BorderLayout(0, 3));
			setBorder(BorderFactory.createEmptyBorder(6, 7, 6, 7));
			name.setFont(name.getFont().deriveFont(Font.BOLD, 12f));
			detail.setFont(detail.getFont().deriveFont(10f));
			add(name, BorderLayout.NORTH); add(detail, BorderLayout.SOUTH);
		}
		@Override public Component getListCellRendererComponent(JList<? extends ModelEntry> source, ModelEntry value, int index, boolean selected, boolean focused)
		{
			setBackground(selected ? new Color(72, 57, 100) : ColorScheme.DARKER_GRAY_COLOR);
			name.setText(value.name);
			name.setForeground(selected ? new Color(255, 218, 165) : ColorScheme.TEXT_COLOR);
			detail.setText(value.detail()); detail.setForeground(new Color(170, 170, 180));
			setToolTipText(value.toString());
			setSize(Math.max(1, source.getWidth()), source.getFixedCellHeight());
			doLayout();
			return this;
		}
	}
}
