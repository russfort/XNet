package mcjty.xnet.init;

import mcjty.xnet.blocks.cables.NetCableSetup;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class ModRecipes {

    public static void init(){
        NetCableSetup.initCrafting();

        Block machineFrame;
        if (Loader.isModLoaded("rftools")) {
            machineFrame = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("rftools", "machine_frame"));
        } else {
            machineFrame = Blocks.IRON_BLOCK;
        }

        GameRegistry.addRecipe(new ItemStack(ModBlocks.controllerBlock), "RCR", "rMr", "igi",
                'M', machineFrame, 'R', Items.REPEATER, 'C', Items.COMPARATOR, 'r', Items.REDSTONE,
                'i', Items.IRON_INGOT, 'g', Items.GOLD_INGOT);

        GameRegistry.addRecipe(new ItemStack(ModBlocks.routerBlock), "RCR", "rMr", "igi",
                'M', machineFrame, 'R', Blocks.GOLDEN_RAIL, 'C', Items.COMPARATOR, 'r', Items.REDSTONE,
                'i', Items.IRON_INGOT, 'g', Items.ENDER_PEARL);

        GameRegistry.addRecipe(new ItemStack(ModBlocks.facadeBlock, 16), "pwp", "wXw", "pwp",
                'p', Items.PAPER, 'w', Blocks.WOOL, 'X', Blocks.GLASS);

        GameRegistry.addRecipe(new ItemStack(ModBlocks.redstoneProxyBlock), "rrr", "rmr", "rrr",
                'r', Items.REDSTONE, 'm', machineFrame);

        GameRegistry.addRecipe(new ItemStack(ModItems.xNetManualItem), "rsr", " b ", "r r", 'r', Items.REDSTONE, 'b', Items.BOOK, 's', Items.STRING);

    }

}
