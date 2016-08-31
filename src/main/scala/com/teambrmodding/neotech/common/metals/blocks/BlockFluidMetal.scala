package com.teambrmodding.neotech.common.metals.blocks

import com.teambrmodding.neotech.lib.Reference
import com.teambrmodding.neotech.utils.ClientUtils
import net.minecraft.block.material.Material
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraftforge.fluids.{BlockFluidClassic, Fluid}

/**
  * This file was created for NeoTech
  *
  * NeoTech is licensed under the
  * Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License:
  * http://creativecommons.org/licenses/by-nc-sa/4.0/
  *
  * @author Paul Davis <pauljoda>
  * @since 2/16/2016
  */
class BlockFluidMetal(fluidMetal: Fluid) extends BlockFluidClassic(fluidMetal, Material.LAVA) {
    setUnlocalizedName(Reference.MOD_ID + "." + fluidMetal.getName)


    def getBlockColor : Int = fluidMetal.getColor

    //For setting fire and not burning ourselves
    override def getFireSpreadSpeed(world : IBlockAccess, pos : BlockPos, facing : EnumFacing) : Int = 300
    override def getFlammability(world : IBlockAccess, pos: BlockPos, facing: EnumFacing) : Int = 0

    override def getLocalizedName : String = {
        ClientUtils.translate("fluid." + fluidMetal.getName + ".name")
    }
}