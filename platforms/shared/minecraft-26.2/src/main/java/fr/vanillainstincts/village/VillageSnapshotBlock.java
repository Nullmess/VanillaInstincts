package fr.vanillainstincts.village;

import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.state.BlockState;

/** Block state remembered for daytime repair. */
record VillageSnapshotBlock(BlockState state,
                            VillagerProfession profession,
                            boolean road) {
}
