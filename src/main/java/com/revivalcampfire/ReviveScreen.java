package com.revivalcampfire;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Menu de resurreccion: marco de madera con dos paneles. A la izquierda la lista de baneados
 * (el elegido se marca en verde, con su cara); a la derecha la cuchara y el boton "Revivir".
 */
public class ReviveScreen extends AbstractContainerScreen<ReviveMenu> {
    private static final int W = 176, H = 154;
    private static final int LX = 20, LY = 20, LW = 65, LH = 111, ROW = 12, ROWS = 9;
    private static final int RX = 93;

    private static final int WOOD_DARK = 0xFF332014, WOOD_POST = 0xFF4F3525, WOOD_EDGE = 0xFF2A1A10;
    private static final int PANEL = 0xFF74542C, PANEL_HI = 0xFF86642F, PANEL_LO = 0xFF624624;
    private static final int DIVIDER = 0xFF422819, GOLD = 0xFFC08A3A;
    private static final int GREEN = 0xFF3D843F, GREEN_HI = 0xFF518D50;

    private static final ResourceLocation TEX = new ResourceLocation(RevivalCampfireMod.MODID, "textures/gui/revive_menu.png");

    private int scroll = 0;
    private int selected = -1;
    private Button reviveButton;

    public ReviveScreen(ReviveMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        reviveButton = Button.builder(Component.translatable("gui.revivalcampfire.revive"), b -> {
            if (selected >= 0 && selected < menu.banned.size()) {
                Network.CHANNEL.sendToServer(new Network.ReviveRequest(menu.pos, menu.banned.get(selected).id()));
                onClose();
            }
        }).bounds(leftPos + 103, topPos + 83, 45, 20).build();
        addRenderableWidget(reviveButton);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        reviveButton.active = selected >= 0 && selected < menu.banned.size();
        renderBackground(g);
        super.render(g, mx, my, pt);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;

        // marco de madera, paneles y marco del icono (textura)
        g.blit(TEX, x, y, 0, 0, W, H, 256, 256);
        g.drawCenteredString(font, Component.translatable("gui.revivalcampfire.title"), x + W / 2, y + 9, 0xFFD27A);

        // lista
        if (menu.banned.isEmpty()) {
            g.drawWordWrap(font, Component.translatable("gui.revivalcampfire.empty"), x + LX + 4, y + LY + 5, LW - 8, 0xFFFFFF);
        }
        for (int r = 0; r < ROWS; r++) {
            int idx = scroll + r;
            if (idx >= menu.banned.size()) break;
            ReviveMenu.Entry e = menu.banned.get(idx);
            int ry = y + LY + 1 + r * ROW;
            boolean hover = mx >= x + LX && mx < x + LX + LW && my >= ry && my < ry + ROW;
            if (idx == selected) {
                g.fill(x + LX + 1, ry, x + LX + LW - 1, ry + ROW - 1, GREEN);
                g.fill(x + LX + 1, ry, x + LX + LW - 1, ry + 1, GREEN_HI);
            } else if (hover) {
                g.fill(x + LX + 1, ry, x + LX + LW - 1, ry + ROW - 1, 0x30FFFFFF);
            }
            ResourceLocation skin = DefaultPlayerSkin.getDefaultSkin(e.id());
            g.blit(skin, x + LX + 3, ry + 1, 9, 9, 8f, 8f, 8, 8, 64, 64);     // cara
            g.blit(skin, x + LX + 3, ry + 1, 9, 9, 40f, 8f, 8, 8, 64, 64);    // capa del sombrero
            g.drawString(font, e.name(), x + LX + 15, ry + 2, 0xFFFFFF, true);
        }
        int total = menu.banned.size();
        if (total > ROWS) {
            int h = Math.max(10, LH * ROWS / total);
            int sy = y + LY + (LH - h) * scroll / (total - ROWS);
            g.fill(x + LX + LW - 3, sy, x + LX + LW - 1, sy + h, 0xFF4A3319);
        }

        // cuchara en su marco
        g.renderItem(new ItemStack(RevivalCampfireMod.SPOON.get()), x + 117, y + 56);
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 1, PANEL_HI);
        g.fill(x, y, x + 1, y + h, PANEL_HI);
        g.fill(x, y + h - 1, x + w, y + h, PANEL_LO);
        g.fill(x + w - 1, y, x + w, y + h, PANEL_LO);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // sin titulo ni etiqueta de inventario
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int lx = leftPos + LX, ly = topPos + LY + 1;
        if (mx >= lx && mx < lx + LW && my >= ly && my < ly + ROW * ROWS) {
            int idx = scroll + (int) ((my - ly) / ROW);
            if (idx < menu.banned.size()) selected = idx;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, menu.banned.size() - ROWS);
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, max);
        return true;
    }
}
