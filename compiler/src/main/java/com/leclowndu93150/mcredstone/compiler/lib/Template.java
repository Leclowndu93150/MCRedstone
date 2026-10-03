package com.leclowndu93150.mcredstone.compiler.lib;

import java.util.List;

public record Template(String name, List<Placed> voxels, List<int[]> blocked, List<Pin> pins, List<Edge> internalEdges, int delayTicks) {
    public Pin pin(String pinName) {
        for (Pin pin : pins) {
            if (pin.name().equals(pinName)) {
                return pin;
            }
        }
        throw new IllegalArgumentException(name + " has no pin " + pinName);
    }
}
