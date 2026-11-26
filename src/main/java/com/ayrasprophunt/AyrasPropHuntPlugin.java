/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasprophunt;

import com.google.inject.Provides;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Ayra's Prop Hunt",
	description = "Become any catalogued NPC, scenery object, item, or model with a searchable picker",
	tags = {"prop", "model", "cosmetic", "transmog", "search", "party"}
)
public class AyrasPropHuntPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(AyrasPropHuntPlugin.class);
	private static final long PRESENCE_TTL = 45_000_000_000L;
	private static final int HEARTBEAT_TICKS = 20;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private RenderCallbackManager renderCallbacks;

	@Inject
	private AyrasPropHuntConfig config;

	@Inject
	private PartyService party;

	@Inject
	private WSClient wsClient;
	private volatile boolean running;
	private volatile long lifecycle;
	private int ticksUntilSync;
	private boolean advertised;
	private LongSupplier nanoTime = System::nanoTime;
	private String renderStatus;
	@Inject private ModelCatalog catalog;
	@Inject private PropHuntUi ui;
	private final PropModelLoader models = new PropModelLoader();
	private final Map<Player, PropActor> actors = new IdentityHashMap<>();
	// Keys can only come from the fixed catalog. Retry on selection/config or scene changes.
	private final Set<String> failedModels = new HashSet<>();
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
			PropActor prop = actors.get(player);
			if (prop == null || !prop.isActive())
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
	AyrasPropHuntConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(AyrasPropHuntConfig.class);
	}

	@Override
	protected void startUp()
	{
		running = true;
		long token = ++lifecycle;
		ui.start(selectedEntry());
		clientThread.invoke(() ->
		{
			if (!running || token != lifecycle)
			{
				return;
			}
			wsClient.registerMessage(PropAppearanceMessage.class);
			renderCallbacks.register(renderCallback);
			broadcast(true);
		});
	}

	@Override
	protected void shutDown()
	{
		running = false;
		++lifecycle;
		ui.stop();
		clientThread.invoke(() ->
		{
			broadcast(false);
			wsClient.unregisterMessage(PropAppearanceMessage.class);
			renderCallbacks.unregister(renderCallback);
			resetScene();
			appearances.clear();
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!AyrasPropHuntConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		long token = lifecycle;
		clientThread.invoke(() ->
		{
			if (running && token == lifecycle)
			{
				failedModels.clear();
				renderStatus = null;
				broadcast(true);
				ui.selection(selectedEntry());
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
		removeOtherProps();
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
		PropActor prop = actors.remove(event.getPlayer());
		if (prop != null)
		{
			prop.remove();
		}
	}

	@Subscribe
	public void onPropAppearanceMessage(PropAppearanceMessage message)
	{
		long token = lifecycle;
		long partyId = party.getPartyId();
		clientThread.invoke(() ->
		{
			if (!running || token != lifecycle || partyId != party.getPartyId()
			|| !party.isInParty() || message == null || !message.isValid(catalog))
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
			message.getWorld(), message.getEntry(catalog), received));
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
		boolean active = enabled && running && selectedEntry() != null && config.shareAppearance() && client.getGameState() == GameState.LOGGED_IN;
		if (!active && !advertised)
		{
			return;
		}
		PropAppearanceMessage message = new PropAppearanceMessage(player.getName(), client.getWorld(), selectedEntry() == null ? catalog.defaultEntry() : selectedEntry(), active);
		if (!message.isValid(catalog))
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
			/* A disconnected party never prevents local Ayra's Prop Hunt. */
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
		if (local != null && selectedEntry() != null)
		{
			updateProp(local, selectedEntry(), present);
		}
		else if (selectedEntry() == null) { status("Normal character"); }
		if (otherPropsAllowed())
		{
			Set<WorldView> views = Collections.newSetFromMap(new IdentityHashMap<>());
			updateWorld(client.getTopLevelWorldView(), views, present);
		}
		Iterator<Map.Entry<Player, PropActor>> iterator = actors.entrySet().iterator();
		while (iterator.hasNext())
		{
			Map.Entry<Player, PropActor> entry = iterator.next();
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
				ModelEntry choice = remoteEntry(player);
				if (choice != null && !revealForInteraction(player))
				{
					updateProp(player, choice, present);
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
		if (!otherPropsAllowed())
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
	private boolean otherPropsAllowed()
	{
		Set<WorldType> types = client.getWorldType();
		boolean pvpWorld = types != null && (types.contains(WorldType.PVP) || types.contains(WorldType.DEADMAN)
		|| types.contains(WorldType.BOUNTY) || types.contains(WorldType.PVP_ARENA)
		|| types.contains(WorldType.LAST_MAN_STANDING) || types.contains(WorldType.TOURNAMENT_WORLD));
		return config.showOtherProps() && party.isInParty() && !pvpWorld
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
	private ModelEntry remoteEntry(Player player)
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
				return presence.prop;
			}
		}
		return null;
	}
	private ModelEntry selectedEntry()
	{
		return "NONE".equals(config.modelKey()) ? null : catalog.get(config.modelKey()) == null ? catalog.defaultEntry() : catalog.get(config.modelKey());
	}
	private void updateProp(Player player, ModelEntry choice, Set<Player> present)
	{
		PlayerComposition appearance = player.getPlayerComposition();
		LocalPoint point = player.getLocalLocation();
		if (appearance == null || appearance.getTransformedNpcId() != -1 || player.isDead()
		|| player.getWorldView() == null || point == null || !point.isInScene())
		{
			return;
		}
		PropActor actor = actors.get(player);
		if (actor != null && actor.getEntry() != choice)
		{
			actor.remove();
			actors.remove(player);
			actor = null;
		}
		if (failedModels.contains(choice.key))
		{
			if (player == client.getLocalPlayer()) { status("Unable to render " + choice.name + ". Try another model."); }
			return;
		}
		try
		{
			if (actor == null)
			{
				Model model = models.load(client, choice);
				if (model == null)
				{
					if (player == client.getLocalPlayer()) { status("Loading " + choice.name + " from the game cache…"); }
					return;
				}
				actor = new PropActor(client, choice, model);
				actors.put(player, actor);
			}
			if (actor.update(client, player))
			{
				present.add(player);
				if (player == client.getLocalPlayer()) { status(choice.detail()); }
			}
		}
		catch (RuntimeException ex)
		{
			actors.remove(player);
			if (actor != null) { actor.remove(); }
			failedModels.add(choice.key);
			if (player == client.getLocalPlayer()) { status("Unable to render " + choice.name + ". Try another model."); }
			log.warn("Unable to render prop {} ({}, model parts {}); keeping the normal player model",
				choice.name, choice.key, Arrays.toString(choice.models), ex);
		}
	}
	private void removeOtherProps()
	{
		actors.entrySet().removeIf(entry ->
		{
			if (entry.getKey() == client.getLocalPlayer())
			{
				return false;
			}
			entry.getValue().remove();
			return true;
		});
	}
	private void status(String text)
	{
		if (!text.equals(renderStatus)) { renderStatus = text; ui.status(text); }
	}

	@Subscribe
	public void onAnimationChanged(net.runelite.api.events.AnimationChanged event)
	{
		PropActor actor = actors.get(event.getActor());
		if (actor != null) { actor.actionChanged(); }
	}

	private void resetScene()
	{
		actors.values().forEach(PropActor::remove);
		actors.clear();
		models.clear();
		failedModels.clear();
		renderStatus = null;
	}
	private static final class Presence
	{
		final String name;
		final int world;
		final ModelEntry prop;
		final long received;
		Presence(String name, int world, ModelEntry prop, long received)
		{
			this.name = name;
			this.world = world;
			this.prop = prop;
			this.received = received;
		}
	}
}
