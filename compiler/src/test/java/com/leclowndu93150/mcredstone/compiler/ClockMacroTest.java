package com.leclowndu93150.mcredstone.compiler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.leclowndu93150.mcredstone.compiler.lib.ClockMacro;
import com.leclowndu93150.mcredstone.compiler.timing.ClockTuner;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClockMacroTest {
    @Test
    void oscillates() {
        ClockMacro macro = new ClockMacro(4);
        Map<String, Integer> delays = new LinkedHashMap<>();
        for (String name : macro.tunables().keySet()) {
            delays.put(name, 1);
        }
        delays.put("d1", 2);
        delays.put("d2", 2);
        delays.put("d3", 1);
        List<int[]> transitions = ClockTuner.waveform(macro, delays, 200);
        StringBuilder out = new StringBuilder();
        for (int[] t : transitions) {
            out.append(t[0]).append(":K").append(t[1]).append('=').append(t[2]).append(' ');
        }
        System.out.println(out);
        assertTrue(transitions.size() > 8, out.toString());
    }
}
