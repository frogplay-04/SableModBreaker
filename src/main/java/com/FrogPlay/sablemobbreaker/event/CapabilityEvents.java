package com.FrogPlay.sablemobbreaker.event;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.block.ModBlockEntities;
import com.FrogPlay.sablemobbreaker.item.PlasmaCutterInventory;
import com.FrogPlay.sablemobbreaker.item.WeldingTorchInventory;
import com.FrogPlay.sablemobbreaker.item.FuelTankFluidHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@EventBusSubscriber(modid = SableMobBreaker.MOD_ID)
public class CapabilityEvents {

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {

        event.registerItem(Capabilities.ItemHandler.ITEM,
                (stack, ctx) -> new WeldingTorchInventory(stack),
                SableMobBreaker.WELDING_TORCH.get());

        event.registerItem(Capabilities.ItemHandler.ITEM,
                (stack, ctx) -> new PlasmaCutterInventory(stack),
                SableMobBreaker.PLASMA_CUTTER.get());

        event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, ctx) -> new FuelTankFluidHandler(stack),
                SableMobBreaker.FUEL_TANK.get());

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.OXYGEN_SHELF_ENTITY.get(),
                (be, side) -> be.getEnergyStorage()
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.OXYGEN_SHELF_ENTITY.get(),
                (be, side) -> be.getItemHandler()
        );
    }
}