package com.FrogPlay.sablemobbreaker.block;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, SableMobBreaker.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BrokenBlockEntity>> BROKEN_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("broken_block", () ->
                    BlockEntityType.Builder.of(BrokenBlockEntity::new, ModBlocks.BROKEN_BLOCK.get()).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OxygenShelfBlockEntity>> OXYGEN_SHELF_ENTITY =
            BLOCK_ENTITIES.register("oxygen_shelf", () ->
                    BlockEntityType.Builder.of(OxygenShelfBlockEntity::new, ModBlocks.OXYGEN_SHELF.get()).build(null)
            );
}