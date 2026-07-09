package boblovespi.compass.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(net.minecraft.client.renderer.GameRenderer.class)
public interface GameRendererAccessor
{
	@Invoker
	double callGetFov(Camera camera, float delta, boolean idk);

	@Invoker
	void callBobHurt(PoseStack poseStack, float f);

	@Invoker
	void callBobView(PoseStack poseStack, float f);
}
