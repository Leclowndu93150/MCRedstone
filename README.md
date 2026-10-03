# MCRedstone

A Fabric mod that turns hardware written in Python into working vanilla redstone. You describe a circuit with Amaranth (a Python HDL), and the mod compiles it into an actual redstone machine and builds it in your world. There are a few games in the examples (Flappy Bird, Pong) that you can play with your keyboard.

Minecraft 26.3, Fabric, Java 25. Install [Alternate Current](https://modrinth.com/mod/alternate-current) next to it, the compiler expects its wire behaviour.

## How it works

1. You write a design in Python using Amaranth. Inputs are levers (optionally bound to a key), outputs are lamps or a lamp screen.
2. Yosys turns the design into a list of NOR gates and flip-flops.
3. The compiler (plain Java, in `compiler/`) takes that gate list and:
   - places every gate as a small redstone cell (a torch on a block is a NOR gate, flip-flops are two locked repeaters),
   - wires everything together with dust, repeaters and staircases, making sure no two wires touch,
   - checks the finished build block by block against the gate list,
   - for clocked designs, adds a clock generator and tunes its delays so every flip-flop sees a clean clock.
4. The mod places the machine in the world, already powered up in its starting state, and keeps its chunks loaded.

The compiler has its own redstone simulator that matches the game tick for tick, so designs get checked before you ever build them.

Compiled layouts are cached in `run/mcredstone/layouts`, so a design only compiles once. Big designs take a few minutes the first time.

## The build

Machines are built in layers you can walk through. Every layer has a wool floor with some headroom and open sides. The floor color tells you what's on it:

- light blue: NOR gates
- lime: flip-flops
- red: the clock
- yellow: input levers
- orange: output lamps
- white and light gray: wiring

Screens are made of redstone lamps with no gaps between pixels. A pixel is 2x2 lamps by default, or 4x4 with `scale=2`.

## Writing a design

Set up Python once:

```
python -m venv hdl/.venv
hdl/.venv/Scripts/pip install -e hdl
```

A design is an Amaranth `Elaboratable` with ports from `mcrhdl`:

```python
from mcrhdl import Lever, Led, LampMatrix, pixel

self.jump = Lever("jump", key="up")
self.score = Led("score", 4)
self.screen = LampMatrix("screen", 16, 12, scale=2)
```

Keys you can bind: `up`, `down`, `left`, `right`, `a` (Z) and `b` (X). Clocked logic goes in `m.d.sync` like any Amaranth design.

Then synthesize it:

```
hdl/.venv/Scripts/python -m mcrhdl examples.pong
```

This writes the gate list and some test vectors into `run/mcredstone/designs/`, where the mod picks it up.

## In game

| Command | What it does |
|---|---|
| `/mcr build <design>` | Compile and build it in front of you |
| `/mcr play <machine> [tps]` | Start the machine and control it with your keys. Run it again to stop |
| `/mcr reset <machine>` | Put the machine back to its starting state |
| `/mcr test <machine> [count]` | Replay the recorded test vectors on the real build |
| `/mcr run <machine> [tps]` / `/mcr stop <machine>` | Start or stop the clock without play mode |
| `/mcr remove <machine>` | Remove a machine |
| `/mcr list` | List built machines |

Games need a lot of redstone ticks per frame, so play mode speeds up the server tick rate while you play and puts it back to 20 when you stop.

Designs listed in `run/mcredstone/autobuild.txt` get built automatically next to spawn when the world loads.

## Examples

All in `hdl/examples/`:

- `adder4`: 4 bit adder
- `counter8`: 8 bit counter
- `lfsr16`: 16 bit LFSR
- `dot`: move a dot around a small screen
- `flappy`: Flappy Bird, up to flap
- `pong`: Pong against a bot on a 64x48 lamp screen, up and down to move, Z to restart. Hit the ball with the edge of your paddle to send it at an angle, that's how you beat the bot.

## Building from source

```
gradlew build -x runGameTest
```

`gradlew :compiler:test` runs the compiler tests and `gradlew runGameTest` runs the in game tests on a headless server.

## License

MIT
