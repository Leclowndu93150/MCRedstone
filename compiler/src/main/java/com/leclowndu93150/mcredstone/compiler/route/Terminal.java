package com.leclowndu93150.mcredstone.compiler.route;

import com.leclowndu93150.mcredstone.compiler.voxel.Dir;
import java.util.List;

public record Terminal(int index, List<Dir> dirs, int strength) {
}
