package com.FrogPlay.sablemobbreaker.block;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, SableMobBreaker.MOD_ID);

    public static final DeferredHolder<Block, BrokenBlock> BROKEN_BLOCK =
            BLOCKS.register("broken_block", BrokenBlock::new);

    public static final DeferredHolder<Block, OxygenShelfBlock> OXYGEN_SHELF =
            BLOCKS.register("oxygen_shelf", () -> new OxygenShelfBlock(
                    BlockBehaviour.Properties.of()
                            .strength(3.0F)
                            .noOcclusion()
            ));
}