/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasdoggies;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(AyrasDoggiesConfig.GROUP)
public interface AyrasDoggiesConfig extends Config
{
	String GROUP = "dogmode";

	@ConfigItem(keyName = "dog", name = "Your dog", description = "Choose your dog, coat, or puppy. Changes appear immediately.", position = 0)
	default DogModel dog()
	{
		return DogModel.STRAY_DOG;
	}

	@ConfigItem(keyName = "shareAppearance", name = "Share with party", description = "Share your dog choice and in-game name with your current RuneLite Party.", position = 1)
	default boolean shareAppearance()
	{
		return false;
	}

	@ConfigItem(keyName = "showOtherDogs", name = "Show party dogs", description = "Show opted-in Ayra's Doggies users in your RuneLite Party as their chosen dogs. Disabled in the Wilderness.", position = 2)
	default boolean showOtherDogs()
	{
		return true;
	}
}
