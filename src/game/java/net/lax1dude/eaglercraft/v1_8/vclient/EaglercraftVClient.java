package net.lax1dude.eaglercraft.v1_8.vclient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.lax1dude.eaglercraft.v1_8.Keyboard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.MathHelper;

/**
 * Stable browser-client layer for EaglercraftV. All modules are client-side and
 * isolated so experimental HUD/UI features cannot break EaglercraftX gameplay,
 * networking, or world compatibility.
 */
public final class EaglercraftVClient {

	public static final String CLIENT_NAME = "EaglercraftV";
	public static final String STARTUP_TEXT = "Velocity Initialized";
	public static final int COLOR_PURPLE = 0xFF8B5CFF;
	public static final int COLOR_NEON = 0xFFC65BFF;
	public static final int COLOR_PANEL = 0xCC11111A;
	private static final int ERROR_LIMIT = 3;
	private static final int GRAPH_SIZE = 96;
	private static final int GRID_SIZE = 4;

	private static final EaglercraftVClient INSTANCE = new EaglercraftVClient();

	private final List<VModule> modules = new ArrayList();
	private final float[] frameGraph = new float[GRAPH_SIZE];
	private Theme theme = Theme.NEON_PURPLE;
	private FpsPreset fpsPreset = FpsPreset.BALANCED;
	private boolean chromebookMode = false;
	private boolean lowMemoryMode = false;
	private boolean turboWizardApplied = false;
	private boolean panicMode = false;
	private boolean cosmeticsInFpsMode = false;
	private boolean integrityChecked = false;
	private String cape = "None";
	private String hat = "None";
	private String bandana = "None";
	private String trail = "None";
	private String activeProfile = "Minimal";
	private long startupTime = System.currentTimeMillis();
	private long lastFrameSample = System.currentTimeMillis();
	private long lastCleanup = System.currentTimeMillis();
	private long usedMemoryEstimate;
	private int graphCursor;
	private int lastRenderDistance = 4;
	private float adaptiveRenderScale = 1.0F;

	private EaglercraftVClient() {
		registerDefaults();
		loadLocalProfile();
	}

	public static EaglercraftVClient getInstance() {
		return INSTANCE;
	}

	public List<VModule> getModules() {
		return Collections.unmodifiableList(modules);
	}

	public Theme getTheme() {
		return theme;
	}

	public void nextTheme() {
		Theme[] values = Theme.values();
		theme = values[(theme.ordinal() + 1) % values.length];
		saveLocalProfile();
	}

	public FpsPreset getFpsPreset() {
		return fpsPreset;
	}

	public boolean isChromebookMode() {
		return chromebookMode;
	}

	public boolean isLowMemoryMode() {
		return lowMemoryMode;
	}

	public boolean isPanicMode() {
		return panicMode;
	}

	public float getAdaptiveRenderScale() {
		return adaptiveRenderScale;
	}

	public float getAverageFrameMs() {
		float total = 0.0F;
		int samples = 0;
		for (int i = 0; i < frameGraph.length; ++i) {
			if (frameGraph[i] > 0.0F) {
				total += frameGraph[i];
				samples++;
			}
		}
		return samples == 0 ? 16.0F : total / (float) samples;
	}

	public String getPerformanceSummary() {
		float avg = getAverageFrameMs();
		int fps = avg <= 0.0F ? 0 : (int) (1000.0F / avg);
		return "FPS~" + fps + "  Scale " + (int) (adaptiveRenderScale * 100.0F) + "%  Mem " + (usedMemoryEstimate >> 20) + "MB";
	}

	public void cycleFpsPreset(Minecraft mc) {
		FpsPreset[] values = FpsPreset.values();
		fpsPreset = values[(fpsPreset.ordinal() + 1) % values.length];
		applyPreset(mc, fpsPreset, false);
	}

	public void runTurboWizard(Minecraft mc) {
		boolean lowEnd = detectLowEndDevice(mc);
		applyPreset(mc, lowEnd ? FpsPreset.PERFORMANCE : FpsPreset.BALANCED, lowEnd);
		turboWizardApplied = true;
		saveLocalProfile();
	}

	public boolean hasTurboWizardApplied() {
		return turboWizardApplied;
	}

	public String getCosmeticSummary() {
		return "Cape: " + cape + "  Hat: " + hat + "  Bandana: " + bandana + "  Trail: " + trail;
	}

	public String getActiveProfile() {
		return activeProfile;
	}

	public void toggleModule(VModule module) {
		if (module == null || module.autoDisabled) {
			return;
		}
		if (!module.enabled && !dependenciesEnabled(module)) {
			return;
		}
		module.enabled = !module.enabled;
		saveLocalProfile();
	}

	public void toggleFavorite(VModule module) {
		if (module != null) {
			module.favorite = !module.favorite;
			saveLocalProfile();
		}
	}

	public void saveLocalProfile() {
		StringBuilder builder = new StringBuilder();
		builder.append("theme=").append(theme.name()).append('\n');
		builder.append("fps=").append(fpsPreset.name()).append('\n');
		builder.append("chromebook=").append(chromebookMode).append('\n');
		builder.append("lowMemory=").append(lowMemoryMode).append('\n');
		builder.append("cosmeticsInFps=").append(cosmeticsInFpsMode).append('\n');
		builder.append("profile=").append(activeProfile).append('\n');
		builder.append("cape=").append(cape).append('\n');
		builder.append("hat=").append(hat).append('\n');
		builder.append("bandana=").append(bandana).append('\n');
		builder.append("trail=").append(trail).append('\n');
		for (VModule module : modules) {
			builder.append("module.").append(module.name).append('=').append(module.enabled).append('\n');
			builder.append("favorite.").append(module.name).append('=').append(module.favorite).append('\n');
			builder.append("hud.").append(module.name).append('=').append(module.anchor.name()).append(',').append(module.x).append(',')
					.append(module.y).append(',').append(module.scale).append('\n');
		}
		EagRuntime.setStorage("v", builder.toString().getBytes());
	}

	private void loadLocalProfile() {
		byte[] data = EagRuntime.getStorage("v");
		if (data == null || data.length == 0) {
			return;
		}
		String[] lines = new String(data).split("\n");
		for (String line : lines) {
			int eq = line.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String key = line.substring(0, eq);
			String value = line.substring(eq + 1);
			if ("theme".equals(key)) {
				theme = parseTheme(value, theme);
			} else if ("fps".equals(key)) {
				fpsPreset = parsePreset(value, fpsPreset);
			} else if ("chromebook".equals(key)) {
				chromebookMode = Boolean.parseBoolean(value);
			} else if ("lowMemory".equals(key)) {
				lowMemoryMode = Boolean.parseBoolean(value);
			} else if ("cosmeticsInFps".equals(key)) {
				cosmeticsInFpsMode = Boolean.parseBoolean(value);
			} else if ("profile".equals(key)) {
				activeProfile = value;
			} else if ("cape".equals(key)) {
				cape = value;
			} else if ("hat".equals(key)) {
				hat = value;
			} else if ("bandana".equals(key)) {
				bandana = value;
			} else if ("trail".equals(key)) {
				trail = value;
			} else if (key.startsWith("module.")) {
				VModule module = findModule(key.substring(7));
				if (module != null) {
					module.enabled = Boolean.parseBoolean(value);
				}
			} else if (key.startsWith("favorite.")) {
				VModule module = findModule(key.substring(9));
				if (module != null) {
					module.favorite = Boolean.parseBoolean(value);
				}
			} else if (key.startsWith("hud.")) {
				VModule module = findModule(key.substring(4));
				if (module != null) {
					loadHudState(module, value);
				}
			}
		}
	}

	private void loadHudState(VModule module, String value) {
		String[] parts = value.split(",");
		if (parts.length >= 4) {
			module.anchor = parseAnchor(parts[0], module.anchor);
			module.x = parseInt(parts[1], module.x);
			module.y = parseInt(parts[2], module.y);
			module.scale = Math.max(0.5F, Math.min(2.0F, parseFloat(parts[3], module.scale)));
		}
	}

	private Theme parseTheme(String value, Theme fallback) {
		for (Theme candidate : Theme.values()) {
			if (candidate.name().equals(value)) {
				return candidate;
			}
		}
		return fallback;
	}

	private FpsPreset parsePreset(String value, FpsPreset fallback) {
		for (FpsPreset candidate : FpsPreset.values()) {
			if (candidate.name().equals(value)) {
				return candidate;
			}
		}
		return fallback;
	}

	private Anchor parseAnchor(String value, Anchor fallback) {
		for (Anchor candidate : Anchor.values()) {
			if (candidate.name().equals(value)) {
				return candidate;
			}
		}
		return fallback;
	}

	private int parseInt(String value, int fallback) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	private float parseFloat(String value, float fallback) {
		try {
			return Float.parseFloat(value);
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	public void applyModuleProfile(String profile) {
		String normalized = profile == null ? "" : profile.toLowerCase(Locale.ROOT);
		for (VModule module : modules) {
			module.enabled = false;
		}
		if ("pvp".equals(normalized)) {
			activeProfile = "PvP";
			enable("Keystrokes", "CPS Counter", "Combo Counter", "Armor HUD", "Potion HUD", "Toggle Sprint", "Zoom Mod",
					"Hit Particles", "Hit Color", "Crosshair Editor", "Direction HUD", "Ping Display", "Reach Display",
					"Minimal Fire", "Block Overlay");
		} else if ("survival".equals(normalized)) {
			activeProfile = "Survival";
			enable("Minimap", "Waypoints", "Coordinates HUD", "Item Durability Viewer", "Light Level Viewer", "Better Chat",
					"Recipe Helper", "Chat Timestamps");
		} else if ("creative".equals(normalized)) {
			activeProfile = "Creative";
			enable("Coordinates HUD", "Hotbar Presets", "Block Palette Menu", "Structure Saver", "Fast Inventory Search",
					"Perspective/Freelook");
		} else if ("school safe".equals(normalized)) {
			activeProfile = "School Safe";
			theme = Theme.SCHOOL_SAFE;
			enable("Keystrokes", "CPS Counter", "Coordinates HUD", "Ping Display");
		} else {
			activeProfile = "Minimal";
			enable("Keystrokes", "CPS Counter", "Coordinates HUD", "Ping Display");
		}
		saveLocalProfile();
	}

	public void renderHud(Minecraft mc, ScaledResolution sr) {
		if (mc == null || mc.gameSettings == null || mc.gameSettings.hideGUI) {
			return;
		}
		long hudStart = System.currentTimeMillis();
		int accent = theme.accentColor;
		int topLeftY = GRID_SIZE * 2;
		int topCenterY = GRID_SIZE * 2;
		List<VModule> visible = new ArrayList();
		for (VModule module : modules) {
			if (module.enabled && module.hud && isVisible(module, mc) && dependenciesEnabled(module)) {
				visible.add(module);
			}
		}
		for (VModule module : visible) {
			long start = System.currentTimeMillis();
			try {
				String label = module.getHudText(mc);
				if (label != null && label.length() > 0) {
					int x = snap(module.x);
					int y = snap(module.y);
					int width = (int) ((mc.fontRendererObj.getStringWidth(label) + 8) * module.scale);
					int height = (int) (13 * module.scale);
					if (module.anchor == Anchor.TOP_LEFT) {
						x = GRID_SIZE * 2;
						y = topLeftY;
						topLeftY += height + GRID_SIZE;
					} else if (module.anchor == Anchor.TOP_CENTER) {
						x = sr.getScaledWidth() / 2 - width / 2;
						y = topCenterY;
						topCenterY += height + GRID_SIZE;
					} else if (module.anchor == Anchor.BOTTOM_RIGHT) {
						x = sr.getScaledWidth() - width - GRID_SIZE * 2;
						y = sr.getScaledHeight() - height - Math.max(GRID_SIZE * 2, module.y);
					}
					Gui.drawRect(x, y, x + width, y + height, 0x99000000);
					Gui.drawRect(x, y, x + Math.max(2, (int) (2 * module.scale)), y + height, accent);
					mc.fontRendererObj.drawString(label, x + (int) (5 * module.scale), y + (int) (3 * module.scale), 0xFFFFFF);
				}
				recordModuleCost(module, System.currentTimeMillis() - start, false);
			} catch (Throwable ex) {
				recordModuleCost(module, System.currentTimeMillis() - start, true);
			}
		}
		if (System.currentTimeMillis() - startupTime < 4200L) {
			String s = STARTUP_TEXT;
			int w = mc.fontRendererObj.getStringWidth(s) + 18;
			int px = sr.getScaledWidth() - w - 10;
			int py = sr.getScaledHeight() - 34;
			Gui.drawRect(px, py, px + w, py + 20, 0xB0000000);
			Gui.drawRect(px, py, px + 3, py + 20, accent);
			mc.fontRendererObj.drawString(s, px + 10, py + 6, 0xFFFFFF);
		}
		recordGlobalHudCost(System.currentTimeMillis() - hudStart);
	}

	public void tickPerformance(Minecraft mc) {
		long now = System.currentTimeMillis();
		float frameMs = Math.max(1.0F, (float) (now - lastFrameSample));
		lastFrameSample = now;
		frameGraph[graphCursor++ % GRAPH_SIZE] = frameMs;
		if (mc == null || mc.gameSettings == null) {
			return;
		}
		runStartupIntegrityCheck();
		monitorMemory(now);
		GameSettings settings = mc.gameSettings;
		if (lastRenderDistance <= 0) {
			lastRenderDistance = Math.max(2, settings.renderDistanceChunks);
		}
		float avg = getAverageFrameMs();
		panicMode = avg > 85.0F || usedMemoryEstimate > 0L && usedMemoryEstimate > (EagRuntime.maxMemory() * 9L / 10L);
		if (panicMode) {
			settings.renderDistanceChunks = Math.min(settings.renderDistanceChunks, 2);
			settings.particleSetting = 2;
			settings.fancyGraphics = false;
			settings.ambientOcclusion = 0;
			adaptiveRenderScale = 0.70F;
			cosmeticsInFpsMode = false;
		} else if (fpsPreset == FpsPreset.PERFORMANCE || chromebookMode || lowMemoryMode) {
			if (settings.renderDistanceChunks > 4) {
				settings.renderDistanceChunks = 4;
			}
			settings.particleSetting = Math.max(settings.particleSetting, avg > 45.0F ? 2 : 1);
			settings.fancyGraphics = false;
			settings.ambientOcclusion = 0;
			adaptiveRenderScale = avg > 45.0F ? 0.82F : 0.92F;
		} else {
			adaptiveRenderScale = avg > 38.0F ? 0.92F : 1.0F;
		}
	}

	private void applyPreset(Minecraft mc, FpsPreset preset, boolean chromebook) {
		if (mc == null || mc.gameSettings == null) {
			return;
		}
		fpsPreset = preset;
		chromebookMode = chromebook;
		lowMemoryMode = chromebook || preset == FpsPreset.PERFORMANCE;
		GameSettings settings = mc.gameSettings;
		if (preset == FpsPreset.PERFORMANCE) {
			settings.renderDistanceChunks = 3;
			settings.particleSetting = 2;
			settings.fancyGraphics = false;
			settings.ambientOcclusion = 0;
			settings.limitFramerate = 120;
			settings.mipmapLevels = 0;
			adaptiveRenderScale = 0.85F;
		} else if (preset == FpsPreset.BALANCED) {
			settings.renderDistanceChunks = MathHelper.clamp_int(settings.renderDistanceChunks, 4, 6);
			settings.particleSetting = Math.max(settings.particleSetting, 1);
			settings.fancyGraphics = false;
			settings.ambientOcclusion = Math.min(settings.ambientOcclusion, 1);
			settings.limitFramerate = Math.max(settings.limitFramerate, 120);
			adaptiveRenderScale = 1.0F;
		} else {
			settings.renderDistanceChunks = MathHelper.clamp_int(settings.renderDistanceChunks, 6, 10);
			settings.particleSetting = 0;
			settings.fancyGraphics = true;
			settings.ambientOcclusion = 1;
			settings.limitFramerate = Math.max(settings.limitFramerate, 260);
			adaptiveRenderScale = 1.0F;
		}
		lastRenderDistance = settings.renderDistanceChunks;
		settings.saveOptions();
		saveLocalProfile();
	}

	private boolean detectLowEndDevice(Minecraft mc) {
		if (mc == null) {
			return true;
		}
		long maxMemory = EagRuntime.maxMemory();
		return mc.displayWidth <= 1366 || mc.displayHeight <= 768 || maxMemory > 0L && maxMemory < 384L * 1024L * 1024L
				|| !Keyboard.isCreated();
	}

	private void monitorMemory(long now) {
		long max = EagRuntime.maxMemory();
		long free = EagRuntime.freeMemory();
		usedMemoryEstimate = max > 0L && free >= 0L ? Math.max(0L, max - free) : 0L;
		if (now - lastCleanup > 30000L && max > 0L && usedMemoryEstimate > max * 3L / 4L) {
			System.gc();
			lastCleanup = now;
		}
	}

	private void runStartupIntegrityCheck() {
		if (integrityChecked) {
			return;
		}
		integrityChecked = true;
		for (VModule module : modules) {
			if (module.enabled && !dependenciesEnabled(module)) {
				module.enabled = false;
			}
		}
	}

	private boolean dependenciesEnabled(VModule module) {
		for (int i = 0; i < module.dependencies.length; ++i) {
			VModule dependency = findModule(module.dependencies[i]);
			if (dependency == null || !dependency.enabled || dependency.autoDisabled) {
				return false;
			}
		}
		return true;
	}

	private boolean isVisible(VModule module, Minecraft mc) {
		if (module.visibility == Visibility.ALWAYS) {
			return true;
		}
		if (module.visibility == Visibility.IN_WORLD) {
			return mc.theWorld != null && mc.thePlayer != null;
		}
		if (module.visibility == Visibility.PVP) {
			return mc.theWorld != null && mc.thePlayer != null && !panicMode;
		}
		if (module.visibility == Visibility.SURVIVAL) {
			return mc.playerController != null && mc.playerController.gameIsSurvivalOrAdventure();
		}
		return !panicMode || cosmeticsInFpsMode;
	}

	private void recordModuleCost(VModule module, long costMs, boolean error) {
		module.lastCostMs = costMs;
		module.avgCostMs = module.avgCostMs <= 0.0F ? (float) costMs : module.avgCostMs * 0.85F + (float) costMs * 0.15F;
		if (error) {
			module.errorCount++;
			if (module.errorCount >= ERROR_LIMIT) {
				module.enabled = false;
				module.autoDisabled = true;
			}
		} else if (module.errorCount > 0) {
			module.errorCount--;
		}
	}

	private void recordGlobalHudCost(long costMs) {
		if (costMs > 12L && fpsPreset != FpsPreset.QUALITY) {
			lowMemoryMode = true;
		}
	}

	private int snap(int value) {
		return Math.round((float) value / (float) GRID_SIZE) * GRID_SIZE;
	}

	private VModule findModule(String name) {
		for (VModule module : modules) {
			if (module.name.equals(name)) {
				return module;
			}
		}
		return null;
	}

	private void enable(String... names) {
		for (String name : names) {
			VModule module = findModule(name);
			if (module != null && !module.autoDisabled && dependenciesEnabled(module)) {
				module.enabled = true;
			}
		}
	}

	private void registerDefaults() {
		module("Keystrokes", Category.PVP, "Input", "Displays WASD and mouse inputs.", true, true, Anchor.TOP_LEFT, Visibility.IN_WORLD);
		module("CPS Counter", Category.PVP, "Input", "Smoothed click speed for PvP timing.", true, true, Anchor.TOP_LEFT,
				Visibility.PVP);
		module("Combo Counter", Category.PVP, "Combat", "Tracks visible combo streaks client-side.", false, true, Anchor.TOP_LEFT,
				Visibility.PVP);
		module("Armor HUD", Category.PVP, "Combat", "Shows armor status in a compact HUD.", true, true, Anchor.BOTTOM_RIGHT,
				Visibility.IN_WORLD);
		module("Potion HUD", Category.PVP, "Combat", "Shows active potion timers.", true, true, Anchor.BOTTOM_RIGHT,
				Visibility.IN_WORLD);
		module("Toggle Sprint", Category.PVP, "Movement", "Keeps sprint behavior convenient for browser PvP.", true, false,
				Anchor.TOP_LEFT, Visibility.IN_WORLD);
		module("Zoom Mod", Category.PVP, "Visual", "Smooth client-side zoom helper.", true, false, Anchor.TOP_LEFT, Visibility.IN_WORLD);
		module("Hit Particles", Category.PVP, "Feedback", "Load-aware hit feedback particles.", false, false, Anchor.TOP_LEFT,
				Visibility.PVP);
		module("Hit Color", Category.PVP, "Feedback", "Tint hit feedback for readability.", false, false, Anchor.TOP_LEFT, Visibility.PVP,
				"Hit Particles");
		module("Crosshair Editor", Category.PVP, "Feedback", "Custom visual crosshair controls.", false, false, Anchor.TOP_CENTER,
				Visibility.PVP);
		module("Direction HUD", Category.PVP, "Info", "Displays facing direction.", true, true, Anchor.TOP_LEFT, Visibility.IN_WORLD);
		module("Ping Display", Category.PVP, "Info", "Shows connection ping where available.", true, true, Anchor.TOP_LEFT,
				Visibility.IN_WORLD);
		module("Reach Display", Category.PVP, "Info", "Visual-only reach readout; does not alter gameplay.", false, true,
				Anchor.TOP_LEFT, Visibility.PVP);
		module("Motion Blur", Category.PVP, "Visual", "Ultra-light screen smoothing option.", false, false, Anchor.TOP_LEFT,
				Visibility.PVP);
		module("Damage Tilt", Category.PVP, "Visual", "Toggle damage tilt visuals.", true, false, Anchor.TOP_LEFT, Visibility.IN_WORLD);
		module("Minimal Fire", Category.PVP, "Visual", "Reduces fire overlay obstruction.", true, false, Anchor.TOP_LEFT,
				Visibility.IN_WORLD);
		module("Block Overlay", Category.PVP, "Visual", "Improves selected block visibility.", false, false, Anchor.TOP_LEFT,
				Visibility.IN_WORLD);
		module("Minimap", Category.SURVIVAL, "Navigation", "Compact terrain awareness HUD with clustered updates.", false, true,
				Anchor.TOP_CENTER, Visibility.SURVIVAL);
		module("Waypoints", Category.SURVIVAL, "Navigation", "Local browser waypoint list with smart clustering.", false, true,
				Anchor.TOP_CENTER, Visibility.IN_WORLD);
		module("Coordinates HUD", Category.SURVIVAL, "Info", "Shows X/Y/Z coordinates.", true, true, Anchor.TOP_LEFT, Visibility.IN_WORLD);
		module("Item Durability Viewer", Category.SURVIVAL, "Inventory", "Highlights item wear.", false, true, Anchor.BOTTOM_RIGHT,
				Visibility.IN_WORLD);
		module("Light Level Viewer", Category.SURVIVAL, "Info", "Shows local brightness information.", false, true, Anchor.TOP_LEFT,
				Visibility.SURVIVAL);
		module("Better Chat", Category.SURVIVAL, "Chat", "Cleaner chat presentation.", true, false, Anchor.TOP_LEFT, Visibility.IN_WORLD);
		module("Inventory Search", Category.SURVIVAL, "Inventory", "Find items faster in menus.", false, false, Anchor.TOP_LEFT,
				Visibility.ALWAYS);
		module("Screenshot Waypoints", Category.SURVIVAL, "Navigation", "Create waypoint notes from screenshots.", false, false,
				Anchor.TOP_LEFT, Visibility.IN_WORLD, "Waypoints");
		module("Recipe Helper", Category.SURVIVAL, "Inventory", "Lightweight recipe reminders.", false, false, Anchor.TOP_LEFT,
				Visibility.SURVIVAL);
		module("Chat Timestamps", Category.SURVIVAL, "Chat", "Adds readable timestamps to chat.", false, false, Anchor.TOP_LEFT,
				Visibility.IN_WORLD, "Better Chat");
		module("Hotbar Presets", Category.CREATIVE, "Inventory", "Save local hotbar layouts.", false, false, Anchor.TOP_LEFT,
				Visibility.ALWAYS);
		module("Block Palette Menu", Category.CREATIVE, "Inventory", "Quick creative block palette.", false, false, Anchor.TOP_LEFT,
				Visibility.ALWAYS);
		module("Structure Saver", Category.CREATIVE, "Building", "Local structure planning memory cache.", false, false,
				Anchor.TOP_LEFT, Visibility.ALWAYS);
		module("Fast Inventory Search", Category.CREATIVE, "Inventory", "Accelerated creative search workflow.", false, false,
				Anchor.TOP_LEFT, Visibility.ALWAYS);
		module("Perspective/Freelook", Category.CREATIVE, "Camera", "Smoothed browser-friendly perspective utility.", false, false,
				Anchor.TOP_LEFT, Visibility.IN_WORLD);
		module("VHS Mode", Category.FUN, "Filters", "Retro VHS post-style overlay.", false, false, Anchor.TOP_LEFT, Visibility.ALWAYS);
		module("Retro Minecraft UI", Category.FUN, "Filters", "Classic-styled menu mode.", false, false, Anchor.TOP_LEFT,
				Visibility.ALWAYS);
		module("CRT Screen Filter", Category.FUN, "Filters", "Subtle CRT scanline effect.", false, false, Anchor.TOP_LEFT,
				Visibility.ALWAYS);
		module("Meme Splash Texts", Category.FUN, "Menu", "Rare startup splash messages.", true, false, Anchor.TOP_LEFT,
				Visibility.ALWAYS);
		module("Menu Music", Category.FUN, "Menu", "Optional menu ambience hook.", false, false, Anchor.TOP_LEFT, Visibility.ALWAYS);
		module("Custom Hit Sounds", Category.FUN, "Feedback", "Local hit sound feedback.", false, false, Anchor.TOP_LEFT,
				Visibility.PVP);
		module("Emote: Wave", Category.COSMETICS, "Emotes", "Common local emote preview.", false, false, Anchor.TOP_LEFT,
				Visibility.COSMETIC);
		module("Emote: Sit", Category.COSMETICS, "Emotes", "Common local emote preview.", false, false, Anchor.TOP_LEFT,
				Visibility.COSMETIC);
		module("Emote: Dance", Category.COSMETICS, "Emotes", "Rare local emote preview.", false, false, Anchor.TOP_LEFT,
				Visibility.COSMETIC);
		module("Emote: Point", Category.COSMETICS, "Emotes", "Common local emote preview.", false, false, Anchor.TOP_LEFT,
				Visibility.COSMETIC);
		module("Capes", Category.COSMETICS, "Wearables", "Rare local profile cape selection.", false, false, Anchor.TOP_LEFT,
				Visibility.COSMETIC);
		module("Hats", Category.COSMETICS, "Wearables", "Epic local profile hat selection.", false, false, Anchor.TOP_LEFT,
				Visibility.COSMETIC);
		module("Bandanas", Category.COSMETICS, "Wearables", "Uncommon local profile bandana selection.", false, false,
				Anchor.TOP_LEFT, Visibility.COSMETIC);
		module("Particle Trails", Category.COSMETICS, "Trails", "Optimized lightweight trail cosmetics.", false, false,
				Anchor.TOP_LEFT, Visibility.COSMETIC);
	}

	private void module(String name, Category category, String group, String description, boolean enabled, boolean hud, Anchor anchor,
			Visibility visibility, String... dependencies) {
		VModule module = new VModule(name, category, group, description, enabled, hud, anchor, visibility, dependencies);
		module.x = GRID_SIZE * 2;
		module.y = GRID_SIZE * 2 + modules.size() % 8 * 16;
		modules.add(module);
	}

	public enum Category {
		PVP, SURVIVAL, CREATIVE, FUN, COSMETICS
	}

	public enum FpsPreset {
		PERFORMANCE, BALANCED, QUALITY
	}

	public enum Theme {
		NEON_PURPLE("Neon Purple", 0xFF8B5CFF), CRIMSON_RED("Crimson Red", 0xFFFF4B5C), MATRIX_GREEN("Matrix Green", 0xFF2DFF7A),
		RETRO_BLUE("Retro Blue", 0xFF4BA3FF), DARK_DEFAULT("Dark Default", 0xFF7A7A88), SCHOOL_SAFE("School Safe", 0xFF6F8FAF);

		public final String displayName;
		public final int accentColor;

		Theme(String displayName, int accentColor) {
			this.displayName = displayName;
			this.accentColor = accentColor;
		}
	}

	public enum Anchor {
		TOP_LEFT, TOP_CENTER, BOTTOM_RIGHT, CUSTOM
	}

	public enum Visibility {
		ALWAYS, IN_WORLD, PVP, SURVIVAL, COSMETIC
	}

	public static final class VModule {
		public final String name;
		public final Category category;
		public final String group;
		public final String description;
		public final String[] dependencies;
		public final boolean hud;
		public final Visibility visibility;
		public boolean enabled;
		public boolean favorite;
		public boolean autoDisabled;
		public int x;
		public int y;
		public float scale = 1.0F;
		public Anchor anchor;
		public long lastCostMs;
		public float avgCostMs;
		public int errorCount;

		private VModule(String name, Category category, String group, String description, boolean enabled, boolean hud, Anchor anchor,
				Visibility visibility, String[] dependencies) {
			this.name = name;
			this.category = category;
			this.group = group;
			this.description = description;
			this.enabled = enabled;
			this.hud = hud;
			this.anchor = anchor;
			this.visibility = visibility;
			this.dependencies = dependencies;
		}

		private String getHudText(Minecraft mc) {
			if ("Coordinates HUD".equals(name) && mc.thePlayer != null) {
				return "XYZ " + MathHelper.floor_double(mc.thePlayer.posX) + " / " + MathHelper.floor_double(mc.thePlayer.posY) + " / "
						+ MathHelper.floor_double(mc.thePlayer.posZ);
			}
			if ("Direction HUD".equals(name) && mc.thePlayer != null) {
				return "Facing " + mc.thePlayer.getHorizontalFacing().getName().toUpperCase(Locale.ROOT);
			}
			if ("Ping Display".equals(name)) {
				return "Ping -- ms";
			}
			if ("CPS Counter".equals(name)) {
				return "CPS 0.0 | 0.0";
			}
			if ("Keystrokes".equals(name)) {
				return "WASD  LMB/RMB";
			}
			return name;
		}
	}
}
