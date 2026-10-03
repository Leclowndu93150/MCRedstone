package com.leclowndu93150.mcredstone.compiler.route;

import java.util.List;

public record RouteNet(int id, Terminal source, List<Terminal> sinks) {
}
