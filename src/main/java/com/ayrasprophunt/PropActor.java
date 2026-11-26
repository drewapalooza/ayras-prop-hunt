/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasprophunt;

import net.runelite.api.Animation;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;

final class PropActor
{
	private final ModelEntry entry;
	private final RuneLiteObject object;
	private int animation = Integer.MIN_VALUE;
	private int lastAction = -1;
	private int lastActionFrame;
	private boolean restartAttack;
	private boolean attackFinished;

	PropActor(Client client, ModelEntry entry, Model model)
	{
		this.entry = entry;
		object = client.createRuneLiteObject();
		object.setModel(model);
		model.calculateBoundsCylinder();
		object.setRadius(Math.max(60, Math.min(1024, model.getXYZMag())));
	}

	ModelEntry getEntry() { return entry; }
	boolean isActive() { return object.isActive(); }
	void remove() { object.setActive(false); }
	void actionChanged() { restartAttack = true; }

	boolean update(Client client, Player player)
	{
		int pose = player.getPoseAnimation();
		boolean moving = pose != -1 && pose != player.getIdlePoseAnimation()
			&& pose != player.getIdleRotateLeft() && pose != player.getIdleRotateRight();
		int action = player.getAnimation(), actionFrame = player.getAnimationFrame();
		boolean attack = entry.attack >= 0 && action != -1 && player.getInteracting() != null;
		if (attack && (restartAttack || action != lastAction || actionFrame < lastActionFrame)) { attackFinished = false; animation = Integer.MIN_VALUE; }
		restartAttack = false;
		lastAction = action; lastActionFrame = actionFrame;
		boolean playingAttack = attack && !attackFinished;
		int next = playingAttack ? entry.attack : entry.motionAnimation(moving, pose == player.getRunAnimation());
		if (animation != next)
		{
			if (next == -1) { object.setAnimationController(null); animation = next; }
			else
			{
				Animation sequence = client.loadAnimation(next);
				if (sequence != null)
				{
					AnimationController controller = new AnimationController(client, sequence);
					if (playingAttack) { controller.setOnFinished(finished -> { finished.loop(); attackFinished = true; }); }
					else { controller.setOnFinished(AnimationController::loop); }
					object.setAnimationController(controller);
					animation = next;
				}
				// Keep a static mesh visible while its animation is loading; retry next frame.
			}
		}
		object.setLocation(player.getLocalLocation(), player.getWorldView().getPlane());
		object.setOrientation(player.getCurrentOrientation());
		if (!object.isActive()) { object.setActive(true); }
		return true;
	}
}
