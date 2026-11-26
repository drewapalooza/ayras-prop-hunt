/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasprophunt;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;

final class PropModelLoader
{
	private final Map<String, Model> models = new LinkedHashMap<String, Model>(32, 0.75f, true)
	{
		@Override protected boolean removeEldestEntry(Map.Entry<String, Model> eldest) { return size() > 32; }
	};
	void clear() { models.clear(); }

	Model load(Client client, ModelEntry entry)
	{
		Model cached = models.get(entry.key);
		if (cached != null) { return cached; }
		ModelData[] parts = new ModelData[entry.models.length];
		for (int i = 0; i < parts.length; i++)
		{
			parts[i] = client.loadModelData(entry.models[i]);
			if (parts[i] == null) { return null; }
		}
		ModelData data = client.mergeModels(parts);
		if (data == null) { return null; }
		data = data.cloneColors().cloneVertices().cloneTextures();
		for (int i = 0; i < entry.colors.length; i++) { data.recolor(entry.colors[i], entry.colorTo[i]); }
		for (int i = 0; i < entry.textures.length; i++) { data.retexture(entry.textures[i], entry.textureTo[i]); }
		if (entry.fullColor != -1 && data.getFaceColors() != null) { Arrays.fill(data.getFaceColors(), (short) entry.fullColor); }
		if (entry.mirrored)
		{
			// mergeModels creates the face arrays; cloned vertices isolate the cache mesh.
			float[] z = data.getVerticesZ();
			for (int i = 0; i < z.length; i++) { z[i] = -z[i]; }
			int[] a = data.getFaceIndices1(), c = data.getFaceIndices3();
			for (int i = 0; i < a.length; i++) { int swap = a[i]; a[i] = c[i]; c[i] = swap; }
		}
		data.scale(entry.sx, entry.sy, entry.sz);
		data.translate(entry.ox, entry.oy, entry.oz);
		if (entry.kind == ModelKind.ITEM || entry.kind == ModelKind.RAW)
		{
			float bottom = 0;
			if (data.getVerticesY() != null) { for (float y : data.getVerticesY()) { bottom = Math.max(bottom, y); } }
			if (bottom != 0) { data.translate(0, -Math.round(bottom), 0); }
		}
		boolean npc = entry.kind == ModelKind.NPC;
		Model model = data.light(entry.ambient, entry.contrast, npc ? -30 : -50, npc ? -50 : -10, npc ? -30 : -50);
		if (model != null) { models.put(entry.key, model); }
		return model;
	}
}
