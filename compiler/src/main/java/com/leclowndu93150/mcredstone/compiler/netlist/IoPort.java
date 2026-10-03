package com.leclowndu93150.mcredstone.compiler.netlist;

public record IoPort(String name, int width, String kind, String key, int columns, int scale) {
    public IoPort(String name, int width, String kind, String key) {
        this(name, width, kind, key, 0, 1);
    }

    public boolean matrix() {
        return columns > 0;
    }

    public int rows() {
        return columns == 0 ? 0 : width / columns;
    }
}
