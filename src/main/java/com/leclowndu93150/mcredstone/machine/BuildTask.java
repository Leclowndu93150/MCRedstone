package com.leclowndu93150.mcredstone.machine;

import com.leclowndu93150.mcredstone.compiler.layout.IoPin;
import com.leclowndu93150.mcredstone.compiler.layout.Compiler;
import com.leclowndu93150.mcredstone.compiler.layout.Layout;
import com.leclowndu93150.mcredstone.compiler.voxel.Kind;
import com.leclowndu93150.mcredstone.compiler.voxel.Pos;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BuildTask implements Tasks.Task {
    private static final int BLOCKS_PER_TICK = 16384;
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE | Block.UPDATE_SUPPRESS_DROPS;

    private final ServerLevel level;
    private final Machine machine;
    private final Consumer<Component> report;
    private final Consumer<Machine> onDone;
    private final List<BlockPos> positions = new ArrayList<>();
    private final List<BlockState> states = new ArrayList<>();
    private final List<BlockPos> comparators = new ArrayList<>();
    private final List<Integer> comparatorOutputs = new ArrayList<>();
    private final String summary;
    private int clearX;
    private int clearY;
    private int clearZ;
    private int placed;
    private boolean cleared;

    public BuildTask(ServerLevel level, Layout layout, BlockPos origin, String name, Consumer<Component> report, Consumer<Machine> onDone) {
        this.level = level;
        this.report = report;
        this.onDone = onDone;
        int[] size = layout.size();
        this.summary = size[0] + "x" + size[1] + "x" + size[2] + ", " + layout.grid().size() + " blocks"
            + (layout.timing() == null ? "" : ", clock " + layout.timing().period() + " gt");
        collect(layout, origin, positions, states, comparators, comparatorOutputs);
        this.machine = machine(name, level, layout, origin);
        this.clearX = machine.min().getX();
        this.clearY = machine.min().getY();
        this.clearZ = machine.min().getZ();
        for (ChunkPos chunk : machine.chunks()) {
            level.setChunkForced(chunk.x(), chunk.z(), true);
        }
    }

    public static Machine machine(String name, ServerLevel level, Layout layout, BlockPos origin) {
        int[] bounds = layout.grid().bounds();
        BlockPos min = origin.offset(bounds[0], bounds[1], bounds[2]);
        BlockPos max = origin.offset(bounds[3], bounds[4], bounds[5]);
        int period = layout.timing() == null ? 0 : layout.timing().period();
        int inputDelay = layout.timing() == null ? 0 : layout.timing().inputDelay();
        Optional<BlockPos> probe = layout.timing() == null ? Optional.empty() : Optional.of(world(origin, layout.timing().clockProbe()));
        return new Machine(name, layout.name(), level.dimension(), min, max, bindings(layout.inputs(), origin), bindings(layout.outputs(), origin),
            layout.settleTicks(), period, inputDelay, probe, Compiler.VERSION);
    }

    private static void collect(Layout layout, BlockPos origin, List<BlockPos> positions, List<BlockState> states, List<BlockPos> comparators, List<Integer> outputs) {
        layout.grid().forEach((pos, voxel) -> {
            BlockPos world = world(origin, pos);
            VoxelState painted = layout.states().get(pos);
            positions.add(world);
            states.add(VoxelStates.state(voxel, layout.grid(), pos, painted, layout.decor().get(pos)));
            if (voxel.kind() == Kind.COMPARATOR) {
                comparators.add(world);
                outputs.add(painted == null ? 0 : painted.power());
            }
        });
    }

    public static void demolish(ServerLevel level, Machine machine) {
        for (BlockPos pos : BlockPos.betweenClosed(machine.min(), machine.max())) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
        for (ChunkPos chunk : machine.chunks()) {
            level.setChunkForced(chunk.x(), chunk.z(), false);
        }
    }

    public static void placeAll(ServerLevel level, Layout layout, BlockPos origin) {
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        List<BlockPos> comparators = new ArrayList<>();
        List<Integer> outputs = new ArrayList<>();
        collect(layout, origin, positions, states, comparators, outputs);
        for (int i = 0; i < positions.size(); i++) {
            level.setBlock(positions.get(i), states.get(i), FLAGS);
        }
        setComparators(level, comparators, outputs);
    }

    private static void setComparators(ServerLevel level, List<BlockPos> comparators, List<Integer> outputs) {
        for (int i = 0; i < comparators.size(); i++) {
            if (level.getBlockEntity(comparators.get(i)) instanceof ComparatorBlockEntity comparator) {
                comparator.setOutputSignal(outputs.get(i));
            }
        }
    }

    private static BlockPos world(BlockPos origin, long pos) {
        return origin.offset(Pos.x(pos), Pos.y(pos), Pos.z(pos));
    }

    public static List<IoBinding> bindings(List<IoPin> pins, BlockPos origin) {
        List<IoBinding> bindings = new ArrayList<>();
        for (IoPin pin : pins) {
            bindings.add(new IoBinding(pin.port(), pin.bit(), world(origin, pin.pos()), pin.kind(), Optional.ofNullable(pin.key())));
        }
        return bindings;
    }

    public Machine machine() {
        return machine;
    }

    @Override
    public boolean tick(MinecraftServer server) {
        int budget = BLOCKS_PER_TICK;
        if (!cleared) {
            budget = clear(budget);
            if (!cleared) {
                return false;
            }
        }
        while (budget-- > 0 && placed < positions.size()) {
            level.setBlock(positions.get(placed), states.get(placed), FLAGS);
            placed++;
        }
        if (placed < positions.size()) {
            return false;
        }
        setComparators(level, comparators, comparatorOutputs);
        MachineRegistry.get(server).put(machine);
        report.accept(Component.literal("Built " + machine.name() + " (" + summary + ") at " + machine.min().toShortString()));
        onDone.accept(machine);
        return true;
    }

    private int clear(int budget) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos max = machine.max();
        BlockPos min = machine.min();
        while (budget > 0) {
            cursor.set(clearX, clearY, clearZ);
            if (!level.getBlockState(cursor).isAir()) {
                level.setBlock(cursor, Blocks.AIR.defaultBlockState(), FLAGS);
                budget--;
            }
            if (++clearX > max.getX()) {
                clearX = min.getX();
                if (++clearZ > max.getZ()) {
                    clearZ = min.getZ();
                    if (++clearY > max.getY()) {
                        cleared = true;
                        return budget;
                    }
                }
            }
        }
        return budget;
    }
}
