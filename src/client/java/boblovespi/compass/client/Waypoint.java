package boblovespi.compass.client;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;


public record Waypoint(ResourceKey<Level> level, Vec3 pos, int color)
{
	public String formatted(String name)
	{
		return String.format("waypoint,%s,%.0f,%.0f,%.0f,%s,0x%06x", name, pos.x, pos.y, pos.z, level.location(), color);
	}
}
