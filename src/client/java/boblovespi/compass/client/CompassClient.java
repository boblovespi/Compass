package boblovespi.compass.client;

import boblovespi.compass.Compass;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.Items;

public class CompassClient implements ClientModInitializer
{
	private static final ResourceLocation compassPointer = Compass.id("textures/hud/compass_pointer.png");

	@Override
	public void onInitializeClient()
	{
		HudRenderCallback.EVENT.register(this::drawHudElements);
	}

	private void drawHudElements(GuiGraphics graphics, float delta)
	{
		if (Minecraft.getInstance().getDebugOverlay().showDebugScreen())
			return;
		var player = Minecraft.getInstance().player;
		if (player == null)
			return;
		var font = Minecraft.getInstance().font;
		var pos = player.getPosition(delta);
		var dir = (((player.getViewYRot(delta) + 180) % 360) + 360) % 360;

		var totalWidth = graphics.guiWidth() / 5 * 3 - 20;
		var halfWidth = totalWidth / 2;
		var start = graphics.guiWidth() / 2 - halfWidth;
		var center = start + halfWidth;
		var gradientLength = Math.min(totalWidth / 10, 20);
		// graphics.enableScissor(start, 14, start + totalWidth, 16);

		// top bg
		graphics.fill(start + gradientLength, 3, start + totalWidth - gradientLength, 14, 0x40707070);
		fillGradientHorizontal(graphics, start, 3, start + gradientLength, 14, 0x00707070, 0x40707070);
		fillGradientHorizontal(graphics, start + totalWidth - gradientLength, 3, start + totalWidth, 14, 0x40707070, 0x00707070);

		// top markers
		var nearest15 = ((int) dir) / 15 * 15;
		for (var the15 = nearest15; the15 >= dir - 44; the15 -= 15)
		{
			var x = (int) ((the15 - dir) / 90f * totalWidth);
			var alpha = x + halfWidth > gradientLength ? 0xFF : (x + halfWidth) * 0xFF / gradientLength;
			alpha <<= 24;
			var str = toCompassDir(the15);
			drawCenteredStr(graphics, font, str, center + x, 5, 0xDDDDDD | alpha, false);
		}
		for (var the15 = nearest15 + 15; the15 <= dir + 44; the15 += 15)
		{
			var x = (int) ((the15 - dir) / 90f * totalWidth);
			var alpha = halfWidth - x > gradientLength ? 0xFF : (halfWidth - x) * 0xFF / gradientLength;
			alpha <<= 24;
			var str = toCompassDir(the15);
			drawCenteredStr(graphics, font, str, center + x, 5, 0xDDDDDD | alpha, false);
		}

		// compass
		for (var item : player.getInventory().items)
		{
			if (item.is(Items.COMPASS))
			{
				var targetPos = CompassItem.isLodestoneCompass(item) ? CompassItem.getLodestonePosition(item.getTag()) : CompassItem.getSpawnPosition(player.level());
				if (targetPos == null)
					continue;
				var targetPosCenter = targetPos.pos().getCenter();
				var distance = pos.distanceTo(targetPosCenter);
				var targetDir = Mth.wrapDegrees(Math.toDegrees(Mth.atan2(pos.x - targetPosCenter.x, targetPosCenter.z - pos.z))) + 180;
				var x = (int) (Mth.wrapDegrees(targetDir - dir) / 90f * totalWidth);
				if (-x >= halfWidth - gradientLength)
				{
					var str = String.format("< %.0fm", distance);
					drawCenteredStr(graphics, font, str, start + gradientLength, 21, 0xAAFF00, true);
				}
				else if (x >= halfWidth - gradientLength)
				{
					var str = String.format("%.0fm >", distance);
					drawCenteredStr(graphics, font, str, start + totalWidth - gradientLength, 21, 0xAAFF00, true);
				}
				else
				{
					var str = String.format("%.0fm", distance);
					drawCenteredStr(graphics, font, str, center + x, 21, 0xAAFF00, true);
				}
				if (x <= halfWidth && x >= -halfWidth)
				{
					var alpha = halfWidth - Math.abs(x) > gradientLength ? 0xFF : (halfWidth - Math.abs(x)) * 0xFF / gradientLength;
					alpha <<= 24;
					graphics.fillGradient(center + x - 1, 8, center + x + 1, 20, 0x00AAFF00, 0xAAFF00 | alpha);
				}
			}
		}

		// line
		graphics.fill(start + gradientLength, 14, center - 4, 16, 0xFFDDDDDD);
		graphics.fill(center + 4, 14, start + totalWidth - gradientLength, 16, 0xFFDDDDDD);
		fillGradientHorizontal(graphics, start, 14, start + gradientLength, 16, 0x00DDDDDD, 0xFFDDDDDD);
		fillGradientHorizontal(graphics, start + totalWidth - gradientLength, 14, start + totalWidth, 16, 0xFFDDDDDD, 0x00DDDDDD);
		graphics.blit(compassPointer, center - 8, 14, 16, 6, 0, 0, 16, 6, 16, 16);

		graphics.drawString(font, String.format("%.0f %.0f (%.0f)", pos.x, pos.z, pos.y), 10, 10, 0xFFFFFF, false);
		graphics.drawString(font, String.format("%.0f", dir), 50, 50, 0xFFFFFF, false);
	}

	private void fillGradientHorizontal(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color1, int color2)
	{
		var vertexConsumer = graphics.bufferSource().getBuffer(RenderType.gui());
		var z = 0;
		var a1 = (float) FastColor.ARGB32.alpha(color1) / 255.0F;
		var r1 = (float) FastColor.ARGB32.red(color1) / 255.0F;
		var g1 = (float) FastColor.ARGB32.green(color1) / 255.0F;
		var b1 = (float) FastColor.ARGB32.blue(color1) / 255.0F;
		var a2 = (float) FastColor.ARGB32.alpha(color2) / 255.0F;
		var r2 = (float) FastColor.ARGB32.red(color2) / 255.0F;
		var g2 = (float) FastColor.ARGB32.green(color2) / 255.0F;
		var b2 = (float) FastColor.ARGB32.blue(color2) / 255.0F;
		var matrix4f = graphics.pose().last().pose();
		vertexConsumer.vertex(matrix4f, (float) x1, (float) y1, (float) z).color(r1, g1, b1, a1).endVertex();
		vertexConsumer.vertex(matrix4f, (float) x1, (float) y2, (float) z).color(r1, g1, b1, a1).endVertex();
		vertexConsumer.vertex(matrix4f, (float) x2, (float) y2, (float) z).color(r2, g2, b2, a2).endVertex();
		vertexConsumer.vertex(matrix4f, (float) x2, (float) y1, (float) z).color(r2, g2, b2, a2).endVertex();
	}

	private String toCompassDir(int the15)
	{
		the15 = (the15 + 360) % 360;
		return switch (the15)
		{
			case 0 -> "N";
			case 45 -> "NE";
			case 90 -> "E";
			case 135 -> "SE";
			case 180 -> "S";
			case 225 -> "SW";
			case 270 -> "W";
			case 315 -> "NW";
			default -> the15 + "";
		};
	}

	private void drawCenteredStr(GuiGraphics graphics, Font font, String str, int x, int y, int color, boolean drawBackground)
	{
		var width = font.width(str);
		var height = font.lineHeight;
		if (drawBackground)
		{
			fillGradientHorizontal(graphics, x - width / 2 - 4, y - 1, x - width / 2, y + height, 0x00707070, 0x40707070);
			fillGradientHorizontal(graphics, x + width / 2, y - 1, x + width / 2 + 4, y + height, 0x40707070, 0x00707070);
			graphics.fill(x - width / 2, y - 1, x + width / 2, y + height, 0x40707070);
		}
		graphics.drawString(font, str, x - width / 2, y, color, false);
	}
}