/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasdoggies;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPCComposition;

final class DogModelLoader
{
	private final Map<DogModel, Model> models = new EnumMap<>(DogModel.class);
	void clear()
	{
		models.clear();
	}
	Model load(Client client, DogModel dog)
	{
		Model cached = models.get(dog);
		if (cached != null)
		{
			return cached;
		}
		NPCComposition definition = client.getNpcDefinition(dog.getNpcId());
		if (definition == null || definition.getModels() == null || definition.getModels().length == 0)
		{
			return null;
		}
		int[] ids = definition.getModels();
		ModelData[] parts = new ModelData[ids.length];
		for (int i = 0; i < ids.length; i++)
		{
			parts[i] = client.loadModelData(ids[i]);
			if (parts[i] == null)
			{
				return null;
			}
		}
		ModelData data = client.mergeModels(parts);
		if (data == null)
		{
			return null;
		}
		data = data.cloneColors().cloneVertices();
		short[] original = definition.getColorToReplace();
		short[] replacement = definition.getColorToReplaceWith();
		if (original != null && replacement != null)
		{
			for (int i = 0; i < Math.min(original.length, replacement.length); i++)
			{
				data.recolor(original[i], replacement[i]);
			}
		}
		data.scale(definition.getWidthScale(), definition.getHeightScale(), definition.getWidthScale());
		Model model = data.light(64 + dog.getAmbient(), 850 + 5 * dog.getContrast(), -30, -50, -30);
		if (model != null)
		{
			models.put(dog, model);
		}
		return model;
	}
}
