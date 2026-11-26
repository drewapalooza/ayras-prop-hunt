/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasprophunt;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.zip.GZIPInputStream;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class ModelCatalog
{
	public static final String DEFAULT_KEY = "NPC:2902:0";
	private final Map<String, ModelEntry> byKey = new HashMap<>();
	private final List<ModelEntry> entries = new ArrayList<>();

	@Inject public ModelCatalog()
	{
		try (InputStream resource = ModelCatalog.class.getResourceAsStream("models.bin.gz"))
		{
			if (resource == null) { throw new IOException("Model catalog resource is missing"); }
			try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(resource))))
			{
				if (in.readInt() != 0x50525031) { throw new IOException("Unsupported model catalog"); }
				int count = in.readInt();
				if (count < 1 || count > 500_000) { throw new IOException("Invalid catalog size"); }
				for (int i = 0; i < count; i++)
				{
					ModelEntry entry = ModelEntry.read(in);
					if (byKey.put(entry.key, entry) != null) { throw new IOException("Duplicate catalog key " + entry.key); }
					entries.add(entry);
				}
				if (in.read() != -1) { throw new IOException("Trailing model catalog data"); }
			}
		}
		catch (IOException ex) { throw new IllegalStateException("Cannot load Prop Hunt's model catalog", ex); }
	}

	public ModelEntry get(String key) { return byKey.get(key); }
	public ModelEntry defaultEntry() { return byKey.get(DEFAULT_KEY); }
	public int size() { return entries.size(); }
	public int count(ModelKind kind) { return (int) entries.stream().filter(e -> e.kind == kind).count(); }

	/** Bounded top results, with full match count; called off the Swing/game threads. */
	public SearchResult search(String query, ModelKind kind, int limit)
	{
		String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
		String[] tokens = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
		Comparator<Ranked> order = Comparator.comparingInt((Ranked r) -> r.rank)
			.thenComparingInt(r -> featured(r.entry))
			.thenComparing(r -> r.entry.name, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(r -> r.entry.key);
		PriorityQueue<Ranked> best = new PriorityQueue<>(order.reversed());
		int matches = 0;
		int bound = Math.max(1, Math.min(1000, limit));
		for (ModelEntry entry : entries)
		{
			if (Thread.currentThread().isInterrupted()) { return new SearchResult(Collections.emptyList(), 0); }
			if (kind != null && entry.kind != kind) { continue; }
			boolean match = true;
			for (String token : tokens) { if (!entry.search.contains(token)) { match = false; break; } }
			if (!match) { continue; }
			matches++;
			String name = entry.name.toLowerCase(Locale.ROOT);
			int rank = normalized.isEmpty() ? featured(entry) : name.equals(normalized) ? 0 : name.startsWith(normalized) ? 1 : name.contains(normalized) ? 2 : 3;
			Ranked candidate = new Ranked(entry, rank);
			if (best.size() < bound) { best.add(candidate); }
			else if (order.compare(candidate, best.peek()) < 0) { best.poll(); best.add(candidate); }
		}
		List<Ranked> ranked = new ArrayList<>(best);
		ranked.sort(order);
		List<ModelEntry> result = new ArrayList<>(ranked.size());
		for (Ranked entry : ranked) { result.add(entry.entry); }
		return new SearchResult(Collections.unmodifiableList(result), matches);
	}

	private static int featured(ModelEntry entry)
	{
		if (entry.kind == ModelKind.NPC && entry.id == 7706) { return 0; }
		if (entry.kind == ModelKind.OBJECT && entry.id == 1104) { return 1; }
		if (entry.kind == ModelKind.NPC && entry.id == 1173) { return 2; }
		if (entry.key.equals(DEFAULT_KEY)) { return 3; }
		return entry.kind == ModelKind.RAW ? 10 : 5;
	}

	private static final class Ranked
	{
		final ModelEntry entry; final int rank;
		Ranked(ModelEntry entry, int rank) { this.entry = entry; this.rank = rank; }
	}
	public static final class SearchResult
	{
		public final List<ModelEntry> entries;
		public final int total;
		SearchResult(List<ModelEntry> entries, int total) { this.entries = entries; this.total = total; }
	}
}
