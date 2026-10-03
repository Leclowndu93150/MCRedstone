package com.leclowndu93150.mcredstone.compiler.voxel;

public final class Pos {
    private static final int BITS = 21;
    private static final long MASK = (1L << BITS) - 1;
    private static final int OFFSET = 1 << (BITS - 1);

    private Pos() {
    }

    public static long of(int x, int y, int z) {
        return ((long) (x + OFFSET) & MASK) << (2 * BITS) | ((long) (y + OFFSET) & MASK) << BITS | ((long) (z + OFFSET) & MASK);
    }

    public static int x(long pos) {
        return (int) ((pos >>> (2 * BITS)) & MASK) - OFFSET;
    }

    public static int y(long pos) {
        return (int) ((pos >>> BITS) & MASK) - OFFSET;
    }

    public static int z(long pos) {
        return (int) (pos & MASK) - OFFSET;
    }

    public static long offset(long pos, Dir dir) {
        return of(x(pos) + dir.dx, y(pos) + dir.dy, z(pos) + dir.dz);
    }

    public static long offset(long pos, int dx, int dy, int dz) {
        return of(x(pos) + dx, y(pos) + dy, z(pos) + dz);
    }

    public static String toString(long pos) {
        return "(" + x(pos) + ", " + y(pos) + ", " + z(pos) + ")";
    }
}
