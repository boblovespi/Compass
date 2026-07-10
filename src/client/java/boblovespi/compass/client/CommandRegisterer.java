package boblovespi.compass.client;

import boblovespi.compass.client.config.ColorArgumentType;
import boblovespi.compass.client.config.Config;
import boblovespi.compass.client.waypoint.Waypoint;
import boblovespi.compass.client.waypoint.WaypointManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandRegisterer
{
	public static void registerCommands(WaypointManager waypointManager, CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess)
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
																																				Component.translatable("commands.bob-compass.copy"))))
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
													  Config.HANDLER.instance().whitelistNames.clear();
													  Config.HANDLER.save();
													  return Command.SINGLE_SUCCESS;
												  }))
												  .then(literal("list").executes(c ->
												  {
													  if (!Config.HANDLER.instance().enableWhitelist)
														  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.whitelist.list.disabled"));
													  if (Config.HANDLER.instance().whitelistNames.isEmpty())
													  {
														  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.whitelist.list.empty"));
														  return Command.SINGLE_SUCCESS;
													  }
													  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.whitelist.list.header"));
													  Config.HANDLER.instance().whitelistNames.forEach(n -> c.getSource().sendFeedback(Component.literal(n)));
													  return Command.SINGLE_SUCCESS;
												  }))
												  .then(literal("enable").executes(c ->
												  {
													  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.whitelist.enable"));
													  Config.HANDLER.instance().enableWhitelist = true;
													  Config.HANDLER.save();
													  return Command.SINGLE_SUCCESS;
												  }))
												  .then(literal("disable").executes(c ->
												  {
													  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.whitelist.disable"));
													  Config.HANDLER.instance().enableWhitelist = false;
													  Config.HANDLER.save();
													  return Command.SINGLE_SUCCESS;
												  }))
												  .then(literal("remove")
																.then(argument("player", StringArgumentType.word())
																			  .suggests((c, b) ->
																			  {
																				  Config.HANDLER.instance().whitelistNames.stream()
																								  .filter(s -> s.toLowerCase().startsWith(b.getRemainingLowerCase()))
																								  .forEach(b::suggest);
																				  return b.buildFuture();
																			  })
																			  .executes(c ->
																			  {
																				  var player = c.getArgument("player", String.class);
																				  if (!Config.HANDLER.instance().whitelistNames.contains(player))
																					  throw unknownPlayer.create(player);
																				  c.getSource()
																				   .sendFeedback(Component.translatable("commands.bob-compass.whitelist.remove", player));
																				  Config.HANDLER.instance().whitelistNames.remove(player);
																				  Config.HANDLER.save();
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
																				  if (Config.HANDLER.instance().whitelistNames.contains(player))
																					  throw existingPlayer.create(player);
																				  c.getSource()
																				   .sendFeedback(Component.translatable("commands.bob-compass.whitelist.add", player));
																				  Config.HANDLER.instance().whitelistNames.add(player);
																				  Config.HANDLER.save();
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
																	if (Config.HANDLER.instance().whitelistNames.contains(player))
																		throw existingPlayer.create(player);
																	c.getSource()
																	 .sendFeedback(Component.translatable("commands.bob-compass.whitelist.add", player));
																	Config.HANDLER.instance().whitelistNames.add(player);
																	Config.HANDLER.save();
																	return Command.SINGLE_SUCCESS;
																}))
												  .executes(c ->
												  {
													  if (!Config.HANDLER.instance().enableWhitelist)
													  {
														  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.whitelist.list.disabled"));
														  return Command.SINGLE_SUCCESS;
													  }
													  if (Config.HANDLER.instance().whitelistNames.isEmpty())
													  {
														  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.whitelist.list.empty"));
														  return Command.SINGLE_SUCCESS;
													  }
													  c.getSource().sendFeedback(Component.translatable("commands.bob-compass.whitelist.list.header"));
													  Config.HANDLER.instance().whitelistNames.forEach(n -> c.getSource().sendFeedback(Component.literal(n)));
													  return Command.SINGLE_SUCCESS;
												  })
										 )
						   );
		// @formatter:on
	}
}
