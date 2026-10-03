package com.leclowndu93150.mcredstone.compiler;

import com.leclowndu93150.mcredstone.compiler.layout.Ascii;
import com.leclowndu93150.mcredstone.compiler.layout.Compiler;
import com.leclowndu93150.mcredstone.compiler.layout.Layout;
import com.leclowndu93150.mcredstone.compiler.layout.LayoutCache;
import com.leclowndu93150.mcredstone.compiler.layout.LayoutIO;
import com.leclowndu93150.mcredstone.compiler.netlist.Design;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length >= 4 && args[0].equals("cache")) {
            Layout layout = LayoutCache.load(Path.of(args[1]), Path.of(args[2]), args[3], System.out::println);
            System.out.println(layout.report());
            return;
        }
        if (args.length < 3 || !(args[0].equals("compile") || args[0].equals("render"))) {
            System.err.println("usage: compile|render <designs-dir> <name> [-o <output-file>] | cache <designs-dir> <layouts-dir> <name>");
            System.exit(2);
        }
        Compiler.Assembly assembly = Compiler.assemble(Design.load(Path.of(args[1]), args[2]), System.out::println);
        System.out.println(assembly.layout().report());
        for (String error : assembly.lvs().errors()) {
            System.out.println("  " + error);
        }
        if (args.length >= 5 && args[3].equals("-o")) {
            Path output = Path.of(args[4]);
            if (args[0].equals("render")) {
                Files.writeString(output, Ascii.render(assembly.layout().grid()));
            } else {
                LayoutIO.write(assembly.layout(), output);
            }
        }
        if (!assembly.lvs().clean()) {
            System.exit(1);
        }
    }
}
