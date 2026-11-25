/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasdoggies;

import net.runelite.client.party.messages.PartyMemberMessage;

/** Only opt-in presence, world, and an allowlisted dog ID; no coordinates. */
public final class DogAppearanceMessage extends PartyMemberMessage
{
	private int version;
	private String playerName;
	private int world;
	private int npcId;
	private boolean enabled;
	public DogAppearanceMessage()
	{
	}
	public DogAppearanceMessage(String playerName, int world, DogModel dog, boolean enabled)
	{
		version = 1;
		this.playerName = playerName;
		this.world = world;
		npcId = dog.getNpcId();
		this.enabled = enabled;
	}
	public boolean isValid()
	{
		return version == 1 && playerName != null && playerName.matches("[A-Za-z0-9 _\\-\\u00a0]{1,12}")
		&& world > 0 && world <= 65535 && DogModel.fromNpcId(npcId) != null;
	}
	public String getPlayerName()
	{
		return playerName;
	}
	public int getWorld()
	{
		return world;
	}
	public DogModel getDog()
	{
		return DogModel.fromNpcId(npcId);
	}
	public boolean isEnabled()
	{
		return enabled;
	}
}
