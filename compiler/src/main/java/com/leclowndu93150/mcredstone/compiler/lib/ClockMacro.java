package com.leclowndu93150.mcredstone.compiler.lib;

import com.leclowndu93150.mcredstone.compiler.voxel.Dir;
import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClockMacro {
    public static final String HOLD = "hold";
    public static final String K1 = "k1";
    public static final String K2 = "k2";
    public static final int MAX_RETURN_REPEATERS = 8;
    public static final int WIDTH = 14;
    public static final int DEPTH = 9;
    public static final int[] REDSTONE_BLOCK = {3, 3};
    public static final int[] COMPARATOR = {4, 3};
    public static final int[] A = {6, 3};
    public static final int[] B = {8, 3};
    public static final int[] C = {7, 3};
    public static final String[] SHIFT_SLOTS = {"a", "c", "b"};
    public static final String[] DELAY_NAMES = {"d1", "d2", "d3", "d4", "d5"};
    public static final int[] LOCKED = {11, 3};
    public static final int[] HOLD_REPEATER = {11, 2};
    public static final int[] RETURN_INTO_COMPARATOR = {4, 4};
    public static final int[] TAP1 = {5, 2};
    public static final int[] TAP2 = {9, 2};
    public static final int[] TAP3 = {4, 6};
    public static final int[] DELAY1 = {3, 5};
    public static final int[] DELAY2 = {1, 5};
    public static final int[] DELAY3 = {0, 6};
    public static final int[] DELAY4 = {2, 5};
    public static final int[] DELAY5 = {1, 7};
    public static final int[] K2_LAST = DELAY5;
    public static final int DELAY_SLOTS = DELAY_NAMES.length;
    private static final int[][] RETURN_SLOTS = {{12, 5}, {11, 5}, {10, 5}, {9, 5}, {8, 5}, {7, 5}, {6, 5}, {5, 5}};

    private final Template template;
    private final Map<String, int[]> tunables = new LinkedHashMap<>();

    public ClockMacro(int returnRepeaters) {
        Map<Long, Voxel> voxels = new LinkedHashMap<>();
        put(voxels, REDSTONE_BLOCK, Voxel.REDSTONE_BLOCK);
        put(voxels, COMPARATOR, Voxel.comparator(Dir.EAST, true));
        put(voxels, new int[]{5, 3}, Voxel.WIRE);
        put(voxels, A, Voxel.repeater(Dir.EAST, 1));
        put(voxels, C, Voxel.repeater(Dir.EAST, 1));
        put(voxels, B, Voxel.repeater(Dir.EAST, 1));
        put(voxels, new int[]{9, 3}, Voxel.WIRE);
        put(voxels, new int[]{10, 3}, Voxel.WIRE);
        put(voxels, LOCKED, Voxel.repeater(Dir.EAST, 1));
        put(voxels, new int[]{12, 3}, Voxel.WIRE);
        put(voxels, new int[]{13, 3}, Voxel.WIRE);
        put(voxels, new int[]{13, 4}, Voxel.WIRE);
        put(voxels, new int[]{13, 5}, Voxel.WIRE);
        for (int x = 5; x <= 12; x++) {
            put(voxels, new int[]{x, 5}, Voxel.WIRE);
        }
        for (int i = 0; i < returnRepeaters; i++) {
            put(voxels, RETURN_SLOTS[i], Voxel.repeater(Dir.WEST, 1));
            tunables.put("r" + (i + 1), RETURN_SLOTS[i]);
        }
        put(voxels, new int[]{4, 5}, Voxel.WIRE);
        put(voxels, RETURN_INTO_COMPARATOR, Voxel.repeater(Dir.NORTH, 1));
        put(voxels, TAP1, Voxel.repeater(Dir.NORTH, 1));
        put(voxels, TAP2, Voxel.repeater(Dir.NORTH, 1));
        for (int x = 5; x <= 9; x++) {
            put(voxels, new int[]{x, 1}, Voxel.WIRE);
        }
        put(voxels, HOLD_REPEATER, Voxel.repeater(Dir.SOUTH, 1));
        put(voxels, TAP3, Voxel.repeater(Dir.SOUTH, 1));
        put(voxels, DELAY1, Voxel.repeater(Dir.WEST, 1));
        put(voxels, DELAY4, Voxel.repeater(Dir.WEST, 1));
        put(voxels, DELAY2, Voxel.repeater(Dir.WEST, 1));
        put(voxels, new int[]{0, 5}, Voxel.WIRE);
        put(voxels, DELAY3, Voxel.repeater(Dir.SOUTH, 1));
        put(voxels, new int[]{0, 7}, Voxel.WIRE);
        put(voxels, DELAY5, Voxel.repeater(Dir.EAST, 1));
        for (int x = 2; x <= 4; x++) {
            put(voxels, new int[]{x, 7}, Voxel.WIRE);
        }
        tunables.put("a", A);
        tunables.put("b", B);
        tunables.put("c", C);
        tunables.put("l", LOCKED);
        tunables.put("rs", RETURN_INTO_COMPARATOR);
        tunables.put("d1", DELAY1);
        tunables.put("d2", DELAY2);
        tunables.put("d3", DELAY3);
        tunables.put("d4", DELAY4);
        tunables.put("d5", DELAY5);
        List<Pin> pins = List.of(
            new Pin(HOLD, PinRole.INPUT, 11, 1, List.of(Dir.SOUTH), HOLD_REPEATER[0], HOLD_REPEATER[1]),
            new Pin(K1, PinRole.OUTPUT, 7, 0, List.of(Dir.NORTH), TAP1[0], TAP1[1], List.<int[]>of(TAP2), 12),
            new Pin(K2, PinRole.OUTPUT, 2, 8, List.of(Dir.SOUTH), TAP3[0], TAP3[1], List.<int[]>of(K2_LAST), 12)
        );
        Set<Long> used = new HashSet<>(voxels.keySet());
        for (Pin pin : pins) {
            used.add(key(pin.dx(), pin.dz()));
            for (Dir dir : pin.dirs()) {
                int sign = pin.role() == PinRole.INPUT ? -1 : 1;
                used.add(key(pin.dx() + sign * dir.dx, pin.dz() + sign * dir.dz));
            }
        }
        List<int[]> blocked = new ArrayList<>();
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                if (!used.contains(key(x, z))) {
                    blocked.add(new int[]{x, z});
                }
            }
        }
        List<Placed> placed = new ArrayList<>();
        for (Map.Entry<Long, Voxel> entry : voxels.entrySet()) {
            placed.add(new Placed((int) (entry.getKey() >> 16), 0, (int) (entry.getKey() & 0xFFFF), entry.getValue()));
        }
        int[] ringEnd = returnRepeaters > 0 ? RETURN_SLOTS[returnRepeaters - 1] : LOCKED;
        List<Edge> edges = new ArrayList<>();
        edges.add(edge(REDSTONE_BLOCK, COMPARATOR, false));
        edges.add(edge(COMPARATOR, A, false));
        edges.add(edge(COMPARATOR, TAP1, false));
        edges.add(edge(A, C, false));
        edges.add(edge(C, B, false));
        edges.add(edge(B, LOCKED, false));
        edges.add(edge(B, TAP2, false));
        int[] previous = LOCKED;
        for (int i = 0; i < returnRepeaters; i++) {
            edges.add(edge(previous, RETURN_SLOTS[i], false));
            previous = RETURN_SLOTS[i];
        }
        edges.add(edge(ringEnd, RETURN_INTO_COMPARATOR, false));
        edges.add(edge(ringEnd, TAP3, false));
        edges.add(edge(ringEnd, DELAY1, false));
        edges.add(edge(RETURN_INTO_COMPARATOR, COMPARATOR, true));
        edges.add(edge(HOLD_REPEATER, LOCKED, true));
        edges.add(edge(DELAY1, DELAY4, false));
        edges.add(edge(DELAY4, DELAY2, false));
        edges.add(edge(DELAY2, DELAY3, false));
        edges.add(edge(DELAY3, DELAY5, false));
        this.template = new Template("clock" + returnRepeaters, placed, blocked, pins, edges, 0);
    }

    public Template template() {
        return template;
    }

    public Map<String, int[]> tunables() {
        return tunables;
    }

    private static void put(Map<Long, Voxel> voxels, int[] at, Voxel voxel) {
        voxels.put(key(at[0], at[1]), voxel);
    }

    private static long key(int x, int z) {
        return ((long) x << 16) | z;
    }

    private static Edge edge(int[] from, int[] to, boolean side) {
        return new Edge(from[0], 0, from[1], to[0], 0, to[1], side);
    }
}
