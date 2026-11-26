/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasprophunt;

import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PropModelLoaderTest
{
	private static final ModelCatalog CATALOG = new ModelCatalog();
	private final PropModelLoader loader = new PropModelLoader();
	private Client client;
	private ModelData data;
	private Model model;

	@Before public void setUp()
	{
		client = mock(Client.class);
		data = mock(ModelData.class);
		model = mock(Model.class);
		when(client.loadModelData(anyInt())).thenReturn(data);
		when(client.mergeModels(any(ModelData[].class))).thenReturn(data);
		when(data.getFaceColors()).thenReturn(new short[]{1});
		when(data.cloneVertices()).thenReturn(data);
		// The real client clones these optional arrays without checking for null.
		// Model metadata alone does not imply that its mesh has a texture buffer.
		when(data.cloneColors()).thenAnswer(i -> { data.getFaceColors().clone(); return data; });
		when(data.cloneTextures()).thenAnswer(i -> { data.getFaceTextures().clone(); return data; });
		when(data.light(anyInt(), anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(model);
	}

	@Test public void untexturedNpcSceneryItemAndRawMeshesCanBeLit()
	{
		for (String key : new String[]{"NPC:7706:0", "NPC:1173:0", "OBJECT:1104:10", "ITEM:4151:0", "RAW:1297:0"})
		{
			ModelEntry entry = CATALOG.get(key);
			assertNotNull(key, entry);
			assertSame(key, model, loader.load(client, entry));
		}
		verify(data, never()).cloneTextures();
	}

	@Test public void texturedMeshesStillCopyTheTextureBuffer()
	{
		when(data.getFaceTextures()).thenReturn(new short[]{3});
		assertSame(model, loader.load(client, CATALOG.defaultEntry()));
		verify(data).cloneTextures();
		verify(data).cloneColors();
		verify(data).cloneVertices();
	}

	@Test public void absentColorBuffersAreAlsoOptional()
	{
		when(data.getFaceColors()).thenReturn(null);
		assertSame(model, loader.load(client, CATALOG.get("RAW:1297:0")));
		verify(data, never()).cloneColors();
	}
}
