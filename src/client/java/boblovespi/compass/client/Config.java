package boblovespi.compass.client;

import boblovespi.compass.Compass;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;

public class Config
{
	public static ConfigClassHandler<Config> HANDLER = ConfigClassHandler.createBuilder(Config.class)
																		 .id(Compass.id("config"))
																		 .serializer(c -> GsonConfigSerializerBuilder.create(c)
																													 .setPath(FabricLoader.getInstance()
																																		  .getConfigDir()
																																		  .resolve(Compass.MOD_ID +
																																				   ".json5"))
																													 .setJson5(true)
																													 .build())
																		 .build();
	@SerialEntry
	public boolean requireCompassForCompassBar = false;
	@SerialEntry
	public int compassWaypointColor = 0xAAFF00;
	@SerialEntry
	public int pingWaypointColor = 0xff5900;
	@SerialEntry
	public int yOffset = 3;
}
