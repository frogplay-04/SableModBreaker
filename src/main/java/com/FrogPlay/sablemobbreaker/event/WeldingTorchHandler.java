package com.FrogPlay.sablemobbreaker.event;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.block.ModBlocks;
import com.FrogPlay.sablemobbreaker.item.FuelTankFluidHandler;
import com.FrogPlay.sablemobbreaker.item.WeldingTorchInventory;
import com.FrogPlay.sablemobbreaker.item.WeldingTorchItem;
import com.FrogPlay.sablemobbreaker.network.BlockHealthSyncPacket;
import com.FrogPlay.sablemobbreaker.util.BlockHealthManager;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = SableMobBreaker.MOD_ID)
public class WeldingTorchHandler {

    private static final int REPAIR_RADIUS = 5;

    private static final Set<BlockPos> LOGGED_REPAIR_FAILURES = ConcurrentHashMap.newKeySet();
    private static final int LOGGED_REPAIR_FAILURES_MAX = 256;

    private static void logRepairFailure(BlockPos pos, String message, Throwable t) {
        if (LOGGED_REPAIR_FAILURES.size() > LOGGED_REPAIR_FAILURES_MAX) {
            LOGGED_REPAIR_FAILURES.clear();
        }
        if (LOGGED_REPAIR_FAILURES.add(pos.immutable())) {
            SableMobBreaker.LOGGER.error(message + " at {}: {}", pos, t.toString());
        }
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.ItemHandler.ITEM,
                (stack, ctx) -> new WeldingTorchInventory(stack),
                SableMobBreaker.WELDING_TORCH.get());
        event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, ctx) -> new FuelTankFluidHandler(stack),
                SableMobBreaker.FUEL_TANK.get());
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().getMainHandItem().getItem() instanceof WeldingTorchItem) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof WeldingTorchItem) {
                int fuse = WeldingTorchItem.getFuse(stack);
                if (fuse > 0) {
                    fuse--;
                    WeldingTorchItem.setFuse(stack, fuse);
                    if (fuse % 5 == 0) {
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 0.5f, 1.0f);
                    }
                    if (fuse <= 0) {
                        player.level().explode(null, player.getX(), player.getY() + 0.5, player.getZ(),
                                3.0f, Level.ExplosionInteraction.BLOCK);
                        stack.shrink(1);
                        player.hurt(player.damageSources().explosion(null), 10.0f);
                        return;
                    }
                }
            }
        }

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof WeldingTorchItem && WeldingTorchItem.isOn(mainHand)) {
            Level level = player.level();
            ItemStack bottle = WeldingTorchItem.getBottle(mainHand);
            int fuel = getFuelAmount(bottle);

            if (fuel <= 0) {
                WeldingTorchItem.setOn(mainHand, false);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.0f);
                return;
            }

            if (player.tickCount % 2 == 0 && level instanceof ServerLevel serverLevel) {
                Vec3 look = player.getLookAngle();
                Vec3 start = player.getEyePosition().add(look.scale(1.2));
                if (player instanceof ServerPlayer serverPlayer) {
                    serverLevel.sendParticles(serverPlayer, ParticleTypes.FLAME, true,
                            start.x, start.y, start.z, 3, 0.05, 0.05, 0.05, 0.01);
                    serverLevel.sendParticles(serverPlayer, ParticleTypes.SMOKE, true,
                            start.x, start.y, start.z, 2, 0.05, 0.05, 0.05, 0.01);
                }
            }

            if (player.tickCount % 10 == 0) {
                List<LivingEntity> entities = level.getEntitiesOfClass(
                        LivingEntity.class,
                        player.getBoundingBox().inflate(1.5),
                        e -> !(e instanceof Player));
                for (LivingEntity mob : entities) {
                    mob.igniteForSeconds(2);
                    mob.hurt(player.damageSources().playerAttack(player), 1.0f);
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.FLAME,
                                mob.getX(), mob.getY() + 1, mob.getZ(),
                                5, 0.2, 0.2, 0.2, 0.05);
                    }
                }
            }

            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                SubLevel targetSubLevel = null;
                double closestDistSq = 25.0;

                for (SubLevel sl : container.getAllSubLevels()) {
                    if (sl.isRemoved()) continue;
                    BoundingBox3dc box = sl.boundingBox();
                    double dx = Math.max(0, Math.max(box.minX() - player.getX(), player.getX() - box.maxX()));
                    double dy = Math.max(0, Math.max(box.minY() - player.getY(), player.getY() - box.maxY()));
                    double dz = Math.max(0, Math.max(box.minZ() - player.getZ(), player.getZ() - box.maxZ()));
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq <= closestDistSq) {
                        closestDistSq = distSq;
                        targetSubLevel = sl;
                    }
                }

                if (targetSubLevel != null) {
                    int cost = autoRepair(player, targetSubLevel, true, fuel);
                    if (cost > 0) {
                        autoRepair(player, targetSubLevel, false, cost);
                        drainFuel(bottle, cost);
                        WeldingTorchItem.setBottle(mainHand, bottle);
                        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.5f, 1.5f);
                    }
                }
            }

            if (player.tickCount % 20 == 0) {
                drainFuel(bottle, 1);
                WeldingTorchItem.setBottle(mainHand, bottle);
                if (getFuelAmount(bottle) <= 0) {
                    WeldingTorchItem.setOn(mainHand, false);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.0f);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            if (stack.getItem() instanceof WeldingTorchItem) {
                int fuse = WeldingTorchItem.getFuse(stack);
                if (fuse > 0) {
                    fuse--;
                    WeldingTorchItem.setFuse(stack, fuse);
                    itemEntity.setItem(stack);
                    if (fuse <= 0) {
                        itemEntity.level().explode(null, itemEntity.getX(), itemEntity.getY() + 0.5,
                                itemEntity.getZ(), 3.0f, Level.ExplosionInteraction.BLOCK);
                        itemEntity.discard();
                    }
                }
            }
        }
    }

    private static int autoRepair(Player player, SubLevel subLevel, boolean simulate, int maxBlocks) {
        Level level = player.level();
        UUID subId = subLevel.getUniqueId();
        if (subId == null) return 0;

        int repaired = 0;
        BlockPos pCenter = player.blockPosition();

        outer:
        for (int dx = -REPAIR_RADIUS; dx <= REPAIR_RADIUS; dx++) {
            for (int dy = -REPAIR_RADIUS; dy <= REPAIR_RADIUS; dy++) {
                for (int dz = -REPAIR_RADIUS; dz <= REPAIR_RADIUS; dz++) {
                    if (repaired >= maxBlocks) break outer;

                    Vec3 hitVec = Vec3.atCenterOf(pCenter.offset(dx, dy, dz));
                    Vec3 plotPos = subLevel.logicalPose().transformPositionInverse(hitVec);
                    BlockPos plotGridBlockPos = BlockPos.containing(plotPos.x, plotPos.y, plotPos.z);

                    if (repairBlock(level, subLevel, subId, plotGridBlockPos, hitVec, simulate)) {
                        repaired++;
                    }
                }
            }
        }
        return repaired;
    }

    public static boolean isValidBottle(ItemStack bottle) {
        return isOxygenBottle(bottle) || getFuelAmount(bottle) > 0 || isCustomFuelBottle(bottle);
    }

    public static boolean isOxygenBottle(ItemStack bottle) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(bottle.getItem());
        String path = id.getPath();
        String namespace = id.getNamespace();
        if (ModList.get().isLoaded("scguns") && namespace.equals("scguns") && path.contains("air_canister")) {
            CustomData data = bottle.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            return data.copyTag().getInt("AirStored") > 0;
        }
        if (path.contains("backtank") || path.contains("netherite_tank") || path.contains("netherite_backtank")
                || path.contains("oxygen") || path.contains("air") || path.contains("canister") || path.contains("bottle")) {
            if (getFuelAmount(bottle) > 0) return false;
            return true;
        }
        return false;
    }

    private static boolean isCustomFuelBottle(ItemStack bottle) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(bottle.getItem());
        if (id.equals(ResourceLocation.fromNamespaceAndPath(SableMobBreaker.MOD_ID, "fuel_tank"))) return true;
        return id.getNamespace().equals("tfmg")
                && (id.getPath().contains("propane") || id.getPath().contains("butane"));
    }

    public static int getFuelAmount(ItemStack bottle) {
        IFluidHandlerItem handler = bottle.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler != null) {
            FluidStack fluid = handler.getFluidInTank(0);
            if (!fluid.isEmpty()) {
                String id = BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
                if (id.contains("propane") || id.contains("butane")) {
                    return fluid.getAmount();
                }
            }
        }
        if (isCustomFuelBottle(bottle)) {
            CustomData data = bottle.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            return Math.min(2000, data.copyTag().getInt("GasStored"));
        }
        return 0;
    }

    private static void drainFuel(ItemStack bottle, int amount) {
        IFluidHandlerItem handler = bottle.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler != null) {
            FluidStack fluid = handler.getFluidInTank(0);
            if (!fluid.isEmpty()) {
                String id = BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
                if (id.contains("propane") || id.contains("butane")) {
                    handler.drain(amount, IFluidHandler.FluidAction.EXECUTE);
                    return;
                }
            }
        }
        if (isCustomFuelBottle(bottle)) {
            CustomData data = bottle.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = data.copyTag();
            int current = tag.contains("GasStored") ? tag.getInt("GasStored") : 0;
            tag.putInt("GasStored", Math.max(0, current - amount));
            bottle.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private static boolean repairBlock(Level level, SubLevel subLevel, UUID subId,
                                       BlockPos plotGridBlockPos, Vec3 hitVec, boolean simulate) {
        int y = plotGridBlockPos.getY();
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return false;
        if (!(level instanceof ServerLevel serverLevel)) return false;

        ChunkPos cp = new ChunkPos(plotGridBlockPos.getX() >> 4, plotGridBlockPos.getZ() >> 4);
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !container.inBounds(cp)) return false;

        LevelChunk chunk = container.getChunk(cp);
        PlotChunkHolder holder = container.getChunkHolder(cp);
        if (chunk == null || holder == null) return false;

        int chunkLocalX = plotGridBlockPos.getX() & 15;
        int chunkLocalZ = plotGridBlockPos.getZ() & 15;
        BlockPos blockInChunk = new BlockPos(chunkLocalX, y, chunkLocalZ);

        BlockState state = chunk.getBlockState(blockInChunk);

        var saved = BlockHealthManager.getDestroyedBlock(
                subId, plotGridBlockPos.getX(), y, plotGridBlockPos.getZ());

        if (saved != null && saved.state() != null) {
            int maxHp = BlockHealthManager.getMaxHp(saved.state(), saved.nbt());

            if (simulate) {
                return true;
            }

            try {
                if (state != saved.state()) {
                    try {
                        holder.handleBlockChange(
                                plotGridBlockPos.getX(), y, plotGridBlockPos.getZ(),
                                state, saved.state());
                    } catch (Throwable t) {
                        logRepairFailure(plotGridBlockPos,
                                "[SMB] repair handleBlockChange failed", t);
                    }

                    // ИСПРАВЛЕНИЕ: Всегда пишем в массив блоков для восстановления пути
                    try {
                        LevelChunkSection[] sections = chunk.getSections();
                        int sectionIndex = chunk.getSectionIndex(y);
                        if (sectionIndex >= 0 && sectionIndex < sections.length) {
                            LevelChunkSection section = sections[sectionIndex];
                            if (section != null) {
                                section.setBlockState(
                                        chunkLocalX & 15,
                                        y & 15,
                                        chunkLocalZ & 15,
                                        saved.state());
                                chunk.setUnsaved(true);
                            }
                        }
                    } catch (Throwable t) {
                        logRepairFailure(plotGridBlockPos,
                                "[SMB] repair section.setBlockState failed", t);
                    }
                }

                BlockEntity newBe = chunk.getBlockEntity(blockInChunk);
                if (newBe == null && saved.nbt() != null) {
                    newBe = BlockEntity.loadStatic(
                            plotGridBlockPos, saved.state(), saved.nbt(), level.registryAccess());
                    if (newBe != null) chunk.setBlockEntity(newBe);
                } else if (newBe != null && saved.nbt() != null) {
                    newBe.loadWithComponents(saved.nbt(), level.registryAccess());
                    newBe.setChanged();
                }

                ClientboundBlockUpdatePacket plotPacket =
                        new ClientboundBlockUpdatePacket(plotGridBlockPos, saved.state());

                ClientboundBlockEntityDataPacket bePacket = null;
                if (newBe != null) {
                    try {
                        Constructor<ClientboundBlockEntityDataPacket> constructor =
                                ClientboundBlockEntityDataPacket.class.getDeclaredConstructor(
                                        BlockPos.class, BlockEntityType.class, CompoundTag.class);
                        constructor.setAccessible(true);
                        CompoundTag nbt = saved.nbt() != null
                                ? saved.nbt()
                                : newBe.getUpdateTag(level.registryAccess());
                        bePacket = constructor.newInstance(plotGridBlockPos, newBe.getType(), nbt);
                    } catch (Throwable t) {
                        bePacket = ClientboundBlockEntityDataPacket.create(newBe);
                    }
                }

                for (ServerPlayer p : serverLevel.players()) {
                    if (p.distanceToSqr(hitVec.x, hitVec.y, hitVec.z) < 4096) {
                        p.connection.send(plotPacket);
                        if (bePacket != null) p.connection.send(bePacket);
                    }
                }

                BlockHealthManager.setHp(
                        subId, plotGridBlockPos.getX(), y, plotGridBlockPos.getZ(), maxHp);
                BlockHealthManager.removeDestroyedBlock(
                        subId, plotGridBlockPos.getX(), y, plotGridBlockPos.getZ());

                BlockHealthSyncPacket hpPacket = new BlockHealthSyncPacket(
                        subId, plotGridBlockPos.getX(), y, plotGridBlockPos.getZ(),
                        maxHp, maxHp);
                for (ServerPlayer p : serverLevel.players()) {
                    if (p.distanceToSqr(hitVec.x, hitVec.y, hitVec.z) < 4096) {
                        PacketDistributor.sendToPlayer(p, hpPacket);
                    }
                }

                LOGGED_REPAIR_FAILURES.remove(plotGridBlockPos.immutable());

                return true;
            } catch (Throwable t) {
                logRepairFailure(plotGridBlockPos, "[SMB] repair failed", t);
                return false;
            }
        }

        if (state.is(ModBlocks.BROKEN_BLOCK.get())) {
            BlockHealthManager.reset(subId, plotGridBlockPos.getX(), y, plotGridBlockPos.getZ());
            return false;
        }

        BlockEntity be = chunk.getBlockEntity(blockInChunk);
        int maxHp = BlockHealthManager.getMaxHp(state, be);
        int currentHp = BlockHealthManager.getHp(
                subId, plotGridBlockPos.getX(), y, plotGridBlockPos.getZ(), state, be);

        if (currentHp >= maxHp) return false;

        if (!simulate) {
            BlockHealthManager.setHp(
                    subId, plotGridBlockPos.getX(), y, plotGridBlockPos.getZ(), maxHp);
            BlockHealthSyncPacket packet = new BlockHealthSyncPacket(
                    subId, plotGridBlockPos.getX(), y, plotGridBlockPos.getZ(), maxHp, maxHp);
            for (ServerPlayer player : serverLevel.players()) {
                if (player.distanceToSqr(hitVec.x, hitVec.y, hitVec.z) < 4096) {
                    PacketDistributor.sendToPlayer(player, packet);
                }
            }
        }
        return true;
    }
}