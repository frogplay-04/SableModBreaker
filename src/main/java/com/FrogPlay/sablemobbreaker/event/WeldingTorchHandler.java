package com.FrogPlay.sablemobbreaker.event;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.block.ModBlocks;
import com.FrogPlay.sablemobbreaker.block.BrokenBlockEntity;
import com.FrogPlay.sablemobbreaker.config.ModConfig;
import com.FrogPlay.sablemobbreaker.item.FuelTankItem;
import com.FrogPlay.sablemobbreaker.item.FuelTankFluidHandler;
import com.FrogPlay.sablemobbreaker.item.OxygenTankItem;
import com.FrogPlay.sablemobbreaker.item.PlasmaCutterItem;
import com.FrogPlay.sablemobbreaker.item.WeldingTorchInventory;
import com.FrogPlay.sablemobbreaker.item.WeldingTorchItem;
import com.FrogPlay.sablemobbreaker.network.BlockHealthSyncPacket;
import com.FrogPlay.sablemobbreaker.network.RepairHpNoticePacket;
import com.FrogPlay.sablemobbreaker.util.BlockHealthManager;
import com.FrogPlay.sablemobbreaker.util.SubLevelDamageHelper;
import com.FrogPlay.sablemobbreaker.util.WeldManager;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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

    private static class MiningData {
        BlockPos pos;
        BlockState state;
        SubLevel subLevel;
        BlockPos subLevelLocalPos;
        float progress;
        float subLevelDamageRemainder;
    }
    private static final java.util.Map<UUID, MiningData> MINING_DATA = new ConcurrentHashMap<>();

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
            if (stack.getItem() instanceof WeldingTorchItem || stack.getItem() instanceof PlasmaCutterItem) {
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
        Level level = player.level();

        if (mainHand.getItem() instanceof WeldingTorchItem && WeldingTorchItem.isOn(mainHand)) {
            ItemStack bottle = WeldingTorchItem.getBottle(mainHand);

            if (bottle.getItem() instanceof OxygenTankItem) {
                level.explode(null, player.getX(), player.getY() + 0.5, player.getZ(),
                        3.0f, Level.ExplosionInteraction.BLOCK);
                mainHand.shrink(1);
                player.hurt(player.damageSources().explosion(null), 10.0f);
                return;
            }

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

            if (player.tickCount % 5 == 0) {
                SubLevelContainer container = SubLevelContainer.getContainer(level);
                if (container != null) {
                    Vec3 rayStart = player.getEyePosition();
                    Vec3 rayEnd = rayStart.add(player.getLookAngle().scale(6.0D));
                    SubLevel targetSubLevel = null;
                    double closestHit = Double.MAX_VALUE;

                    for (SubLevel sl : container.getAllSubLevels()) {
                        if (sl.isRemoved()) continue;
                        BoundingBox3dc box = sl.boundingBox();
                        AABB worldBounds = new AABB(box.minX(), box.minY(), box.minZ(),
                                box.maxX(), box.maxY(), box.maxZ());
                        java.util.Optional<Vec3> hit = worldBounds.clip(rayStart, rayEnd);
                        if (hit.isPresent()) {
                            double distance = hit.get().distanceToSqr(rayStart);
                            if (distance < closestHit) {
                                closestHit = distance;
                                targetSubLevel = sl;
                            }
                        }
                    }

                    if (targetSubLevel != null) {
                        int maxToRepair = Math.min(ModConfig.WELD_REPAIR_MAX_BLOCKS.get(), fuel);
                        int repaired = autoRepair(player, targetSubLevel, true, maxToRepair);
                        if (repaired > 0) {
                            int cost = autoRepair(player, targetSubLevel, false, repaired);
                            if (cost > 0) {
                                drainFuel(bottle, cost);
                                WeldingTorchItem.setBottle(mainHand, bottle);
                                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                                        SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.5f, 1.5f);
                            }
                        }
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

        if (mainHand.getItem() instanceof PlasmaCutterItem && PlasmaCutterItem.isOn(mainHand)) {
            ItemStack bottle = PlasmaCutterItem.getBottle(mainHand);

            if (bottle.getItem() instanceof FuelTankItem || getFuelAmount(bottle) > 0) {
                level.explode(null, player.getX(), player.getY() + 0.5, player.getZ(), 3.0f, Level.ExplosionInteraction.BLOCK);
                mainHand.shrink(1);
                player.hurt(player.damageSources().explosion(null), 10.0f);
                return;
            }

            int air = 0;
            if (bottle.getItem() instanceof OxygenTankItem) {
                air = OxygenTankItem.getAir(bottle);
            }
            if (air <= 0) {
                PlasmaCutterItem.setOn(mainHand, false);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.0f);
                return;
            }

            if (player.tickCount % 2 == 0 && level instanceof ServerLevel serverLevel) {
                Vec3 look = player.getLookAngle();
                Vec3 start = player.getEyePosition().add(look.scale(1.2));
                if (player instanceof ServerPlayer serverPlayer) {
                    serverLevel.sendParticles(serverPlayer, ParticleTypes.SOUL_FIRE_FLAME, true, start.x, start.y, start.z, 3, 0.05, 0.05, 0.05, 0.01);
                }
            }

            Vec3 eyePos = player.getEyePosition();
            Vec3 lookVec = player.getLookAngle();
            Vec3 targetVec = eyePos.add(lookVec.scale(4.5));

            boolean hitSomething = false;
            MiningData data = MINING_DATA.computeIfAbsent(player.getUUID(), k -> new MiningData());

            ClipContext clipContext = new ClipContext(eyePos, targetVec, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
            BlockHitResult hitResult = level.clip(clipContext);

            if (hitResult.getType() == HitResult.Type.BLOCK) {
                BlockPos hitPos = hitResult.getBlockPos();
                BlockState hitState = level.getBlockState(hitPos);

                if (WeldManager.isWelded(level, hitPos)) {
                    if (data.pos != null && level instanceof ServerLevel serverLevel) {
                        serverLevel.destroyBlockProgress(player.getId(), data.pos, -1);
                    }
                    data.pos = null;
                    data.state = null;
                    data.subLevel = null;
                    data.subLevelLocalPos = null;
                    data.progress = 0.0F;
                    data.subLevelDamageRemainder = 0.0F;
                    hitSomething = false;
                } else if (!hitState.isAir() && hitState.getDestroySpeed(level, hitPos) >= 0) {
                    if (!hitPos.equals(data.pos) || data.subLevel != null) {
                        data.pos = hitPos.immutable();
                        data.state = hitState;
                        data.subLevel = null;
                        data.subLevelLocalPos = null;
                        data.progress = 0.0F;
                        data.subLevelDamageRemainder = 0.0F;
                    } else {
                        data.state = hitState;
                    }
                    hitSomething = true;
                }
            } else {
                SubLevelContainer container = SubLevelContainer.getContainer(level);
                if (container != null) {
                    for (SubLevel sl : container.getAllSubLevels()) {
                        if (sl.isRemoved() || !sl.boundingBox().contains(targetVec.x, targetVec.y, targetVec.z)) {
                            continue;
                        }

                        Vec3 localVec = sl.logicalPose().transformPositionInverse(targetVec);
                        BlockPos localBlockPos = BlockPos.containing(localVec.x, localVec.y, localVec.z);
                        ChunkPos chunkPos = new ChunkPos(localBlockPos.getX() >> 4, localBlockPos.getZ() >> 4);
                        if (!container.inBounds(chunkPos)) continue;

                        LevelChunk subLevelChunk = container.getChunk(chunkPos);
                        if (subLevelChunk == null) continue;

                        BlockState subLevelState = subLevelChunk.getBlockState(localBlockPos);
                        if (subLevelState.isAir() || subLevelState.is(ModBlocks.BROKEN_BLOCK.get())
                                || SubLevelDamageHelper.isIgnoredBlock(subLevelState)
                                || subLevelState.getDestroySpeed(subLevelChunk, localBlockPos) < 0.0F) {
                            continue;
                        }

                        boolean changedTarget = data.subLevel != sl
                                || data.subLevelLocalPos == null
                                || !data.subLevelLocalPos.equals(localBlockPos);
                        if (changedTarget) {
                            data.subLevel = sl;
                            data.subLevelLocalPos = localBlockPos.immutable();
                            data.pos = BlockPos.containing(targetVec);
                            data.state = subLevelState;
                            data.progress = 0.0F;
                            data.subLevelDamageRemainder = 0.0F;
                        } else {
                            data.state = subLevelState;
                        }

                        hitSomething = true;
                        break;
                    }
                }
            }

            if (hitSomething && data.state != null) {
                if (data.subLevel != null && data.subLevelLocalPos != null) {
                    SubLevelContainer container = SubLevelContainer.getContainer(level);
                    if (container != null) {
                        ChunkPos chunkPos = new ChunkPos(data.subLevelLocalPos.getX() >> 4,
                                data.subLevelLocalPos.getZ() >> 4);
                        LevelChunk subLevelChunk = container.inBounds(chunkPos)
                                ? container.getChunk(chunkPos) : null;

                        if (subLevelChunk != null) {
                            BlockState currentState = subLevelChunk.getBlockState(data.subLevelLocalPos);
                            if (currentState.isAir() || currentState.is(ModBlocks.BROKEN_BLOCK.get())) {
                                hitSomething = false;
                                float destroyProgress = currentState.getDestroyProgress(
                                        player, subLevelChunk, data.subLevelLocalPos);

                                if (destroyProgress > 0.0F && Float.isFinite(destroyProgress)) {
                                    data.progress += destroyProgress;
                                    BlockEntity blockEntity = subLevelChunk.getBlockEntity(data.subLevelLocalPos);
                                    int maxHp = BlockHealthManager.getMaxHp(currentState, blockEntity);
                                    data.subLevelDamageRemainder += Math.max(0.25F, destroyProgress * maxHp * 0.25F);

                                    int damage = (int) data.subLevelDamageRemainder;
                                    if (damage > 0) {
                                        data.subLevelDamageRemainder -= damage;
                                        Vec3 blockCenterWorld = data.subLevel.logicalPose().transformPosition(
                                                new Vec3(data.subLevelLocalPos.getX() + 0.5,
                                                        data.subLevelLocalPos.getY() + 0.5,
                                                        data.subLevelLocalPos.getZ() + 0.5));
                                        SubLevelDamageHelper.damageBlockExact(
                                                level, data.subLevel, blockCenterWorld, damage);
                                    }
                                    Vec3 blockCenterWorld = data.subLevel.logicalPose().transformPosition(
                                            new Vec3(data.subLevelLocalPos.getX() + 0.5,
                                                    data.subLevelLocalPos.getY() + 0.5,
                                                    data.subLevelLocalPos.getZ() + 0.5));
                                    if (SubLevelDamageHelper.isBlockDead(level, data.subLevel, blockCenterWorld)) {
                                        data.progress = 0.0F;
                                        data.subLevelDamageRemainder = 0.0F;
                                        OxygenTankItem.setAir(bottle, Math.max(0, air - 10));
                                        PlasmaCutterItem.setBottle(mainHand, bottle);
                                    }
                                }
                            }
                        } else {
                            hitSomething = false;
                        }
                    } else {
                        hitSomething = false;
                    }
                } else {
                    // Тот же ванильный расчёт, который Minecraft использует при добыче блоков.
                    float destroyProgress = data.state.getDestroyProgress(player, level, data.pos);
                    if (destroyProgress > 0.0F && Float.isFinite(destroyProgress)) {
                        data.progress += destroyProgress;
                    }

                    if (level instanceof ServerLevel serverLevel) {
                        int stage = Math.min(9, Math.max(0, (int) (data.progress * 10.0F)));
                        serverLevel.destroyBlockProgress(player.getId(), data.pos, stage);
                    }

                    if (data.progress >= 1.0F) {
                        List<LivingEntity> entities = level.getEntitiesOfClass(
                                LivingEntity.class,
                                new AABB(data.pos).inflate(1.0),
                                e -> !(e instanceof Player));
                        for (LivingEntity mob : entities) {
                            mob.hurt(player.damageSources().playerAttack(player), 2.0f);
                            mob.igniteForSeconds(1);
                        }

                        boolean isUnderwater = true;
                        for (Direction dir : Direction.values()) {
                            if (level.getBlockState(data.pos.relative(dir)).isAir()) {
                                isUnderwater = false;
                                break;
                            }
                        }

                        if (isUnderwater) {
                            level.destroyBlock(data.pos, true, player);
                            level.setBlockAndUpdate(data.pos, Blocks.WATER.defaultBlockState());
                            level.playSound(null, data.pos, SoundEvents.FIRE_EXTINGUISH,
                                    SoundSource.BLOCKS, 1.0f, 1.0f);
                        } else {
                            level.destroyBlock(data.pos, true, player);
                        }

                        if (level instanceof ServerLevel serverLevel) {
                            serverLevel.destroyBlockProgress(player.getId(), data.pos, -1);
                        }

                        data.progress = 0.0F;
                        OxygenTankItem.setAir(bottle, Math.max(0, air - 10));
                        PlasmaCutterItem.setBottle(mainHand, bottle);
                    }
                }
            }

            if (!hitSomething) {
                if (data.pos != null && level instanceof ServerLevel serverLevel) {
                    serverLevel.destroyBlockProgress(player.getId(), data.pos, -1);
                }
                data.progress = 0.0F;
                data.subLevelDamageRemainder = 0.0F;
                data.pos = null;
                data.state = null;
                data.subLevel = null;
                data.subLevelLocalPos = null;
            }
        } else {
            MINING_DATA.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            if (stack.getItem() instanceof WeldingTorchItem || stack.getItem() instanceof PlasmaCutterItem) {
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
        if (maxBlocks <= 0 || subLevel == null || subLevel.isRemoved()) return 0;
        Level level = player.level();
        UUID subId = subLevel.getUniqueId();
        if (subId == null) return 0;

        Vec3 worldStart = player.getEyePosition();
        Vec3 worldDirection = player.getLookAngle().normalize();
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        int repaired = 0;
        int repairRange = 6;
        for (double distance = 0.15D; distance <= repairRange; distance += 0.08D) {
            Vec3 worldPoint = worldStart.add(worldDirection.scale(distance));
            Vec3 localPoint = subLevel.logicalPose().transformPositionInverse(worldPoint);
            BlockPos localPos = BlockPos.containing(localPoint.x, localPoint.y, localPoint.z);
            if (!visited.add(localPos)) continue;

            ChunkPos cp = new ChunkPos(localPos.getX() >> 4, localPos.getZ() >> 4);
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null || !container.inBounds(cp)) continue;
            LevelChunk chunk = container.getChunk(cp);
            if (chunk == null) continue;

            BlockState state = chunk.getBlockState(localPos);
            boolean candidate = state.is(ModBlocks.BROKEN_BLOCK.get());
            if (!candidate && !state.isAir()) {
                BlockEntity be = chunk.getBlockEntity(localPos);
                int maxHp = BlockHealthManager.getMaxHp(state, be);
                int currentHp = BlockHealthManager.getHp(subId, localPos.getX(), localPos.getY(), localPos.getZ(), state, be);
                candidate = currentHp < maxHp;
                // An intact block blocks the repair ray; do not repair through walls.
                if (!candidate) break;
            }
            if (!candidate) continue;

            if (repairBlock(level, subLevel, subId, localPos, worldPoint, player, simulate)) {
                repaired++;
                if (repaired >= maxBlocks) break;
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
                                       BlockPos plotGridBlockPos, Vec3 hitVec, Player player, boolean simulate) {
        int y = plotGridBlockPos.getY();
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return false;
        if (!(level instanceof ServerLevel serverLevel)) return false;

        ChunkPos cp = new ChunkPos(plotGridBlockPos.getX() >> 4, plotGridBlockPos.getZ() >> 4);
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !container.inBounds(cp)) return false;
        LevelChunk chunk = container.getChunk(cp);
        PlotChunkHolder holder = container.getChunkHolder(cp);
        if (chunk == null || holder == null) return false;

        int localX = plotGridBlockPos.getX() & 15;
        int localZ = plotGridBlockPos.getZ() & 15;
        BlockPos blockInChunk = new BlockPos(localX, y, localZ);
        BlockState state = chunk.getBlockState(blockInChunk);
        BlockEntity blockEntity = chunk.getBlockEntity(blockInChunk);

        BlockHealthManager.SavedBlock saved = BlockHealthManager.getDestroyedBlock(
                subId, plotGridBlockPos.getX(), y, plotGridBlockPos.getZ());
        if (saved == null && blockEntity instanceof BrokenBlockEntity brokenEntity
                && brokenEntity.getOriginalState() != null) {
            saved = new BlockHealthManager.SavedBlock(
                    brokenEntity.getOriginalState(), brokenEntity.getOriginalNbt());
        }

        int repairAmount = ModConfig.REPAIR_HP_PER_PULSE.get();
        if (saved != null && state.is(ModBlocks.BROKEN_BLOCK.get())) {
            if (simulate) return true;
            try {
                BlockState restoredState = saved.state();
                holder.handleBlockChange(plotGridBlockPos.getX(), y, plotGridBlockPos.getZ(), state, restoredState);
                LevelChunkSection[] sections = chunk.getSections();
                int sectionIndex = chunk.getSectionIndex(y);
                if (sectionIndex < 0 || sectionIndex >= sections.length || sections[sectionIndex] == null) return false;
                sections[sectionIndex].setBlockState(localX, y & 15, localZ, restoredState);
                chunk.removeBlockEntity(blockInChunk);

                BlockEntity restoredBe = null;
                if (saved.nbt() != null) {
                    restoredBe = BlockEntity.loadStatic(plotGridBlockPos, restoredState, saved.nbt(), level.registryAccess());
                    if (restoredBe != null) chunk.setBlockEntity(restoredBe);
                }
                chunk.setUnsaved(true);
                int maxHp = BlockHealthManager.getMaxHp(restoredState, restoredBe);
                int restoredHp = Math.min(maxHp, repairAmount);
                BlockHealthManager.setHp(subId, plotGridBlockPos.getX(), y, plotGridBlockPos.getZ(), restoredHp);
                sendRepairUpdate(serverLevel, plotGridBlockPos, restoredState, subId, restoredHp, maxHp, hitVec);
                if (player instanceof ServerPlayer serverPlayer && !isWearingCreateGoggles(serverPlayer)) {
                    PacketDistributor.sendToPlayer(serverPlayer, new RepairHpNoticePacket(restoredHp, maxHp));
                }
                return true;
            } catch (Throwable t) {
                logRepairFailure(plotGridBlockPos, "[SMB] gradual broken-block restoration failed", t);
                return false;
            }
        }

        if (state.isAir() || state.is(ModBlocks.BROKEN_BLOCK.get())) return false;
        int maxHp = BlockHealthManager.getMaxHp(state, blockEntity);
        int currentHp = BlockHealthManager.getHp(subId, plotGridBlockPos.getX(), y, plotGridBlockPos.getZ(), state, blockEntity);
        if (currentHp >= maxHp) return false;
        if (simulate) return true;

        int newHp = Math.min(maxHp, currentHp + repairAmount);
        BlockHealthManager.setHp(subId, plotGridBlockPos.getX(), y, plotGridBlockPos.getZ(), newHp);
        sendRepairUpdate(serverLevel, plotGridBlockPos, state, subId, newHp, maxHp, hitVec);
        if (player instanceof ServerPlayer serverPlayer && !isWearingCreateGoggles(serverPlayer)) {
            PacketDistributor.sendToPlayer(serverPlayer, new RepairHpNoticePacket(newHp, maxHp));
        }
        return true;
    }
    private static boolean isWearingCreateGoggles(ServerPlayer player) {
        ItemStack headStack = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(headStack.getItem());
        return ResourceLocation.fromNamespaceAndPath("create", "goggles").equals(itemId);
    }

    private static void sendRepairUpdate(ServerLevel level, BlockPos pos, BlockState state, UUID subId,
                                         int hp, int maxHp, Vec3 hitVec) {
        ClientboundBlockUpdatePacket blockPacket = new ClientboundBlockUpdatePacket(pos, state);
        BlockHealthSyncPacket hpPacket = new BlockHealthSyncPacket(
                subId, pos.getX(), pos.getY(), pos.getZ(), hp, maxHp);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(hitVec.x, hitVec.y, hitVec.z) < 4096) {
                viewer.connection.send(blockPacket);
                PacketDistributor.sendToPlayer(viewer, hpPacket);
            }
        }
    }
}
