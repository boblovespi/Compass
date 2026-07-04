package boblovespi.compass.client;

import boblovespi.compass.Compass;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WaypointManager
{
	private final Path savePath;
	private final Map<String, Waypoint> waypoints = new LinkedHashMap<>();

	public static WaypointManager of()
	{
		var minecraft = Minecraft.getInstance();
		var gameDir = minecraft.gameDirectory.toPath();
		var dataDir = gameDir.resolve("compass");
		var currentServer = minecraft.getCurrentServer();
		if (currentServer != null)
		{
			dataDir = dataDir.resolve("server");
			dataDir = dataDir.resolve(String.format("%s_%s", currentServer.name, currentServer.ip.replace('.', '_').replace(':', '_')));
		}
		else
		{
			dataDir = dataDir.resolve("local");
			if (minecraft.getSingleplayerServer() == null)
			{
				Compass.LOGGER.error("tried making a singleplayer save for not a singleplayer server");
				throw new AssertionError();
			}
			dataDir = dataDir.resolve(minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).getParent().getFileName());
		}
		var saveFile = dataDir.resolve("data.csv");
		var manager = new WaypointManager(saveFile);
		if (Files.exists(saveFile))
		{
			try
			{
				var csv = Files.readAllLines(saveFile);
				csv.forEach(s -> {
					var line = s.split(",");
					if (line.length != 6)
					{
						Compass.LOGGER.warn("failed to parse line {} of file {}", s, saveFile);
						return;
					}
					var name = line[0];
					var x = Utils.tryParseFloat(line[1], Float::parseFloat);
					if (x.isEmpty())
					{
						Compass.LOGGER.warn("failed to parse x of line {} of file {}", s, saveFile);
						return;
					}
					var y = Utils.tryParseFloat(line[2], Float::parseFloat);
					if (y.isEmpty())
					{
						Compass.LOGGER.warn("failed to parse y of line {} of file {}", s, saveFile);
						return;
					}
					var z = Utils.tryParseFloat(line[3], Float::parseFloat);
					if (z.isEmpty())
					{
						Compass.LOGGER.warn("failed to parse z of line {} of file {}", s, saveFile);
						return;
					}
					var dim = ResourceLocation.tryParse(line[4]);
					if (dim == null)
					{
						Compass.LOGGER.warn("failed to parse dim of line {} of file {}", s, saveFile);
						return;
					}
					var color = Utils.tryParseInt(line[5], Integer::decode);
					if (color.isEmpty())
					{
						Compass.LOGGER.warn("failed to parse color of line {} of file {}", s, saveFile);
						return;
					}
					manager.modifyWaypoint(name, new Waypoint(ResourceKey.create(Registries.DIMENSION, dim), new Vec3(x.get(), y.get(), z.get()), color.getAsInt()));
				});
			}
			catch (IOException e)
			{
				Compass.LOGGER.error("failed to read csv from {}", saveFile);
				Compass.LOGGER.error(e.toString());
			}
		}
		return manager;
	}

	private WaypointManager(Path savePath)
	{
		this.savePath = savePath;
	}

	public void removeWaypoint(String name)
	{
		waypoints.remove(name);
	}

	public void modifyWaypoint(String name, Waypoint waypoint)
	{
		waypoints.put(name, waypoint);
	}

	public void modifyWaypointAndSave(String name, Waypoint waypoint)
	{
		modifyWaypoint(name, waypoint);
		saveWaypoints();
	}

	public void saveWaypoints()
	{
		try
		{
			Files.createDirectories(savePath.getParent());
			Files.writeString(savePath, waypoints.entrySet().stream().filter(e -> !e.getKey().startsWith(".")).map(e -> {
				var n = e.getKey();
				var w = e.getValue();
				return String.format("%s,%f,%f,%f,%s,0x%06x", n, w.pos().x, w.pos().y, w.pos().z, w.level().location(), w.color());
			}).collect(Collectors.joining("\n")));
		}
		catch (IOException e)
		{
			Compass.LOGGER.error("failed to save csv to {}", savePath);
			Compass.LOGGER.error(e.toString());
		}
	}

	public String nextWaypoint(String currentWaypoint)
	{
		return waypoints.keySet().stream().dropWhile(s -> !s.equals(currentWaypoint)).skip(1).findFirst().or(() -> waypoints.keySet().stream().findFirst()).orElse("");
	}

	public void forEach(BiConsumer<String, Waypoint> f)
	{
		waypoints.forEach(f);
	}

	public void clearAllWaypoints()
	{
		waypoints.clear();
		saveWaypoints();
	}

	public Stream<String> streamNames()
	{
		return waypoints.keySet().stream();
	}

	@Nullable
	public Waypoint getWaypoint(String name)
	{
		return waypoints.get(name);
	}
}
