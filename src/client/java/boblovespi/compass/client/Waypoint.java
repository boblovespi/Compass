package boblovespi.compass.client;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;


public record Waypoint(ResourceKey<Level> level, Vec3 pos, int color)
{
}
