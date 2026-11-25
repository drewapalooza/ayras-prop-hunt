/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasdoggies;

import net.runelite.api.Animation;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;

/** One animation controller per visible player; base meshes are shared. */
final class DogActor
{
	private final DogModel dogModel;
	private final RuneLiteObject object;
	private int animation = Integer.MIN_VALUE;
	DogActor(Client client, DogModel dogModel, Model model)
	{
		this.dogModel = dogModel;
		object = client.createRuneLiteObject();
		object.setModel(model);
	}
	DogModel getDogModel()
	{
		return dogModel;
	}
	boolean isActive()
	{
		return object.isActive();
	}
	void remove()
	{
		object.setActive(false);
	}
	boolean update(Client client, Player player)
	{
		int pose = player.getPoseAnimation();
		boolean moving = pose != -1 && pose != player.getIdlePoseAnimation()
		&& pose != player.getIdleRotateLeft() && pose != player.getIdleRotateRight();
		int next = dogModel.animation(moving);
		if (animation != next)
		{
			if (next == -1)
			{
				object.setAnimationController(null);
			}
			else
			{
				Animation sequence = client.loadAnimation(next);
				if (sequence == null)
				{
					remove();
					return false;
				}
				object.setAnimationController(new AnimationController(client, sequence)
				.setOnFinished(AnimationController::loop));
			}
			animation = next;
		}
		object.setLocation(player.getLocalLocation(), player.getWorldView().getPlane());
		object.setOrientation(player.getCurrentOrientation());
		if (!object.isActive())
		{
			object.setActive(true);
		}
		return true;
	}
}
