package boblovespi.compass.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

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
}
