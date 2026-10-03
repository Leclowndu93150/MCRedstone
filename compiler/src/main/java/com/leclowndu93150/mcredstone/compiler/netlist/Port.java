package com.leclowndu93150.mcredstone.compiler.netlist;

public record Port(String name, int[] bits) {
    public int width() {
        return bits.length;
    }
}
