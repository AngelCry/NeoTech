package com.dyonovan.neotech.client.gui.machines.processors

import java.awt.Color
import java.text.NumberFormat
import java.util.Locale

import com.dyonovan.neotech.client.gui.machines.GuiAbstractMachine
import com.dyonovan.neotech.common.container.machines.processors.ContainerThermalBinder
import com.dyonovan.neotech.common.tiles.machines.processors.TileThermalBinder
import com.dyonovan.neotech.utils.ClientUtils
import com.teambr.bookshelf.client.gui.GuiColor
import com.teambr.bookshelf.client.gui.component.control.GuiComponentButton
import com.teambr.bookshelf.client.gui.component.display.{GuiComponentFluidTank, GuiComponentArrow, GuiComponentPowerBarGradient, GuiComponentText}
import net.minecraft.client.Minecraft
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
  * @since August 21, 2015
  */
class GuiThermalBinder (player: EntityPlayer, tileEntity: TileThermalBinder) extends
        GuiAbstractMachine[ContainerThermalBinder](new ContainerThermalBinder(player.inventory, tileEntity), 175, 185,
            "neotech.thermalbinder.title", player, tileEntity) {

    override def addComponents(): Unit = {
        components += new GuiComponentPowerBarGradient(14, 18, 18, 60, new Color(255, 0, 0)) {
            addColor(new Color(255, 150, 0))
            addColor(new Color(255, 255, 0))


            override def getEnergyPercent(scale: Int): Int = {
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

        //Stored Fluid
        components += new GuiComponentFluidTank(150, 18, 18, 50, tileEntity.tanks(tileEntity.TIN_TANK)) {
            override def getDynamicToolTip(x: Int, y: Int): ArrayBuffer[String] = {
                val buffer = new ArrayBuffer[String]()
                buffer += (if(tileEntity.tanks(tileEntity.TIN_TANK).getFluid != null)
                    GuiColor.ORANGE + tileEntity.tanks(tileEntity.TIN_TANK).getFluid.getLocalizedName
                else
                    GuiColor.RED + "Empty")
                buffer += ClientUtils.formatNumber(tileEntity.tanks(tileEntity.TIN_TANK).getFluidAmount) + " / " +
                        ClientUtils.formatNumber(tileEntity.tanks(tileEntity.TIN_TANK).getCapacity) + " mb"
                buffer
            }
        }

        components += new GuiComponentButton(120, 75, 40, 20, "neotech.text.start") {
            override def doAction(): Unit = {
                tileEntity.isRunning = true
                if (tileEntity.getStackInSlot(tileEntity.OBJECT_INPUT) != null && tileEntity.hasValidInput) {
                    if (!Minecraft.getMinecraft.thePlayer.capabilities.isCreativeMode)
                        tileEntity.sendValueToServer(tileEntity.RUNNING_VARIABLE_ID, 0)
                    else
                        tileEntity.sendValueToServer(tileEntity.BUILD_NOW_ID, 0)
                }
            }
        }

        components += new GuiComponentArrow(76, 45) {
            override def getCurrentProgress: Int = tileEntity.getCookProgressScaled(24)
            override def getDynamicToolTip(x: Int, y: Int): ArrayBuffer[String] = {
                ArrayBuffer( NumberFormat.getNumberInstance(Locale.forLanguageTag(Minecraft.getMinecraft.gameSettings.language))
                        .format(tileEntity.getCookTime - tileEntity.cookTime) + " ticks left")
            }
        }

        components += new GuiComponentText(GuiColor.BLACK + StatCollector.translateToLocal("neotech.text.in"), 41, 63)
        components += new GuiComponentText(GuiColor.BLACK + StatCollector.translateToLocal("neotech.text.out"), 123, 63)
        components += new GuiComponentText(GuiColor.BLACK + StatCollector.translateToLocal("neotech.text.upgrade"), 65, 87)
    }
}
