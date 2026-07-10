package boblovespi.compass.client;

import boblovespi.compass.Compass;
import boblovespi.compass.client.config.Config;
import boblovespi.compass.client.mixin.GameRendererAccessor;
import boblovespi.compass.client.utils.RenderUtils;
import boblovespi.compass.client.utils.Utils;
import boblovespi.compass.client.waypoint.Waypoint;
import boblovespi.compass.client.waypoint.WaypointManager;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;

public class CompassClient implements ClientModInitializer
{
	public static final int SECONDS_IN_DAY = 86400;
	public static final int TICKS_IN_DAY = 24000;
	private static final ResourceLocation compassPointer = Compass.id("textures/hud/compass_pointer.png");
	private static final ResourceLocation waypointMarker = Compass.id("textures/hud/waypoint_marker.png");
	private static final ResourceLocation waypointMarkerInner = Compass.id("textures/hud/waypoint_marker_inner.png");
	private static final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("h:mm a");
	private WaypointManager waypointManager;
	private String targetedWaypoint = ".compass";
	private boolean newPing = false;

	private final KeyMapping toggleWaypointKeybind = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.bob-compass.toggle_waypoint", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.bob-compass.category"));
	private final KeyMapping pingKeybind = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.bob-compass.add_waypoint", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, "key.bob-compass.category"));

	// private RenderType WAYPOINT_RENDER = RenderType.create("waypoint_render", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 786432, RenderType.CompositeState.builder().setShaderState(RENDERTYPE_GUI_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST).createCompositeState(false));

	@Override
	public void onInitializeClient()
	{
		HudRenderCallback.EVENT.register(this::drawHudElements);
		// WorldRenderEvents.LAST.register(this::drawWorldWaypoints);
		ClientCommandRegistrationCallback.EVENT.register((d, r) -> CommandRegisterer.registerCommands(waypointManager, d, r));
		ClientReceiveMessageEvents.CHAT.register(this::onChat);
		ClientReceiveMessageEvents.MODIFY_GAME.register(this::onServerChat);
		ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
		ClientPlayConnectionEvents.JOIN.register(this::onJoinNewWorld);
		ClientPlayConnectionEvents.DISCONNECT.register(this::onDisconnectWorld);
	}

	private void onDisconnectWorld(ClientPacketListener clientPacketListener, Minecraft minecraft)
	{
		waypointManager.saveWaypoints();
	}

	private void onJoinNewWorld(ClientPacketListener handler, PacketSender sender, Minecraft client)
	{
		System.out.println("joining new world!");
		waypointManager = WaypointManager.of();
	}

	private void onEndTick(Minecraft minecraft)
	{
		while (toggleWaypointKeybind.consumeClick())
		{
			targetedWaypoint = waypointManager.nextWaypoint(targetedWaypoint);
		}
		if (newPing)
		{
			newPing = false;
			if (minecraft.player != null)
			{
				var waypoint = waypointManager.getWaypoint(".ping");
				var myName = minecraft.getUser().getName();
				if (waypoint != null && Config.HANDLER.instance().sharePing)
					minecraft.player.connection.sendChat(waypoint.formatted(".ping" + myName));
				assert minecraft.level != null;
				minecraft.level.playSound(minecraft.player, minecraft.player, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1, 1);
			}
		}
	}

	private void onChat(Component message, @Nullable PlayerChatMessage signedMessage, @Nullable GameProfile sender, ChatType.Bound params, Instant receptionTimestamp)
	{
		var minecraft = Minecraft.getInstance();
		var myName = minecraft.getUser().getName();
		var sentFrom = sender == null ? null : sender.getName();
		var sentFromMe = myName.equals(sentFrom);
		if (!Config.HANDLER.instance().enableWhitelist || (sentFromMe || Config.HANDLER.instance().whitelistNames.contains(sentFrom)))
		{
			var messageStr = message.getString();
			var waypointIdx = messageStr.indexOf("waypoint");
			if (waypointIdx != -1)
			{
				messageStr = messageStr.substring(waypointIdx).strip();
				if (!messageStr.contains(" "))
					parseChat(sentFromMe, minecraft, messageStr);
			}
		}
	}

	private Component onServerChat(Component component, boolean overlay)
	{
		var minecraft = Minecraft.getInstance();
		if (overlay)
			return component;
		var myName = minecraft.getUser().getName();
		var message = component.getString();
		if (!Config.HANDLER.instance().enableWhitelist)
		{
			var waypointIdx = message.indexOf("waypoint");
			if (waypointIdx != -1)
			{
				var prefix = message.substring(0, waypointIdx);
				var sentFromMe = prefix.contains(myName);
				message = message.substring(waypointIdx).strip();
				if (!message.contains(" "))
					if (parseChat(sentFromMe, minecraft, message) && Config.HANDLER.instance().suppressWaypointMessages)
						return Component.translatable("bob-compass.chat.suppress_waypoint");
			}
		}
		return component;
	}

	private boolean parseChat(boolean sentFromMe, Minecraft minecraft, String message)
	{
		var waypointIdx = message.indexOf("waypoint");
		if (waypointIdx != -1)
		{
			message = message.substring(waypointIdx).strip();
			if (!message.contains(" "))
			{
				var components = message.split(",");
				System.out.println(Arrays.toString(components));
				if (components.length != 7)
					return false;
				if (!components[0].equals("waypoint"))
					return false;
				var name = components[1];
				if (sentFromMe && name.startsWith("."))
					return false;
				var x = Utils.tryParseInt(components[2], Integer::parseInt);
				if (x.isEmpty())
					return false;
				var y = Utils.tryParseInt(components[3], Integer::parseInt);
				if (y.isEmpty())
					return false;
				var z = Utils.tryParseInt(components[4], Integer::parseInt);
				if (z.isEmpty())
					return false;
				var level = components[5];
				var color = Utils.tryParseInt(components[6], Integer::decode);
				if (color.isEmpty())
					return false;
				var location = ResourceLocation.tryParse(level);
				if (location == null)
					return false;
				var pos = Vec3.atCenterOf(new Vec3i(x.getAsInt(), y.getAsInt(), z.getAsInt()));
				var waypoint = new Waypoint(ResourceKey.create(Registries.DIMENSION, location), pos, color.getAsInt());
				waypointManager.modifyWaypointAndSave(name, waypoint);
				if (name.startsWith(".ping") && minecraft.level != null)
					minecraft.level.playSound(minecraft.player, waypoint.pos().x, waypoint.pos().y, waypoint.pos().z, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1, 1);
				return true;
			}
		}
		return false;
	}

	private void drawHudElements(GuiGraphics graphics, float delta)
	{
		var minecraft = Minecraft.getInstance();
		if (minecraft.getDebugOverlay().showDebugScreen())
			return;
		var player = minecraft.player;
		if (player == null)
			return;
		var font = minecraft.font;
		var pos = player.getPosition(delta);
		var dir = (((player.getViewYRot(delta) + 180) % 360) + 360) % 360;

		// ping
		if (pingKeybind.consumeClick())
		{
			if (!removePingIfExists(delta, player))
			{
				var raycast = player.pick(100, delta, false);
				var loc = raycast.getType() == HitResult.Type.BLOCK ? Optional.of(raycast.getLocation()) : Optional.<Vec3>empty();
				var dist = 100.0;
				var view = player.getViewVector(delta);
				var eyePos = player.getEyePosition(delta);
				if (raycast.getType() != HitResult.Type.MISS)
					dist = raycast.getLocation().distanceTo(eyePos);
				var scaledView = view.scale(dist);
				var end = eyePos.add(scaledView);
				var aabb = player.getBoundingBox().expandTowards(scaledView).inflate(1);
				var entityRaycast = ProjectileUtil.getEntityHitResult(player, eyePos, end, aabb, e -> !e.isSpectator() && e.isPickable(), dist * dist);
				if (entityRaycast != null)
					loc = Optional.of(entityRaycast.getLocation());

				if (loc.isPresent())
				{
					waypointManager.modifyWaypointAndSave(".ping", new Waypoint(player.level().dimension(), loc.get(), Config.HANDLER.instance().pingWaypointColor));
					targetedWaypoint = ".ping";
					newPing = true;
				}
			}

		}
		// consume remaining clicks
		//noinspection StatementWithEmptyBody
		while (pingKeybind.consumeClick())
		{
		}

		// compass
		var addedCompass = false;
		var config = Config.HANDLER.instance();
		for (var item : player.getInventory().items)
		{
			if (item.is(Items.COMPASS))
			{
				var targetPos = CompassItem.isLodestoneCompass(item) ? CompassItem.getLodestonePosition(item.getTag()) : CompassItem.getSpawnPosition(player.level());
				if (targetPos == null)
					continue;
				waypointManager.modifyWaypoint(".compass", new Waypoint(targetPos.dimension(), targetPos.pos().getCenter(), config.compassWaypointColor));
				addedCompass = true;
				break;
			}
		}
		if (!addedCompass)
			waypointManager.removeWaypoint(".compass");

		// begin render waypoint
		var camera = minecraft.gameRenderer.getMainCamera();
		switch (config.requireCompassForWaypointMarker)
		{
			case ALWAYS -> renderWaypointMarkers(graphics, delta, config, minecraft, camera, pos, player, font);
			case REQUIRE_COMPASS_IN_HAND ->
			{
				if (player.getMainHandItem().is(ItemTags.COMPASSES) || player.getOffhandItem().is(ItemTags.COMPASSES))
					renderWaypointMarkers(graphics, delta, config, minecraft, camera, pos, player, font);
			}
			case REQUIRE_COMPASS_IN_INVENTORY ->
			{
				if (player.getInventory().contains(ItemTags.COMPASSES))
					renderWaypointMarkers(graphics, delta, config, minecraft, camera, pos, player, font);
			}
		}

		// begin render bar
		switch (config.requireCompassForCompassBar)
		{
			case ALWAYS -> renderCompassBar(graphics, config, dir, font, player, pos);
			case REQUIRE_COMPASS_IN_HAND ->
			{
				if (player.getMainHandItem().is(ItemTags.COMPASSES) || player.getOffhandItem().is(ItemTags.COMPASSES))
					renderCompassBar(graphics, config, dir, font, player, pos);
			}
			case REQUIRE_COMPASS_IN_INVENTORY ->
			{
				if (player.getInventory().contains(ItemTags.COMPASSES))
					renderCompassBar(graphics, config, dir, font, player, pos);
			}
		}

	}

	private void renderCompassBar(GuiGraphics graphics, Config config, float dir, Font font, LocalPlayer player, Vec3 pos)
	{
		var y = config.yOffset;

		var totalWidth = graphics.guiWidth() / 5 * 3 - 20;
		var halfWidth = totalWidth / 2;
		var start = graphics.guiWidth() / 2 - halfWidth;
		var center = start + halfWidth;
		var gradientLength = Math.min(totalWidth / 10, 20);
		// graphics.enableScissor(start, 14, start + totalWidth, 16);
		var rightString = switch (config.rightSideDisplay)
		{
			case NONE -> "";
			case POSITION -> String.format("%.0f (%.0f) %.0f", pos.x, pos.y, pos.z);
			case MINECRAFT_TIME -> LocalTime.ofSecondOfDay((player.level().getDayTime() * SECONDS_IN_DAY / TICKS_IN_DAY + SECONDS_IN_DAY / 4) % SECONDS_IN_DAY).format(timeFormat);
			case REAL_TIME -> LocalDateTime.now().format(timeFormat);
			case HEADING -> String.format("%.0f\u00b0", dir);
		};
		var rightWidth = switch (config.rightSideDisplay)
		{
			case NONE -> 20;
			case POSITION -> Math.max(60, font.width(rightString));
			case MINECRAFT_TIME, REAL_TIME, HEADING -> 60;
		};
		var leftString = switch (config.leftSideDisplay)
		{
			case NONE -> "";
			case POSITION -> String.format("%.0f (%.0f) %.0f", pos.x, pos.y, pos.z);
			case MINECRAFT_TIME -> LocalTime.ofSecondOfDay((player.level().getDayTime() * SECONDS_IN_DAY / TICKS_IN_DAY + SECONDS_IN_DAY / 4) % SECONDS_IN_DAY).format(timeFormat);
			case REAL_TIME -> LocalDateTime.now().format(timeFormat);
			case HEADING -> String.format("%.0f\u00b0", dir);
		};
		var realLeftWidth = font.width(leftString);
		var leftWidth = switch (config.leftSideDisplay)
		{
			case NONE -> 20;
			case POSITION -> Math.max(60, realLeftWidth);
			case MINECRAFT_TIME, REAL_TIME, HEADING -> 60;
		};

		// top bg
		graphics.fill(start + gradientLength, y, start + totalWidth - gradientLength, y + 11, 0x40707070);
		RenderUtils.fillGradientHorizontal(graphics, start, y, start + gradientLength, y + 11, 0x00707070, 0x40707070);
		RenderUtils.fillGradientHorizontal(graphics, start + totalWidth - gradientLength, y, start + totalWidth, y + 11, 0x40707070, 0x00707070);

		graphics.fill(center - leftWidth, y + 13, center + rightWidth, y + 22, 0x40707070);
		RenderUtils.fillGradientHorizontal(graphics, center - leftWidth - gradientLength, y + 11 + 2, center - leftWidth, y + 22, 0x00707070, 0x40707070);
		RenderUtils.fillGradientHorizontal(graphics, center + rightWidth, y + 13, center + rightWidth + gradientLength, y + 22, 0x40707070, 0x00707070);

		// top markers
		var nearest15 = ((int) dir) / 15 * 15;
		for (var the15 = nearest15; the15 >= dir - 44; the15 -= 15)
		{
			var x = (int) ((the15 - dir) / 90f * totalWidth);
			var alpha = x + halfWidth > gradientLength ? 0xFF : (x + halfWidth) * 0xFF / gradientLength;
			alpha <<= 24;
			var str = toCompassDir(the15);
			RenderUtils.drawCenteredStr(graphics, font, str, center + x, y + 2, 0xDDDDDD | alpha, false);
		}
		for (var the15 = nearest15 + 15; the15 <= dir + 44; the15 += 15)
		{
			var x = (int) ((the15 - dir) / 90f * totalWidth);
			var alpha = halfWidth - x > gradientLength ? 0xFF : (halfWidth - x) * 0xFF / gradientLength;
			alpha <<= 24;
			var str = toCompassDir(the15);
			RenderUtils.drawCenteredStr(graphics, font, str, center + x, y + 2, 0xDDDDDD | alpha, false);
		}

		// waypoints
		waypointManager.forEach((name, waypoint) -> {
			if (!player.level().dimension().equals(waypoint.level()))
				return;
			var targetDir = Mth.wrapDegrees(Math.toDegrees(Mth.atan2(pos.x - waypoint.pos().x, waypoint.pos().z - pos.z))) + 180;
			var x = (int) (Mth.wrapDegrees(targetDir - dir) / 90f * totalWidth);
			var y2 = y + 14;
			if (name.equals(targetedWaypoint))
			{
				y2 = y + 25;
				var distance = pos.distanceTo(waypoint.pos());
				var str = "";
				var realX = 0;
				if (-x >= halfWidth - gradientLength)
				{
					realX = start + gradientLength;
					str = String.format("< %.0fm", distance);
				}
				else if (x >= halfWidth - gradientLength)
				{
					realX = start + totalWidth - gradientLength;
					str = String.format("%.0fm >", distance);
				}
				else
				{
					str = String.format("%.0fm", distance);
					realX = center + x;
				}
				RenderUtils.drawCenteredStr(graphics, font, str, realX, y2, waypoint.color(), true);
				if (!name.startsWith("."))
					RenderUtils.drawCenteredStr(graphics, font, name, realX, y2 + 10, waypoint.color(), true);
			}
			if (x <= halfWidth && x >= -halfWidth)
			{
				var alpha = halfWidth - Math.abs(x) > gradientLength ? 0xFF : (halfWidth - Math.abs(x)) * 0xFF / gradientLength;
				alpha <<= 24;
				graphics.fillGradient(center + x - 1, y + 3, center + x + 1, y2 - 1, waypoint.color(), waypoint.color() | alpha);
			}
		});

		// line
		graphics.fill(start + gradientLength, y + 11, center - 4, y + 11 + 2, 0xFFDDDDDD);
		graphics.fill(center + 4, y + 11, start + totalWidth - gradientLength, y + 11 + 2, 0xFFDDDDDD);
		RenderUtils.fillGradientHorizontal(graphics, start, y + 11, start + gradientLength, y + 11 + 2, 0x00DDDDDD, 0xFFDDDDDD);
		RenderUtils.fillGradientHorizontal(graphics, start + totalWidth - gradientLength, y + 11, start + totalWidth, y + 11 + 2, 0xFFDDDDDD, 0x00DDDDDD);
		graphics.blit(compassPointer, center - 8, y + 11, 16, 6, 0, 0, 16, 6, 16, 16);

		// coords, time
		graphics.drawString(font, rightString, center + 8, y + 14, 0xDDDDDD, false);
		graphics.drawString(font, leftString, center - realLeftWidth - 8, y + 14, 0xDDDDDD, false);
	}

	private void renderWaypointMarkers(GuiGraphics graphics, float delta, Config config, Minecraft minecraft, Camera camera, Vec3 pos, LocalPlayer player, Font font)
	{
		switch (config.waypointMode)
		{
			case NEVER ->
			{
			}
			case PING ->
			{
				var accessor = (GameRendererAccessor) minecraft.gameRenderer;
				var fov = accessor.callGetFov(camera, delta, true);
				var stack2 = new PoseStack();
				stack2.mulPoseMatrix(minecraft.gameRenderer.getProjectionMatrix(fov));
				// accessor.callBobHurt(stack2, delta);
				// accessor.callBobView(stack2, delta);
				waypointManager.forEach((n, ping) -> {
					var dist = pos.distanceToSqr(ping.pos());
					if (dist >= Mth.square((float) Config.HANDLER.instance().markerRenderDistance))
						return;
					if (!player.level().dimension().equals(ping.level()))
						return;
					if (!n.startsWith(".ping"))
						return;
					var name = n.substring(5);
					renderMarker(graphics, minecraft, ping, name, font, stack2.last().pose(), camera);
				});
			}
			case WAYPOINT ->
			{
				var accessor = (GameRendererAccessor) minecraft.gameRenderer;
				var fov = accessor.callGetFov(camera, delta, true);
				var stack2 = new PoseStack();
				stack2.mulPoseMatrix(minecraft.gameRenderer.getProjectionMatrix(fov));
				// accessor.callBobHurt(stack2, delta);
				// accessor.callBobView(stack2, delta);
				var ping = waypointManager.getWaypoint(targetedWaypoint);
				if (ping == null)
					break;
				var dist = pos.distanceToSqr(ping.pos());
				if (dist >= Mth.square((float) Config.HANDLER.instance().markerRenderDistance))
					break;
				if (!player.level().dimension().equals(ping.level()))
					break;
				renderMarker(graphics, minecraft, ping, "", font, stack2.last().pose(), camera);
			}
			case BOTH ->
			{
				var accessor = (GameRendererAccessor) minecraft.gameRenderer;
				var fov = accessor.callGetFov(camera, delta, true);
				var stack2 = new PoseStack();
				stack2.mulPoseMatrix(minecraft.gameRenderer.getProjectionMatrix(fov));
				// accessor.callBobHurt(stack2, delta);
				// accessor.callBobView(stack2, delta);
				waypointManager.forEach((n, ping) -> {
					var dist = pos.distanceToSqr(ping.pos());
					if (dist >= Mth.square((float) Config.HANDLER.instance().markerRenderDistance))
						return;
					if (!player.level().dimension().equals(ping.level()))
						return;
					if (!n.startsWith(".ping"))
						return;
					var name = n.substring(5);
					renderMarker(graphics, minecraft, ping, name, font, stack2.last().pose(), camera);
				});
				var ping = waypointManager.getWaypoint(targetedWaypoint);
				if (ping == null)
					break;
				var dist = pos.distanceToSqr(ping.pos());
				if (dist >= Mth.square((float) Config.HANDLER.instance().markerRenderDistance))
					break;
				if (!player.level().dimension().equals(ping.level()))
					break;
				renderMarker(graphics, minecraft, ping, "", font, stack2.last().pose(), camera);
			}
		}
	}

	private void renderMarker(GuiGraphics graphics, Minecraft minecraft, Waypoint ping, String name, Font font, Matrix4f cameraProjection, Camera camera)
	{
		var offset = ping.pos().subtract(camera.getPosition());
		if (camera.getLookVector().dot((float) offset.x, (float) offset.y, (float) offset.z) <= 0)
			return;
		var cameraCoords = camera.rotation().transformInverse(offset.toVector3f());
		var viewPos = new Vector4f();
		cameraProjection.transform(cameraCoords.x, cameraCoords.y, cameraCoords.z, 1, viewPos);
		viewPos.div(viewPos.w);
		var sx = (viewPos.x * 0.5f + 0.5f) * graphics.guiWidth();
		var sy = (viewPos.y * 0.5f + 0.5f) * graphics.guiHeight();
		var stack = graphics.pose();
		stack.pushPose();
		stack.translate(sx, sy, -2);
		var scale = switch (minecraft.options.guiScale().get())
		{
			case 1 -> 1f;
			case 2 -> 1f;
			case 3 -> 2f / 3f;
			case 4 -> 0.5f;
			case 5 -> 3f / 5f;
			default -> 0.25f;
		};
		stack.scale(scale, scale, scale);
		RenderUtils.blitColoredSprite(graphics, waypointMarkerInner, -8, 8, -8, 8, 0, 0, 0, 16, 16, 16, 16, ping.color() | 0xFF000000);
		graphics.blit(waypointMarker, -8, -8, 16, 16, 0, 0, 16, 16, 16, 16);
		if (!name.isEmpty())
			RenderUtils.drawCenteredStr(graphics, font, name, 0, 8, ping.color(), true);
		stack.popPose();
	}

	private boolean removePingIfExists(float delta, LocalPlayer player)
	{
		return waypointManager.removeIf((n, ping) -> {
			if (!n.startsWith(".ping"))
				return false;
			if (!player.level().dimension().equals(ping.level()))
				return false;
			var viewVec = player.getViewVector(delta);
			var pingVec = ping.pos().subtract(player.getEyePosition(delta)).normalize();
			// 0.996194698 = cosine of 5 degrees
			return viewVec.dot(pingVec) >= 0.996194698;
		});
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

}