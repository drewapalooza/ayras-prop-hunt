/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasprophunt;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Animation;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.WorldType;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserPart;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AyrasPropHuntPluginTest
{
	private static final ModelCatalog CATALOG = new ModelCatalog();
	private AyrasPropHuntPlugin plugin;
	private Client client;
	private Player player;
	private PlayerComposition appearance;
	private WorldView world;
	private NPCComposition definition;
	private ModelData modelData;
	private RuneLiteObject dog;
	private Animation idle;
	private Animation walk;
	private RenderCallbackManager callbacks;
	private PartyService party;
	private ModelEntry chosenEntry = CATALOG.defaultEntry();
	private boolean showOthers = true;
	private boolean sharing = true;
	private final AtomicLong clock = new AtomicLong(1_000_000L);
	private final List<Player> roster = new ArrayList<>();
	private final LocalPoint location = new LocalPoint(6400, 6400, -1);
	private final AtomicBoolean dogActive = new AtomicBoolean();

	@Before
	public void setUp() throws Exception
	{
		plugin = new AyrasPropHuntPlugin();
		client = mock(Client.class);
		player = mock(Player.class);
		appearance = mock(PlayerComposition.class);
		world = mock(WorldView.class);
		definition = mock(NPCComposition.class);
		modelData = mock(ModelData.class);
		dog = mock(RuneLiteObject.class);
		idle = mock(Animation.class);
		walk = mock(Animation.class);
		callbacks = new RenderCallbackManager();
		party = mock(PartyService.class);
		ClientThread thread = mock(ClientThread.class);
		doAnswer(i ->
		{
			i.getArgument(0, Runnable.class).run();
			return null;
		})
		.when(thread).invoke(any(Runnable.class));
		inject("client", client);
		inject("clientThread", thread);
		inject("renderCallbacks", callbacks);
		inject("party", party);
		inject("catalog", CATALOG);
		inject("ui", mock(PropHuntUi.class));
		inject("wsClient", mock(WSClient.class));
		inject("nanoTime", (java.util.function.LongSupplier) clock::get);
		inject("config", new AyrasPropHuntConfig()
		{
		@Override
		public String modelKey()
		{
			return chosenEntry == null ? "NONE" : chosenEntry.key;
		}
		@Override
		public boolean showOtherProps()
		{
			return showOthers;
		}
		@Override
		public boolean shareAppearance()
		{
			return sharing;
		}
		});
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getLocalPlayer()).thenReturn(player);
		when(client.getWorld()).thenReturn(301);
		when(player.getName()).thenReturn("Me");
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3210, 3420, 0));
		when(player.getPlayerComposition()).thenReturn(appearance);
		when(appearance.getTransformedNpcId()).thenReturn(-1);
		when(player.getWorldView()).thenReturn(world);
		when(client.getTopLevelWorldView()).thenReturn(world);
		IndexedObjectSet<Player> players = mock(IndexedObjectSet.class);
		when(players.iterator()).thenAnswer(i -> roster.iterator());
		doReturn(players).when(world).players();
		when(world.getPlane()).thenReturn(2);
		when(player.getLocalLocation()).thenReturn(location);
		when(player.getCurrentOrientation()).thenReturn(512);
		when(player.getIdlePoseAnimation()).thenReturn(808);
		when(player.getIdleRotateLeft()).thenReturn(823);
		when(player.getIdleRotateRight()).thenReturn(824);
		when(player.getPoseAnimation()).thenReturn(808);
		when(client.getNpcDefinition(anyInt())).thenReturn(definition);
		when(definition.getModels()).thenReturn(new int[]{101, 102});
		when(definition.getWidthScale()).thenReturn(128);
		when(definition.getHeightScale()).thenReturn(128);
		when(client.loadModelData(anyInt())).thenReturn(modelData);
		when(client.mergeModels(any(ModelData[].class))).thenReturn(modelData);
		when(modelData.cloneColors()).thenReturn(modelData);
		when(modelData.cloneVertices()).thenReturn(modelData);
		when(modelData.cloneTextures()).thenReturn(modelData);
		when(modelData.light(anyInt(), anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(mock(Model.class));
		when(client.createRuneLiteObject()).thenAnswer(i ->
		{
			if (!dogActive.get())
			{
				return dog;
			}
			RuneLiteObject other = mock(RuneLiteObject.class);
			AtomicBoolean active = new AtomicBoolean();
			when(other.isActive()).thenAnswer(call -> active.get());
			doAnswer(call ->
			{
				active.set(call.getArgument(0));
				return null;
			}).when(other).setActive(anyBoolean());
			return other;
		});
		when(client.loadAnimation(AnimationID.STRAYDOG_READY)).thenReturn(idle);
		when(client.loadAnimation(AnimationID.STRAYDOG_WALK)).thenReturn(walk);
		when(dog.isActive()).thenAnswer(i -> dogActive.get());
		doAnswer(i ->
		{
			dogActive.set(i.getArgument(0));
			return null;
		})
		.when(dog).setActive(anyBoolean());
		plugin.startUp();
	}
	private void inject(String name, Object value) throws Exception
	{
		Field field = AyrasPropHuntPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}
	private void frame()
	{
		plugin.onBeforeRender(new BeforeRender());
	}
	private void state(GameState state)
	{
		when(client.getGameState()).thenReturn(state);
		GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		plugin.onGameStateChanged(event);
	}

	@Test
	public void replacesOnlyYourSceneModelAndPreservesOverheadsAndPlayerState()
	{
		assertTrue(callbacks.addEntity(player, false));
		frame();
		assertFalse(callbacks.addEntity(player, false));
		assertTrue(callbacks.addEntity(player, true));
		assertTrue(callbacks.addEntity(mock(Player.class), false));
		verify(dog).setLocation(location, 2);
		verify(dog).setOrientation(512);
		verify(player, never()).setAnimation(anyInt());
		verify(player, never()).setPoseAnimation(anyInt());
		verify(appearance, never()).setTransformedNpcId(anyInt());
		verify(appearance, never()).setHash();
	}

	@Test
	public void missingCachePartsNeverMakeThePlayerInvisibleAndRetryOnNextFrame()
	{
		when(client.loadModelData(CATALOG.defaultEntry().models[1])).thenReturn(null);
		frame();
		assertTrue(callbacks.addEntity(player, false));
		verify(client, never()).createRuneLiteObject();
		when(client.loadModelData(CATALOG.defaultEntry().models[1])).thenReturn(modelData);
		frame();
		assertFalse(callbacks.addEntity(player, false));
	}

	@Test
	public void missingAnimationUsesStaticMeshUntilItLoads()
	{
		when(client.loadAnimation(AnimationID.STRAYDOG_READY)).thenReturn(null);
		frame();
		assertFalse(callbacks.addEntity(player, false));
		assertTrue(dogActive.get());
		when(client.loadAnimation(AnimationID.STRAYDOG_READY)).thenReturn(idle);
		frame();
		assertFalse(callbacks.addEntity(player, false));
	}

	@Test
	public void switchesBetweenIdleAndWalkingWithoutRestartingEveryFrame()
	{
		frame();
		frame();
		when(player.getPoseAnimation()).thenReturn(819);
		// Run.
		frame();
		frame();
		when(player.getPoseAnimation()).thenReturn(823);
		// Idle turn.
		frame();
		ArgumentCaptor<AnimationController> animations = ArgumentCaptor.forClass(AnimationController.class);
		verify(dog, times(3)).setAnimationController(animations.capture());
		assertSame(idle, animations.getAllValues().get(0).getAnimation());
		assertSame(walk, animations.getAllValues().get(1).getAnimation());
		assertSame(idle, animations.getAllValues().get(2).getAnimation());
		verify(client, times(1)).createRuneLiteObject();
	}

	@Test
	public void tracksThePlayersCurrentWorldViewAndSmoothOrientation()
	{
		frame();
		LocalPoint nestedLocation = new LocalPoint(6500, 6600, 3);
		when(player.getLocalLocation()).thenReturn(nestedLocation);
		when(world.getPlane()).thenReturn(1);
		when(player.getCurrentOrientation()).thenReturn(1410);
		frame();
		verify(dog).setLocation(nestedLocation, 1);
		verify(dog).setOrientation(1410);
	}

	@Test
	public void loadingLogoutAndWorldHopRemoveTheDogAndLoginRecreatesIt()
	{
		for (GameState state : new GameState[]{GameState.LOADING, GameState.HOPPING, GameState.LOGIN_SCREEN})
		{
			state(GameState.LOGGED_IN);
			frame();
			assertFalse(callbacks.addEntity(player, false));
			state(state);
			assertFalse(dogActive.get());
			assertTrue(callbacks.addEntity(player, false));
		}
	}

	@Test
	public void nativeTransformationsAndDeathTemporarilyRestoreTheGameModel()
	{
		frame();
		when(appearance.getTransformedNpcId()).thenReturn(1234);
		frame();
		assertFalse(dogActive.get());
		assertTrue(callbacks.addEntity(player, false));
		when(appearance.getTransformedNpcId()).thenReturn(-1);
		frame();
		assertTrue(dogActive.get());
		when(player.isDead()).thenReturn(true);
		frame();
		assertFalse(dogActive.get());
		assertTrue(callbacks.addEntity(player, false));
	}

	@Test
	public void missingPlayerOrCompositionCannotLeaveAnOrphanedDog()
	{
		frame();
		when(player.getPlayerComposition()).thenReturn(null);
		frame();
		assertFalse(dogActive.get());
		when(client.getLocalPlayer()).thenReturn(null);
		frame();
		assertTrue(callbacks.addEntity(player, false));
	}

	@Test
	public void disablingAndReenablingCleansUpTheReplacement()
	{
		frame();
		plugin.shutDown();
		assertFalse(dogActive.get());
		assertTrue(callbacks.addEntity(player, false));
		frame();
		assertFalse(dogActive.get());
		plugin.startUp();
		frame();
		assertTrue(dogActive.get());
		assertFalse(callbacks.addEntity(player, false));
	}
	private Player partyFriend()
	{
		when(party.isInParty()).thenReturn(true);
		when(party.getPartyId()).thenReturn(100L);
		PartyMember member = mock(PartyMember.class);
		when(member.getDisplayName()).thenReturn("Friend");
		when(party.getMemberById(2L)).thenReturn(member);
		Player friend = mock(Player.class);
		when(friend.getName()).thenReturn("Friend");
		when(friend.getPlayerComposition()).thenReturn(appearance);
		when(friend.getWorldView()).thenReturn(world);
		when(friend.getLocalLocation()).thenReturn(location);
		when(friend.getWorldLocation()).thenReturn(new WorldPoint(3211, 3420, 0));
		when(friend.getIdlePoseAnimation()).thenReturn(808);
		when(friend.getPoseAnimation()).thenReturn(808);
		roster.add(friend);
		return friend;
	}
	private void announce(String name, int worldId, ModelEntry choice, boolean enabled)
	{
		clock.addAndGet(600_000_000L);
		PropAppearanceMessage message = new PropAppearanceMessage(name, worldId, choice, enabled);
		message.setMemberId(2L);
		plugin.onPropAppearanceMessage(message);
	}

	@Test
	public void partyMembersAreHumanUntilTheyOptInAndNormalPlayersStayHuman()
	{
		Player friend = partyFriend();
		frame();
		assertTrue(callbacks.addEntity(friend, false));
		announce("Friend", 301, CATALOG.defaultEntry(), true);
		assertFalse(callbacks.addEntity(friend, false));
		assertTrue(callbacks.addEntity(friend, true));
		assertTrue(callbacks.addEntity(mock(Player.class), false));
		announce("Friend", 301, CATALOG.defaultEntry(), false);
		assertTrue(callbacks.addEntity(friend, false));
	}

	@Test
	public void enteringWildernessRestoresOtherPlayersAndKeepsYourOwnDog()
	{
		Player friend = partyFriend();
		announce("Friend", 301, CATALOG.defaultEntry(), true);
		assertFalse(callbacks.addEntity(friend, false));
		when(client.getVarbitValue(VarbitID.INSIDE_WILDERNESS)).thenReturn(1);
		// Even between frames the render callback stops hiding other players.
		assertTrue(callbacks.addEntity(friend, false));
		frame();
		assertTrue(callbacks.addEntity(friend, false));
		assertFalse(callbacks.addEntity(player, false));
		when(client.getVarbitValue(VarbitID.INSIDE_WILDERNESS)).thenReturn(0);
		frame();
		assertFalse(callbacks.addEntity(friend, false));
	}

	@Test
	public void aFriendAcrossTheWildernessDitchIsNotReplaced()
	{
		Player friend = partyFriend();
		when(friend.getWorldLocation()).thenReturn(new WorldPoint(3100, 3520, 0));
		announce("Friend", 301, CATALOG.defaultEntry(), true);
		assertTrue(callbacks.addEntity(friend, false));
		when(friend.getWorldLocation()).thenReturn(new WorldPoint(3100, 3519, 0));
		frame();
		assertFalse(callbacks.addEntity(friend, false));
	}

	@Test
	public void differentWorldsUnknownMembersAndSpoofedNamesAreRejected()
	{
		Player friend = partyFriend();
		announce("Friend", 302, CATALOG.defaultEntry(), true);
		assertTrue(callbacks.addEntity(friend, false));
		announce("SomebodyElse", 301, CATALOG.defaultEntry(), true);
		assertTrue(callbacks.addEntity(friend, false));
		when(party.getMemberById(2L)).thenReturn(null);
		announce("Friend", 301, CATALOG.defaultEntry(), true);
		assertTrue(callbacks.addEntity(friend, false));
	}

	@Test
	public void stalePresenceAndPartyDeparturesRestoreTheFriend()
	{
		Player friend = partyFriend();
		announce("Friend", 301, CATALOG.defaultEntry(), true);
		clock.addAndGet(46_000_000_000L);
		frame();
		assertTrue(callbacks.addEntity(friend, false));
		announce("Friend", 301, CATALOG.defaultEntry(), true);
		assertFalse(callbacks.addEntity(friend, false));
		plugin.onUserPart(new UserPart(2L));
		assertTrue(callbacks.addEntity(friend, false));
		announce("Friend", 301, CATALOG.defaultEntry(), true);
		plugin.onPartyChanged(new PartyChanged("different", 101L));
		assertTrue(callbacks.addEntity(friend, false));
	}

	@Test
	public void turningOffOtherDogsRestoresTheirModelsImmediately()
	{
		Player friend = partyFriend();
		announce("Friend", 301, CATALOG.defaultEntry(), true);
		showOthers = false;
		frame();
		assertTrue(callbacks.addEntity(friend, false));
	}

	@Test
	public void changingYourPickerUpdatesTheMeshAndBroadcastsTheChoice()
	{
		partyFriend();
		frame();
		chosenEntry = CATALOG.get("NPC:16361:0");
		when(client.loadAnimation(chosenEntry.idle)).thenReturn(idle);
		ConfigChanged change = new ConfigChanged();
		change.setGroup(AyrasPropHuntConfig.GROUP);
		change.setKey("dog");
		plugin.onConfigChanged(change);
		verify(client).loadModelData(chosenEntry.models[0]);
		ArgumentCaptor<PropAppearanceMessage> message = ArgumentCaptor.forClass(PropAppearanceMessage.class);
		verify(party).send(message.capture());
		assertSame(chosenEntry, message.getValue().getEntry(CATALOG));
		assertTrue(message.getValue().isEnabled());
	}

	@Test
	public void disablingSharingSendsAnOptOutWithoutDisablingYourOwnDog()
	{
		partyFriend();
		plugin.onUserSync(new net.runelite.client.party.messages.UserSync());
		plugin.onGameTick(new net.runelite.api.events.GameTick());
		clearInvocations(party);
		sharing = false;
		ConfigChanged change = new ConfigChanged();
		change.setGroup(AyrasPropHuntConfig.GROUP);
		change.setKey("shareAppearance");
		plugin.onConfigChanged(change);
		ArgumentCaptor<PropAppearanceMessage> message = ArgumentCaptor.forClass(PropAppearanceMessage.class);
		verify(party).send(message.capture());
		assertFalse(message.getValue().isEnabled());
		assertFalse(callbacks.addEntity(player, false));
	}

	@Test
	public void sharingIsOffByDefaultAndNoPresenceIsSentBeforeOptIn()
	{
		assertFalse(new AyrasPropHuntConfig()
		{
		}
		.shareAppearance());
		partyFriend();
		sharing = false;
		plugin.onGameTick(new net.runelite.api.events.GameTick());
		verify(party, never()).send(any(PropAppearanceMessage.class));
	}

	@Test
	public void pvpWorldsAndPvpAreasAlwaysRestoreOtherPlayers()
	{
		Player friend = partyFriend();
		announce("Friend", 301, CATALOG.defaultEntry(), true);
		when(client.getWorldType()).thenReturn(java.util.EnumSet.of(WorldType.PVP));
		frame();
		assertTrue(callbacks.addEntity(friend, false));
		when(client.getWorldType()).thenReturn(java.util.EnumSet.noneOf(WorldType.class));
		when(client.getVarbitValue(VarbitID.PVP_AREA_CLIENT)).thenReturn(1);
		frame();
		assertTrue(callbacks.addEntity(friend, false));
		assertFalse(callbacks.addEntity(player, false));
	}

	@Test
	public void aFriendUsesTheirOwnChosenDogAndDisappearsWhenTheyDespawn()
	{
		Player friend = partyFriend();
		when(client.loadAnimation(CATALOG.get("NPC:16461:0").idle)).thenReturn(idle);
		announce("Friend", 301, CATALOG.get("NPC:16461:0"), true);
		verify(client).loadModelData(CATALOG.get("NPC:16461:0").models[0]);
		assertFalse(callbacks.addEntity(friend, false));
		roster.clear();
		frame();
		assertTrue(callbacks.addEntity(friend, false));
		assertFalse(callbacks.addEntity(player, false));
	}

	@Test
	public void hoveringAnotherPlayerRevealsThemForNativePlayerMenus()
	{
		Player friend = partyFriend();
		when(friend.getConvexHull()).thenReturn(new java.awt.Rectangle(100, 100, 60, 100));
		announce("Friend", 301, CATALOG.defaultEntry(), true);
		assertFalse(callbacks.addEntity(friend, false));
		when(client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(130, 150));
		frame();
		assertTrue(callbacks.addEntity(friend, false));
		when(client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(500, 500));
		frame();
		assertFalse(callbacks.addEntity(friend, false));
	}

	@Test public void returningToNormalRestoresYourCharacterAndOptsOutOfSharing()
	{
		partyFriend();
		plugin.onUserSync(new net.runelite.client.party.messages.UserSync());
		plugin.onGameTick(new net.runelite.api.events.GameTick());
		clearInvocations(party);
		frame();
		chosenEntry = null;
		ConfigChanged change = new ConfigChanged();
		change.setGroup(AyrasPropHuntConfig.GROUP); change.setKey("model");
		plugin.onConfigChanged(change);
		assertTrue(callbacks.addEntity(player, false));
		ArgumentCaptor<PropAppearanceMessage> message = ArgumentCaptor.forClass(PropAppearanceMessage.class);
		verify(party).send(message.capture());
		assertFalse(message.getValue().isEnabled());
	}
}
