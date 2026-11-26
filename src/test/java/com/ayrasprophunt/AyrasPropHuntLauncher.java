/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasprophunt;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class AyrasPropHuntLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(AyrasPropHuntPlugin.class);
		RuneLite.main(args);
	}
}
