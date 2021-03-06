package com.dyonovan.neotech.common.tiles

import com.dyonovan.neotech.collections.InputOutput
import com.dyonovan.neotech.common.blocks.traits.Upgradeable
import com.teambr.bookshelf.common.tiles.traits.{EnergyHandler, InventorySided, RedstoneAware, Syncable}
import com.teambr.bookshelf.util.InventoryUtils
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.{EnumFacing, StatCollector}
import net.minecraft.world.World

/**
  * This file was created for NeoTech
  *
  * NeoTech is licensed under the
  * Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License:
  * http://creativecommons.org/licenses/by-nc-sa/4.0/
  *
  * @author Dyonovan
  * @since August 11, 2015
  */
abstract class AbstractMachine extends Syncable with Upgradeable with InventorySided
        with RedstoneAware with InputOutput with EnergyHandler {

    var redstone : Int      = 0
    val REDSTONE_FIELD_ID   = 0
    val IO_FIELD_ID         = 1
    val UPDATE_CLIENT       = 2
    val ENERGY_UPDATE       = 3

    def BASE_ENERGY         = 10000

    var working             = false

    /**
      * Used to define the default energy storage for this energy handler
      *
      * @return
      */
    def defaultEnergyStorageSize : Int = BASE_ENERGY

    /**
      * Used to get what particles to spawn. This will be called when the tile is active
      */
    def spawnActiveParticles(xPos: Double, yPos: Double, zPos: Double)

    /**
      * Used to get what slots are allowed to be output
      *
      * @return The slots to output from
      */
    def getOutputSlots : Array[Int]

    /**
      * Used to get what slots are allowed to be input
      *
      * @return The slots to input from
      */
    def getInputSlots : Array[Int]

    /**
      * Used to output the redstone single from this structure
      *
      * Use a range from 0 - 16.
      *
      * 0 Usually means that there is nothing in the tile, so take that for lowest level. Like the generator has no energy while
      * 16 is usually the flip side of that. Output 16 when it is totally full and not less
      *
      * @return int range 0 - 16
      */
    def getRedstoneOutput: Int

    /**
      * Used to actually do the processes needed. For processors this should be cooking items and generators should
      * generate RF. This is called every tick allowed, provided redstone mode requirements are met
      */
    protected def doWork(): Unit

    /**
      * Use this to set all variables back to the default values, usually means the operation failed
      */
    def reset() : Unit

    /**
      * Used to check if this tile is active or not
      *
      * @return True if active state
      */
    def isActive: Boolean

    /**
      * The initial size of the inventory
      *
      * @return
      */
    override def initialSize: Int

    /**
      * Used to get the information to display on the tabs in machines. This can be the unlocalized version
      */
    def getDescription : String = getBlockType.getUnlocalizedName + ".description"

    /**
      * Return the container for this tile
      *
      * @param ID Id, probably not needed but could be used for multiple guis
      * @param player The player that is opening the gui
      * @param world The world
      * @param x X Pos
      * @param y Y Pos
      * @param z Z Pos
      * @return The container to open
      */
    def getServerGuiElement(ID: Int, player: EntityPlayer, world: World, x: Int, y: Int, z: Int): AnyRef = null

    /**
      * Return the gui for this tile
      *
      * @param ID Id, probably not needed but could be used for multiple guis
      * @param player The player that is opening the gui
      * @param world The world
      * @param x X Pos
      * @param y Y Pos
      * @param z Z Pos
      * @return The gui to open
      */
    def getClientGuiElement(ID: Int, player: EntityPlayer, world: World, x: Int, y: Int, z: Int): AnyRef = null

    /** ****************************************************************************************************************
      * *************************************************  Tile Methods  ************************************************
      * *****************************************************************************************************************/

    /**
      * We want to make sure the client knows what to render
      */
    override def onClientTick() : Unit = {
        super[EnergyHandler].onClientTick()
        if(getSupposedEnergy != energyStorage.getMaxEnergyStored)
            sendValueToServer(UPDATE_CLIENT, 0)
        //Mark for render update if needed
        if(working != isActive)
            worldObj.markBlockRangeForRenderUpdate(pos, pos)
        working = isActive
    }

    var timeTicker = 0
    override def onServerTick(): Unit = {
        //Make sure our energy storage is correct
        if(getSupposedEnergy != energyStorage.getMaxEnergyStored)
            changeEnergy(energyStorage.getEnergyStored)

        //If redstone mode is not matched, break out of the method
        if(getUpgradeBoard != null && getUpgradeBoard.hasControl) {
            if(redstone == -1 && isPowered)
                return
            if(redstone == 1 && !isPowered)
                return
        }

        //We want to try automatic IO if we are able to once a tick
        if(shouldHandleIO && timeTicker <= 0 && getUpgradeBoard != null && getUpgradeBoard.hasExpansion) {
            timeTicker = 20
            tryInput()
            tryOutput()
        }
        timeTicker -= 1

        //Do what we are programed to do
        doWork()
    }

    /**
      * This will try to take things from our inventory and try to place them in others
      */
    def tryOutput() : Unit = {
        for(dir <- EnumFacing.values) {
            if(canOutputFromSide(dir)) {
                for(slot <- getOutputSlots)
                    InventoryUtils.moveItemInto(this, slot, worldObj.getTileEntity(pos.offset(dir)), -1, 64, dir, doMove = true, checkSidedSource = false)
            }
        }
    }

    /**
      * This will try to take things from other inventories and put it into ours
      */
    def tryInput() : Unit = {
        for(dir <- EnumFacing.values) {
            if(canInputFromSide(dir)) {
                for (x <- getInputSlots) {
                    InventoryUtils.moveItemInto(worldObj.getTileEntity(pos.offset(dir)), -1, this, x, 64, dir.getOpposite, doMove = true, checkSidedTarget = false)
                }
            }
        }
    }

    /**
      * Used to manually disable the IO rendering on tile, true by default
      *
      * @return False to prevent rendering
      */
    def shouldRenderInputOutputOnTile = shouldHandleIO

    /**
      * Used to specify if this tile should handle IO, and render in GUI
      *
      * @return False to prevent
      */
    def shouldHandleIO = true

    /**
      * Write the tag
      */
    override def writeToNBT(tag: NBTTagCompound): Unit = {
        super[Upgradeable].writeToNBT(tag)
        super[TileEntity].writeToNBT(tag)
        super[InventorySided].writeToNBT(tag)
        super[InputOutput].writeToNBT(tag)
        super[EnergyHandler].writeToNBT(tag)
        tag.setInteger("RedstoneMode", redstone)
        if(updateClient && worldObj != null) {
            tag.setBoolean("UpdateEnergy", true)
            updateClient = false
        }
    }

    /**
      * Read the tag
      */
    override def readFromNBT(tag: NBTTagCompound): Unit = {
        super[Upgradeable].readFromNBT(tag)
        super[TileEntity].readFromNBT(tag)
        super[InventorySided].readFromNBT(tag)
        super[InputOutput].readFromNBT(tag)
        super[EnergyHandler].readFromNBT(tag)
        if(tag.hasKey("UpdateEnergy") && worldObj != null  )
            changeEnergy(tag.getInteger("Energy"))
        redstone = tag.getInteger("RedstoneMode")
    }

    /**
      * Called when the board is removed, reset to default values for any upgrades
      */
    override def resetValues(): Unit = {
        resetIO()
        redstone = 0
    }

    /*******************************************************************************************************************
      ************************************************ Energy methods **************************************************
      ******************************************************************************************************************/

    //Tag to let us know if we need to send info to the client
    var updateClient = false

    /**
      * Used to change the energy to a new storage with a different size
      *
      * @param initial How much was in the old storage
      */
    def changeEnergy(initial : Int): Unit = {
        if(getUpgradeBoard != null && getUpgradeBoard.getHardDriveCount > 0) {
            setMaxEnergyStored(BASE_ENERGY * (getUpgradeBoard.getHardDriveCount * 10))
        }
        else {
            setMaxEnergyStored(BASE_ENERGY)
        }
        updateClient = true
        worldObj.markBlockForUpdate(pos)
    }

    /**
      * Used to determine how much energy should be in this tile
      *
      * @return How much energy should be available
      */
    def getSupposedEnergy : Int = {
        if(getUpgradeBoard != null && getUpgradeBoard.getHardDriveCount > 0)
            BASE_ENERGY * (getUpgradeBoard.getHardDriveCount * 10)
        else
            BASE_ENERGY
    }

    /*******************************************************************************************************************
      ********************************************** Syncable methods **************************************************
      ******************************************************************************************************************/

    /**
      * Used to set the variable for this tile, the Syncable will use this when you send a value to the server
      *
      * @param id The ID of the variable to send
      * @param value The new value to set to (you can use this however you want, eg using the ordinal of EnumFacing)
      */
    override def setVariable(id : Int, value : Double): Unit = {
        id match {
            case REDSTONE_FIELD_ID => redstone = value.toInt
            case IO_FIELD_ID       => toggleMode(EnumFacing.getFront(value.toInt))
            case UPDATE_CLIENT     => updateClient = true
            case ENERGY_UPDATE     => energyStorage.setEnergyStored(value.toInt)
            case _ => //No Operation, not defined ID
        }
    }

    /**
      * Used to get the variable
      *
      * @param id The variable ID
      * @return The value of the variable
      */
    override def getVariable(id : Int) : Double = { 0.0 }

    /**
      * Moves the current redstone mode in either direction
      *
      * @param mod The direction to move. This will move it in that direction as many as provided, usually 1
      *            Positive : To the right
      *            Negative : To the left
      */
    def moveRedstoneMode(mod : Int) : Unit = {
        redstone += mod
        if(redstone < -1)
            redstone = 1
        else if(redstone > 1)
            redstone = -1
    }

    /**
      * Get's the display name for the current mode
      *
      * @return The translated name to display
      */
    def getRedstoneModeName : String = {
        redstone match {
            case -1 => StatCollector.translateToLocal("neotech.text.low")
            case 0  =>  StatCollector.translateToLocal("neotech.text.disabled")
            case 1  =>  StatCollector.translateToLocal("neotech.text.high")
            case _  =>  StatCollector.translateToLocal("neotech.text.error")
        }
    }

    /**
      * Set the mode manually
      *
      * @param newMode The new mode to set to
      */
    def setRedstoneMode(newMode : Int) : Unit = {
        this.redstone = newMode
    }
}
