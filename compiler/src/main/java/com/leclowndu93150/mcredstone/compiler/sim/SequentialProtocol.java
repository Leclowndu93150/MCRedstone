package com.leclowndu93150.mcredstone.compiler.sim;

import com.leclowndu93150.mcredstone.compiler.netlist.Vectors;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SequentialProtocol {
    public interface Io {
        boolean clock();

        void drive(String port, BigInteger value);

        BigInteger read(String port);

        void run(boolean on);
    }

    private static final int SHOWN_FAILURES = 5;

    private final Io io;
    private final List<Vectors.Step> steps;
    private final int inputDelay;
    private final int settle;
    private final List<String> failures = new ArrayList<>();
    private int failed;
    private int phase;
    private int index;
    private int wait;
    private int applyIn = -1;
    private boolean lastClock;

    public SequentialProtocol(Io io, List<Vectors.Step> steps, int inputDelay, int settle) {
        this.io = io;
        this.steps = steps;
        this.inputDelay = inputDelay;
        this.settle = settle;
    }

    public boolean done() {
        return phase == 3;
    }

    public int failed() {
        return failed;
    }

    public int total() {
        return steps.size();
    }

    public List<String> failures() {
        return failures;
    }

    public void tick() {
        switch (phase) {
            case 0 -> {
                io.run(false);
                apply(0);
                wait = settle;
                phase = 1;
            }
            case 1 -> {
                if (--wait > 0) {
                    return;
                }
                compare(0);
                index = 1;
                lastClock = io.clock();
                io.run(true);
                phase = 2;
            }
            case 2 -> {
                if (applyIn > 0 && --applyIn == 0) {
                    apply(index - 1);
                }
                boolean clock = io.clock();
                if (clock && !lastClock) {
                    if (index > 1) {
                        compare(index - 1);
                    }
                    if (index >= steps.size()) {
                        io.run(false);
                        phase = 3;
                        return;
                    }
                    index++;
                    applyIn = inputDelay;
                }
                lastClock = clock;
            }
            default -> {
            }
        }
    }

    private void apply(int step) {
        for (Map.Entry<String, BigInteger> entry : steps.get(step).set().entrySet()) {
            io.drive(entry.getKey(), entry.getValue());
        }
    }

    private void compare(int step) {
        for (Map.Entry<String, BigInteger> entry : steps.get(step).expect().entrySet()) {
            BigInteger actual = io.read(entry.getKey());
            if (!actual.equals(entry.getValue())) {
                failed++;
                if (failures.size() < SHOWN_FAILURES) {
                    failures.add("cycle " + step + " " + steps.get(step).set() + ": " + entry.getKey() + " = " + actual + ", expected " + entry.getValue());
                }
                return;
            }
        }
    }
}
