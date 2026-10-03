package com.leclowndu93150.mcredstone.machine;

import com.leclowndu93150.mcredstone.compiler.voxel.Dir;
import com.leclowndu93150.mcredstone.compiler.voxel.Kind;
import com.leclowndu93150.mcredstone.compiler.voxel.Pos;
import com.leclowndu93150.mcredstone.compiler.voxel.Side;
import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelGrid;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelRules;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelState;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.block.state.properties.SlabType;

public final class VoxelStates {
    private VoxelStates() {
    }

    public static BlockState state(Voxel voxel, VoxelGrid grid, long pos, VoxelState painted, String decor) {
        BlockState state = base(voxel, grid, pos, decor);
        if (painted == null) {
            return state;
        }
        return switch (voxel.kind()) {
            case WIRE -> state.setValue(BlockStateProperties.POWER, painted.power());
            case TORCH, WALL_TORCH, LAMP -> state.setValue(BlockStateProperties.LIT, painted.on());
            case REPEATER -> state.setValue(BlockStateProperties.POWERED, painted.on()).setValue(BlockStateProperties.LOCKED, painted.locked());
            case COMPARATOR, LEVER, STONE_BUTTON -> state.setValue(BlockStateProperties.POWERED, painted.on());
            default -> state;
        };
    }

    private static BlockState base(Voxel voxel, VoxelGrid grid, long pos, String decor) {
        return switch (voxel.kind()) {
            case SOLID -> decor == null ? Blocks.SMOOTH_STONE.defaultBlockState() : BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(decor)).defaultBlockState();
            case GLASS -> Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
            case WIRE -> wire(grid, pos);
            case TORCH -> Blocks.REDSTONE_TORCH.defaultBlockState();
            case WALL_TORCH -> Blocks.REDSTONE_WALL_TORCH.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, direction(voxel.dir()));
            case REPEATER -> Blocks.REPEATER.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, direction(voxel.dir().opposite()))
                .setValue(BlockStateProperties.DELAY, voxel.delay());
            case COMPARATOR -> Blocks.COMPARATOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, direction(voxel.dir().opposite()))
                .setValue(BlockStateProperties.MODE_COMPARATOR, voxel.subtract() ? ComparatorMode.SUBTRACT : ComparatorMode.COMPARE);
            case LAMP -> Blocks.REDSTONE_LAMP.defaultBlockState();
            case OBSERVER -> Blocks.OBSERVER.defaultBlockState()
                .setValue(BlockStateProperties.FACING, direction(voxel.dir().opposite()));
            case COPPER_BULB -> Blocks.COPPER_BULB.waxed().pick(WeatheringCopper.WeatherState.UNAFFECTED).defaultBlockState();
            case LEVER -> Blocks.LEVER.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, direction(voxel.dir()));
            case STONE_BUTTON -> Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, direction(voxel.dir()));
            case REDSTONE_BLOCK -> Blocks.REDSTONE_BLOCK.defaultBlockState();
        };
    }

    public static boolean isRedstone(Voxel voxel) {
        return switch (voxel.kind()) {
            case WIRE, TORCH, WALL_TORCH, REPEATER, COMPARATOR, LAMP, OBSERVER, COPPER_BULB -> true;
            default -> false;
        };
    }

    private static BlockState wire(VoxelGrid grid, long pos) {
        Map<Dir, Side> sides = VoxelRules.wireSides(grid, pos);
        return Blocks.REDSTONE_WIRE.defaultBlockState()
            .setValue(BlockStateProperties.NORTH_REDSTONE, side(grid, pos, Dir.NORTH, sides.get(Dir.NORTH)))
            .setValue(BlockStateProperties.EAST_REDSTONE, side(grid, pos, Dir.EAST, sides.get(Dir.EAST)))
            .setValue(BlockStateProperties.SOUTH_REDSTONE, side(grid, pos, Dir.SOUTH, sides.get(Dir.SOUTH)))
            .setValue(BlockStateProperties.WEST_REDSTONE, side(grid, pos, Dir.WEST, sides.get(Dir.WEST)));
    }

    private static RedstoneSide side(VoxelGrid grid, long pos, Dir dir, Side side) {
        return switch (side) {
            case NONE -> RedstoneSide.NONE;
            case SIDE -> RedstoneSide.SIDE;
            case UP -> {
                Voxel step = grid.get(Pos.offset(pos, dir));
                yield step != null && step.kind() == Kind.GLASS ? RedstoneSide.SIDE : RedstoneSide.UP;
            }
        };
    }

    public static Direction direction(Dir dir) {
        return Direction.valueOf(dir.name());
    }
}
