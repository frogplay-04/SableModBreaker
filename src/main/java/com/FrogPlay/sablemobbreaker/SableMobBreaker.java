package com.FrogPlay.sablemobbreaker;

import com.FrogPlay.sablemobbreaker.block.ModBlockEntities;
import com.FrogPlay.sablemobbreaker.block.ModBlocks;
import com.FrogPlay.sablemobbreaker.item.FuelTankItem;
import com.FrogPlay.sablemobbreaker.item.WeldingTorchItem;
import com.FrogPlay.sablemobbreaker.menu.WeldingTorchMenu;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(SableMobBreaker.MOD_ID)
public class SableMobBreaker {

    public static final String MOD_ID = "sablemobbreaker";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    // === СУЩЕСТВУЮЩИЕ ПРЕДМЕТЫ ===
    public static final DeferredHolder<Item, WeldingTorchItem> WELDING_TORCH =
            ITEMS.register("welding_torch", () -> new WeldingTorchItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, FuelTankItem> FUEL_TANK =
            ITEMS.register("fuel_tank", () -> new FuelTankItem(new Item.Properties().stacksTo(1)));

    // === НОВЫЕ 6 ПРЕДМЕТОВ (НАЗВАНИЯ КАК НА ТВОИХ КАРТИНКАХ) ===
    public static final DeferredHolder<Item, Item> CLOCK_00 =
            ITEMS.register("clock_00", () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> BUCKET =
            ITEMS.register("bucket", () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> ONE_OAK_SIGN =
            ITEMS.register("1oak_sign", () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> STRING =
            ITEMS.register("string", () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> OAK_SIGN =
            ITEMS.register("oak_sign", () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> SPYGLASS =
            ITEMS.register("spyglass", () -> new Item(new Item.Properties()));


    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<WeldingTorchMenu>> WELDING_TORCH_MENU =
            MENUS.register("welding_torch", () -> IMenuTypeExtension.create((id, inv, data) -> {
                ItemStack stack = ItemStack.STREAM_CODEC.decode(data);
                return new WeldingTorchMenu(id, inv, stack);
            }));

    public SableMobBreaker(IEventBus modEventBus, ModContainer modContainer) {
        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
    }
}