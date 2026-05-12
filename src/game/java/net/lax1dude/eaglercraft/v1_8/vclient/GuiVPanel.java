package net.lax1dude.eaglercraft.v1_8.vclient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.lax1dude.eaglercraft.v1_8.internal.KeyboardConstants;
import net.lax1dude.eaglercraft.v1_8.vclient.EaglercraftVClient.Category;
import net.lax1dude.eaglercraft.v1_8.vclient.EaglercraftVClient.VModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

/**
 * Modern, lightweight module panel for EaglercraftV. The UI deliberately uses
 * simple batched rectangles and text so it remains smooth on browser and
 * Chromebook builds without requiring shader effects.
 */
public class GuiVPanel extends GuiScreen {

	private static final ResourceLocation CLICK_SOUND = new ResourceLocation("gui.button.press");

	private final GuiScreen parent;
	private final EaglercraftVClient client = EaglercraftVClient.getInstance();
	private final List<VModule> queuedToggles = new ArrayList();
	private GuiTextField search;
	private Category category = Category.PVP;
	private long openedAt;
	private int scroll;
	private int focusedIndex;
	private boolean instantApply = true;

	public GuiVPanel(GuiScreen parent) {
		this.parent = parent;
	}

	public void initGui() {
		this.openedAt = System.currentTimeMillis();
		this.search = new GuiTextField(100, this.fontRendererObj, this.width / 2 - 150, 42, 300, 20);
		this.search.setMaxStringLength(32);
		this.search.setFocused(false);
		this.buttonList.clear();
		int x = this.width / 2 - 202;
		int y = 70;
		int id = 200;
		for (Category cat : Category.values()) {
			this.buttonList.add(new GuiButton(id++, x, y, 96, 20, label(cat)));
			y += 23;
		}
		this.buttonList.add(new GuiButton(10, this.width / 2 + 108, this.height - 72, 94, 20, "Theme"));
		this.buttonList.add(new GuiButton(11, this.width / 2 + 8, this.height - 72, 94, 20, "TurboFPS"));
		this.buttonList.add(new GuiButton(12, this.width / 2 - 92, this.height - 72, 94, 20, "FPS Preset"));
		this.buttonList.add(new GuiButton(13, this.width / 2 - 202, this.height - 72, 104, 20, "PvP Profile"));
		this.buttonList.add(new GuiButton(14, this.width / 2 - 202, this.height - 48, 104, 20, "Survival"));
		this.buttonList.add(new GuiButton(15, this.width / 2 - 92, this.height - 48, 94, 20, "Creative"));
		this.buttonList.add(new GuiButton(16, this.width / 2 + 8, this.height - 48, 94, 20, "Minimal"));
		this.buttonList.add(new GuiButton(17, this.width / 2 + 108, this.height - 48, 94, 20, "School Safe"));
		this.buttonList.add(new GuiButton(18, this.width / 2 - 202, this.height - 24, 104, 20, "Instant Apply"));
		this.buttonList.add(new GuiButton(19, this.width / 2 + 108, this.height - 24, 94, 20, "Apply Queue"));
		this.buttonList.add(new GuiButton(0, this.width / 2 - 50, this.height - 24, 100, 20, I18n.format("gui.done")));
		updateApplyButtons();
	}

	protected void actionPerformed(GuiButton button) {
		playClick(1.0F);
		if (button.id == 0) {
			applyQueuedToggles();
			this.mc.displayGuiScreen(parent);
		} else if (button.id == 10) {
			client.nextTheme();
		} else if (button.id == 11) {
			client.runTurboWizard(mc);
		} else if (button.id == 12) {
			client.cycleFpsPreset(mc);
		} else if (button.id == 13) {
			client.applyModuleProfile("pvp");
			queuedToggles.clear();
		} else if (button.id == 14) {
			client.applyModuleProfile("survival");
			queuedToggles.clear();
		} else if (button.id == 15) {
			client.applyModuleProfile("creative");
			queuedToggles.clear();
		} else if (button.id == 16) {
			client.applyModuleProfile("minimal");
			queuedToggles.clear();
		} else if (button.id == 17) {
			client.applyModuleProfile("school safe");
			queuedToggles.clear();
		} else if (button.id == 18) {
			instantApply = !instantApply;
			if (instantApply) {
				applyQueuedToggles();
			}
		} else if (button.id == 19) {
			applyQueuedToggles();
		} else if (button.id >= 200 && button.id < 200 + Category.values().length) {
			category = Category.values()[button.id - 200];
			scroll = 0;
			focusedIndex = 0;
		}
		updateApplyButtons();
	}

	protected void keyTyped(char c, int keyCode) {
		if (keyCode == KeyboardConstants.KEY_ESCAPE) {
			applyQueuedToggles();
			this.mc.displayGuiScreen(parent);
			return;
		}
		List<VModule> modules = filteredModules();
		if (keyCode == KeyboardConstants.KEY_UP) {
			focusedIndex = Math.max(0, focusedIndex - 1);
			return;
		}
		if (keyCode == KeyboardConstants.KEY_DOWN) {
			focusedIndex = Math.min(Math.max(0, modules.size() - 1), focusedIndex + 1);
			return;
		}
		if (keyCode == KeyboardConstants.KEY_RETURN && modules.size() > 0) {
			queueOrApply(modules.get(Math.min(focusedIndex, modules.size() - 1)));
			return;
		}
		if ((keyCode == KeyboardConstants.KEY_F || keyCode == KeyboardConstants.KEY_SPACE) && modules.size() > 0) {
			client.toggleFavorite(modules.get(Math.min(focusedIndex, modules.size() - 1)));
			return;
		}
		this.search.textboxKeyTyped(c, keyCode);
		focusedIndex = 0;
	}

	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		super.mouseClicked(mouseX, mouseY, mouseButton);
		this.search.mouseClicked(mouseX, mouseY, mouseButton);
		if (mouseButton != 0 && mouseButton != 1) {
			return;
		}
		int panelX = this.width / 2 - 92;
		int y = 80 - scroll;
		String lastGroup = null;
		List<VModule> modules = filteredModules();
		for (int i = 0; i < modules.size(); ++i) {
			VModule module = modules.get(i);
			if (!module.group.equals(lastGroup)) {
				y += 12;
				lastGroup = module.group;
			}
			if (mouseX >= panelX && mouseX <= panelX + 294 && mouseY >= y && mouseY <= y + 32) {
				focusedIndex = i;
				if (mouseButton == 1) {
					client.toggleFavorite(module);
				} else {
					queueOrApply(module);
				}
				playClick(1.12F);
				break;
			}
			y += 38;
		}
	}

	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int wheel = net.lax1dude.eaglercraft.v1_8.Mouse.getDWheel();
		if (wheel != 0) {
			scroll += wheel > 0 ? -24 : 24;
			if (scroll < 0) {
				scroll = 0;
			}
		}
	}

	public void updateScreen() {
		this.search.updateCursorCounter();
		client.tickPerformance(mc);
		updateApplyButtons();
	}

	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawDefaultBackground();
		int accent = client.getTheme().accentColor;
		float anim = easeOutCubic(Math.min(1.0F, (System.currentTimeMillis() - openedAt) / 220.0F));
		int panelW = (int) (420 * (0.92F + anim * 0.08F));
		int panelH = (int) (this.height * 0.72F);
		int panelX = this.width / 2 - panelW / 2;
		int panelY = 24;
		drawSoftPanel(panelX, panelY, panelX + panelW, panelY + panelH, accent);
		drawCenteredString(fontRendererObj, "EaglercraftV", this.width / 2, 14, 0xFFFFFF);
		drawCenteredString(fontRendererObj, EaglercraftVClient.STARTUP_TEXT, this.width / 2, 27, accent & 0xFFFFFF);
		this.search.drawTextBox();
		drawString(fontRendererObj, "VPanel Modules", panelX + 112, 66, 0xFFFFFF);
		drawString(fontRendererObj, "Theme: " + client.getTheme().displayName + "  FPS: " + client.getFpsPreset().name(), panelX + 112,
				this.height - 108, 0xCFCFFF);
		drawString(fontRendererObj, client.getPerformanceSummary() + "  Profile: " + client.getActiveProfile(), panelX + 112,
				this.height - 98, client.isPanicMode() ? 0xFF7777 : 0xCFCFFF);
		drawString(fontRendererObj, client.getCosmeticSummary(), panelX + 112, this.height - 88, 0xCFCFFF);
		drawString(fontRendererObj, "TurboFPS: " + (client.hasTurboWizardApplied() ? "applied" : "ready") + "  Apply: "
				+ (instantApply ? "instant" : "queued " + queuedToggles.size()), panelX + 112, this.height - 78, 0xCFCFFF);
		drawModules(panelX + 110, 80, mouseX, mouseY, accent);
		drawVLogo(panelX + panelW - 42, panelY + 12, accent);
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	private void drawModules(int x, int yStart, int mouseX, int mouseY, int accent) {
		int y = yStart - scroll;
		String query = search == null ? "" : search.getText().toLowerCase(Locale.ROOT).trim();
		String lastGroup = null;
		List<VModule> modules = filteredModules();
		if (focusedIndex >= modules.size()) {
			focusedIndex = Math.max(0, modules.size() - 1);
		}
		for (int i = 0; i < modules.size(); ++i) {
			VModule module = modules.get(i);
			if (!module.group.equals(lastGroup)) {
				if (y > 65 && y < this.height - 112) {
					drawString(fontRendererObj, module.group.toUpperCase(Locale.ROOT), x, y + 1, accent & 0xFFFFFF);
				}
				y += 12;
				lastGroup = module.group;
			}
			if (y > 65 && y < this.height - 112) {
				boolean hover = mouseX >= x && mouseX <= x + 294 && mouseY >= y && mouseY <= y + 32;
				boolean active = isModuleEnabledForUi(module);
				int bg = hover || i == focusedIndex ? 0xAA252535 : 0x8820202C;
				drawRect(x, y, x + 294, y + 32, bg);
				drawRect(x, y, x + 3, y + 32, active ? accent : 0xFF555560);
				int nameColor = active ? 0xFFFFFF : 0xB8B8C8;
				String displayName = module.favorite ? "★ " + module.name : module.name;
				if (query.length() > 0 && module.name.toLowerCase(Locale.ROOT).contains(query)) {
					drawRect(x + 7, y + 4, x + 14 + fontRendererObj.getStringWidth(displayName), y + 16, 0x3333DDFF);
				}
				drawString(fontRendererObj, displayName, x + 10, y + 6, nameColor);
				drawString(fontRendererObj, trim(module.description, 36), x + 10, y + 18, 0xA8A8B8);
				drawString(fontRendererObj, module.avgCostMs > 0.0F ? ((int) (module.avgCostMs * 10.0F) / 10.0F) + "ms" : "ready",
						x + 206, y + 18, module.autoDisabled ? 0xFF6666 : 0x777788);
				int tx = x + 252;
				drawRect(tx, y + 9, tx + 30, y + 21, active ? accent : 0xFF33333C);
				drawRect(active ? tx + 18 : tx + 2, y + 11, active ? tx + 28 : tx + 12, y + 19, 0xFFFFFFFF);
			}
			y += 38;
		}
	}

	private void queueOrApply(VModule module) {
		if (instantApply) {
			client.toggleModule(module);
		} else {
			if (queuedToggles.contains(module)) {
				queuedToggles.remove(module);
			} else {
				queuedToggles.add(module);
			}
		}
	}

	private void applyQueuedToggles() {
		for (int i = 0; i < queuedToggles.size(); ++i) {
			client.toggleModule(queuedToggles.get(i));
		}
		queuedToggles.clear();
		updateApplyButtons();
	}

	private boolean isModuleEnabledForUi(VModule module) {
		return queuedToggles.contains(module) ? !module.enabled : module.enabled;
	}

	private void updateApplyButtons() {
		for (int i = 0; i < buttonList.size(); ++i) {
			GuiButton button = (GuiButton) buttonList.get(i);
			if (button.id == 18) {
				button.displayString = instantApply ? "Instant Apply" : "Queued Apply";
			}
			if (button.id == 19) {
				button.enabled = !instantApply && !queuedToggles.isEmpty();
			}
		}
	}

	private List<VModule> filteredModules() {
		String q = search == null ? "" : search.getText().toLowerCase(Locale.ROOT).trim();
		List<VModule> ret = new ArrayList();
		appendFiltered(ret, q, true);
		appendFiltered(ret, q, false);
		return ret;
	}

	private void appendFiltered(List<VModule> ret, String q, boolean favorites) {
		for (VModule module : client.getModules()) {
			if (module.category == category && module.favorite == favorites && (q.length() == 0
					|| module.name.toLowerCase(Locale.ROOT).contains(q) || module.description.toLowerCase(Locale.ROOT).contains(q)
					|| module.group.toLowerCase(Locale.ROOT).contains(q))) {
				ret.add(module);
			}
		}
	}

	private void drawSoftPanel(int left, int top, int right, int bottom, int accent) {
		drawRect(left - 3, top - 3, right + 3, bottom + 3, 0x33000000);
		drawRect(left, top, right, bottom, EaglercraftVClient.COLOR_PANEL);
		drawRect(left, top, right, top + 2, accent);
		drawRect(left, bottom - 1, right, bottom, 0x88202030);
	}

	private void drawVLogo(int x, int y, int accent) {
		drawRect(x + 2, y, x + 9, y + 7, accent);
		drawRect(x + 26, y, x + 33, y + 7, accent);
		drawRect(x + 6, y + 7, x + 13, y + 18, accent);
		drawRect(x + 22, y + 7, x + 29, y + 18, accent);
		drawRect(x + 12, y + 18, x + 23, y + 25, accent);
		drawString(fontRendererObj, "V", x + 13, y + 6, 0xFFFFFF);
	}

	private void playClick(float pitch) {
		Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.create(CLICK_SOUND, pitch));
	}

	private float easeOutCubic(float value) {
		float inv = 1.0F - value;
		return 1.0F - inv * inv * inv;
	}

	private static String label(Category cat) {
		String n = cat.name().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(n.charAt(0)) + n.substring(1);
	}

	private String trim(String s, int len) {
		return s.length() <= len ? s : s.substring(0, len - 3) + "...";
	}
}
