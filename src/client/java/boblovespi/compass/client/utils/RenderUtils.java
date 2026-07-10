package boblovespi.compass.client.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

public class RenderUtils
{
	public static void blitColoredSprite(GuiGraphics graphics, ResourceLocation texture, int x, int x2, int y, int y2, int z, int u, int v, int w, int h, int tw, int th, int color)
	{
		RenderSystem.setShaderTexture(0, texture);
		RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
		RenderSystem.enableBlend();
		var matrix4f = graphics.pose().last().pose();
		var bufferBuilder = Tesselator.getInstance().getBuilder();
		bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
		var u1 = 1f * u / tw;
		var u2 = 1f * (u + w) / tw;
		var v1 = 1f * v / th;
		var v2 = 1f * (v + h) / th;
		bufferBuilder.vertex(matrix4f, (float) x, (float) y, (float) z).color(color).uv(u1, v1).endVertex();
		bufferBuilder.vertex(matrix4f, (float) x, (float) y2, (float) z).color(color).uv(u1, v2).endVertex();
		bufferBuilder.vertex(matrix4f, (float) x2, (float) y2, (float) z).color(color).uv(u2, v2).endVertex();
		bufferBuilder.vertex(matrix4f, (float) x2, (float) y, (float) z).color(color).uv(u2, v1).endVertex();
		BufferUploader.drawWithShader(bufferBuilder.end());
		RenderSystem.disableBlend();
	}

	public static void fillGradientHorizontal(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color1, int color2)
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

	public static void drawCenteredStr(GuiGraphics graphics, Font font, String str, int x, int y, int color, boolean drawBackground)
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
