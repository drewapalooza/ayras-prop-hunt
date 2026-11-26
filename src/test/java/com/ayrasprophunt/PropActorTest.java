/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasprophunt;

import net.runelite.api.Animation;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PropActorTest
{
	private static final ModelCatalog CATALOG = new ModelCatalog();
	private Client client;
	private Player player;
	private RuneLiteObject object;
	@Before public void setUp()
	{
		client = mock(Client.class); player = mock(Player.class); object = mock(RuneLiteObject.class);
		when(client.createRuneLiteObject()).thenReturn(object);
		when(player.getLocalLocation()).thenReturn(new LocalPoint(6400, 6400, -1));
		when(player.getWorldView()).thenReturn(mock(WorldView.class));
		when(player.getIdlePoseAnimation()).thenReturn(808);
		when(player.getPoseAnimation()).thenReturn(808);
		when(player.getAnimation()).thenReturn(-1);
		when(client.loadAnimation(anyInt())).thenAnswer(i -> { Animation animation = mock(Animation.class); when(animation.getId()).thenReturn(i.getArgument(0)); return animation; });
	}
	@Test public void aBenchRemainsStaticWhileMovingAndAttacking()
	{
		PropActor actor = new PropActor(client, CATALOG.get("OBJECT:1104:10"), mock(Model.class));
		when(player.getPoseAnimation()).thenReturn(819);
		when(player.getAnimation()).thenReturn(422);
		when(player.getInteracting()).thenReturn(mock(NPC.class));
		actor.update(client, player);
		verify(client, never()).loadAnimation(anyInt());
		verify(object).setAnimationController(null);
		verify(object).setActive(true);
	}
	@Test public void zukUsesHisOwnAttackThenReturnsToIdleWithoutLoopingAttacks()
	{
		PropActor actor = new PropActor(client, CATALOG.get("NPC:7706:0"), mock(Model.class));
		when(player.getAnimation()).thenReturn(422);
		when(player.getInteracting()).thenReturn(mock(NPC.class));
		actor.update(client, player);
		ArgumentCaptor<AnimationController> animation = ArgumentCaptor.forClass(AnimationController.class);
		verify(object).setAnimationController(animation.capture());
		assertEquals(7566, animation.getValue().getAnimation().getId());
		when(animation.getValue().getAnimation().isMayaAnim()).thenReturn(true);
		when(animation.getValue().getAnimation().getDuration()).thenReturn(1);
		animation.getValue().tick(2);
		actor.update(client, player);
		verify(client).loadAnimation(7564);
		actor.actionChanged();
		actor.update(client, player);
		verify(client, times(2)).loadAnimation(7566);
		verify(player, never()).setAnimation(anyInt());
	}
	@Test public void aChickenUsesItsOwnWalkingAnimationAndNoHumanAnimation()
	{
		PropActor actor = new PropActor(client, CATALOG.get("NPC:1173:0"), mock(Model.class));
		when(player.getPoseAnimation()).thenReturn(819);
		actor.update(client, player);
		verify(client).loadAnimation(5385);
		verify(client, never()).loadAnimation(819);
	}
}
