package boblovespi.compass.client;

import boblovespi.compass.Compass;
import boblovespi.compass.client.mixin.GameRendererAccessor;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CompassClient implements ClientModInitializer
{
	private static final ResourceLocation compassPointer = Compass.id("textures/hud/compass_pointer.png");
	private static final ResourceLocation waypointMarker = Compass.id("textures/hud/waypoint_marker.png");
	private static final ResourceLocation waypointMarkerInner = Compass.id("textures/hud/waypoint_marker_inner.png");
	private static final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("h:mm a");
	private WaypointManager waypointManager;
	private final Set<String> whitelistedNames = new HashSet<>();
	private boolean enableWhitelist = true;
	private String targetedWaypoint = ".compass";
	private boolean newPing = false;

	private final KeyMapping toggleWaypointKeybind = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.bob-compass.toggle_waypoint", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.bob-compass.category"));
	private final KeyMapping pingKeybind = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.bob-compass.add_waypoint", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, "key.bob-compass.category"));

	// private RenderType WAYPOINT_RENDER = RenderType.create("waypoint_render", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 786432, RenderType.CompositeState.builder().setShaderState(RENDERTYPE_GUI_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST).createCompositeState(false));

	@Override
	public void onInitializeClient()
	{
		HudRenderCallback.EVENT.register(this::drawHudElements);
		// WorldRenderEvents.LAST.register(this::drawWorldWaypoints);
		ClientCommandRegistrationCallback.EVENT.register(this::registerCommands);
		ClientReceiveMessageEvents.CHAT.register(this::onChat);
		ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
		ClientPlayConnectionEvents.JOIN.register(this::onJoinNewWorld);
		ClientPlayConnectionEvents.DISCONNECT.register(this::onDisconnectWorld);
		reloadWhitelist();
	}

	private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess)
	{
		var unknownWaypoint = new DynamicCommandExceptionType(n -> new LiteralMessage("Unknown waypoint '" + n + "'"));
		var unknownPlayer = new DynamicCommandExceptionType(n -> new LiteralMessage("Unknown player '" + n + "'"));
		var existingPlayer = new DynamicCommandExceptionType(n -> new LiteralMessage("Player '" + n + "' is already whitelisted"));
		// @formatter:off
		dispatcher.register(literal("compass")
									.then(literal("waypoints")
												  .then(literal("clear").executes(c ->
												  {
													  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.waypoints.clear"));
													  waypointManager.clearAllWaypoints();
													  return Command.SINGLE_SUCCESS;
												  }))
												  .then(literal("list").executes(c ->
														  {
															  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.waypoints.list.header"));
															  waypointManager.forEach((n, w) -> c.getSource()
																								 .sendFeedback(
																										 Component.translatable("commands.bob-compass.waypoints.list.entry", n,
																														  String.format("%.0f", w.pos().x), String.format("%.0f", w.pos().y),
																														  String.format("%.0f", w.pos().z), w.level().location(),
																														  String.format("#%06X", w.color()))
																												  .withStyle(Style.EMPTY.withClickEvent(
																																		  new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD,
																																				  w.formatted(n)))
																																		.withHoverEvent(new HoverEvent(
																																				HoverEvent.Action.SHOW_TEXT,
																																				Component.literal(w.formatted(n))
																																						 .withColor(w.color()))))
																												  .withColor(w.color())));
															  return Command.SINGLE_SUCCESS;
														  })
													   )
												  .then(literal("here")
																.then(argument("name", StringArgumentType.word())
																			  .then(argument("color", ColorArgumentType.of()).executes(c ->
																					  {
																						  var name = c.getArgument("name", String.class);
																						  var color = c.getArgument("color", int.class) & 0xFFFFFF;
																						  var pos = c.getSource().getPosition();
																						  var dim = c.getSource().getWorld().dimension();
																						  var waypoint = new Waypoint(dim, pos, color);
																						  waypointManager.modifyWaypointAndSave(name, waypoint);
																						  c.getSource()
																						   .sendFeedback(Component.translatable("commands.bob-compass.waypoints.add",
																								   Component.literal(name).withColor(color)));
																						  return Command.SINGLE_SUCCESS;
																					  })
																				   ).executes(c ->
																		{
																			var name = c.getArgument("name", String.class);
																			var color = 0x00BBFF;
																			var pos = c.getSource().getPosition();
																			var dim = c.getSource().getWorld().dimension();
																			var waypoint = new Waypoint(dim, pos, color);
																			waypointManager.modifyWaypointAndSave(name, waypoint);
																			c.getSource()
																			 .sendFeedback(Component.translatable("commands.bob-compass.waypoints.add",
																					 Component.literal(name).withColor(color)));
																			return Command.SINGLE_SUCCESS;
																		})
																	 )
													   )
												  .then(literal("at")
																.then(argument("pos", BlockPosArgument.blockPos())
																			  .then(argument("name", StringArgumentType.word())
																							.then(argument("color", ColorArgumentType.of()).executes(c ->
																									{
																										var name = c.getArgument("name", String.class);
																										var color = c.getArgument("color", int.class) & 0xFFFFFF;
																										var pos = c.getArgument("pos", Coordinates.class)
																												   .getPosition(c.getSource().getPlayer().createCommandSourceStack());
																										var dim = c.getSource().getWorld().dimension();
																										var waypoint = new Waypoint(dim, pos, color);
																										waypointManager.modifyWaypointAndSave(name, waypoint);
																										c.getSource()
																										 .sendFeedback(Component.translatable("commands.bob-compass.waypoints.add",
																												 Component.literal(name).withColor(color)));
																										return Command.SINGLE_SUCCESS;
																									})
																								 ).executes(c ->
																					  {
																						  var name = c.getArgument("name", String.class);
																						  var color = 0x00BBFF;
																						  var pos = c.getArgument("pos", Coordinates.class)
																									 .getPosition(c.getSource().getPlayer().createCommandSourceStack());
																						  var dim = c.getSource().getWorld().dimension();
																						  var waypoint = new Waypoint(dim, pos, color);
																						  waypointManager.modifyWaypointAndSave(name, waypoint);
																						  c.getSource()
																						   .sendFeedback(Component.translatable("commands.bob-compass.waypoints.add",
																								   Component.literal(name).withColor(color)));
																						  return Command.SINGLE_SUCCESS;
																					  })
																				   )
																	 )
													   )
												  .then(literal("share")
																.then(argument("name", StringArgumentType.word()).suggests((c, b) ->
																		{
																			waypointManager.streamNames()
																						   .filter(s -> !s.startsWith("."))
																						   .filter(s -> s.toLowerCase().startsWith(b.getRemainingLowerCase()))
																						   .forEach(b::suggest);
																			return b.buildFuture();
																		}).executes(c ->
																		{
																			var name = c.getArgument("name", String.class);
																			var waypoint = waypointManager.getWaypoint(name);
																			if (waypoint == null)
																				throw unknownWaypoint.create(name);
																			c.getSource().getPlayer().connection.sendChat(waypoint.formatted(name));
																			return Command.SINGLE_SUCCESS;
																		})
																	 )
													   )
												  .then(literal("remove").then(argument("name", StringArgumentType.word()).suggests((c, b) ->
														  {
															  waypointManager.streamNames()
																			 .filter(s -> !s.startsWith("."))
																			 .filter(s -> s.toLowerCase().startsWith(b.getRemainingLowerCase()))
																			 .forEach(b::suggest);
															  return b.buildFuture();
														  }).executes(c ->
														  {
															  var name = c.getArgument("name", String.class);
															  var waypoint = waypointManager.getWaypoint(name);
															  if (waypoint == null)
																  throw unknownPlayer.create(name);
															  c.getSource()
															   .sendFeedback(Component.translatable("commands.bob-compass.waypoints.remove",
																	   Component.literal(name).withColor(waypoint.color())));
															  waypointManager.removeWaypointAndSave(name);
															  return Command.SINGLE_SUCCESS;
														  })
																			  ))
												  .executes(c ->
												  {
													  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.waypoints.list.header"));
													  waypointManager.forEach((n, w) -> c.getSource()
																						 .sendFeedback(
																								 Component.translatable("commands.bob-compass.waypoints.list.entry", n,
																												  String.format("%.0f", w.pos().x), String.format("%.0f", w.pos().y),
																												  String.format("%.0f", w.pos().z), w.level().location(),
																												  String.format("#%06X", w.color()))
																										  .withStyle(Style.EMPTY.withClickEvent(
																																  new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD,
																																		  w.formatted(n)))
																																.withHoverEvent(new HoverEvent(
																																		HoverEvent.Action.SHOW_TEXT,
																																		Component.literal(w.formatted(n))
																																				 .withColor(w.color()))))
																										  .withColor(w.color())));
													  return Command.SINGLE_SUCCESS;
												  })
										 )
									.then(literal("whitelist")
												  .then(literal("clear").executes(c ->
												  {
													  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.whitelist.clear"));
													  whitelistedNames.clear();
													  saveWhitelist();
													  return Command.SINGLE_SUCCESS;
												  }))
												  .then(literal("list").executes(c ->
												  {
													  if (!enableWhitelist)
														  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.whitelist.list.disabled"));
													  if (whitelistedNames.isEmpty())
													  {
														  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.whitelist.list.empty"));
														  return Command.SINGLE_SUCCESS;
													  }
													  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.whitelist.list.header"));
													  whitelistedNames.forEach(n -> c.getSource().sendFeedback(Component.literal(n)));
													  return Command.SINGLE_SUCCESS;
												  }))
												  .then(literal("enable").executes(c ->
												  {
													  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.whitelist.enable"));
													  enableWhitelist = true;
													  saveWhitelist();
													  return Command.SINGLE_SUCCESS;
												  }))
												  .then(literal("disable").executes(c ->
												  {
													  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.whitelist.disable"));
													  enableWhitelist = false;
													  saveWhitelist();
													  return Command.SINGLE_SUCCESS;
												  }))
												  .then(literal("remove")
																.then(argument("player", StringArgumentType.word())
																			  .suggests((c, b) ->
																			  {
																				  whitelistedNames.stream()
																								  .filter(s -> s.toLowerCase().startsWith(b.getRemainingLowerCase()))
																								  .forEach(b::suggest);
																				  return b.buildFuture();
																			  })
																			  .executes(c ->
																			  {
																				  var player = c.getArgument("player", String.class);
																				  if (!whitelistedNames.contains(player))
																					  throw unknownPlayer.create(player);
																				  c.getSource()
																				   .sendFeedback(Component.translatable("commands.bob-compass.whitelist.remove", player));
																				  whitelistedNames.remove(player);
																				  saveWhitelist();
																				  return Command.SINGLE_SUCCESS;
																			  })))
												  .then(literal("add")
																.then(argument("player", StringArgumentType.word())
																			  .suggests((c, b) ->
																			  {
																				  c.getSource()
																				   .getOnlinePlayerNames()
																				   .stream()
																				   .filter(s -> s.toLowerCase().startsWith(b.getRemainingLowerCase()))
																				   .forEach(b::suggest);
																				  return b.buildFuture();
																			  })
																			  .executes(c ->
																			  {
																				  var player = c.getArgument("player", String.class);
																				  if (whitelistedNames.contains(player))
																					  throw existingPlayer.create(player);
																				  c.getSource()
																				   .sendFeedback(Component.translatable("commands.bob-compass.whitelist.add", player));
																				  whitelistedNames.add(player);
																				  saveWhitelist();
																				  return Command.SINGLE_SUCCESS;
																			  })))
												  .then(argument("player", StringArgumentType.word())
																.suggests((c, b) ->
																{
																	c.getSource()
																	 .getOnlinePlayerNames()
																	 .stream()
																	 .filter(s -> s.toLowerCase().startsWith(b.getRemainingLowerCase()))
																	 .forEach(b::suggest);
																	return b.buildFuture();
																})
																.executes(c ->
																{
																	var player = c.getArgument("player", String.class);
																	if (whitelistedNames.contains(player))
																		throw existingPlayer.create(player);
																	c.getSource()
																	 .sendFeedback(Component.translatable("commands.bob-compass.whitelist.add", player));
																	whitelistedNames.add(player);
																	saveWhitelist();
																	return Command.SINGLE_SUCCESS;
																}))
												  .executes(c ->
												  {
													  if (!enableWhitelist)
													  {
														  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.whitelist.list.disabled"));
														  return Command.SINGLE_SUCCESS;
													  }
													  if (whitelistedNames.isEmpty())
													  {
														  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.whitelist.list.empty"));
														  return Command.SINGLE_SUCCESS;
													  }
													  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.whitelist.list.header"));
													  whitelistedNames.forEach(n -> c.getSource().sendFeedback(Component.literal(n)));
													  return Command.SINGLE_SUCCESS;
												  })
										 )
						   );
		// @formatter:on
	}

	private void saveWhitelist()
	{
		var minecraft = Minecraft.getInstance();
		var gameDir = minecraft.gameDirectory.toPath();
		var dataDir = gameDir.resolve("compass");
		var file = dataDir.resolve("whitelist.txt");
		try
		{
			Files.createDirectories(dataDir);
			var whitelist = enableWhitelist + "\n" + String.join("\n", whitelistedNames);
			Files.writeString(file, whitelist);
		}
		catch (IOException e)
		{
			Compass.LOGGER.error("failed to write whitelist to {}", file);
			Compass.LOGGER.error(e.toString());
		}
	}

	private void reloadWhitelist()
	{
		var minecraft = Minecraft.getInstance();
		var gameDir = minecraft.gameDirectory.toPath();
		var dataDir = gameDir.resolve("compass");
		var file = dataDir.resolve("whitelist.txt");
		try
		{
			Files.createDirectories(dataDir);
			var lines = Files.readAllLines(file);
			if (lines.isEmpty())
			{
				Compass.LOGGER.error("whitelist file is empty!");
				return;
			}
			enableWhitelist = Boolean.parseBoolean(lines.get(0));
			whitelistedNames.clear();
			lines.stream().skip(1).forEach(whitelistedNames::add);
		}
		catch (IOException e)
		{
			Compass.LOGGER.error("failed to read whitelist from {}", file);
			Compass.LOGGER.error(e.toString());
		}
	}

	private void onDisconnectWorld(ClientPacketListener clientPacketListener, Minecraft minecraft)
	{
		waypointManager.saveWaypoints();
	}

	private void onJoinNewWorld(ClientPacketListener handler, PacketSender sender, Minecraft client)
	{
		System.out.println("joining new world!");
		waypointManager = WaypointManager.of();
	}

	private void onEndTick(Minecraft minecraft)
	{
		while (toggleWaypointKeybind.consumeClick())
		{
			targetedWaypoint = waypointManager.nextWaypoint(targetedWaypoint);
		}
		if (newPing)
		{
			newPing = false;
			if (minecraft.player != null)
			{
				var waypoint = waypointManager.getWaypoint(".ping");
				var myName = minecraft.getUser().getName();
				if (waypoint != null && Config.HANDLER.instance().sharePing)
					minecraft.player.connection.sendChat(waypoint.formatted(".ping" + myName));
				assert minecraft.level != null;
				minecraft.level.playSound(minecraft.player, minecraft.player, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1, 1);
			}
		}
	}

	private void onChat(Component message, @Nullable PlayerChatMessage signedMessage, @Nullable GameProfile sender, ChatType.Bound params, Instant receptionTimestamp)
	{
		var minecraft = Minecraft.getInstance();
		var myName = minecraft.getUser().getName();
		var sentFrom = sender == null ? null : sender.getName();
		var sentFromMe = myName.equals(sentFrom);
		if (!enableWhitelist || (sentFromMe || whitelistedNames.contains(sentFrom)))
		{
			// System.out.println(message.toString());
			var messageStr = message.getString();
			var waypointIdx = messageStr.indexOf("waypoint");
			if (waypointIdx != -1)
			{
				messageStr = messageStr.substring(waypointIdx).strip();
				if (!messageStr.contains(" "))
				{
					var components = messageStr.split(",");
					System.out.println(Arrays.toString(components));
					if (components.length != 7)
						return;
					if (!components[0].equals("waypoint"))
						return;
					var name = components[1];
					if (sentFromMe && name.startsWith("."))
						return;
					var x = Utils.tryParseInt(components[2], Integer::parseInt);
					if (x.isEmpty())
						return;
					var y = Utils.tryParseInt(components[3], Integer::parseInt);
					if (y.isEmpty())
						return;
					var z = Utils.tryParseInt(components[4], Integer::parseInt);
					if (z.isEmpty())
						return;
					var level = components[5];
					var color = Utils.tryParseInt(components[6], Integer::decode);
					if (color.isEmpty())
						return;
					var location = ResourceLocation.tryParse(level);
					if (location == null)
						return;
					var pos = Vec3.atCenterOf(new Vec3i(x.getAsInt(), y.getAsInt(), z.getAsInt()));
					var waypoint = new Waypoint(ResourceKey.create(Registries.DIMENSION, location), pos, color.getAsInt());
					waypointManager.modifyWaypointAndSave(name, waypoint);
					if (name.startsWith(".ping") && minecraft.level != null)
						minecraft.level.playSound(minecraft.player, waypoint.pos().x, waypoint.pos().y, waypoint.pos().z, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1, 1);
				}
			}
		}
	}

	private void drawHudElements(GuiGraphics graphics, float delta)
	{
		var minecraft = Minecraft.getInstance();
		if (minecraft.getDebugOverlay().showDebugScreen())
			return;
		var player = minecraft.player;
		if (player == null)
			return;
		var font = minecraft.font;
		var pos = player.getPosition(delta);
		var dir = (((player.getViewYRot(delta) + 180) % 360) + 360) % 360;

		var totalWidth = graphics.guiWidth() / 5 * 3 - 20;
		var halfWidth = totalWidth / 2;
		var start = graphics.guiWidth() / 2 - halfWidth;
		var center = start + halfWidth;
		var gradientLength = Math.min(totalWidth / 10, 20);
		// graphics.enableScissor(start, 14, start + totalWidth, 16);
		var time = LocalDateTime.now();
		var timeString = time.format(timeFormat);
		var posStr = String.format("%.0f (%.0f) %.0f", pos.x, pos.y, pos.z);
		var posWidth = font.width(posStr);
		var minPosWidth = Math.max(60, posWidth);

		// ping
		if (pingKeybind.consumeClick())
		{
			if (!removePingIfExists(delta, player))
			{
				var raycast = player.pick(100, delta, false);
				if (raycast.getType() != HitResult.Type.MISS)
				{
					var loc = raycast.getLocation();
					waypointManager.modifyWaypointAndSave(".ping", new Waypoint(player.level().dimension(), loc, Config.HANDLER.instance().pingWaypointColor));
					targetedWaypoint = ".ping";
					newPing = true;
				}
			}

		}
		// consume remaining clicks
		//noinspection StatementWithEmptyBody
		while (pingKeybind.consumeClick())
		{
		}

		// compass
		var addedCompass = false;
		var config = Config.HANDLER.instance();
		for (var item : player.getInventory().items)
		{
			if (item.is(Items.COMPASS))
			{
				var targetPos = CompassItem.isLodestoneCompass(item) ? CompassItem.getLodestonePosition(item.getTag()) : CompassItem.getSpawnPosition(player.level());
				if (targetPos == null)
					continue;
				waypointManager.modifyWaypoint(".compass", new Waypoint(targetPos.dimension(), targetPos.pos().getCenter(), config.compassWaypointColor));
				addedCompass = true;
				break;
			}
		}
		if (!addedCompass)
			waypointManager.removeWaypoint(".compass");

		if (config.requireCompassForCompassBar && !player.getMainHandItem().is(ItemTags.COMPASSES) && !player.getOffhandItem().is(ItemTags.COMPASSES))
			return;

		// begin render waypoint
		var camera = minecraft.gameRenderer.getMainCamera();
		switch (config.waypointMode)
		{
			case NEVER ->
			{
			}
			case PING ->
			{
				var accessor = (GameRendererAccessor) minecraft.gameRenderer;
				var fov = accessor.callGetFov(camera, delta, true);
				var stack2 = new PoseStack();
				stack2.mulPoseMatrix(minecraft.gameRenderer.getProjectionMatrix(fov));
				// accessor.callBobHurt(stack2, delta);
				// accessor.callBobView(stack2, delta);
				waypointManager.forEach((n, ping) -> {
					var dist = pos.distanceToSqr(ping.pos());
					if (dist >= Mth.square((float) Config.HANDLER.instance().waypointRenderDistance))
						return;
					if (!player.level().dimension().equals(ping.level()))
						return;
					if (!n.startsWith(".ping"))
						return;
					var name = n.substring(5);
					renderMarker(graphics, minecraft, ping, name, font, stack2.last().pose(), camera);
				});
			}
			case WAYPOINT ->
			{
				var accessor = (GameRendererAccessor) minecraft.gameRenderer;
				var fov = accessor.callGetFov(camera, delta, true);
				var stack2 = new PoseStack();
				stack2.mulPoseMatrix(minecraft.gameRenderer.getProjectionMatrix(fov));
				// accessor.callBobHurt(stack2, delta);
				// accessor.callBobView(stack2, delta);
				var ping = waypointManager.getWaypoint(targetedWaypoint);
				if (ping == null)
					break;
				var dist = pos.distanceToSqr(ping.pos());
				if (dist >= Mth.square((float) Config.HANDLER.instance().waypointRenderDistance))
					break;
				if (!player.level().dimension().equals(ping.level()))
					break;
				renderMarker(graphics, minecraft, ping, "", font, stack2.last().pose(), camera);
			}
		}

		// begin render bar

		var y = config.yOffset;

		// top bg
		graphics.fill(start + gradientLength, y, start + totalWidth - gradientLength, y + 11, 0x40707070);
		fillGradientHorizontal(graphics, start, y, start + gradientLength, y + 11, 0x00707070, 0x40707070);
		fillGradientHorizontal(graphics, start + totalWidth - gradientLength, y, start + totalWidth, y + 11, 0x40707070, 0x00707070);


		graphics.fill(center - minPosWidth, y + 13, center + 60, y + 22, 0x40707070);
		fillGradientHorizontal(graphics, center - minPosWidth - gradientLength, y + 11 + 2, center - minPosWidth, y + 22, 0x00707070, 0x40707070);
		fillGradientHorizontal(graphics, center + 60, y + 13, center + 60 + gradientLength, y + 22, 0x40707070, 0x00707070);

		// top markers
		var nearest15 = ((int) dir) / 15 * 15;
		for (var the15 = nearest15; the15 >= dir - 44; the15 -= 15)
		{
			var x = (int) ((the15 - dir) / 90f * totalWidth);
			var alpha = x + halfWidth > gradientLength ? 0xFF : (x + halfWidth) * 0xFF / gradientLength;
			alpha <<= 24;
			var str = toCompassDir(the15);
			drawCenteredStr(graphics, font, str, center + x, y + 2, 0xDDDDDD | alpha, false);
		}
		for (var the15 = nearest15 + 15; the15 <= dir + 44; the15 += 15)
		{
			var x = (int) ((the15 - dir) / 90f * totalWidth);
			var alpha = halfWidth - x > gradientLength ? 0xFF : (halfWidth - x) * 0xFF / gradientLength;
			alpha <<= 24;
			var str = toCompassDir(the15);
			drawCenteredStr(graphics, font, str, center + x, y + 2, 0xDDDDDD | alpha, false);
		}

		// waypoints
		waypointManager.forEach((name, waypoint) -> {
			if (!player.level().dimension().equals(waypoint.level()))
				return;
			var targetDir = Mth.wrapDegrees(Math.toDegrees(Mth.atan2(pos.x - waypoint.pos().x, waypoint.pos().z - pos.z))) + 180;
			var x = (int) (Mth.wrapDegrees(targetDir - dir) / 90f * totalWidth);
			var y2 = y + 14;
			if (name.equals(targetedWaypoint))
			{
				y2 = y + 25;
				var distance = pos.distanceTo(waypoint.pos());
				var str = "";
				var realX = 0;
				if (-x >= halfWidth - gradientLength)
				{
					realX = start + gradientLength;
					str = String.format("< %.0fm", distance);
				}
				else if (x >= halfWidth - gradientLength)
				{
					realX = start + totalWidth - gradientLength;
					str = String.format("%.0fm >", distance);
				}
				else
				{
					str = String.format("%.0fm", distance);
					realX = center + x;
				}
				drawCenteredStr(graphics, font, str, realX, y2, waypoint.color(), true);
				if (!name.startsWith("."))
					drawCenteredStr(graphics, font, name, realX, y2 + 10, waypoint.color(), true);
			}
			if (x <= halfWidth && x >= -halfWidth)
			{
				var alpha = halfWidth - Math.abs(x) > gradientLength ? 0xFF : (halfWidth - Math.abs(x)) * 0xFF / gradientLength;
				alpha <<= 24;
				graphics.fillGradient(center + x - 1, y + 3, center + x + 1, y2 - 1, waypoint.color(), waypoint.color() | alpha);
			}
		});

		// line
		graphics.fill(start + gradientLength, y + 11, center - 4, y + 11 + 2, 0xFFDDDDDD);
		graphics.fill(center + 4, y + 11, start + totalWidth - gradientLength, y + 11 + 2, 0xFFDDDDDD);
		fillGradientHorizontal(graphics, start, y + 11, start + gradientLength, y + 11 + 2, 0x00DDDDDD, 0xFFDDDDDD);
		fillGradientHorizontal(graphics, start + totalWidth - gradientLength, y + 11, start + totalWidth, y + 11 + 2, 0xFFDDDDDD, 0x00DDDDDD);
		graphics.blit(compassPointer, center - 8, y + 11, 16, 6, 0, 0, 16, 6, 16, 16);

		// coords, time
		graphics.drawString(font, timeString, center + 8, y + 14, 0xDDDDDD, false);
		graphics.drawString(font, posStr, center - posWidth - 8, y + 14, 0xDDDDDD, false);

		// graphics.drawString(font, String.format("%.0f", dir), 50, 50, 0xFFFFFF, false);
	}

	private void renderMarker(GuiGraphics graphics, Minecraft minecraft, Waypoint ping, String name, Font font, Matrix4f cameraProjection, Camera camera)
	{
		var offset = ping.pos().subtract(camera.getPosition());
		if (camera.getLookVector().dot((float) offset.x, (float) offset.y, (float) offset.z) <= 0)
			return;
		var cameraCoords = camera.rotation().transformInverse(offset.toVector3f());
		var viewPos = new Vector4f();
		cameraProjection.transform(cameraCoords.x, cameraCoords.y, cameraCoords.z, 1, viewPos);
		viewPos.div(viewPos.w);
		var sx = (viewPos.x * 0.5f + 0.5f) * graphics.guiWidth();
		var sy = (viewPos.y * 0.5f + 0.5f) * graphics.guiHeight();
		var stack = graphics.pose();
		stack.pushPose();
		stack.translate(sx, sy, -2);
		var scale = switch (minecraft.options.guiScale().get())
		{
			case 1 -> 1f;
			case 2 -> 1f;
			case 3 -> 2f / 3f;
			case 4 -> 0.5f;
			case 5 -> 3f / 5f;
			default -> 0.25f;
		};
		stack.scale(scale, scale, scale);
		RenderUtils.blitColoredSprite(graphics, waypointMarkerInner, -8, 8, -8, 8, 0, 0, 0, 16, 16, 16, 16, ping.color() | 0xFF000000);
		graphics.blit(waypointMarker, -8, -8, 16, 16, 0, 0, 16, 16, 16, 16);
		if (!name.isEmpty())
			drawCenteredStr(graphics, font, name, 0, 8, ping.color(), true);
		stack.popPose();
	}

	private boolean removePingIfExists(float delta, LocalPlayer player)
	{
		return waypointManager.removeIf((n, ping) -> {
			if (!n.startsWith(".ping"))
				return false;
			if (!player.level().dimension().equals(ping.level()))
				return false;
			var viewVec = player.getViewVector(delta);
			var pingVec = ping.pos().subtract(player.getEyePosition(delta)).normalize();
			// 0.996194698 = cosine of 5 degrees
			return viewVec.dot(pingVec) >= 0.996194698;
		});
	}

	private void fillGradientHorizontal(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color1, int color2)
	{
		var vertexConsumer = graphics.bufferSource().getBuffer(RenderType.gui());
		var z = 0;
		var a1 = (float) FastColor.ARGB32.alpha(color1) / 255.0F;
		var r1 = (float) FastColor.ARGB32.red(color1) / 255.0F;
		var g1 = (float) FastColor.ARGB32.green(color1) / 255.0F;
		var b1 = (float) FastColor.ARGB32.blue(color1) / 255.0F;
		var a2 = (float) FastColor.ARGB32.alpha(color2) / 255.0F;
		var r2 = (float) FastColor.ARGB32.red(color2) / 255.0F;
		var g2 = (float) FastColor.ARGB32.green(color2) / 255.0F;
		var b2 = (float) FastColor.ARGB32.blue(color2) / 255.0F;
		var matrix4f = graphics.pose().last().pose();
		vertexConsumer.vertex(matrix4f, (float) x1, (float) y1, (float) z).color(r1, g1, b1, a1).endVertex();
		vertexConsumer.vertex(matrix4f, (float) x1, (float) y2, (float) z).color(r1, g1, b1, a1).endVertex();
		vertexConsumer.vertex(matrix4f, (float) x2, (float) y2, (float) z).color(r2, g2, b2, a2).endVertex();
		vertexConsumer.vertex(matrix4f, (float) x2, (float) y1, (float) z).color(r2, g2, b2, a2).endVertex();
	}

	private String toCompassDir(int the15)
	{
		the15 = (the15 + 360) % 360;
		return switch (the15)
		{
			case 0 -> "N";
			case 45 -> "NE";
			case 90 -> "E";
			case 135 -> "SE";
			case 180 -> "S";
			case 225 -> "SW";
			case 270 -> "W";
			case 315 -> "NW";
			default -> the15 + "";
		};
	}

	private void drawCenteredStr(GuiGraphics graphics, Font font, String str, int x, int y, int color, boolean drawBackground)
	{
		var width = font.width(str);
		var height = font.lineHeight;
		if (drawBackground)
		{
			fillGradientHorizontal(graphics, x - width / 2 - 4, y - 1, x - width / 2, y + height, 0x00707070, 0x40707070);
			fillGradientHorizontal(graphics, x + width / 2, y - 1, x + width / 2 + 4, y + height, 0x40707070, 0x00707070);
			graphics.fill(x - width / 2, y - 1, x + width / 2, y + height, 0x40707070);
		}
		graphics.drawString(font, str, x - width / 2, y, color, false);
	}
}