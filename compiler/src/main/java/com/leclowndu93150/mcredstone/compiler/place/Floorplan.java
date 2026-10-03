package com.leclowndu93150.mcredstone.compiler.place;

import java.util.List;

public record Floorplan(List<PlacedCell> cells, List<NetSpec> nets, List<IoBit> inputs, List<IoBit> outputs, int tiers, int minX, int maxX, int minZ, int maxZ, int zoneEnd) {
    public static int deviceY(int tier) {
        return 2 * tier + 1;
    }

    public static int floorY(int tier) {
        return 2 * tier;
    }

    public PlacedCell cell(int id) {
        return cells.get(id);
    }
}
