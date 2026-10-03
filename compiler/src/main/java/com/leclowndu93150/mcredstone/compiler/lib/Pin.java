package com.leclowndu93150.mcredstone.compiler.lib;

import com.leclowndu93150.mcredstone.compiler.voxel.Dir;
import java.util.ArrayList;
import java.util.List;

public record Pin(String name, PinRole role, int dx, int dz, List<Dir> dirs, int ownerDx, int ownerDz, List<int[]> moreOwners, int strength) {
    public Pin(String name, PinRole role, int dx, int dz, List<Dir> dirs, int ownerDx, int ownerDz) {
        this(name, role, dx, dz, dirs, ownerDx, ownerDz, List.of(), 15);
    }

    public List<int[]> owners() {
        List<int[]> owners = new ArrayList<>();
        owners.add(new int[]{ownerDx, ownerDz});
        owners.addAll(moreOwners);
        return owners;
    }
}
