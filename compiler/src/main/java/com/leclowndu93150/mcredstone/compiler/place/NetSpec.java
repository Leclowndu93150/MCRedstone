package com.leclowndu93150.mcredstone.compiler.place;

import java.util.List;

public record NetSpec(int id, int bit, PinRef source, List<PinRef> sinks) {
}
