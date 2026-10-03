package com.leclowndu93150.mcredstone.compiler.verify;

import java.util.Map;

public record Reach(Map<Long, Integer> inputs, Map<Long, Integer> sides) {
    public boolean reaches(long reader) {
        return inputs.containsKey(reader) || sides.containsKey(reader);
    }
}
