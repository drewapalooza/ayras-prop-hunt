/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasdoggies;

import com.google.gson.Gson;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class DogModelTest
{

	@Test
	public void containsEveryNewAdultAndPuppyCoatExactlyOnce()
	{
		Set<Integer> ids = new HashSet<>();
		for (DogModel dog : DogModel.values())
		{
			assertTrue("Duplicate dog " + dog, ids.add(dog.getNpcId()));
			assertSame(dog, DogModel.fromNpcId(dog.getNpcId()));
			assertTrue(dog.getIdle() >= -1);
			assertTrue(dog.getWalk() >= -1);
		}
		for (int id = 16361; id <= 16396; id++)
		{
			assertTrue("Missing adult " + id, ids.contains(id));
		}
		for (int id = 16433; id <= 16468; id++)
		{
			assertTrue("Missing puppy " + id, ids.contains(id));
		}
		assertEquals(110, ids.size());
		assertNull(DogModel.fromNpcId(-1));
		assertNull(DogModel.fromNpcId(1));
	}

	@Test
	public void rigsAreSpecificToTheModelAndSittingDogsStaySeated()
	{
		assertEquals(4773, DogModel.STRAY_DOG.animation(true));
		assertEquals(4777, DogModel.STRAY_DOG.animation(false));
		assertEquals(6560, DogModel.LABRADOR_YELLOW.animation(true));
		assertEquals(7858, DogModel.LABRADOR_YELLOW_PUPPY.animation(false));
		assertEquals(2268, DogModel.SHEEPDOG.animation(true));
		assertEquals(-1, DogModel.ROSIE.animation(true));
	}

	@Test
	public void appearanceMessagesRoundTripAndRejectUnknownVersionsModelsAndNames()
	{
		Gson gson = new Gson();
		DogAppearanceMessage original = new DogAppearanceMessage("Friend", 301, DogModel.CORGI_TAN, true);
		String json = gson.toJson(original);
		DogAppearanceMessage message = gson.fromJson(json, DogAppearanceMessage.class);
		assertTrue(message.isValid());
		assertSame(DogModel.CORGI_TAN, message.getDog());
		assertEquals("Friend", message.getPlayerName());
		assertFalse(gson.fromJson("{}", DogAppearanceMessage.class).isValid());
		assertFalse(gson.fromJson(json.replace("\"version\":1", "\"version\":9"), DogAppearanceMessage.class).isValid());
		assertFalse(gson.fromJson(json.replace("\"npcId\":16370", "\"npcId\":1"), DogAppearanceMessage.class).isValid());
		assertFalse(new DogAppearanceMessage("<col=bad>", 301, DogModel.DOG, true).isValid());
		assertFalse(new DogAppearanceMessage("Friend", -1, DogModel.DOG, true).isValid());
		assertFalse(new DogAppearanceMessage("NameTooLongToBeReal", 301, DogModel.DOG, true).isValid());
	}
}
