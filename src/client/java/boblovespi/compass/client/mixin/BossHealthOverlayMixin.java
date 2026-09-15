package boblovespi.compass.client.mixin;

import boblovespi.compass.client.config.Config;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.tags.ItemTags;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin
{
	@Shadow
	@Final
	private Minecraft minecraft;

	@ModifyExpressionValue(at = @At(value = "CONSTANT", args = "intValue=12", ordinal = 0), method = "render(Lnet/minecraft/client/gui/GuiGraphics;)V")
	private int renderChangeYPos(int original)
	{
		var moveDown = switch (Config.HANDLER.instance().requireCompassForCompassBar)
		{
			case ALWAYS -> true;
			case REQUIRE_COMPASS_IN_HAND -> this.minecraft.player.getMainHandItem().is(ItemTags.COMPASSES) || this.minecraft.player.getOffhandItem().is(ItemTags.COMPASSES);
			case REQUIRE_COMPASS_IN_INVENTORY -> this.minecraft.player.getInventory().contains(ItemTags.COMPASSES);
		};
		if (moveDown)
			return original + Config.HANDLER.instance().yOffset + 25 + 11 + 6;
		return original;
	}
}
