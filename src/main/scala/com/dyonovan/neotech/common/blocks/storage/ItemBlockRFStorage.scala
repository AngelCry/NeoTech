package com.dyonovan.neotech.common.blocks.storage

import com.dyonovan.neotech.managers.BlockManager
import com.teambr.bookshelf.client.gui.GuiColor
import com.teambr.bookshelf.common.items.traits.ItemBattery
import net.minecraft.block.Block
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.{ItemBlock, ItemStack}
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

/**
 * This file was created for NeoTech
 *
 * NeoTech is licensed under the
 * Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License:
 * http://creativecommons.org/licenses/by-nc-sa/4.0/
 *
 * @author Dyonovan
 * @since August 16, 2015
 */
class ItemBlockRFStorage(block: Block) extends ItemBlock(block) with ItemBattery {

    setNoRepair()
    setMaxStackSize(1)
    setHasSubtypes(true)

    val info = getEnergyInfo
    override var capacity: Int = info._2
    override var maxExtract: Int = info._2
    override var maxReceive: Int = info._2

    @SideOnly(Side.CLIENT)
    override def addInformation(stack: ItemStack, player: EntityPlayer, list: java.util.List[String], boolean: Boolean): Unit = {
        if (getEnergyInfo._1 != 4) {
            if (stack.hasTagCompound) {
                if (stack.getTagCompound.getInteger("Energy") != 0) {
                    list.asInstanceOf[java.util.List[String]].add(GuiColor.ORANGE + (stack.getTagCompound.getInteger("Energy") + "/" + getEnergyInfo._2 + " RF"))
                } else list.asInstanceOf[java.util.List[String]].add(GuiColor.RED + "0/" + getEnergyInfo._2 + " RF")
            } else list.asInstanceOf[java.util.List[String]].add(GuiColor.RED + "0/" + getEnergyInfo._2 + " RF")
        }
    }

    private def getEnergyInfo: (Int, Int, Int) = {
        block match {
            case BlockManager.basicRFStorage => (1, 25000, 200)
            case BlockManager.advancedRFStorage => (2, 1000000, 1000)
            case BlockManager.eliteRFStorage => (3, 10000000, 10000)
            case BlockManager.creativeRFStorage => (4, 100000000, 100000)
            case _ => (1, 25000, 200)
        }
    }
}

