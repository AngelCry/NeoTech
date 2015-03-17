package com.dyonovan.jatm.client.gui.generators;

import com.dyonovan.jatm.common.container.generators.ContainerGenerator;
import com.dyonovan.jatm.common.tileentity.generator.TileGenerator;
import com.dyonovan.jatm.lib.Constants;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

public class GuiGenerator extends GuiContainer {

    private TileGenerator tile;
    private ResourceLocation background = new ResourceLocation(Constants.MODID + ":textures/gui/generator.png");

    public GuiGenerator(InventoryPlayer inventoryPlayer, TileGenerator tileGenerator) {
        super(new ContainerGenerator(inventoryPlayer, tileGenerator));

        this.tile = tileGenerator;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        final String invTitle = "RF Generator";
        fontRendererObj.drawString(invTitle, (((ySize + 10) - fontRendererObj.getStringWidth(invTitle)) / 2), 6, 4210752);
        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"), 5, ySize - 96 + 2, 4210752);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int i, int j) {

        int x = (width - xSize) / 2;
        int y = (height - ySize) / 2;

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(background);
        drawTexturedModalRect(x, y, 0, 0, xSize, ySize);

        //RF Energy bar
        int widthRF = tile.energyRF.getEnergyStored() * 52 / tile.energyRF.getMaxEnergyStored();
        drawTexturedModalRect(x + 62, y + 18, 176, 14, widthRF, 15);

        //Buring Bar
        int heightBurn = tile.currentBurnTime == 0 ? 13 : tile.currentBurnTime * 13 / tile.totalBurnTime;
        drawTexturedModalRect(x + 81,   y + 37 + heightBurn,   176,    heightBurn,    14,     14 - heightBurn);

    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float par3) {
        super.drawScreen(mouseX, mouseY, par3);
    }
}
