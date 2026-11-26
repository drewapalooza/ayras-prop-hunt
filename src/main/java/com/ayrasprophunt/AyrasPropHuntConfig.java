/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasprophunt;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(AyrasPropHuntConfig.GROUP)
public interface AyrasPropHuntConfig extends Config
{
	String GROUP = "ayrasprophunt";
	@ConfigItem(keyName = "model", name = "Selected model", description = "Choose your character model in the Character Transmog sidebar.", hidden = true)
	default String modelKey() { return ModelCatalog.DEFAULT_KEY; }
	@ConfigItem(keyName = "shareAppearance", name = "Share with party", description = "Share your chosen prop and in-game name with your current RuneLite Party.", position = 1)
	default boolean shareAppearance() { return false; }
	@ConfigItem(keyName = "showOtherProps", name = "Show party props", description = "Show opted-in party members as their chosen props outside the Wilderness and PvP.", position = 2)
	default boolean showOtherProps() { return true; }
}
