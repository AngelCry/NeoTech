package com.dyonovan.neotech.client.gui.storage

import java.awt.Color

import com.dyonovan.neotech.common.container.storage.ContainerRFStorage
import com.dyonovan.neotech.common.tiles.storage.TileRFStorage
import com.dyonovan.neotech.utils.ClientUtils
import com.teambr.bookshelf.client.gui.component.display.{GuiComponentPowerBarGradient, GuiComponentText}
import com.teambr.bookshelf.client.gui.{GuiBase, GuiColor}
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.StatCollector

import scala.collection.mutable.ArrayBuffer

/**
 * This file was created for NeoTech
 *
 * NeoTech is licensed under the
 * Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License:
 * http://creativecommons.org/licenses/by-nc-sa/4.0/
 *
 * @author Dyonovan
 * @since August 15, 2015
 */
class GuiRFStorage(player: EntityPlayer, tileEntity: TileRFStorage, title: String) extends
    GuiBase[ContainerRFStorage](new ContainerRFStorage(player.inventory, tileEntity), 175, 165, title) {

    override def addComponents(): Unit = {
        if (tileEntity != null) {
            //Stored Energy
            components += new GuiComponentPowerBarGradient(8, 18, 18, 60, new Color(255, 0, 0)) {
                addColor(new Color(255, 150, 0))
                addColor(new Color(255, 255, 0))


                override def getEnergyPercent(scale: Int): Int = {
                    GlStateManager.enableBlend()
                    tileEntity.getEnergyStored(null) * scale / tileEntity.getMaxEnergyStored(null)
                }
                override def getDynamicToolTip(x: Int, y: Int): ArrayBuffer[String] = {
                    val buffer = new ArrayBuffer[String]()
                    buffer += GuiColor.ORANGE + StatCollector.translateToLocal("neotech.text.redstoneFlux")
                    buffer += ClientUtils.formatNumber(tileEntity.getEnergyStored(null)) + " / " +
                            ClientUtils.formatNumber(tileEntity.getMaxEnergyStored(null)) + " RF"
                    buffer
                }
            }

            components += new GuiComponentText(StatCollector.translateToLocal("neotech.text.rfIn"), 55, 20, new Color(0, 0, 0))
            val inputRate = tileEntity.getRFInPerTick
            val colorIn = {
                if(inputRate > 0)
                    new Color(0, 150, 0)
                else if(inputRate < 0)
                    new Color(150, 0, 0)
                else
                    new Color(0, 0, 0)
            }
            components += new GuiComponentText("   " + inputRate.toString + " RF/tick", 55, 30, colorIn) {

                /**
                  * Called after base render, is already translated to guiLeft and guiTop, just move offset
                  */
                override def renderOverlay(guiLeft: Int, guiTop: Int, mouseX : Int, mouseY : Int): Unit ={
                    val inputRate = tileEntity.getRFInPerTick
                    val colorIn = {
                        if(inputRate > 0)
                            new Color(0, 150, 0)
                        else if(inputRate < 0)
                            new Color(150, 0, 0)
                        else
                            new Color(0, 0, 0)
                    }
                    setText("   " + inputRate.toString + " RF/tick")
                    color = colorIn
                    super.renderOverlay(guiLeft, guiTop, mouseX, mouseY)
                }
            }

            components += new GuiComponentText(StatCollector.translateToLocal("neotech.text.rfOut"), 55, 50, new Color(0, 0, 0))
            val outputRate = tileEntity.getRFOutPerTick
            val colorOut = {
                if(outputRate > 0)
                    new Color(0, 150, 0)
                else if(outputRate < 0)
                    new Color(150, 0, 0)
                else
                    new Color(0, 0, 0)
            }
            components += new GuiComponentText("   " + inputRate.toString + " RF/tick", 55, 60, colorOut) {
                /**
                  * Called after base render, is already translated to guiLeft and guiTop, just move offset
                  */
                override def renderOverlay(guiLeft: Int, guiTop: Int, mouseX : Int, mouseY : Int): Unit ={
                    val outputRate = tileEntity.getRFOutPerTick
                    val colorOut = {
                        if(outputRate > 0)
                            new Color(0, 150, 0)
                        else if(outputRate < 0)
                            new Color(150, 0, 0)
                        else
                            new Color(0, 0, 0)
                    }
                    setText("   " + outputRate.toString + " RF/tick")
                    color = colorOut
                    super.renderOverlay(guiLeft, guiTop, mouseX, mouseY)
                }
            }
        }
    }
}
