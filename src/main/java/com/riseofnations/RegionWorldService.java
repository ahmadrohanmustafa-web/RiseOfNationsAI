package com.riseofnations;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Locale;

public final class RegionWorldService {
    private RegionWorldService() {}

    public static void prepareAndTeleport(ServerPlayer player, String region) {
        ServerLevel level = player.serverLevel();
        Region regionData = Region.forName(region);
        if (regionData == null) return;

        buildRegionIfNeeded(level, regionData);
        player.teleportTo(level, regionData.x + 0.5, 102.0, regionData.z + 0.5,
            java.util.Set.of(), player.getYRot(), player.getXRot());
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "You have arrived in " + regionData.displayName + ". Find the Job Center, then visit the historical market."));
    }

    private static void buildRegionIfNeeded(ServerLevel level, Region region) {
        BlockPos marker = new BlockPos(region.x, 90, region.z);
        if (level.getBlockState(marker).is(Blocks.GOLD_BLOCK)) return;

        Block ground = region.ground;
        Block path = region.path;
        Block wall = region.wall;
        Block roof = region.roof;
        Block accent = region.accent;

        // Clear and prepare a compact, safe starter village. The marker prevents
        // later visitors from rebuilding over players' changes.
        for (int x = region.x - 24; x <= region.x + 24; x++) {
            for (int z = region.z - 24; z <= region.z + 24; z++) {
                for (int y = 96; y <= 108; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
                level.setBlock(new BlockPos(x, 99, z), ground.defaultBlockState(), 3);
            }
        }
        level.setBlock(marker, Blocks.GOLD_BLOCK.defaultBlockState(), 3);

        // Main road and village square.
        for (int n = -20; n <= 20; n++) {
            for (int w = -2; w <= 2; w++) {
                level.setBlock(new BlockPos(region.x + n, 100, region.z + w), path.defaultBlockState(), 3);
                level.setBlock(new BlockPos(region.x + w, 100, region.z + n), path.defaultBlockState(), 3);
            }
        }
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                level.setBlock(new BlockPos(region.x + x, 100, region.z + z), accent.defaultBlockState(), 3);
            }
        }

        // Job Center: recognizable building, with a lectern at the entrance.
        buildHouse(level, region.x - 10, 100, region.z + 5, wall, roof, Blocks.SPRUCE_PLANKS);
        level.setBlock(new BlockPos(region.x - 10, 101, region.z + 5), Blocks.LECTERN.defaultBlockState(), 3);
        level.setBlock(new BlockPos(region.x - 10, 100, region.z + 3), path.defaultBlockState(), 3);

        // Historical market with stalls and storage barrels.
        buildHouse(level, region.x + 7, 100, region.z + 5, wall, roof, Blocks.OAK_PLANKS);
        level.setBlock(new BlockPos(region.x + 7, 101, region.z + 5), Blocks.BARREL.defaultBlockState(), 3);
        level.setBlock(new BlockPos(region.x + 10, 101, region.z + 5), Blocks.BARREL.defaultBlockState(), 3);
        level.setBlock(new BlockPos(region.x + 7, 101, region.z + 8), Blocks.CHEST.defaultBlockState(), 3);

        // Small town hall and a well make the settlement feel like a real hub.
        buildHouse(level, region.x, 100, region.z - 11, Blocks.STONE_BRICKS, roof, Blocks.OAK_PLANKS);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                    level.setBlock(new BlockPos(region.x + x, 100, region.z - 2 + z), Blocks.COBBLESTONE.defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(new BlockPos(region.x, 101, region.z - 2), Blocks.WATER.defaultBlockState(), 3);

        // Four lamp posts around the square.
        for (int[] offset : new int[][]{{-6,-6},{-6,6},{6,-6},{6,6}}) {
            int x = region.x + offset[0];
            int z = region.z + offset[1];
            level.setBlock(new BlockPos(x, 101, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, 102, z), Blocks.LANTERN.defaultBlockState(), 3);
        }
    }

    private static void buildHouse(ServerLevel level, int x, int floorY, int z, Block wall, Block roof, Block floor) {
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                level.setBlock(new BlockPos(x + dx, floorY, z + dz), floor.defaultBlockState(), 3);
                for (int dy = 1; dy <= 4; dy++) {
                    boolean edge = dx == 0 || dx == 6 || dz == 0 || dz == 6;
                    Block block = edge ? wall : Blocks.AIR;
                    if (edge && dy == 1 && dx == 3 && dz == 0) block = Blocks.AIR;
                    level.setBlock(new BlockPos(x + dx, floorY + dy, z + dz), block.defaultBlockState(), 3);
                }
            }
        }
        for (int dx = -1; dx <= 7; dx++) {
            for (int dz = -1; dz <= 7; dz++) {
                int roofY = floorY + 5 + Math.max(Math.abs(dx - 3), Math.abs(dz - 3)) / 2;
                level.setBlock(new BlockPos(x + dx, roofY, z + dz), roof.defaultBlockState(), 3);
            }
        }
        level.setBlock(new BlockPos(x + 3, floorY + 1, z), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(new BlockPos(x + 3, floorY + 2, z), Blocks.AIR.defaultBlockState(), 3);
    }

    private record Region(String slug, String displayName, int x, int z, Block ground, Block path, Block wall, Block roof, Block accent) {
        static Region forName(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "northreach" -> new Region("northreach", "Northreach", 0, 0,
                    Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BLUE_ICE);
                case "sunvale" -> new Region("sunvale", "Sunvale", 600, 0,
                    Blocks.GRASS_BLOCK, Blocks.DIRT_PATH, Blocks.OAK_PLANKS, Blocks.HAY_BLOCK, Blocks.YELLOW_TERRACOTTA);
                case "ironhold" -> new Region("ironhold", "Ironhold", 1200, 0,
                    Blocks.STONE, Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.IRON_BLOCK);
                case "stormhaven" -> new Region("stormhaven", "Stormhaven", 1800, 0,
                    Blocks.SAND, Blocks.SANDSTONE, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.PRISMARINE);
                default -> null;
            };
        }
    }
}
