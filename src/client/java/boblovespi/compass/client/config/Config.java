package boblovespi.compass.client.config;

import boblovespi.compass.Compass;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;

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
	// compass bar
	@SerialEntry
	public DisplayMode requireCompassForCompassBar = DisplayMode.ALWAYS;
	@SerialEntry
	public int compassWaypointColor = 0xAAFF00;
	@SerialEntry
	public int pingWaypointColor = 0xff5900;
	@SerialEntry
	public SideDisplay leftSideDisplay = SideDisplay.POSITION;
	@SerialEntry
	public SideDisplay rightSideDisplay = SideDisplay.REAL_TIME;
	@SerialEntry
	public int yOffset = 3;
	@SerialEntry
	// markers
	public DisplayMode requireCompassForWaypointMarker = DisplayMode.ALWAYS;
	@SerialEntry
	public WaypointMode waypointMode = WaypointMode.PING;
	@SerialEntry
	public boolean sharePing = false;
	@SerialEntry
	public boolean suppressWaypointMessages = false;
	@SerialEntry
	public int markerRenderDistance = 200;
	// whitelist
	@SerialEntry
	public boolean enableWhitelist = true;
	@SerialEntry
	public List<String> whitelistNames = new ArrayList<>();
}
