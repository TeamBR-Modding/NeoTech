package com.dyonovan.neotech.pipes.tiles.item

import java.util

import com.dyonovan.neotech.pipes.entities.{ResourceEntity, ItemResourceEntity}
import com.dyonovan.neotech.pipes.types.{InterfacePipe, SimplePipe}
import com.teambr.bookshelf.common.tiles.traits.{InventorySided, Inventory}
import com.teambr.bookshelf.util.InventoryUtils
import net.minecraft.inventory.{IInventory, ISidedInventory}
import net.minecraft.item.ItemStack
import net.minecraft.nbt.{NBTTagCompound, NBTTagList}
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.{BlockPos, EnumFacing}
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.items.{CapabilityItemHandler, IItemHandler}
import net.minecraftforge.items.wrapper.{SidedInvWrapper, InvWrapper}

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
class ItemInterfacePipe extends InterfacePipe[ItemStack, ItemResourceEntity] {

    /*******************************************************************************************************************
      ************************************** Extraction Methods ********************************************************
      ******************************************************************************************************************/

    override def canConnect(facing: EnumFacing): Boolean =
        if(super.canConnect(facing))
            getWorld.getTileEntity(pos.offset(facing)) match {
                case inventory : IInventory => true
                case itemHandler : IItemHandler => true
                case pipe : SimplePipe => true
                case _ => false
            }
        else
            super.canConnect(facing)

    /**
      * This is the speed to extract from. You should be calling this when building your resources to send.
      *
      * This is included as a reminder to the child to have variable speeds
      *
      * @return
      */
    override def getSpeed: Double = {
        if(getUpgradeBoard != null && getUpgradeBoard.getProcessorCount > 0)
            getUpgradeBoard.getProcessorCount * 0.05
        else
            0.05
    }

    /**
      * Used to specify how big a stack to pull. Judge with upgrades here
      *
      * @return
      */
    def getMaxStackExtract : Int = {
        if(getUpgradeBoard != null && getUpgradeBoard.getHardDriveCount > 0) {
            getUpgradeBoard.getHardDriveCount * 8
        } else
            1
    }

    /**
      * Get how many ticks to 'cooldown' between operations.
      *
      * @return 20 = 1 second
      */
    override def getDelay: Int = {
        if(getUpgradeBoard != null && getUpgradeBoard.getProcessorCount > 0)
            20 - getUpgradeBoard.getProcessorCount * 2
        else
            20
    }

    override def doExtraction(): Unit = {
        tryExtractResources()
    }

    override def tryExtractResources(): Unit = {
        val tempInv = new Inventory() {
            override def initialSize: Int = 1
        }

        for(dir <- EnumFacing.values()) {
            if (canConnectExtract(dir)) {
                val otherObject = worldObj.getTileEntity(pos.offset(dir))
                var otherInv: IItemHandler = null

                if (!otherObject.isInstanceOf[IItemHandler]) {
                    otherObject match {
                        case iInventory: IInventory if !iInventory.isInstanceOf[ISidedInventory] => otherInv = new InvWrapper(iInventory)
                        case iSided: ISidedInventory => otherInv = new SidedInvWrapper(iSided, dir.getOpposite)
                        case _ =>
                    }
                } else otherObject match { //If we are a ItemHandler, we want to make sure not to wrap, it can be both IInventory and IItemHandler
                    case itemHandler: IItemHandler => otherInv = itemHandler
                    case _ =>
                }

                otherObject match { //Check for sidedness
                    case tileEntity: TileEntity if tileEntity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, dir.getOpposite) =>
                        otherInv = tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, dir.getOpposite)
                    case _ =>
                }

                if (otherInv != null) {
                    for (x <- 0 until otherInv.getSlots) {
                        if (otherInv.extractItem(x, getMaxStackExtract, true) != null) {
                            if (otherInv.getStackInSlot(x) != null && extractOnMode(new ItemResourceEntity(otherInv.extractItem(x, getMaxStackExtract, true),
                                pos.getX + 0.5, pos.getY + 0.5, pos.getZ + 0.5, getSpeed,
                                pos, pos.offset(dir), pos, worldObj), simulate = true)) {
                                InventoryUtils.moveItemInto(otherInv, x, tempInv, 0, getMaxStackExtract, dir, doMove = true)
                                if (tempInv.getStackInSlot(0) != null) {
                                    nextResource.resource = tempInv.getStackInSlot(0)
                                    extractOnMode(nextResource, simulate = false)
                                    return
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    /**
      * This is called when we fail to send a resource. You should put the resource back where you found it or
      * add it to the world
      */
    override def returnResource(resource: ItemResourceEntity): Unit = {
        //If we couldn't fill, move back to source
        if(!resource.isDead) {
            resource.destination = new BlockPos(resource.from)
            resource.findPathToDestination()
        }
    }

    override def writeToNBT(tag : NBTTagCompound) : Unit = {
        super.writeToNBT(tag)
        super[TileEntity].writeToNBT(tag)
        tag.setInteger("SizeResources", resources.size())
        val resourceList = new NBTTagList
        for(i <- 0 until resources.size()) {
            val resourceTag = new NBTTagCompound
            resources.get(i).writeToNBT(resourceTag)
            resourceList.appendTag(resourceTag)
        }
        tag.setTag("Resources", resourceList)
    }

    override def readFromNBT(tag : NBTTagCompound) : Unit = {
        super.readFromNBT(tag)
        super[TileEntity].readFromNBT(tag)
        val resourceList = tag.getTagList("Resources", 10)
        resources = new util.ArrayList[ItemResourceEntity]()
        for(i <- 0 until resourceList.tagCount()) {
            val resourceTag = resourceList.getCompoundTagAt(i)
            val item = new ItemResourceEntity()
            item.readFromNBT(resourceTag)
            if(worldObj != null && !worldObj.isRemote) {
                item.setWorld(worldObj)
            }
            resources.add(item)
        }
    }

    /*******************************************************************************************************************
      *************************************** Insertion Methods ********************************************************
      ******************************************************************************************************************/

    val waitingQueue  = new util.ArrayList[ItemResourceEntity]()
    var tempInventory: Inventory = null
    var tempInventoryTest: Inventory = null

    /**
      * Used to check if this pipe can accept a resource
      *
      * You should not actually change anything, all simulation
      *
      * @param resourceEntity
      * @return
      */
    override def willAcceptResource(resourceEntity: ResourceEntity[_], isSending : Boolean): Boolean = {
        if(resourceEntity == null || !resourceEntity.isInstanceOf[ItemResourceEntity] || resourceEntity.resource == null || !super.willAcceptResource(resourceEntity, isSending))
            return false

        val resource = resourceEntity.asInstanceOf[ItemResourceEntity]

        tempInventory = new Inventory() {
            override def initialSize: Int = 1
        }

        tempInventory.setInventorySlotContents(0, resource.resource.copy())

        //Try and insert the stack
        for(dir <- EnumFacing.values()) {
            if (canConnectSink(dir) && pos.offset(dir) != resource.fromTileLocation) {
                if (InventoryUtils.moveItemInto(tempInventory, 0, test(worldObj.getTileEntity(pos.offset(dir)), dir.getOpposite), -1, 64, dir, doMove = false)) {
                    if(isSending)
                        waitingQueue.add(resource)
                    return true
                }
            }
        }

        false
    }

    def test(otherObject : AnyRef, dir : EnumFacing) : AnyRef = {
        if(waitingQueue.isEmpty)
            otherObject
        else {
            var otherInv : IItemHandler = null

            if(!otherObject.isInstanceOf[IItemHandler]) {
                otherObject match {
                    case iInventory: IInventory if !iInventory.isInstanceOf[ISidedInventory] => otherInv = new InvWrapper(iInventory)
                    case iSided: ISidedInventory => otherInv = new SidedInvWrapper(iSided, dir)
                    case _ => return null
                }
            } else otherObject match { //If we are a ItemHandler, we want to make sure not to wrap, it can be both IInventory and IItemHandler
                case itemHandler: IItemHandler => otherInv = itemHandler
                case _ => return null
            }

            otherObject match { //Check for sidedness
                case tileEntity: TileEntity if tileEntity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, dir) =>
                    otherInv = tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, dir)
                case _ =>
            }
            val iterator = waitingQueue.iterator() //Remove deads
            while (iterator.hasNext) {
                if (iterator.next().isDead)
                    iterator.remove()
            }

            otherInv match {
                case sided: InventorySided =>
                    tempInventoryTest = new InventorySided() {
                        override def initialSize: Int = otherInv.getSlots
                        override def getSlotsForFace(side: EnumFacing): Array[Int] = sided.getSlotsForFace(side)
                        override def getCapabilityFromTile[T](capability: Capability[T], facing: EnumFacing): T = sided.getCapability(capability, facing)
                        override def canExtractItem(index: Int, stack: ItemStack, direction: EnumFacing): Boolean = sided.canExtractItem(index, stack, direction)
                        override def canInsertItem(slot: Int, itemStackIn: ItemStack, direction: EnumFacing): Boolean = sided.canInsertItem(slot, itemStackIn, direction)
                    }
                case _ =>
                    tempInventoryTest = new Inventory() {
                        override def initialSize: Int = otherInv.getSlots
                    }
            }

            tempInventoryTest.copyFrom(otherInv)

            for(x <- 0 until waitingQueue.size) {
                val tempInventoryUs = new Inventory() {
                    override def initialSize: Int = 1
                }

                val resource = waitingQueue.get(x)

                if(resource == null || resource.resource == null)
                    return tempInventoryTest

                tempInventoryUs.setInventorySlotContents(0, resource.resource.copy())
                InventoryUtils.moveItemInto(tempInventoryUs, 0, tempInventoryTest, -1, 64, dir.getOpposite, doMove = true)
            }
            tempInventoryTest
        }
    }

    override def tryInsertResource(resource : ItemResourceEntity) : Unit = {
        val tempActualInsert = new Inventory() {
            override def initialSize: Int = 1
        }

        tempActualInsert.setInventorySlotContents(0, resource.resource)

        //Try and insert the stack
        for(dir <- EnumFacing.values()) {
            if(canConnectSink(dir) && InventoryUtils.moveItemInto(tempActualInsert, 0, worldObj.getTileEntity(pos.offset(dir)), -1, 64, dir, doMove = true)) {
                resource.resource = tempActualInsert.getStackInSlot(0)
                if(resource.resource == null || resource.resource.stackSize <= 0) {
                    resource.isDead = true
                    resource.resource = null
                    waitingQueue.remove(resource)
                }
            }
        }
        //If we couldn't fill, move back to source
        if(!resource.isDead) {
            resource.isDead = true
        }
    }

    var coolDownInput = 0
    override def onServerTick(): Unit = {
        super.onServerTick()
        coolDownInput -= 1
        if(coolDownInput < 0) {
            coolDownInput = 200
            if (!waitingQueue.isEmpty) {
                val iterator = waitingQueue.iterator()
                while (iterator.hasNext) {
                    if (iterator.next().isDead)
                        iterator.remove()
                }
            }
        }
    }
}