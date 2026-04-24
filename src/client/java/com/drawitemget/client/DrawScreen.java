package com.drawitemget.client;

import com.drawitemget.net.DrawNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Full-screen drawing surface. 64×64 internal canvas, rendered to a large cell
 * grid with palette on the right, tool rail on the left, and a submit button at
 * the bottom. All visuals are pure gui-graphics fills — no extra assets.
 */
public final class DrawScreen extends Screen {

    private static final int SIZE = DrawNetwork.CANVAS_SIZE;     // 64
    private static final int[] PALETTE = new int[] {
            0xFF000000, 0xFF444444, 0xFF888888, 0xFFCCCCCC, 0xFFFFFFFF,
            0xFFB4202A, 0xFFE8722C, 0xFFEFC027, 0xFFDE3F70, 0xFF9E5D34,
            0xFF5DA130, 0xFF2CB37B, 0xFF2F9DE8, 0xFF2962C6, 0xFF8836E2,
            0xFFE55CC0
    };
    private static final int[] BRUSH_SIZES = new int[] { 1, 2, 3, 5, 8, 12 };

    private final int[] pixels = new int[SIZE * SIZE];
    private final Deque<int[]> undo = new ArrayDeque<>();
    private final Deque<int[]> redo = new ArrayDeque<>();

    private int currentColor = 0xFF000000;
    private int brushIndex = 2;                 // => brush radius in cell units
    private Tool tool = Tool.BRUSH;

    // Animated elements.
    private long openedAtMs;
    private long lastScanMs;                    // set when we trigger the submit animation
    private boolean submitted;

    // Canvas rectangle (recomputed in render).
    private int canvasX0, canvasY0, cell, canvasPx;
    private int hoverCellX = -1, hoverCellY = -1;
    private int lastPaintedCellX = -1, lastPaintedCellY = -1;
    private boolean painting;

    public DrawScreen() {
        super(Component.translatable("drawitemget.ui.title"));
        clearCanvas(0);
        openedAtMs = System.currentTimeMillis();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        super.init();
    }

    private void clearCanvas(int fillColor) {
        java.util.Arrays.fill(pixels, fillColor);
    }

    private void snapshotForUndo() {
        undo.push(pixels.clone());
        if (undo.size() > 40) undo.pollLast();
        redo.clear();
    }

    // ================================================================
    //   RENDER
    // ================================================================

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int w = this.width, h = this.height;
        UiFx.cosmicBackdrop(g, w, h, 0xFF6A4CFF, 1337);

        // Layout: canvas in the center, tool rail on the left, palette on the right.
        int canvasCellsDesired = SIZE;
        int maxCanvasPx = Math.min(h - 110, w - 260);
        cell = Math.max(4, maxCanvasPx / canvasCellsDesired);
        canvasPx = cell * SIZE;
        canvasX0 = (w - canvasPx) / 2;
        canvasY0 = Math.max(56, (h - canvasPx) / 2 - 10);

        renderHeader(g, w);
        renderCanvas(g, mouseX, mouseY);
        renderToolRail(g, mouseX, mouseY);
        renderPalette(g, mouseX, mouseY);
        renderFooter(g, mouseX, mouseY);
        if (submitted) renderScanOverlay(g);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphicsExtractor g, int w) {
        int h0 = 6, h1 = 44;
        UiFx.fancyPanel(g, 16, h0, w - 16, h1, 0xFF251548, 0xFF100820, 0xFF8D6CFF, 0xFFE4C6FF);
        Component title = Component.translatable("drawitemget.ui.title")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("drawitemget.ui.subtitle")
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        g.centeredText(this.font, title, w / 2, 12, 0xFFFFD060);
        g.centeredText(this.font, subtitle, w / 2, 26, 0xFFBFA0FF);

        // Animated corner chevrons.
        long now = System.currentTimeMillis();
        double pulse = 0.5 + 0.5 * Math.sin((now - openedAtMs) / 260.0);
        int chev = UiFx.withAlpha(UiFx.brighten(0xFF6A4CFF, (float) pulse), 0xE0);
        drawChevron(g, 24, 14, chev);
        drawChevron(g, w - 36, 14, chev);
    }

    private void drawChevron(GuiGraphicsExtractor g, int x, int y, int color) {
        for (int i = 0; i < 6; i++) {
            g.fill(x + i, y + i, x + i + 2, y + i + 2, color);
            g.fill(x + i, y + 12 - i, x + i + 2, y + 14 - i, color);
        }
    }

    private void renderCanvas(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x0 = canvasX0, y0 = canvasY0;
        int x1 = x0 + canvasPx, y1 = y0 + canvasPx;

        // Outer glow.
        for (int k = 10; k >= 1; k--) {
            int a = 0x04 + (10 - k) * 0x04;
            g.fill(x0 - k, y0 - k, x1 + k, y1 + k, (a << 24) | 0x8D6CFF);
        }
        // Frame.
        g.fill(x0 - 3, y0 - 3, x1 + 3, y0,     0xFFE4C6FF);
        g.fill(x0 - 3, y1,     x1 + 3, y1 + 3, 0xFFE4C6FF);
        g.fill(x0 - 3, y0 - 3, x0,     y1 + 3, 0xFFE4C6FF);
        g.fill(x1,     y0 - 3, x1 + 3, y1 + 3, 0xFFE4C6FF);
        // Inner shadow.
        g.fill(x0 - 2, y0 - 2, x1 + 2, y0 - 1, 0xFF1B0F3C);
        g.fill(x0 - 2, y1 + 1, x1 + 2, y1 + 2, 0xFF1B0F3C);
        g.fill(x0 - 2, y0 - 2, x0 - 1, y1 + 2, 0xFF1B0F3C);
        g.fill(x1 + 1, y0 - 2, x1 + 2, y1 + 2, 0xFF1B0F3C);

        // Canvas background (soft checker).
        for (int cy = 0; cy < SIZE; cy++) {
            for (int cx = 0; cx < SIZE; cx++) {
                int p = pixels[cy * SIZE + cx];
                int rx = x0 + cx * cell;
                int ry = y0 + cy * cell;
                int bg = (((cx >> 2) + (cy >> 2)) & 1) == 0 ? 0xFF14092D : 0xFF1C0F3B;
                g.fill(rx, ry, rx + cell, ry + cell, bg);
                if ((p >>> 24) != 0) {
                    g.fill(rx, ry, rx + cell, ry + cell, p);
                }
            }
        }

        // Hover outline.
        hoverCellX = hoverCellY = -1;
        if (mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1) {
            int cx = (mouseX - x0) / cell;
            int cy = (mouseY - y0) / cell;
            hoverCellX = cx;
            hoverCellY = cy;
            int radius = BRUSH_SIZES[brushIndex];
            int pxColor = tool == Tool.ERASER ? 0xFFFFFFFF : currentColor;
            drawBrushOutline(g, cx, cy, radius, UiFx.withAlpha(pxColor, 0x80));
        }
    }

    private void drawBrushOutline(GuiGraphicsExtractor g, int cx, int cy, int radius, int color) {
        int r = radius;
        int r2 = r * r;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                int d2 = dx * dx + dy * dy;
                if (d2 > r2 || d2 < (r - 1) * (r - 1)) continue;
                int ccx = cx + dx, ccy = cy + dy;
                if (ccx < 0 || ccy < 0 || ccx >= SIZE || ccy >= SIZE) continue;
                int rx = canvasX0 + ccx * cell;
                int ry = canvasY0 + ccy * cell;
                g.fill(rx, ry, rx + cell, ry + cell, color);
            }
        }
    }

    private void renderToolRail(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int railX = Math.max(16, canvasX0 - 76);
        int railY = canvasY0;
        int railW = 56;
        int railH = canvasPx;
        UiFx.fancyPanel(g, railX, railY, railX + railW, railY + railH, 0xFF1C0F3C, 0xFF0C0524, 0xFF6A4CFF, 0xFF8D6CFF);

        int cx = railX + railW / 2;
        int y = railY + 14;
        Component lbl = Component.translatable("drawitemget.ui.tools").withStyle(ChatFormatting.LIGHT_PURPLE);
        g.centeredText(this.font, lbl, cx, y, 0xFFD2B7FF);
        y += 16;

        // Tool buttons.
        for (Tool t : Tool.values()) {
            boolean selected = tool == t;
            drawToolButton(g, railX + 8, y, railW - 16, 24, t.glyph(), t.label().getString(), selected, mouseX, mouseY);
            y += 28;
        }

        y += 6;
        g.fill(railX + 8, y, railX + railW - 8, y + 1, 0xFF6A4CFF);
        y += 8;

        Component blbl = Component.translatable("drawitemget.ui.brush").withStyle(ChatFormatting.LIGHT_PURPLE);
        g.centeredText(this.font, blbl, cx, y, 0xFFD2B7FF);
        y += 14;

        // Brush size selector (6 buttons 2 per row).
        for (int i = 0; i < BRUSH_SIZES.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int bx = railX + 6 + col * 22;
            int by = y + row * 22;
            drawBrushButton(g, bx, by, 20, 20, BRUSH_SIZES[i], i == brushIndex);
        }
        y += ((BRUSH_SIZES.length + 1) / 2) * 22 + 6;

        // Undo / Redo / Clear.
        drawTextButton(g, railX + 8, y, railW - 16, 18, "↶", !undo.isEmpty()); y += 22;
        drawTextButton(g, railX + 8, y, railW - 16, 18, "↷", !redo.isEmpty()); y += 22;
        drawTextButton(g, railX + 8, y, railW - 16, 18, "✕", true);
    }

    private void drawToolButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String glyph, String label,
                                boolean selected, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int top = selected ? 0xFF4E2E9E : hover ? 0xFF2E1968 : 0xFF1B0F3C;
        int bot = selected ? 0xFF27146B : hover ? 0xFF1B0F3C : 0xFF100724;
        UiFx.vGradient(g, x, y, x + w, y + h, top, bot);
        g.fill(x, y, x + w, y + 1, selected ? 0xFFFFD060 : 0xFF6A4CFF);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF080418);
        g.centeredText(this.font, Component.literal(glyph)
                .withStyle(selected ? ChatFormatting.GOLD : ChatFormatting.WHITE), x + w / 2, y + 6, 0xFFFFFFFF);
    }

    private void drawBrushButton(GuiGraphicsExtractor g, int x, int y, int w, int h, int size, boolean selected) {
        int top = selected ? 0xFF4E2E9E : 0xFF1B0F3C;
        int bot = selected ? 0xFF27146B : 0xFF100724;
        UiFx.vGradient(g, x, y, x + w, y + h, top, bot);
        g.fill(x, y, x + w, y + 1, selected ? 0xFFFFD060 : 0xFF6A4CFF);
        int dotR = Math.min((w - 4) / 2, Math.max(1, size / 2 + 1));
        UiFx.fillCircle(g, x + w / 2, y + h / 2, dotR, 0xFFE4C6FF);
    }

    private void drawTextButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean enabled) {
        int top = enabled ? 0xFF321B78 : 0xFF1B0F3C;
        int bot = enabled ? 0xFF1B0F3C : 0xFF100724;
        UiFx.vGradient(g, x, y, x + w, y + h, top, bot);
        g.fill(x, y, x + w, y + 1, enabled ? 0xFFBFA0FF : 0xFF3A2A6A);
        int tc = enabled ? 0xFFFFFFFF : 0xFF555070;
        g.centeredText(this.font, Component.literal(label), x + w / 2, y + (h - 8) / 2, tc);
    }

    private void renderPalette(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int railX = Math.min(this.width - 72, canvasX0 + canvasPx + 20);
        int railY = canvasY0;
        int railW = 56;
        int railH = canvasPx;
        UiFx.fancyPanel(g, railX, railY, railX + railW, railY + railH, 0xFF1C0F3C, 0xFF0C0524, 0xFF6A4CFF, 0xFF8D6CFF);

        int cx = railX + railW / 2;
        int y = railY + 14;
        Component lbl = Component.translatable("drawitemget.ui.palette").withStyle(ChatFormatting.LIGHT_PURPLE);
        g.centeredText(this.font, lbl, cx, y, 0xFFD2B7FF);
        y += 16;

        // 16 palette swatches in 4 columns × 4 rows.
        int sw = 20, sh = 20;
        int ox = railX + (railW - sw * 2 - 4) / 2;
        for (int i = 0; i < PALETTE.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int sx = ox + col * (sw + 4);
            int sy = y + row * (sh + 4);
            boolean selected = PALETTE[i] == currentColor;
            boolean hover = mouseX >= sx && mouseX < sx + sw && mouseY >= sy && mouseY < sy + sh;
            // Shadow + swatch.
            g.fill(sx + 1, sy + 1, sx + sw + 1, sy + sh + 1, 0x50000000);
            g.fill(sx, sy, sx + sw, sy + sh, PALETTE[i]);
            if (selected) {
                g.fill(sx - 2, sy - 2, sx + sw + 2, sy - 1, 0xFFFFD060);
                g.fill(sx - 2, sy + sh + 1, sx + sw + 2, sy + sh + 2, 0xFFFFD060);
                g.fill(sx - 2, sy - 2, sx - 1, sy + sh + 2, 0xFFFFD060);
                g.fill(sx + sw + 1, sy - 2, sx + sw + 2, sy + sh + 2, 0xFFFFD060);
            } else if (hover) {
                g.fill(sx - 1, sy - 1, sx + sw + 1, sy, 0xFFE4C6FF);
                g.fill(sx - 1, sy + sh, sx + sw + 1, sy + sh + 1, 0xFFE4C6FF);
                g.fill(sx - 1, sy - 1, sx, sy + sh + 1, 0xFFE4C6FF);
                g.fill(sx + sw, sy - 1, sx + sw + 1, sy + sh + 1, 0xFFE4C6FF);
            }
        }
        int paletteEndY = y + ((PALETTE.length + 1) / 2) * (sh + 4) + 4;
        g.fill(railX + 8, paletteEndY, railX + railW - 8, paletteEndY + 1, 0xFF6A4CFF);

        // Current color preview.
        int prevY = paletteEndY + 8;
        Component label = Component.translatable("drawitemget.ui.current").withStyle(ChatFormatting.GRAY);
        g.centeredText(this.font, label, cx, prevY, 0xFFBFA0FF);
        prevY += 12;
        int prevW = 32, prevH = 20;
        int px = cx - prevW / 2;
        // Animated glow behind it.
        long t = System.currentTimeMillis() - openedAtMs;
        double pulse = 0.5 + 0.5 * Math.sin(t / 220.0);
        int glow = UiFx.withAlpha(currentColor, (int) (0x40 + 0x70 * pulse));
        g.fill(px - 3, prevY - 3, px + prevW + 3, prevY + prevH + 3, glow);
        g.fill(px, prevY, px + prevW, prevY + prevH, currentColor);
        g.fill(px, prevY, px + prevW, prevY + 1, UiFx.brighten(currentColor, 0.4f));
        g.fill(px, prevY + prevH - 1, px + prevW, prevY + prevH, UiFx.darken(currentColor, 0.5f));
    }

    private void renderFooter(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int w = this.width;
        int y0 = canvasY0 + canvasPx + 16;
        if (y0 + 40 > this.height - 4) y0 = this.height - 48;
        int btnW = 240, btnH = 30;
        int btnX = (w - btnW) / 2;
        boolean hover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= y0 && mouseY < y0 + btnH;
        int top = hover ? 0xFFFFD060 : 0xFFB89128;
        int bot = hover ? 0xFFB89128 : 0xFF5E4710;
        UiFx.fancyPanel(g, btnX, y0, btnX + btnW, y0 + btnH, top, bot, 0xFFFFF2B8, 0xFFFFF7D0);
        Component lbl = Component.translatable("drawitemget.ui.submit")
                .withStyle(ChatFormatting.BLACK, ChatFormatting.BOLD);
        g.centeredText(this.font, lbl, btnX + btnW / 2, y0 + (btnH - 8) / 2, 0xFF000000);

        // Close hint.
        Component hint = Component.translatable("drawitemget.ui.hint_close").withStyle(ChatFormatting.DARK_GRAY);
        g.centeredText(this.font, hint, w / 2, y0 + btnH + 6, 0xFF887AA8);
    }

    private void renderScanOverlay(GuiGraphicsExtractor g) {
        long t = System.currentTimeMillis() - lastScanMs;
        float phase = Math.min(1f, t / 900f);
        int x0 = canvasX0, y0 = canvasY0;
        int x1 = x0 + canvasPx, y1 = y0 + canvasPx;
        int lineY = y0 + (int) (canvasPx * phase);

        // Dim canvas.
        g.fill(x0, y0, x1, y1, 0x500E0730);
        // Scan line with glow.
        for (int k = 8; k >= 1; k--) {
            int a = 0x10 + (8 - k) * 0x08;
            g.fill(x0, lineY - k, x1, lineY + k, (a << 24) | 0x8D6CFF);
        }
        g.fill(x0, lineY - 1, x1, lineY + 2, 0xFFFFD060);

        // Sparkles trailing.
        long now = System.currentTimeMillis();
        for (int i = 0; i < 18; i++) {
            double r = (now / 20.0 + i * 37) % canvasPx;
            int sx = x0 + (int) r;
            int sy = lineY + (int) (Math.sin((now + i * 90) / 80.0) * 6);
            g.fill(sx, sy, sx + 2, sy + 2, 0xFFFFE6A0);
        }

        if (phase >= 1f) {
            submitted = false;
        }
    }

    // ================================================================
    //   INPUT
    // ================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean doubleClick) {
        double mx = ev.x();
        double my = ev.y();
        int button = ev.button();
        if (button != 0 && button != 1) return super.mouseClicked(ev, doubleClick);

        int ix = (int) mx, iy = (int) my;

        // Palette swatches.
        int railX = Math.min(this.width - 72, canvasX0 + canvasPx + 20);
        int railY = canvasY0;
        int railW = 56;
        int y = railY + 14 + 16;
        int sw = 20, sh = 20;
        int ox = railX + (railW - sw * 2 - 4) / 2;
        for (int i = 0; i < PALETTE.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int sx = ox + col * (sw + 4);
            int sy = y + row * (sh + 4);
            if (ix >= sx && ix < sx + sw && iy >= sy && iy < sy + sh) {
                currentColor = PALETTE[i];
                if (tool == Tool.ERASER) tool = Tool.BRUSH;
                return true;
            }
        }

        // Tool rail.
        int toolX = Math.max(16, canvasX0 - 76);
        int toolY = railY + 14 + 16;
        for (Tool t : Tool.values()) {
            int bx = toolX + 8, by = toolY, bw = 56 - 16, bh = 24;
            if (ix >= bx && ix < bx + bw && iy >= by && iy < by + bh) {
                tool = t;
                return true;
            }
            toolY += 28;
        }
        toolY += 6 + 1 + 8 + 14; // divider + label
        // Brush sizes.
        for (int i = 0; i < BRUSH_SIZES.length; i++) {
            int col = i % 2, row = i / 2;
            int bx = toolX + 6 + col * 22, by = toolY + row * 22, bw = 20, bh = 20;
            if (ix >= bx && ix < bx + bw && iy >= by && iy < by + bh) {
                brushIndex = i;
                return true;
            }
        }
        int afterBrushY = toolY + ((BRUSH_SIZES.length + 1) / 2) * 22 + 6;
        // Undo/redo/clear.
        int btnW = 40, btnH = 18;
        int bx = toolX + 8;
        if (ix >= bx && ix < bx + btnW && iy >= afterBrushY && iy < afterBrushY + btnH) {
            if (!undo.isEmpty()) {
                redo.push(pixels.clone());
                int[] prev = undo.pop();
                System.arraycopy(prev, 0, pixels, 0, pixels.length);
            }
            return true;
        }
        afterBrushY += 22;
        if (ix >= bx && ix < bx + btnW && iy >= afterBrushY && iy < afterBrushY + btnH) {
            if (!redo.isEmpty()) {
                undo.push(pixels.clone());
                int[] next = redo.pop();
                System.arraycopy(next, 0, pixels, 0, pixels.length);
            }
            return true;
        }
        afterBrushY += 22;
        if (ix >= bx && ix < bx + btnW && iy >= afterBrushY && iy < afterBrushY + btnH) {
            snapshotForUndo();
            clearCanvas(0);
            return true;
        }

        // Submit button.
        int y0 = canvasY0 + canvasPx + 16;
        if (y0 + 40 > this.height - 4) y0 = this.height - 48;
        int sbW = 240, sbH = 30;
        int sbX = (this.width - sbW) / 2;
        if (ix >= sbX && ix < sbX + sbW && iy >= y0 && iy < y0 + sbH) {
            submit();
            return true;
        }

        // Canvas.
        if (ix >= canvasX0 && ix < canvasX0 + canvasPx && iy >= canvasY0 && iy < canvasY0 + canvasPx) {
            int cx = (ix - canvasX0) / cell;
            int cy = (iy - canvasY0) / cell;
            snapshotForUndo();
            painting = true;
            handlePaint(cx, cy, button);
            lastPaintedCellX = cx;
            lastPaintedCellY = cy;
            return true;
        }

        return super.mouseClicked(ev, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent ev, double dx, double dy) {
        if (!painting) return super.mouseDragged(ev, dx, dy);
        double mx = ev.x();
        double my = ev.y();
        int button = ev.button();
        int ix = (int) mx, iy = (int) my;
        if (ix < canvasX0 || ix >= canvasX0 + canvasPx || iy < canvasY0 || iy >= canvasY0 + canvasPx)
            return true;
        int cx = (ix - canvasX0) / cell;
        int cy = (iy - canvasY0) / cell;
        if (lastPaintedCellX >= 0) interpolatePaint(lastPaintedCellX, lastPaintedCellY, cx, cy, button);
        else handlePaint(cx, cy, button);
        lastPaintedCellX = cx;
        lastPaintedCellY = cy;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent ev) {
        painting = false;
        lastPaintedCellX = lastPaintedCellY = -1;
        return super.mouseReleased(ev);
    }

    private void handlePaint(int cx, int cy, int button) {
        if (cx < 0 || cy < 0 || cx >= SIZE || cy >= SIZE) return;
        int color = (button == 1 || tool == Tool.ERASER) ? 0 : currentColor;
        switch (tool) {
            case BRUSH, ERASER -> paintDot(cx, cy, color, BRUSH_SIZES[brushIndex]);
            case BUCKET -> floodFill(cx, cy, button == 1 ? 0 : currentColor);
            case PICKER -> {
                int p = pixels[cy * SIZE + cx];
                if ((p >>> 24) != 0) currentColor = p | 0xFF000000;
            }
        }
    }

    private void interpolatePaint(int x0, int y0, int x1, int y1, int button) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0, y = y0;
        while (true) {
            handlePaint(x, y, button);
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 <  dx) { err += dx; y += sy; }
        }
    }

    private void paintDot(int cx, int cy, int color, int radius) {
        int r = radius;
        int r2 = r * r;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy > r2) continue;
                int x = cx + dx, y = cy + dy;
                if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) continue;
                pixels[y * SIZE + x] = color;
            }
        }
    }

    private void floodFill(int cx, int cy, int color) {
        int target = pixels[cy * SIZE + cx];
        if (target == color) return;
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.push(new int[]{cx, cy});
        while (!q.isEmpty()) {
            int[] cur = q.pop();
            int x = cur[0], y = cur[1];
            if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) continue;
            if (pixels[y * SIZE + x] != target) continue;
            pixels[y * SIZE + x] = color;
            q.push(new int[]{x + 1, y});
            q.push(new int[]{x - 1, y});
            q.push(new int[]{x, y + 1});
            q.push(new int[]{x, y - 1});
        }
    }

    @Override
    public boolean keyPressed(KeyEvent ev) {
        int key = ev.key();
        int modifiers = ev.modifiers();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (key == GLFW.GLFW_KEY_Z) {
                if (!undo.isEmpty()) {
                    redo.push(pixels.clone());
                    int[] prev = undo.pop();
                    System.arraycopy(prev, 0, pixels, 0, pixels.length);
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_Y) {
                if (!redo.isEmpty()) {
                    undo.push(pixels.clone());
                    int[] next = redo.pop();
                    System.arraycopy(next, 0, pixels, 0, pixels.length);
                }
                return true;
            }
        }
        return super.keyPressed(ev);
    }

    private void submit() {
        if (submitted) return;
        submitted = true;
        lastScanMs = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        ClientPlayNetworking.send(new DrawNetwork.SubmitDrawingPayload(pixels.clone()));
    }

    // ================================================================

    private enum Tool {
        BRUSH("✎"),
        ERASER("◻"),
        BUCKET("▣"),
        PICKER("◉");

        private final String glyph;
        Tool(String g) { this.glyph = g; }
        public String glyph() { return glyph; }
        public Component label() {
            return Component.translatable("drawitemget.ui.tool." + name().toLowerCase());
        }
    }
}
