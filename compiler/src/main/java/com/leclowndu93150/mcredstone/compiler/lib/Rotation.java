package com.leclowndu93150.mcredstone.compiler.lib;

import com.leclowndu93150.mcredstone.compiler.voxel.Dir;
import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import java.util.ArrayList;
import java.util.List;

public final class Rotation {
    private Rotation() {
    }

    public static Template rotate(Template template, int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        if (turns == 0) {
            return template;
        }
        List<int[]> points = new ArrayList<>();
        for (Placed placed : template.voxels()) {
            points.add(new int[]{placed.dx(), placed.dz()});
        }
        for (int[] blocked : template.blocked()) {
            points.add(blocked);
        }
        for (Pin pin : template.pins()) {
            points.add(new int[]{pin.dx(), pin.dz()});
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (int[] point : points) {
            int[] rotated = rotate(point[0], point[1], turns);
            minX = Math.min(minX, rotated[0]);
            minZ = Math.min(minZ, rotated[1]);
        }
        List<Placed> voxels = new ArrayList<>();
        for (Placed placed : template.voxels()) {
            int[] p = shift(rotate(placed.dx(), placed.dz(), turns), minX, minZ);
            voxels.add(new Placed(p[0], placed.dy(), p[1], rotate(placed.voxel(), turns)));
        }
        List<int[]> blocked = new ArrayList<>();
        for (int[] cell : template.blocked()) {
            blocked.add(shift(rotate(cell[0], cell[1], turns), minX, minZ));
        }
        List<Pin> pins = new ArrayList<>();
        for (Pin pin : template.pins()) {
            int[] p = shift(rotate(pin.dx(), pin.dz(), turns), minX, minZ);
            int[] owner = shift(rotate(pin.ownerDx(), pin.ownerDz(), turns), minX, minZ);
            List<Dir> dirs = new ArrayList<>();
            for (Dir dir : pin.dirs()) {
                dirs.add(rotate(dir, turns));
            }
            List<int[]> moreOwners = new ArrayList<>();
            for (int[] extra : pin.moreOwners()) {
                moreOwners.add(shift(rotate(extra[0], extra[1], turns), minX, minZ));
            }
            pins.add(new Pin(pin.name(), pin.role(), p[0], p[1], dirs, owner[0], owner[1], moreOwners, pin.strength()));
        }
        List<Edge> edges = new ArrayList<>();
        for (Edge edge : template.internalEdges()) {
            int[] from = shift(rotate(edge.fromDx(), edge.fromDz(), turns), minX, minZ);
            int[] to = shift(rotate(edge.toDx(), edge.toDz(), turns), minX, minZ);
            edges.add(new Edge(from[0], edge.fromDy(), from[1], to[0], edge.toDy(), to[1], edge.side()));
        }
        return new Template(template.name() + "@" + turns, voxels, blocked, pins, edges, template.delayTicks());
    }

    public static Dir rotate(Dir dir, int turns) {
        Dir result = dir;
        for (int i = 0; i < turns; i++) {
            result = result.clockwise();
        }
        return result;
    }

    private static Voxel rotate(Voxel voxel, int turns) {
        if (voxel.dir() == null || !voxel.dir().horizontal()) {
            return voxel;
        }
        return new Voxel(voxel.kind(), rotate(voxel.dir(), turns), voxel.delay(), voxel.subtract());
    }

    private static int[] rotate(int x, int z, int turns) {
        int rx = x;
        int rz = z;
        for (int i = 0; i < turns; i++) {
            int nx = -rz;
            rz = rx;
            rx = nx;
        }
        return new int[]{rx, rz};
    }

    private static int[] shift(int[] point, int minX, int minZ) {
        return new int[]{point[0] - minX, point[1] - minZ};
    }
}
