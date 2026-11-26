/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasprophunt;

import net.runelite.client.party.messages.PartyMemberMessage;

public final class PropAppearanceMessage extends PartyMemberMessage
{
	private int version;
	private String playerName;
	private int world;
	private String modelKey;
	private boolean enabled;
	public PropAppearanceMessage() { }
	public PropAppearanceMessage(String name, int world, ModelEntry entry, boolean enabled)
	{
		version = 2; playerName = name; this.world = world; modelKey = entry.key; this.enabled = enabled;
	}
	public boolean isValid(ModelCatalog catalog)
	{
		return version == 2 && playerName != null && playerName.matches("[A-Za-z0-9 _\\-\\u00a0]{1,12}")
			&& world > 0 && world <= 65535 && modelKey != null && modelKey.length() <= 40 && catalog.get(modelKey) != null;
	}
	public String getPlayerName() { return playerName; }
	public int getWorld() { return world; }
	public ModelEntry getEntry(ModelCatalog catalog) { return catalog.get(modelKey); }
	public boolean isEnabled() { return enabled; }
}
