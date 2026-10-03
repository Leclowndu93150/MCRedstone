package com.leclowndu93150.mcredstone.compiler.place;

import com.leclowndu93150.mcredstone.compiler.lib.Pin;
import com.leclowndu93150.mcredstone.compiler.lib.Template;
import com.leclowndu93150.mcredstone.compiler.voxel.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record PlacedCell(int id, Template template, int x, int tier, int z, Map<String, Integer> pinNets, String label) {
    public int y() {
        return Floorplan.deviceY(tier);
    }

    public long at(int dx, int dy, int dz) {
        return Pos.of(x + dx, y() + dy, z + dz);
    }

    public long pinPos(Pin pin) {
        return at(pin.dx(), 0, pin.dz());
    }

    public long ownerPos(Pin pin) {
        return at(pin.ownerDx(), 0, pin.ownerDz());
    }

    public List<Long> ownerPositions(Pin pin) {
        List<Long> positions = new ArrayList<>();
        for (int[] owner : pin.owners()) {
            positions.add(at(owner[0], 0, owner[1]));
        }
        return positions;
    }
}
