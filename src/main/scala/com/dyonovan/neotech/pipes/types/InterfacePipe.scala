package com.dyonovan.neotech.pipes.types

import java.util

import com.dyonovan.neotech.pipes.collections.WorldPipes
import com.dyonovan.neotech.pipes.entities.ResourceEntity
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.{AxisAlignedBB, BlockPos, EnumFacing, Vec3}
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

/**
  * This file was created for NeoTech
  *
  * NeoTech is licensed under the
  * Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License:
  * http://creativecommons.org/licenses/by-nc-sa/4.0/
  *
  * @author Paul Davis pauljoda
  * @since August 16, 2015
  */
trait InterfacePipe[T, R <: ResourceEntity[T]] extends AdvancedPipe {

    /**
      * Used to get the information to display on the tabs in machines. This can be the unlocalized version
      */
    def getDescription : String = getBlockType.getUnlocalizedName + ".description"

    /*******************************************************************************************************************
      ************************************** Extraction Methods ********************************************************
      ******************************************************************************************************************/

    WorldPipes.pipes.add(this)

    /**
      * Useful in round robin
      */
    var lastSink: Long = 0
    var shouldRefreshCache = true

    //Cache for locations
    val sinkPipes = new util.ArrayList[Long]()
    val sinkTiles = new util.ArrayList[Long]()

    //Used in path finding
    val distance: util.HashMap[Long, Integer] = new util.HashMap[Long, Integer]
    val parent: util.HashMap[Long, BlockPos] = new util.HashMap[Long, BlockPos]
    val queue: util.Queue[BlockPos] = new util.LinkedList[BlockPos]

    //Create a queue
    var nextResource: R = _

    //Set the initial cooldown to max
    var coolDown = getDelay

    //Our storage of resources
    var resources: util.ArrayList[R] = new util.ArrayList[R]()

    /**
      * Get how many ticks to 'cooldown' between operations.
      *
      * @return 20 = 1 second
      */
    def getDelay: Int

    /**
      * This is what is actually called to the child class. Here you should call your extractResources or whatever you want
      * this pipe to do on its action phase. The parent will not automatically call extract
      *
      * This is useful if you wish to set different modes and call different path finding
      */
    def doExtraction() : Unit

    /**
      * The first step in moving things. You should call this from doExtraction. This is an outside method so you can
      * have additional functions to the pipe besides just extracting. For example, a pipe that pulls items in the world
      */
    def tryExtractResources() : Unit

    /**
      * This is the speed to extract from. You should be calling this when building your resources to send.
      *
      * This is included as a reminder to the child to have variable speeds
      *
      * @return
      */
    def getSpeed : Double

    /**
      * This is called when we fail to send a resource. You should put the resource back where you found it or
      * add it to the world
      *
      * @param resource The returned resource
      */
    def returnResource(resource : R)

    /**
      * Used to add a resource
      */
    def addResource(resource: R): Unit = resources.add(resource)

    /**
      * Extracts on the current mode
      *
      * @return
      */
    def extractOnMode(resource : R, simulate : Boolean) : Boolean = {
        if(!isResourceValidForFilter(resource))
            return false
        mode match {
            case 0 => extractResourceOnShortestPath(resource, simulate)
            case 1 => extractResourceOnLongestPath(resource, simulate)
            case 2 => extractOnRoundRobin(resource, simulate)
            case _ => extractResourceOnShortestPath(resource, simulate)
        }
    }

    override def onServerTick() : Unit = {
        //Update our resources
        if(!resources.isEmpty) {
            val iterator = resources.iterator()
            while (iterator.hasNext) {
                val resource = iterator.next()
                if(getWorld != null)
                    resource.setWorld(getWorld)
                if ( resource == null || resource.isDead || resource.resource == null) {
                    resource.onDropInWorld()
                    iterator.remove()
                }
                else
                    resource.updateEntity()
            }
            getWorld.markBlockForUpdate(getPos)
        }

        coolDown -= 1
        if(coolDown <= 0) {
            if(getUpgradeBoard != null && getUpgradeBoard.hasControl) {
                if(redstone == -1 && isPowered)
                    return
                if(redstone == 1 && !isPowered)
                    return
            }
            coolDown = getDelay
            doExtraction()
        }
    }

    /**
      * Called when something passes by. We only care if the resource is being sent back to us
      */
    override def onResourceEnteredPipe(resource: ResourceEntity[_]): Unit = {
        resource match {
            case _ =>
        }
    }

    /**
      * Make sure we don't lose everything when we are broken
      */
    override def onPipeBroken(): Unit =
        for(i <- 0 until resources.size())
            resources.get(i).onDropInWorld()

    /**
      * Used to extract the resource on the shortest path possible
      *
      * This will handle finding things that will accept it and will pick the shortest path of all of them
      *
      * You can use this to simulate as well, you are responsible for removing from the tile
      *
      * @param resource The resource to check
      */
    def extractResourceOnShortestPath(resource: R, simulate: Boolean): Boolean = {
        //Sometimes we won't get anything, get lost
        if (resource == null)
            return false

        if (simulate) {
            if(shouldRefreshCache) {
                sinkPipes.clear()
                sinkTiles.clear()
                distance.clear()
                parent.clear()
                queue.clear()

                distance.put(getPosAsLong, 0) //We are right here
                parent.put(getPosAsLong, null) //No parent
                sinkPipes.add(getPosAsLong)

                queue.add(BlockPos.fromLong(getPosAsLong)) //Add ourselves

                //Search the graph
                while (!queue.isEmpty) {
                    val thisPos: BlockPos = queue.poll
                    getWorld.getTileEntity(thisPos) match {
                        //Make sure this is a pipe
                        case thisPipe: SimplePipe =>
                            for (facing <- EnumFacing.values) {
                                //Add children
                                if (thisPipe.canConnect(facing)) {
                                    val otherPos: BlockPos = thisPos.offset(facing)
                                    if (distance.get(otherPos.toLong) == null) {
                                        //If it hasn't already been added
                                        queue.add(otherPos)
                                        distance.put(otherPos.toLong, Integer.MAX_VALUE) //We will set the distance later
                                        parent.put(otherPos.toLong, null) //Also parent

                                        val newDistance: Int = (distance.get(thisPos.toLong) + thisPos.distanceSq(otherPos)).toInt
                                        //If our distance is less than what existed, replace
                                        if (newDistance < distance.get(otherPos.toLong)) {
                                            distance.put(otherPos.toLong, newDistance)
                                            parent.put(otherPos.toLong, thisPos)
                                        }

                                        getWorld.getTileEntity(otherPos) match {
                                            //Add to sinks
                                            case pipe: InterfacePipe[T, R] if pipe.frequency == frequency && doTypesMatch(pipe) =>
                                                if(!sinkPipes.contains(pipe.getPosAsLong))
                                                    sinkPipes.add(pipe.getPosAsLong)
                                            case _ =>
                                        }
                                    }
                                }
                            }
                        case _ =>
                    }
                }
                //Add all sink tiles
                for(x <- 0 until sinkPipes.size()) { //Iterate Sink Pipes
                val sinkPipe = sinkPipes.get(x) //Get sink pipe
                    for(j <- 0 until getWorld.getTileEntity(BlockPos.fromLong(sinkPipe)).asInstanceOf[InterfacePipe[T, R]].getAttachedSinks.size()) { //Get attached tiles
                    val tilePos = getWorld.getTileEntity(BlockPos.fromLong(sinkPipe)).asInstanceOf[InterfacePipe[T, R]].getAttachedSinks.get(j) //Get tile at side
                        distance.put(tilePos, distance.get(sinkPipe) + 1) //Add one to distance with pipe as base
                        parent.put(tilePos, BlockPos.fromLong(sinkPipe)) //Add pipe as parent
                        if(!sinkTiles.contains(tilePos))sinkTiles.add(tilePos)
                    }
                }

                shouldRefreshCache = false
            }

            //Find the shortest
            var destination = new BlockPos(getPos)
            var shortest = Integer.MAX_VALUE
            for (i <- 0 until sinkTiles.size()) {
                if (getWorld != null && getWorld.getTileEntity(BlockPos.fromLong(sinkTiles.get(i))) != null &&
                        parent.get(sinkTiles.get(i)) != null &&
                        getWorld.getTileEntity(parent.get(sinkTiles.get(i))) != null &&
                        getWorld.getTileEntity(parent.get(sinkTiles.get(i))).asInstanceOf[InterfacePipe[T, R]].willAcceptResource(resource, BlockPos.fromLong(sinkTiles.get(i)))) {
                    val d = BlockPos.fromLong(sinkTiles.get(i))
                    if (distance.get(d.toLong) < shortest) {
                        destination = d
                        shortest = distance.get(d.toLong)
                    }
                }
            }

            //Build the path to the shortest
            resource.pathQueue.clear()
            resource.destinationTile = destination
            resource.destinationPipe = parent.get(destination.toLong)
            var u: BlockPos = destination

            //Build path
            while (parent.get(u.toLong) != null) {
                resource.pathQueue.push(new Vec3(u.getX + 0.5, u.getY + 0.5, u.getZ + 0.5))
                u = parent.get(u.toLong)
            }
        }

        if (!resource.pathQueue.isEmpty) {
            //If we have a path add it
            if (!simulate) {
                resources.add(resource)
                queue.clear()
            } else {
                getWorld.getTileEntity(resource.destinationPipe).asInstanceOf[InterfacePipe[T, R]].resourceBeingExtracted(resource)
                nextResource = resource
            }
            true
        }
        else
            false
    }

    /**
      * Used to extract the resource on the longest path possible
      *
      * This will handle finding things that will accept it and will pick the longest path of all of them
      *
      * You can use this to simulate as well, you are responsible for removing from the tile
      *
      * @param resource The resource to check
      */
    def extractResourceOnLongestPath(resource: R, simulate: Boolean): Boolean = {
        //Sometimes we won't get anything, get lost
        if (resource == null)
            return false

        if (simulate) {
            if(shouldRefreshCache) {
                sinkPipes.clear()
                sinkTiles.clear()
                distance.clear()
                parent.clear()
                queue.clear()

                distance.put(getPosAsLong, 0) //We are right here
                parent.put(getPosAsLong, null) //No parent
                sinkPipes.add(getPosAsLong)

                queue.add(BlockPos.fromLong(getPosAsLong)) //Add ourselves

                //Search the graph
                while (!queue.isEmpty) {
                    val thisPos: BlockPos = queue.poll
                    getWorld.getTileEntity(thisPos) match {
                        //Make sure this is a pipe
                        case thisPipe: SimplePipe =>
                            for (facing <- EnumFacing.values) {
                                //Add children
                                if (thisPipe.canConnect(facing)) {
                                    val otherPos: BlockPos = thisPos.offset(facing)
                                    if (distance.get(otherPos.toLong) == null) {
                                        //If it hasn't already been added
                                        queue.add(otherPos)
                                        distance.put(otherPos.toLong, Integer.MAX_VALUE) //We will set the distance later
                                        parent.put(otherPos.toLong, null) //Also parent

                                        val newDistance: Int = (distance.get(thisPos.toLong) + thisPos.distanceSq(otherPos)).toInt
                                        //If our distance is less than what existed, replace
                                        if (newDistance < distance.get(otherPos.toLong)) {
                                            distance.put(otherPos.toLong, newDistance)
                                            parent.put(otherPos.toLong, thisPos)
                                        }

                                        getWorld.getTileEntity(otherPos) match {
                                            //Add to sinks
                                            case pipe: InterfacePipe[T, R] if pipe.frequency == frequency && doTypesMatch(pipe) =>
                                                if(!sinkPipes.contains(pipe.getPosAsLong))
                                                    sinkPipes.add(pipe.getPosAsLong)
                                            case _ =>
                                        }
                                    }
                                }
                            }
                        case _ =>
                    }
                }
                //Add all sink tiles
                for(x <- 0 until sinkPipes.size()) { //Iterate Sink Pipes
                val sinkPipe = sinkPipes.get(x) //Get sink pipe
                    for(j <- 0 until getWorld.getTileEntity(BlockPos.fromLong(sinkPipe)).asInstanceOf[InterfacePipe[T, R]].getAttachedSinks.size()) { //Get attached tiles
                    val tilePos = getWorld.getTileEntity(BlockPos.fromLong(sinkPipe)).asInstanceOf[InterfacePipe[T, R]].getAttachedSinks.get(j) //Get tile at side
                        distance.put(tilePos, distance.get(sinkPipe) + 1) //Add one to distance with pipe as base
                        parent.put(tilePos, BlockPos.fromLong(sinkPipe)) //Add pipe as parent
                        if(!sinkTiles.contains(tilePos))sinkTiles.add(tilePos)
                    }
                }

                shouldRefreshCache = false
            }

            //Find the longest
            var destination = new BlockPos(getPos)
            var longest = Integer.MIN_VALUE
            for (i <- 0 until sinkTiles.size()) {
                if (getWorld != null && getWorld.getTileEntity(BlockPos.fromLong(sinkTiles.get(i))) != null &&
                        parent.get(sinkTiles.get(i)) != null &&
                        getWorld.getTileEntity(parent.get(sinkTiles.get(i))) != null &&
                        getWorld.getTileEntity(parent.get(sinkTiles.get(i))).asInstanceOf[InterfacePipe[T, R]].willAcceptResource(resource, BlockPos.fromLong(sinkTiles.get(i)))) {
                    val d = BlockPos.fromLong(sinkTiles.get(i))
                    if (distance.get(d.toLong) > longest) {
                        destination = d
                        longest = distance.get(d.toLong)
                    }
                }
            }

            //Build the path to the shortest
            resource.pathQueue.clear()
            resource.destinationTile = destination
            resource.destinationPipe = parent.get(destination.toLong)
            var u: BlockPos = destination

            //Build path
            while (parent.get(u.toLong) != null) {
                resource.pathQueue.push(new Vec3(u.getX + 0.5, u.getY + 0.5, u.getZ + 0.5))
                u = parent.get(u.toLong)
            }
        }

        if (!resource.pathQueue.isEmpty) {
            //If we have a path add it
            if (!simulate) {
                resources.add(resource)
                queue.clear()
            } else {
                getWorld.getTileEntity(resource.destinationPipe).asInstanceOf[InterfacePipe[T, R]].resourceBeingExtracted(resource)
                nextResource = resource
            }
            true
        }
        else
            false
    }

    /**
      * Extracts the resource in a round robin path, you are responsible for moving resource
      *
      * @param resource The resource to send
      * @param simulate Attach and send or just simulate, true to simulate
      * @return True if valid source
      */
    def extractOnRoundRobin(resource: R, simulate: Boolean): Boolean = {
        //Sometimes we won't get anything, get lost
        if (resource == null)
            return false

        if (simulate) {
            if(shouldRefreshCache) {
                sinkPipes.clear()
                sinkTiles.clear()
                distance.clear()
                parent.clear()
                queue.clear()

                distance.put(getPosAsLong, 0) //We are right here
                parent.put(getPosAsLong, null) //No parent
                sinkPipes.add(getPosAsLong)

                queue.add(BlockPos.fromLong(getPosAsLong)) //Add ourselves

                //Search the graph
                while (!queue.isEmpty) {
                    val thisPos: BlockPos = queue.poll
                    getWorld.getTileEntity(thisPos) match {
                        //Make sure this is a pipe
                        case thisPipe: SimplePipe =>
                            for (facing <- EnumFacing.values) {
                                //Add children
                                if (thisPipe.canConnect(facing)) {
                                    val otherPos: BlockPos = thisPos.offset(facing)
                                    if (distance.get(otherPos.toLong) == null) {
                                        //If it hasn't already been added
                                        queue.add(otherPos)
                                        distance.put(otherPos.toLong, Integer.MAX_VALUE) //We will set the distance later
                                        parent.put(otherPos.toLong, null) //Also parent

                                        val newDistance: Int = (distance.get(thisPos.toLong) + thisPos.distanceSq(otherPos)).toInt
                                        //If our distance is less than what existed, replace
                                        if (newDistance < distance.get(otherPos.toLong)) {
                                            distance.put(otherPos.toLong, newDistance)
                                            parent.put(otherPos.toLong, thisPos)
                                        }

                                        getWorld.getTileEntity(otherPos) match {
                                            //Add to sinks
                                            case pipe: InterfacePipe[T, R] if pipe.frequency == frequency && doTypesMatch(pipe) =>
                                                if(!sinkPipes.contains(pipe.getPosAsLong))
                                                    sinkPipes.add(pipe.getPosAsLong)
                                            case _ =>
                                        }
                                    }
                                }
                            }
                        case _ =>
                    }
                }
                //Add all sink tiles
                for(x <- 0 until sinkPipes.size()) { //Iterate Sink Pipes
                val sinkPipe = sinkPipes.get(x) //Get sink pipe
                    for(j <- 0 until getWorld.getTileEntity(BlockPos.fromLong(sinkPipe)).asInstanceOf[InterfacePipe[T, R]].getAttachedSinks.size()) { //Get attached tiles
                    val tilePos = getWorld.getTileEntity(BlockPos.fromLong(sinkPipe)).asInstanceOf[InterfacePipe[T, R]].getAttachedSinks.get(j) //Get tile at side
                        distance.put(tilePos, distance.get(sinkPipe) + 1) //Add one to distance with pipe as base
                        parent.put(tilePos, BlockPos.fromLong(sinkPipe)) //Add pipe as parent
                        if(!sinkTiles.contains(tilePos))
                            sinkTiles.add(tilePos)
                    }
                }
                shouldRefreshCache = false
            }

            //Find the next source
            var destination: BlockPos = null
            var pickNext: Boolean = lastSink == 0
            val lastLastSink = lastSink
            for (i <- 0 until sinkTiles.size()) {
                if (getWorld != null && getWorld.getTileEntity(BlockPos.fromLong(sinkTiles.get(i))) != null &&
                        parent.get(sinkTiles.get(i)) != null &&
                        getWorld.getTileEntity(parent.get(sinkTiles.get(i))) != null &&
                        getWorld.getTileEntity(parent.get(sinkTiles.get(i))).asInstanceOf[InterfacePipe[T, R]].willAcceptResource(resource, BlockPos.fromLong(sinkTiles.get(i)))) {
                    if (pickNext) {
                        destination = BlockPos.fromLong(sinkTiles.get(i))
                        lastSink = sinkTiles.get(i)
                        pickNext = false
                    }
                    if (sinkTiles.get(i) == lastSink && destination == null)
                        pickNext = true
                }
            }

            if(destination == null && pickNext) {
                for (i <- 0 until sinkTiles.size()) {
                    if (getWorld != null && getWorld.getTileEntity(BlockPos.fromLong(sinkTiles.get(i))) != null &&
                            parent.get(sinkTiles.get(i)) != null &&
                            getWorld.getTileEntity(parent.get(sinkTiles.get(i))) != null &&
                            getWorld.getTileEntity(parent.get(sinkTiles.get(i))).asInstanceOf[InterfacePipe[T, R]].willAcceptResource(resource, BlockPos.fromLong(sinkTiles.get(i)))) {
                        if (pickNext) {
                            destination = BlockPos.fromLong(sinkTiles.get(i))
                            lastSink = sinkTiles.get(i)
                            pickNext = false
                        }
                        if (sinkTiles.get(i) == lastSink && destination == null)
                            pickNext = true
                    }
                }
            }
            if(destination == null) {
                lastSink = 0
                return false
            }

            if(!simulate)
                lastSink = lastLastSink

            //Build the path to the shortest
            resource.pathQueue.clear()
            resource.destinationTile = destination
            resource.destinationPipe = parent.get(destination.toLong)
            var u: BlockPos = destination
            //Build path
            while (parent.get(u.toLong) != null) {
                resource.pathQueue.push(new Vec3(u.getX + 0.5, u.getY + 0.5, u.getZ + 0.5))
                u = parent.get(u.toLong)
            }
        }

        if (!resource.pathQueue.isEmpty) {
            //If we have a path add it
            if (!simulate) {
                resources.add(resource)
                getWorld.getTileEntity(resource.destinationPipe).asInstanceOf[InterfacePipe[T, R]].resourceBeingExtracted(resource)
                queue.clear()
            } else {
                getWorld.getTileEntity(resource.destinationPipe).asInstanceOf[InterfacePipe[T, R]].resourceBeingExtracted(resource)
                nextResource = resource
            }
            true
        }
        else
            false
    }

    /**
      * If we have some important stuff, make sure we always render. Otherwise you'll only see what is in the pipe
      * if the pipe is in view. To cut on resources, if there is nothing in the buffer we return the standard render box
      *
      * @return
      */
    @SideOnly(Side.CLIENT)
    override def getRenderBoundingBox : AxisAlignedBB = {
        if(!resources.isEmpty)
            TileEntity.INFINITE_EXTENT_AABB
        else
            super.getRenderBoundingBox
    }

    /**
      * Used to make sure the other pipe matches our type
      * @param pipe The other pipe
      * @return True if matched
      */
    def doTypesMatch(pipe : InterfacePipe[_, _]) : Boolean = pipe.getPipeTypeID == getPipeTypeID

    /**
      * Used to define types, that way only like minded will interact
      * @return
      */
    def getPipeTypeID : Int

    /*******************************************************************************************************************
      *************************************** Insertion Methods ********************************************************
      ******************************************************************************************************************/

    /**
      * Try and insert the resource into an inventory.
      *
      * It is pretty good practice to send the resource back if you can't remove all of it
      *
      * @param resource
      */
    def tryInsertResource(resource : R, dir : EnumFacing)

    /**
      * Used to get a list of what tiles are attached that can accept resources. Don't worry about if full or not,
      * just if this pipe interfaces with the tile add it here
      *
      * @return A list of the tiles that are valid sinks
      */
    def getAttachedSinks: util.List[Long]

    /**
      * Called when the resource has found its target and is actually sending, change resource size here
 *
      * @param resource
      */
    def resourceBeingExtracted(resource : R) : Unit

    /**
      * Used to check if this pipe can accept a resource
      *
      * You should not actually change anything, all simulation
      *
      * @param resource
      * @return
      */
    def willAcceptResource(resource: ResourceEntity[_], pos : BlockPos) : Boolean = {
        if(getUpgradeBoard != null && getUpgradeBoard.hasControl) {
            if(redstone == -1 && isPowered)
                return false
            if(redstone == 1 && !isPowered)
                return false
        }
        if(!isResourceValidForFilter(resource))
            return false
        true
    }
}
