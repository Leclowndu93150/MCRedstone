package com.leclowndu93150.mcredstone.compiler.sim;

import com.leclowndu93150.mcredstone.compiler.voxel.Kind;
import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class Node {
    final int id;
    final long pos;
    Voxel voxel;
    final Kind kind;
    final List<int[]> inputs = new ArrayList<>();
    final List<int[]> sides = new ArrayList<>();
    final List<Node> readers = new ArrayList<>();
    final Deque<Long> toggles = new ArrayDeque<>();
    boolean prioritized;
    boolean on;
    int output;
    boolean pending;
    boolean dueNow;

    Node(int id, long pos, Voxel voxel) {
        this.id = id;
        this.pos = pos;
        this.voxel = voxel;
        this.kind = voxel.kind();
    }

    public long pos() {
        return pos;
    }

    public Voxel voxel() {
        return voxel;
    }

    public boolean on() {
        return on;
    }

    public int signal() {
        return switch (kind) {
            case COMPARATOR -> on ? output : 0;
            case REDSTONE_BLOCK -> 15;
            case LAMP -> 0;
            default -> on ? 15 : 0;
        };
    }

    public int comparatorOutput() {
        return output;
    }
}
