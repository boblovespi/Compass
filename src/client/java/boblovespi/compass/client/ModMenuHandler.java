package boblovespi.compass.client;

import boblovespi.compass.client.config.Config;
import boblovespi.compass.client.config.DisplayMode;
import boblovespi.compass.client.config.SideDisplay;
import boblovespi.compass.client.config.WaypointMode;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.util.Locale;

public class ModMenuHandler implements ModMenuApi
{
	private static ControllerBuilder<WaypointMode> createWaypointModeController(Option<WaypointMode> o)
	{
		return EnumControllerBuilder.create(o)
									.enumClass(WaypointMode.class)
									.formatValue(f -> Component.translatable("bob-compass.config.waypoint_mode." + f.name().toLowerCase(Locale.ROOT)));
	}

	private static ControllerBuilder<DisplayMode> createDisplayModeController(Option<DisplayMode> o)
	{
		return EnumControllerBuilder.create(o)
									.enumClass(DisplayMode.class)
									.formatValue(f -> Component.translatable("bob-compass.config.display_mode." + f.name().toLowerCase(Locale.ROOT)));
	}

	private static ControllerBuilder<SideDisplay> createSideDisplayController(Option<SideDisplay> option)
	{
		return EnumControllerBuilder.create(option)
									.enumClass(SideDisplay.class)
									.formatValue(f -> Component.translatable("bob-compass.config.side_display." + f.name().toLowerCase(Locale.ROOT)));
	}

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory()
	{
		Config.HANDLER.load();
		var inst = Config.HANDLER.instance();
		var defaults = Config.HANDLER.defaults();
		// @formatter:off
		return p -> YetAnotherConfigLib
							.createBuilder()
							.title(Component.literal("Compass Config"))
							.category(
							ConfigCategory
									.createBuilder()
									.name(Component.translatable("bob-compass.config.name"))
									.group(OptionGroup.createBuilder().name(Component.translatable("bob-compass.config.compass_bar"))
									.option(
											Option
											.<DisplayMode>createBuilder()
											.name(Component.translatable("bob-compass.config.require_compass.name"))
											.description(
													OptionDescription.of(Component.translatable("bob-compass.config.require_compass.tooltip"))
														)
											.binding(defaults.requireCompassForCompassBar, () -> inst.requireCompassForCompassBar,
													b -> inst.requireCompassForCompassBar = b)
											.controller(ModMenuHandler::createDisplayModeController)
											.build()
										   )
									.option(
											Option
											.<Color>createBuilder()
											.name(Component.translatable("bob-compass.config.compass_color.name"))
											.description(
													OptionDescription.of(
															Component.translatable("bob-compass.config.compass_color.tooltip")))
											.binding(new Color(defaults.compassWaypointColor), () -> new Color(inst.compassWaypointColor),
													b -> inst.compassWaypointColor = b.getRGB())
											.controller(ColorControllerBuilder::create)
											.build()
										   )
									.option(
											Option
											.<Color>createBuilder()
											.name(Component.translatable("bob-compass.config.ping_color.name"))
											.description(
													OptionDescription.of(
															Component.translatable("bob-compass.config.ping_color.tooltip")))
											.binding(new Color(defaults.pingWaypointColor), () -> new Color(inst.pingWaypointColor),
													b -> inst.pingWaypointColor = b.getRGB())
											.controller(ColorControllerBuilder::create)
											.build()
										   )
									.option(
											Option
											.<SideDisplay>createBuilder()
											.name(Component.translatable("bob-compass.config.left_side_display.name"))
											.description(
													OptionDescription.of(
															Component.translatable("bob-compass.config.left_side_display.tooltip")))
											.binding(defaults.leftSideDisplay, () -> inst.leftSideDisplay,
													b -> inst.leftSideDisplay = b)
											.controller(ModMenuHandler::createSideDisplayController)
											.build()
										   )
									.option(
											Option
											.<SideDisplay>createBuilder()
											.name(Component.translatable("bob-compass.config.right_side_display.name"))
											.description(
													OptionDescription.of(
															Component.translatable("bob-compass.config.right_side_display.tooltip")))
											.binding(defaults.rightSideDisplay, () -> inst.rightSideDisplay,
													  b -> inst.rightSideDisplay = b)
											.controller(ModMenuHandler::createSideDisplayController)
											.build()
										   )
									.option(
											Option
											.<Integer>createBuilder()
											.name(Component.translatable("bob-compass.config.y_offset.name"))
											.description(
											  OptionDescription.of(Component.translatable("bob-compass.config.y_offset.tooltip"))
												  )
											.binding(defaults.yOffset, () -> inst.yOffset,
											  b -> inst.yOffset = b)
											.controller(o -> IntegerFieldControllerBuilder.create(o).min(0))
											.build()
										   )
									.build())
									.group(OptionGroup.createBuilder().name(Component.translatable("bob-compass.config.waypoint_marker"))
									.option(
											Option
											.<DisplayMode>createBuilder()
											.name(Component.translatable("bob-compass.config.require_compass_marker.name"))
											.description(
													OptionDescription.of(
															Component.translatable("bob-compass.config.require_compass_marker.tooltip")))
											.binding(defaults.requireCompassForCompassBar, () -> inst.requireCompassForCompassBar,
													b -> inst.requireCompassForCompassBar = b)
											.controller(ModMenuHandler::createDisplayModeController)
											.build())
									.option(
											Option
											.<WaypointMode>createBuilder()
											.name(Component.translatable("bob-compass.config.waypoint_mode.name"))
											.description(
													OptionDescription.of(Component.translatable("bob-compass.config.waypoint_mode.tooltip"))
														)
											.binding(defaults.waypointMode, () -> inst.waypointMode,
													b -> inst.waypointMode = b)
											.controller(ModMenuHandler::createWaypointModeController)
											.build()
										   )
									.option(
											Option
											.<Boolean>createBuilder()
											.name(Component.translatable("bob-compass.config.share_ping.name"))
											.description(
													OptionDescription.of(Component.translatable("bob-compass.config.share_ping.tooltip"))
														)
											.binding(defaults.sharePing, () -> inst.sharePing,
													b -> inst.sharePing = b)
											.controller(o -> BooleanControllerBuilder.create(o).yesNoFormatter())
											.build()
										   )
									.option(
											Option
											.<Boolean>createBuilder()
											.name(Component.translatable("bob-compass.config.suppress_messages.name"))
											.description(
													  OptionDescription.of(Component.translatable("bob-compass.config.suppress_messages.tooltip"))
														)
											.binding(defaults.suppressWaypointMessages, () -> inst.suppressWaypointMessages,
													b -> inst.suppressWaypointMessages = b)
											.controller(o -> BooleanControllerBuilder.create(o).yesNoFormatter())
											.build()
											)
									.option(
											Option
											.<Integer>createBuilder()
											.name(Component.translatable("bob-compass.config.waypoint_render_distance.name"))
											.description(
													OptionDescription.of(Component.translatable("bob-compass.config.waypoint_render_distance.tooltip"))
														)
											.binding(defaults.markerRenderDistance, () -> inst.markerRenderDistance,
													b -> inst.markerRenderDistance = b)
											.controller(o -> IntegerFieldControllerBuilder.create(o).min(-1))
											.build()
										   )
									.build())
									.group(OptionGroup.createBuilder().name(Component.translatable("bob-compass.config.whitelist"))
									.option(
											Option
											.<Boolean>createBuilder()
											.name(Component.translatable("bob-compass.config.enable_whitelist.name"))
											.description(
													OptionDescription.of(Component.translatable("bob-compass.config.enable_whitelist.tooltip"))
														)
											.binding(defaults.enableWhitelist, () -> inst.enableWhitelist,
													b -> inst.enableWhitelist = b)
											.controller(o -> BooleanControllerBuilder.create(o).onOffFormatter())
											.build()
										   )
									.build())
									.group(
											ListOption
											.<String>createBuilder()
											.name(Component.translatable("bob-compass.config.whitelist_names.name"))
											.description(
													OptionDescription.of(Component.translatable("bob-compass.config.whitelist_names.tooltip"))
														)
											.initial("")
											.binding(defaults.whitelistNames, () -> inst.whitelistNames,
												b -> inst.whitelistNames = b)
											.controller(StringControllerBuilder::create)
											.build()
										   )
									.build()
									 )
							.save(() -> Config.HANDLER.save())
							.build().generateScreen(p);
		// @formatter:on
	}
}
