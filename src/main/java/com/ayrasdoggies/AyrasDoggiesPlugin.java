/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasdoggies;

import com.google.inject.Provides;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Renderable;
import net.runelite.api.WorldView;
import net.runelite.api.WorldType;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.UserSync;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Ayra's Doggies",
	description = "Choose a dog and see opted-in RuneLite Party members as dogs outside the Wilderness",
	tags = {"dog", "pet", "cosmetic", "transmog", "fun", "party"}
)
public class AyrasDoggiesPlugin extends Plugin
{
	private static final long PRESENCE_TTL = 45_000_000_000L;
	private static final int HEARTBEAT_TICKS = 20;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private RenderCallbackManager renderCallbacks;

	@Inject
	private AyrasDoggiesConfig config;

	@Inject
	private PartyService party;

	@Inject
	private WSClient wsClient;
	private volatile boolean running;
	private volatile long lifecycle;
	private int ticksUntilSync;
	private boolean advertised;
	private LongSupplier nanoTime = System::nanoTime;
	private final DogModelLoader models = new DogModelLoader();
	private final Map<Player, DogActor> dogs = new IdentityHashMap<>();
	private final Map<Long, Presence> appearances = new HashMap<>();
	private final RenderCallback renderCallback = new RenderCallback()
	{
		@Override
		public boolean addEntity(Renderable renderable, boolean ui)
		{
			if (ui || !running || !(renderable instanceof Player))
			{
				return true;
			}
			Player player = (Player) renderable;
			DogActor dog = dogs.get(player);
			if (dog == null || !dog.isActive())
			{
				return true;
			}
			if (player != client.getLocalPlayer() && (!canShowOther(player) || revealForInteraction(player)))
			{
				return true;
			}
			return false;
		}
	};

	@Provides
	AyrasDoggiesConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(AyrasDoggiesConfig.class);
	}

	@Override
	protected void startUp()
	{
		running = true;
		long token = ++lifecycle;
		clientThread.invoke(() ->
		{
			if (!running || token != lifecycle)
			{
				return;
			}
			wsClient.registerMessage(DogAppearanceMessage.class);
			renderCallbacks.register(renderCallback);
			broadcast(true);
		});
	}

	@Override
	protected void shutDown()
	{
		running = false;
		++lifecycle;
		clientThread.invoke(() ->
		{
			broadcast(false);
			wsClient.unregisterMessage(DogAppearanceMessage.class);
			renderCallbacks.unregister(renderCallback);
			resetScene();
			appearances.clear();
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!AyrasDoggiesConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		long token = lifecycle;
		clientThread.invoke(() ->
		{
			if (running && token == lifecycle)
			{
				broadcast(true);
				onBeforeRender(new BeforeRender());
			}
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			resetScene();
			if (event.getGameState() != GameState.LOADING)
			{
				broadcast(false);
				appearances.clear();
			}
		}
		ticksUntilSync = 0;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (running && --ticksUntilSync <= 0)
		{
			broadcast(true);
		}
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		appearances.clear();
		advertised = false;
		ticksUntilSync = 0;
		removeOtherDogs();
	}

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		if (event.getPartyId() == party.getPartyId())
		{
			ticksUntilSync = 0;
		}
	}

	@Subscribe
	public void onUserSync(UserSync event)
	{
		ticksUntilSync = 0;
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		appearances.remove(event.getMemberId());
		onBeforeRender(new BeforeRender());
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		DogActor dog = dogs.remove(event.getPlayer());
		if (dog != null)
		{
			dog.remove();
		}
	}

	@Subscribe
	public void onDogAppearanceMessage(DogAppearanceMessage message)
	{
		long token = lifecycle;
		long partyId = party.getPartyId();
		clientThread.invoke(() ->
		{
			if (!running || token != lifecycle || partyId != party.getPartyId()
			|| !party.isInParty() || message == null || !message.isValid())
			{
				return;
			}
			PartyMember member = party.getMemberById(message.getMemberId());
			if (member == null || member == party.getLocalMember())
			{
				return;
			}
			// A member can announce only their own identity, never another player's name.
			if (!Text.standardize(member.getDisplayName()).equals(Text.standardize(message.getPlayerName())))
			{
				return;
			}
			long received = nanoTime.getAsLong();
			Presence previous = appearances.get(message.getMemberId());
			if (message.isEnabled() && previous != null && received - previous.received < 500_000_000L)
			{
				return;
			}
			if (message.isEnabled())
			{
			if (previous == null && appearances.size() >= 256)
			{
				return;
			}
			appearances.put(message.getMemberId(), new Presence(Text.standardize(message.getPlayerName()),
			message.getWorld(), message.getDog(), received));
			}
			else { appearances.remove(message.getMemberId());
			}
			onBeforeRender(new BeforeRender());
		});
	}
	private void broadcast(boolean enabled)
	{
		ticksUntilSync = HEARTBEAT_TICKS;
		Player player = client.getLocalPlayer();
		if (!party.isInParty() || player == null || player.getName() == null)
		{
			return;
		}
		boolean active = enabled && running && config.shareAppearance() && client.getGameState() == GameState.LOGGED_IN;
		if (!active && !advertised)
		{
			return;
		}
		DogAppearanceMessage message = new DogAppearanceMessage(player.getName(), client.getWorld(), selectedDog(), active);
		if (!message.isValid())
		{
			return;
		}
		try
		{
			party.send(message);
			advertised = active;
		}
		catch (IllegalStateException ex)
		{
			/* A disconnected party never prevents local Ayra's Doggies. */
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		if (!running || client.getGameState() != GameState.LOGGED_IN)
		{
			resetScene();
			return;
		}
		Set<Player> present = Collections.newSetFromMap(new IdentityHashMap<>());
		Player local = client.getLocalPlayer();
		if (local != null)
		{
			updateDog(local, selectedDog(), present);
		}
		if (otherDogsAllowed())
		{
			Set<WorldView> views = Collections.newSetFromMap(new IdentityHashMap<>());
			updateWorld(client.getTopLevelWorldView(), views, present);
		}
		Iterator<Map.Entry<Player, DogActor>> iterator = dogs.entrySet().iterator();
		while (iterator.hasNext())
		{
			Map.Entry<Player, DogActor> entry = iterator.next();
			if (!present.contains(entry.getKey()))
			{
				entry.getValue().remove();
				iterator.remove();
			}
		}
	}
	private void updateWorld(WorldView world, Set<WorldView> seen, Set<Player> present)
	{
		if (world == null || !seen.add(world))
		{
			return;
		}
		if (world.players() != null)
		{
			for (Player player : world.players())
			{
				if (player == null || player == client.getLocalPlayer() || !canShowOther(player))
				{
					continue;
				}
				DogModel choice = remoteDog(player);
				if (choice != null && !revealForInteraction(player))
				{
					updateDog(player, choice, present);
				}
			}
		}
		if (world.worldViews() != null)
		{
			for (WorldView child : world.worldViews())
			{
				updateWorld(child, seen, present);
			}
		}
	}
	private boolean canShowOther(Player player)
	{
		if (!otherDogsAllowed())
		{
			return false;
		}
		WorldPoint point = player.getWorldLocation();
		if (point == null)
		{
			return false;
		}
		// Guard the far side of the ditch even while the viewer is still outside.
		int x = point.getX(), y = point.getY();
		boolean wilderness = x >= 2944 && x < 3392 && (y >= 3520 && y < 4352 || y >= 9920 && y < 10752);
		return !wilderness;
	}
	private boolean otherDogsAllowed()
	{
		Set<WorldType> types = client.getWorldType();
		boolean pvpWorld = types != null && (types.contains(WorldType.PVP) || types.contains(WorldType.DEADMAN)
		|| types.contains(WorldType.BOUNTY) || types.contains(WorldType.PVP_ARENA)
		|| types.contains(WorldType.LAST_MAN_STANDING) || types.contains(WorldType.TOURNAMENT_WORLD));
		return config.showOtherDogs() && party.isInParty() && !pvpWorld
		&& client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 0
		&& client.getVarbitValue(VarbitID.PVP_AREA_CLIENT) == 0;
	}
	private boolean revealForInteraction(Player player)
	{
		// Render callbacks remove the original clickbox along with the model.
		// Reveal the actual player under the cursor so native player menus work.
		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return false;
		}
		java.awt.Shape hull = player.getConvexHull();
		return hull != null && hull.contains(mouse.getX(), mouse.getY());
	}
	private DogModel remoteDog(Player player)
	{
		if (player.getName() == null)
		{
			return null;
		}
		String name = Text.standardize(player.getName());
		long now = nanoTime.getAsLong();
		for (Map.Entry<Long, Presence> entry : appearances.entrySet())
		{
			Presence presence = entry.getValue();
			PartyMember member = party.getMemberById(entry.getKey());
			if (presence.world == client.getWorld() && now - presence.received < PRESENCE_TTL
			&& presence.name.equals(name) && member != null
			&& presence.name.equals(Text.standardize(member.getDisplayName())))
			{
				return presence.dog;
			}
		}
		return null;
	}
	private DogModel selectedDog()
	{
		return config.dog() == null ? DogModel.STRAY_DOG : config.dog();
	}
	private void updateDog(Player player, DogModel choice, Set<Player> present)
	{
		PlayerComposition appearance = player.getPlayerComposition();
		LocalPoint point = player.getLocalLocation();
		if (appearance == null || appearance.getTransformedNpcId() != -1 || player.isDead()
		|| player.getWorldView() == null || point == null || !point.isInScene())
		{
			return;
		}
		DogActor actor = dogs.get(player);
		if (actor != null && actor.getDogModel() != choice)
		{
			actor.remove();
			dogs.remove(player);
			actor = null;
		}
		if (actor == null)
		{
			Model model = models.load(client, choice);
			if (model == null)
			{
				return;
			}
			actor = new DogActor(client, choice, model);
			dogs.put(player, actor);
		}
		if (actor.update(client, player))
		{
			present.add(player);
		}
	}
	private void removeOtherDogs()
	{
		dogs.entrySet().removeIf(entry ->
		{
			if (entry.getKey() == client.getLocalPlayer())
			{
				return false;
			}
			entry.getValue().remove();
			return true;
		});
	}
	private void resetScene()
	{
		dogs.values().forEach(DogActor::remove);
		dogs.clear();
		models.clear();
	}
	private static final class Presence
	{
		final String name;
		final int world;
		final DogModel dog;
		final long received;
		Presence(String name, int world, DogModel dog, long received)
		{
			this.name = name;
			this.world = world;
			this.dog = dog;
			this.received = received;
		}
	}
}
