package com.leclowndu93150.mcredstone.compiler.timing;

import com.leclowndu93150.mcredstone.compiler.lib.ClockMacro;
import com.leclowndu93150.mcredstone.compiler.lib.Pin;
import com.leclowndu93150.mcredstone.compiler.lib.Placed;
import com.leclowndu93150.mcredstone.compiler.sim.GraphSim;
import com.leclowndu93150.mcredstone.compiler.voxel.Dir;
import com.leclowndu93150.mcredstone.compiler.voxel.Pos;
import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClockTuner {
    public record Choice(Map<String, Integer> delays, int period, int inputDelay, int[] edges) {
    }

    private static final int MARGIN = 2;
    private static final int WARMUP_PERIODS = 3;
    private static final int MAX_DELAY = 4;

    private ClockTuner() {
    }

    public static Choice tune(ClockMacro macro, Sta.Result sta) {
        int returns = 0;
        for (String name : macro.tunables().keySet()) {
            if (name.startsWith("r") && !name.equals("rs")) {
                returns++;
            }
        }
        Bench bench = new Bench(macro);
        int fixed = 2;
        int shiftSlots = ClockMacro.SHIFT_SLOTS.length;
        int minRing = shiftSlots + returns + fixed;
        int maxRing = MAX_DELAY * (shiftSlots + returns) + fixed;
        for (int ring = minRing; ring <= maxRing; ring++) {
            for (int shift = shiftSlots; shift <= MAX_DELAY * shiftSlots; shift++) {
                int rest = ring - fixed - shift;
                if (rest < returns || rest > MAX_DELAY * returns || (returns == 0 && rest != 0)) {
                    continue;
                }
                for (int line = ClockMacro.DELAY_SLOTS; line <= ClockMacro.DELAY_SLOTS * MAX_DELAY; line++) {
                    Map<String, Integer> delays = new LinkedHashMap<>();
                    delays.put("l", 1);
                    delays.put("rs", 1);
                    spread(delays, ClockMacro.SHIFT_SLOTS, shift);
                    spread(delays, ClockMacro.DELAY_NAMES, line);
                    for (int i = 0; i < returns; i++) {
                        delays.put("r" + (i + 1), rest / returns + (i < rest % returns ? 1 : 0));
                    }
                    int[] edges = bench.edges(delays);
                    if (edges == null) {
                        continue;
                    }
                    int inputDelay = sta.k1Max() + MARGIN;
                    if (valid(edges, sta, inputDelay)) {
                        return new Choice(delays, edges[4] - edges[0], inputDelay, edges);
                    }
                }
            }
        }
        throw new IllegalStateException("no clock timing satisfies the design: " + sta);
    }

    private static void spread(Map<String, Integer> delays, String[] names, int total) {
        int remaining = total - names.length;
        for (String name : names) {
            int extra = Math.min(MAX_DELAY - 1, remaining);
            remaining -= extra;
            delays.put(name, 1 + extra);
        }
    }

    private static boolean valid(int[] e, Sta.Result sta, int inputDelay) {
        int k1Rise = e[0];
        int k2Fall = e[1];
        int k2Rise = e[2];
        int k1Fall = e[3];
        int nextK1Rise = e[4];
        int lockK1Min = sta.k1Min();
        int lockK1Max = sta.k1Max();
        int lockK2Min = sta.k2Min();
        int lockK2Max = sta.k2Max();
        if (k2Fall + lockK2Min < k1Rise + lockK1Max + MARGIN) {
            return false;
        }
        if (k1Fall + lockK1Min < k2Rise + lockK2Max + MARGIN) {
            return false;
        }
        if ((k2Rise + lockK2Min) - (k2Fall + lockK2Max) < 2 + MARGIN) {
            return false;
        }
        int slaveChange = k2Fall + lockK2Max + 2;
        int masterLock = nextK1Rise + lockK1Min;
        if (slaveChange + sta.logic() + 2 + MARGIN > masterLock) {
            return false;
        }
        if (k1Fall + lockK1Max + 2 + MARGIN > masterLock) {
            return false;
        }
        if (k1Rise + inputDelay + sta.input() + 2 + MARGIN > masterLock) {
            return false;
        }
        if (slaveChange + sta.output() + 4 + MARGIN > nextK1Rise) {
            return false;
        }
        return k1Rise + inputDelay + sta.inputOutput() + 4 + MARGIN <= nextK1Rise;
    }

    public static List<int[]> waveform(ClockMacro macro, Map<String, Integer> delays, int ticks) {
        return new Bench(macro).transitions(delays, ticks, Integer.MAX_VALUE);
    }

    private static final class Bench {
        private final ClockMacro macro;
        private final GraphSim sim;
        private final long lever;
        private final long t1;
        private final long t2;
        private final long t3;
        private final long k2Last;

        Bench(ClockMacro macro) {
            this.macro = macro;
            VoxelGrid grid = new VoxelGrid();
            for (int x = -1; x <= ClockMacro.WIDTH; x++) {
                for (int z = -2; z <= ClockMacro.DEPTH; z++) {
                    grid.set(x, 0, z, Voxel.GLASS);
                }
            }
            for (Placed placed : macro.template().voxels()) {
                grid.set(placed.dx(), 1, placed.dz(), placed.voxel());
            }
            for (Pin pin : macro.template().pins()) {
                grid.set(pin.dx(), 1, pin.dz(), Voxel.WIRE);
            }
            Pin hold = macro.template().pin(ClockMacro.HOLD);
            lever = Pos.of(hold.dx(), 1, hold.dz() - 1);
            grid.set(hold.dx(), 1, hold.dz() - 2, Voxel.SOLID);
            grid.set(lever, Voxel.wallLever(Dir.SOUTH));
            sim = new GraphSim(grid);
            t1 = Pos.of(ClockMacro.TAP1[0], 1, ClockMacro.TAP1[1]);
            t2 = Pos.of(ClockMacro.TAP2[0], 1, ClockMacro.TAP2[1]);
            t3 = Pos.of(ClockMacro.TAP3[0], 1, ClockMacro.TAP3[1]);
            k2Last = Pos.of(ClockMacro.K2_LAST[0], 1, ClockMacro.K2_LAST[1]);
        }

        int[] edges(Map<String, Integer> delays) {
            List<int[]> transitions = transitions(delays, 2000, 4 * (WARMUP_PERIODS + 2));
            return transitions == null ? null : ClockTuner.edges(transitions);
        }

        List<int[]> transitions(Map<String, Integer> delays, int ticks, int limit) {
            sim.reset();
            for (Map.Entry<String, int[]> entry : macro.tunables().entrySet()) {
                Integer delay = delays.get(entry.getKey());
                if (delay != null) {
                    sim.setRepeaterDelay(Pos.of(entry.getValue()[0], 1, entry.getValue()[1]), delay);
                }
            }
            sim.kickAll();
            sim.setLever(lever, true);
            if (!sim.settle(500)) {
                return null;
            }
            sim.setLever(lever, false);
            boolean k1 = on(t1) || on(t2);
            boolean k2 = on(t3) || on(k2Last);
            List<int[]> transitions = new ArrayList<>();
            for (int tick = 1; tick <= ticks && transitions.size() < limit; tick++) {
                sim.step();
                boolean nextK1 = on(t1) || on(t2);
                boolean nextK2 = on(t3) || on(k2Last);
                if (nextK1 != k1) {
                    transitions.add(new int[]{tick, 1, nextK1 ? 1 : 0});
                }
                if (nextK2 != k2) {
                    transitions.add(new int[]{tick, 2, nextK2 ? 1 : 0});
                }
                k1 = nextK1;
                k2 = nextK2;
                if (sim.burnouts() > 0) {
                    return null;
                }
            }
            return transitions;
        }

        private boolean on(long pos) {
            return sim.node(pos).on();
        }
    }

    private static int[] edges(List<int[]> transitions) {
        int start = -1;
        int seen = 0;
        for (int i = 0; i < transitions.size(); i++) {
            int[] t = transitions.get(i);
            if (t[1] == 1 && t[2] == 1 && ++seen == WARMUP_PERIODS) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        int[] edges = new int[5];
        int[] expect = {1, 1, 2, 0, 2, 1, 1, 0, 1, 1};
        int found = 0;
        edges[0] = transitions.get(start)[0];
        for (int i = start + 1; i < transitions.size() && found < 4; i++) {
            int[] t = transitions.get(i);
            int want = found + 1;
            if (t[1] == expect[2 * want] && t[2] == expect[2 * want + 1]) {
                edges[want] = t[0];
                found++;
            } else {
                return null;
            }
        }
        return found == 4 ? edges : null;
    }

    public static void apply(VoxelGrid grid, ClockMacro macro, Map<String, Integer> delays, int originX, int originY, int originZ) {
        for (Map.Entry<String, int[]> entry : macro.tunables().entrySet()) {
            Integer delay = delays.get(entry.getKey());
            if (delay == null) {
                continue;
            }
            long pos = Pos.of(originX + entry.getValue()[0], originY + 1, originZ + entry.getValue()[1]);
            Voxel voxel = grid.get(pos);
            grid.set(pos, Voxel.repeater(voxel.dir(), delay));
        }
    }
}
