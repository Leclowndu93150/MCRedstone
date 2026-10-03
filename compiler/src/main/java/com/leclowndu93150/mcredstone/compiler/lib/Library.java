package com.leclowndu93150.mcredstone.compiler.lib;

import com.leclowndu93150.mcredstone.compiler.voxel.Dir;
import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import java.util.ArrayList;
import java.util.List;

public final class Library {
    public static final String IN = "in";
    public static final String OUT = "out";
    public static final String D = "d";
    public static final String K1 = "k1";
    public static final String K2 = "k2";
    public static final List<String> NOR_INPUTS = List.of("in0", "in1", "in2");

    public static final Template TNOR = new Template(
        "tnor",
        List.of(
            new Placed(1, 0, 1, Voxel.SOLID),
            new Placed(1, 0, 2, Voxel.wallTorch(Dir.SOUTH))
        ),
        List.of(new int[]{0, 0}, new int[]{2, 0}, new int[]{0, 2}, new int[]{2, 2}, new int[]{0, 3}, new int[]{2, 3}),
        List.of(
            new Pin("in0", PinRole.INPUT, 1, 0, List.of(Dir.SOUTH), 1, 2),
            new Pin("in1", PinRole.INPUT, 0, 1, List.of(Dir.EAST), 1, 2),
            new Pin("in2", PinRole.INPUT, 2, 1, List.of(Dir.WEST), 1, 2),
            new Pin(OUT, PinRole.OUTPUT, 1, 3, List.of(Dir.SOUTH), 1, 2)
        ),
        List.of(),
        2
    );

    public static final Template INPUT_PAD = new Template(
        "input_pad",
        List.of(
            new Placed(0, 0, -1, Voxel.wallLever(Dir.NORTH)),
            new Placed(0, 0, 0, Voxel.SOLID),
            new Placed(0, 0, 1, Voxel.repeater(Dir.SOUTH, 1))
        ),
        List.of(new int[]{-1, 0}, new int[]{1, 0}),
        List.of(new Pin(OUT, PinRole.OUTPUT, 0, 2, List.of(Dir.SOUTH, Dir.EAST, Dir.WEST), 0, 1)),
        List.of(new Edge(0, 0, -1, 0, 0, 1)),
        2
    );

    public static final Template LED_OUT = new Template(
        "led_out",
        List.of(
            new Placed(0, 0, 0, Voxel.LAMP),
            new Placed(0, 0, 1, Voxel.repeater(Dir.NORTH, 1))
        ),
        List.of(new int[]{-1, 0}, new int[]{1, 0}),
        List.of(new Pin(IN, PinRole.INPUT, 0, 2, List.of(Dir.NORTH, Dir.EAST, Dir.WEST), 0, 1)),
        List.of(new Edge(0, 0, 1, 0, 0, 0)),
        2
    );

    public static final Template MSFF = new Template(
        "msff",
        List.of(
            new Placed(2, 0, 1, Voxel.repeater(Dir.SOUTH, 1)),
            new Placed(2, 0, 2, Voxel.repeater(Dir.SOUTH, 1)),
            new Placed(1, 0, 1, Voxel.repeater(Dir.EAST, 1)),
            new Placed(3, 0, 2, Voxel.repeater(Dir.WEST, 1))
        ),
        List.of(
            new int[]{0, 0}, new int[]{1, 0}, new int[]{3, 0}, new int[]{4, 0},
            new int[]{3, 1}, new int[]{4, 1}, new int[]{0, 2}, new int[]{1, 2},
            new int[]{0, 3}, new int[]{1, 3}, new int[]{3, 3}, new int[]{4, 3}
        ),
        List.of(
            new Pin(D, PinRole.INPUT, 2, 0, List.of(Dir.SOUTH), 2, 1),
            new Pin(K1, PinRole.INPUT, 0, 1, List.of(Dir.EAST), 1, 1),
            new Pin(K2, PinRole.INPUT, 4, 2, List.of(Dir.WEST), 3, 2),
            new Pin(OUT, PinRole.OUTPUT, 2, 3, List.of(Dir.SOUTH), 2, 2)
        ),
        List.of(
            new Edge(2, 0, 1, 2, 0, 2),
            new Edge(1, 0, 1, 2, 0, 1, true),
            new Edge(3, 0, 2, 2, 0, 2, true)
        ),
        4
    );

    public static final int PIXEL_WIDTH = 2;
    public static final int PIXEL_DEPTH = 12;

    public static final Template PIXEL_A = pixel(
        "pixel_a",
        List.of(
            new Placed(0, 0, 4, Voxel.WIRE),
            new Placed(1, 0, 4, Voxel.WIRE),
            new Placed(0, 0, 5, Voxel.repeater(Dir.NORTH, 1)),
            new Placed(0, 0, 6, Voxel.wallTorch(Dir.NORTH)),
            new Placed(0, 0, 7, Voxel.SOLID)
        ),
        List.of(new int[]{1, 5}, new int[]{1, 6}, new int[]{1, 7}, new int[]{1, 8}, new int[]{1, 9}, new int[]{1, 10}, new int[]{1, 11}),
        new Pin(IN, PinRole.INPUT, 0, 8, List.of(Dir.NORTH), 0, 6),
        List.of(new Edge(0, 0, 6, 0, 0, 5), new Edge(0, 0, 5, 0, 0, 3), new Edge(0, 0, 5, 1, 0, 3))
    );

    public static final Template PIXEL_B = pixel(
        "pixel_b",
        List.of(
            new Placed(0, 0, 4, Voxel.repeater(Dir.NORTH, 1)),
            new Placed(1, 0, 4, Voxel.repeater(Dir.NORTH, 1)),
            new Placed(0, 0, 5, Voxel.WIRE),
            new Placed(1, 0, 5, Voxel.WIRE),
            new Placed(0, 0, 6, Voxel.wallTorch(Dir.NORTH)),
            new Placed(0, 0, 7, Voxel.SOLID)
        ),
        List.of(new int[]{1, 6}, new int[]{1, 7}, new int[]{1, 8}, new int[]{1, 9}, new int[]{1, 10}, new int[]{1, 11}),
        new Pin(IN, PinRole.INPUT, 0, 8, List.of(Dir.NORTH), 0, 6),
        List.of(new Edge(0, 0, 6, 0, 0, 4), new Edge(0, 0, 6, 1, 0, 4), new Edge(0, 0, 4, 0, 0, 3), new Edge(1, 0, 4, 1, 0, 3))
    );

    public static final Template PIXEL_LIT = new Template(
        "pixel_lit",
        List.of(
            new Placed(0, 0, 0, Voxel.LAMP),
            new Placed(1, 0, 0, Voxel.LAMP),
            new Placed(0, 1, 0, Voxel.LAMP),
            new Placed(1, 1, 0, Voxel.LAMP),
            new Placed(0, 0, 1, Voxel.wallTorch(Dir.NORTH)),
            new Placed(1, 0, 1, Voxel.wallTorch(Dir.NORTH)),
            new Placed(0, 1, 1, Voxel.SOLID),
            new Placed(1, 1, 1, Voxel.SOLID),
            new Placed(0, 0, 2, Voxel.SOLID),
            new Placed(1, 0, 2, Voxel.SOLID)
        ),
        List.of(),
        List.of(),
        List.of(new Edge(0, 0, 1, 0, 0, 0), new Edge(0, 0, 1, 0, 1, 0), new Edge(1, 0, 1, 1, 0, 0), new Edge(1, 0, 1, 1, 1, 0)),
        2
    );

    public static final int BIG_PIXEL_WIDTH = 4;
    public static final int BIG_PIXEL_DEPTH = 13;

    public static final Template BIG_PIXEL = bigPixel("big_pixel", List.of(PIXEL_A, PIXEL_B), true);
    public static final Template BIG_PIXEL_LIT = bigPixel("big_pixel_lit", List.of(PIXEL_LIT, PIXEL_LIT), false);

    private static Template bigPixel(String name, List<Template> parts, boolean fed) {
        List<Placed> voxels = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        for (int level = 0; level <= 2; level += 2) {
            for (int part = 0; part < parts.size(); part++) {
                int dx = part * PIXEL_WIDTH;
                for (Placed placed : parts.get(part).voxels()) {
                    voxels.add(new Placed(placed.dx() + dx, placed.dy() + level, placed.dz(), placed.voxel()));
                }
                for (Edge edge : parts.get(part).internalEdges()) {
                    edges.add(new Edge(edge.fromDx() + dx, edge.fromDy() + level, edge.fromDz(), edge.toDx() + dx, edge.toDy() + level, edge.toDz(), edge.side()));
                }
            }
            if (fed) {
                for (int[] cell : new int[][]{{0, 8}, {2, 8}, {0, 9}, {1, 9}, {2, 9}, {1, 10}}) {
                    voxels.add(new Placed(cell[0], level, cell[1], Voxel.WIRE));
                }
                edges.add(new Edge(1, 0, 11, 0, level, 6));
                edges.add(new Edge(1, 0, 11, 2, level, 6));
            }
        }
        if (!fed) {
            return new Template(name, voxels, List.of(), List.of(), edges, 2);
        }
        voxels.add(new Placed(1, 0, 11, Voxel.repeater(Dir.NORTH, 1)));
        voxels.add(new Placed(2, 0, 10, Voxel.WIRE));
        voxels.add(new Placed(2, 0, 11, Voxel.GLASS));
        voxels.add(new Placed(2, 1, 11, Voxel.WIRE));
        voxels.add(new Placed(2, 2, 12, Voxel.WIRE));
        voxels.add(new Placed(1, 2, 12, Voxel.WIRE));
        voxels.add(new Placed(1, 2, 11, Voxel.WIRE));
        List<int[]> blocked = new ArrayList<>();
        for (int level = 0; level <= 2; level += 2) {
            for (int x = 0; x < BIG_PIXEL_WIDTH; x++) {
                for (int z = 4; z < BIG_PIXEL_DEPTH; z++) {
                    int dy = level;
                    int dz = z;
                    int dx = x;
                    boolean used = voxels.stream().anyMatch(placed -> placed.dx() == dx && placed.dy() == dy && placed.dz() == dz);
                    if (!used && !(level == 0 && x == 1 && z == 12)) {
                        blocked.add(new int[]{x, z, level});
                    }
                }
            }
        }
        Pin pin = new Pin(IN, PinRole.INPUT, 1, 12, List.of(Dir.NORTH), 1, 11);
        return new Template(name, voxels, blocked, List.of(pin), edges, 6);
    }

    private static Template pixel(String name, List<Placed> feed, List<int[]> blocked, Pin pin, List<Edge> feedEdges) {
        List<Placed> voxels = new ArrayList<>();
        List<Edge> edges = new ArrayList<>(feedEdges);
        for (int column = 0; column < PIXEL_WIDTH; column++) {
            voxels.add(new Placed(column, 0, 0, Voxel.LAMP));
            voxels.add(new Placed(column, 1, 0, Voxel.LAMP));
            voxels.add(new Placed(column, 0, 1, Voxel.wallTorch(Dir.NORTH)));
            voxels.add(new Placed(column, 1, 1, Voxel.SOLID));
            voxels.add(new Placed(column, 0, 2, Voxel.SOLID));
            voxels.add(new Placed(column, 0, 3, Voxel.repeater(Dir.NORTH, 1)));
            edges.add(new Edge(column, 0, 3, column, 0, 1));
            edges.add(new Edge(column, 0, 1, column, 0, 0));
            edges.add(new Edge(column, 0, 1, column, 1, 0));
        }
        voxels.addAll(feed);
        return new Template(name, voxels, blocked, List.of(pin), edges, 6);
    }

    public static final int[] MASTER = {2, 1};
    public static final int[] SLAVE = {2, 2};

    private Library() {
    }
}
