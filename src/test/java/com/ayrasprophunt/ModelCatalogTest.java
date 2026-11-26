/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasprophunt;

import com.google.gson.Gson;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModelCatalogTest
{
	private static final ModelCatalog CATALOG = new ModelCatalog();
	@Test public void includesTheEntireExportedCatalogAndAllFourModelKinds()
	{
		assertEquals(168077, CATALOG.size());
		assertEquals(17000, CATALOG.count(ModelKind.NPC));
		assertEquals(67836, CATALOG.count(ModelKind.OBJECT));
		assertEquals(20167, CATALOG.count(ModelKind.ITEM));
		assertEquals(63074, CATALOG.count(ModelKind.RAW));
		assertNotNull(CATALOG.defaultEntry());
		assertNull(CATALOG.get("RAW:999999:0"));
	}
	@Test public void findsZukABenchAndAChickenByNameAndUsesTheirOwnAnimationRigs()
	{
		assertTrue(CATALOG.search("zuk", ModelKind.NPC, 250).entries.stream().anyMatch(e -> e.id == 7706));
		assertTrue(CATALOG.search("bench", ModelKind.OBJECT, 250).entries.stream().anyMatch(e -> e.id == 1104));
		assertTrue(CATALOG.search("chicken", ModelKind.NPC, 250).entries.stream().anyMatch(e -> e.id == 1173));
		ModelEntry zuk = CATALOG.get("NPC:7706:0");
		assertEquals(7564, zuk.motionAnimation(true, true)); // Zuk has no walking sequence.
		assertEquals(7566, zuk.attack);
		assertEquals(5385, CATALOG.get("NPC:1173:0").motionAnimation(true, false));
		assertEquals(5387, CATALOG.get("NPC:1173:0").attack);
		assertEquals(-1, CATALOG.get("OBJECT:1104:10").motionAnimation(true, true));
		assertFalse(CATALOG.get("OBJECT:1104:10").isAnimated());
	}
	@Test public void searchIsCaseInsensitiveTokenizedFilteredAndBounded()
	{
		assertEquals(CATALOG.search("ZUK", null, 20).entries.get(0), CATALOG.search("zuk", null, 20).entries.get(0));
		ModelCatalog.SearchResult benches = CATALOG.search("bench 1104", ModelKind.OBJECT, 50);
		assertTrue(benches.entries.stream().anyMatch(e -> e.id == 1104));
		for (ModelEntry entry : benches.entries) { assertEquals(ModelKind.OBJECT, entry.kind); assertTrue(entry.name.toLowerCase(Locale.ROOT).contains("bench")); }
		assertEquals(1, CATALOG.search("33011", ModelKind.RAW, 50).entries.size());
		assertEquals(5, CATALOG.search("", null, 5).entries.size());
		assertEquals(CATALOG.size(), CATALOG.search("", null, 5).total);
		assertTrue(CATALOG.search("no-such-prop-xyzzy", null, 50).entries.isEmpty());
	}
	@Test public void messagesAcceptOnlyCatalogEntriesAndRejectMalformedOrOldPayloads()
	{
		Gson gson = new Gson();
		PropAppearanceMessage original = new PropAppearanceMessage("Friend", 301, CATALOG.get("NPC:7706:0"), true);
		String json = gson.toJson(original);
		PropAppearanceMessage roundTrip = gson.fromJson(json, PropAppearanceMessage.class);
		assertTrue(roundTrip.isValid(CATALOG));
		assertEquals(7706, roundTrip.getEntry(CATALOG).id);
		assertFalse(gson.fromJson("{}", PropAppearanceMessage.class).isValid(CATALOG));
		assertFalse(gson.fromJson(json.replace("\"version\":2", "\"version\":1"), PropAppearanceMessage.class).isValid(CATALOG));
		assertFalse(gson.fromJson(json.replace("NPC:7706:0", "RAW:999999:0"), PropAppearanceMessage.class).isValid(CATALOG));
		assertFalse(new PropAppearanceMessage("<col=bad>", 301, CATALOG.defaultEntry(), true).isValid(CATALOG));
	}
}
