package boblovespi.compass.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import net.minecraft.network.chat.Component;

import java.awt.*;

public class ModMenuHandler implements ModMenuApi
{
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
									.option(
											Option
											.<Boolean>createBuilder()
											.name(Component.translatable("bob-compass.config.require_compass.name"))
											.description(
													OptionDescription.of(Component.translatable("bob-compass.config.require_compass.tooltip"))
														)
											.binding(defaults.requireCompassForCompassBar, () -> inst.requireCompassForCompassBar,
													b -> inst.requireCompassForCompassBar = b)
											.controller(BooleanControllerBuilder::create)
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
									.build()
									 )
							.save(() -> Config.HANDLER.save())
							.build().generateScreen(p);
		// @formatter:on
	}
}
