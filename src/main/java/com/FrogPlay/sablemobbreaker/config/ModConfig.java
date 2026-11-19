package com.FrogPlay.sablemobbreaker.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side balance settings for block damage and repair. */
public final class ModConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue HP_PER_HARDNESS;
    public static final ModConfigSpec.IntValue MIN_BLOCK_HP;
    public static final ModConfigSpec.IntValue MAX_BLOCK_HP;
    public static final ModConfigSpec.IntValue WELD_REPAIR_MAX_BLOCKS;
    public static final ModConfigSpec.DoubleValue MELEE_BLOCK_REACH;
    public static final ModConfigSpec.IntValue REPAIR_HP_PER_PULSE;

    static {
        BUILDER.push("block_health");
        HP_PER_HARDNESS = BUILDER.comment("How many HP one point of vanilla block hardness gives.")
                .defineInRange("hp_per_hardness", 5.0D, 0.1D, 100.0D);
        MIN_BLOCK_HP = BUILDER.comment("Minimum HP for a breakable block.")
                .defineInRange("min_block_hp", 5, 1, 10000);
        MAX_BLOCK_HP = BUILDER.comment("Maximum HP for a breakable block.")
                .defineInRange("max_block_hp", 200, 1, 100000);
        BUILDER.pop();

        BUILDER.push("repair");
        WELD_REPAIR_MAX_BLOCKS = BUILDER.comment("Maximum number of damaged blocks repaired along the player look ray per pulse.")
                .defineInRange("max_blocks_per_pulse", 3, 1, 3);
        REPAIR_HP_PER_PULSE = BUILDER.comment("HP restored to each block on one repair pulse.")
                .defineInRange("hp_per_repair_pulse", 10, 1, 200);
        BUILDER.pop();

        BUILDER.push("mobs");
        MELEE_BLOCK_REACH = BUILDER.comment("Maximum distance from a melee mob's bounding box to a structure before it may strike it.")
                .defineInRange("melee_structure_reach", 1.25D, 0.5D, 4.0D);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
    private ModConfig() {}
}
