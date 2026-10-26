package com.riseofnations.server;

import com.riseofnations.core.persist.CoreState;
import com.riseofnations.core.settlement.MarkerStatus;
import com.riseofnations.core.settlement.Settlement;
import com.riseofnations.core.settlement.SettlementError;
import com.riseofnations.core.settlement.SettlementService;
import com.riseofnations.core.settlement.SitePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The only place that looks at or changes real blocks for settlements.
 *
 * The capital marker is a tiny two-block pillar next to the player: a chiseled stone bricks pedestal with a
 * lightning rod on top. It is only ever placed into EMPTY space (air) on solid, dry ground, so no existing block
 * is ever replaced. Its base block id is stored with the settlement, so the server can later check it.
 * The block here MUST match {@link SettlementService#MARKER_BASE_BLOCK}.
 */
public final class SettlementWorld {

    /** Where the marker would go (base block), or why it cannot go anywhere near the player. */
    public record Site(BlockPos base, SettlementError error) { }

    private SettlementWorld() { }

    /** The dimension id for the settlement record, or null for a dimension we do not know. */
    public static String dimensionId(Level level) {
        if (level.dimension().equals(Level.OVERWORLD)) return "minecraft:overworld";
        if (level.dimension().equals(Level.NETHER)) return "minecraft:the_nether";
        if (level.dimension().equals(Level.END)) return "minecraft:the_end";
        return null;
    }

    /**
     * Looks at the four blocks around the player's own position (facing direction first) and returns the first
     * suitable marker spot. Uses the server's real player position only.
     */
    public static Site findSite(ServerPlayer player) {
        Level level = player.level();
        BlockPos feet = player.blockPosition();
        Direction facing = player.getDirection();
        Direction[] order = {facing, facing.getClockWise(), facing.getOpposite(), facing.getCounterClockWise()};
        SettlementError firstProblem = SettlementError.MARKER_BLOCKED;
        boolean first = true;
        for (Direction direction : order) {
            BlockPos base = feet.relative(direction);
            SettlementError problem = SitePlanner.evaluate(inspect(level, base));
            if (problem.isOk()) return new Site(base, SettlementError.NONE);
            if (first) {
                firstProblem = problem;
                first = false;
            }
        }
        return new Site(null, firstProblem);
    }

    private static SitePlanner.SiteInfo inspect(Level level, BlockPos base) {
        BlockPos top = base.above();
        BlockPos ground = base.below();
        if (level.isOutsideBuildHeight(ground) || level.isOutsideBuildHeight(top)) {
            return new SitePlanner.SiteInfo(false, false, false, false);
        }
        BlockState groundState = level.getBlockState(ground);
        boolean groundSolid = !groundState.isAir() && groundState.getFluidState().isEmpty();
        return new SitePlanner.SiteInfo(true, level.getBlockState(base).isAir(), level.getBlockState(top).isAir(), groundSolid);
    }

    /** Places the pillar. Returns false (and leaves nothing behind) if it could not be placed. */
    public static boolean placeMarker(Level level, BlockPos base) {
        BlockPos top = base.above();
        if (!level.setBlock(base, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 3)) {
            return false;
        }
        if (!level.setBlock(top, Blocks.LIGHTNING_ROD.defaultBlockState(), 3)) {
            level.setBlock(base, Blocks.AIR.defaultBlockState(), 3);
            return false;
        }
        return true;
    }

    /** Undoes {@link #placeMarker} right after it, only if the blocks are still exactly what we placed. */
    public static void removeMarker(Level level, BlockPos base) {
        BlockPos top = base.above();
        if (level.getBlockState(top).is(Blocks.LIGHTNING_ROD)) {
            level.setBlock(top, Blocks.AIR.defaultBlockState(), 3);
        }
        if (level.getBlockState(base).is(Blocks.CHISELED_STONE_BRICKS)) {
            level.setBlock(base, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /**
     * Is the owner's marker still standing? Only checked if the player is in the same dimension and the
     * chunk is already loaded, so this never forces chunk loading; otherwise the answer is UNKNOWN.
     */
    public static MarkerStatus markerStatus(ServerPlayer player, CoreState state) {
        Settlement capital = SettlementService.capitalOf(state, player.getUUID().toString());
        if (capital == null) return MarkerStatus.UNKNOWN;
        Level level = player.level();
        if (!capital.dimensionId().equals(dimensionId(level))) return MarkerStatus.UNKNOWN;
        BlockPos base = new BlockPos(capital.markerX(), capital.markerY(), capital.markerZ());
        if (!level.hasChunkAt(base)) return MarkerStatus.UNKNOWN;
        return level.getBlockState(base).is(Blocks.CHISELED_STONE_BRICKS) ? MarkerStatus.PRESENT : MarkerStatus.MISSING;
    }
}
