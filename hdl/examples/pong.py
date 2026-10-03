from amaranth import Cat, Elaboratable, Module, Mux, Signal

from mcrhdl import LampMatrix, Lever, pixel

WIDTH = 16
HEIGHT = 12
TOP = 1
BOTTOM = HEIGHT - 1
PADDLE = 3
PADDLE_MAX = BOTTOM - PADDLE + 1
PADDLE_START = (TOP + PADDLE_MAX) // 2
WIN = 7
SERVE_FRAMES = 4
FLAT = 0
DOWN = 1
UP = 2
LEFT = 1
RIGHT = 0
NET = WIDTH // 2
BALL_X = NET - 1
BALL_Y = (TOP + BOTTOM) // 2


def covers(paddle, row):
    lit = 0
    for top in range(max(TOP, row - PADDLE + 1), min(PADDLE_MAX, row) + 1):
        lit = lit | (paddle == top)
    return lit


class Pong(Elaboratable):
    def __init__(self):
        self.up = Lever("up", key="up")
        self.down = Lever("down", key="down")
        self.restart = Lever("restart", key="a")
        self.screen = LampMatrix("screen", WIDTH, HEIGHT, scale=2)

    def elaborate(self, platform):
        m = Module()
        player = Signal(4, init=PADDLE_START)
        bot = Signal(4, init=PADDLE_START)
        ball_x = Signal(4, init=BALL_X)
        ball_y = Signal(4, init=BALL_Y)
        dx = Signal(init=LEFT)
        dy = Signal(2, init=DOWN)
        serve = Signal(3, init=SERVE_FRAMES)
        player_points = Signal(WIN)
        bot_points = Signal(WIN)
        over = Signal()
        slow = Signal()
        noise = Signal(8, init=0x5A)

        m.d.sync += noise.eq(Cat(noise[1:], noise[0] ^ noise[2] ^ noise[3] ^ noise[4]))
        m.d.sync += slow.eq(~slow)

        with m.If(self.up & (player > TOP)):
            m.d.sync += player.eq(player - 1)
        with m.Elif(self.down & (player < PADDLE_MAX)):
            m.d.sync += player.eq(player + 1)

        target = Signal(4)
        m.d.comb += target.eq(Mux((dx == RIGHT) & (ball_x >= NET), ball_y - 1, PADDLE_START))
        with m.If(slow):
            with m.If((bot > target) & (bot > TOP)):
                m.d.sync += bot.eq(bot - 1)
            with m.Elif((bot < target) & (bot < PADDLE_MAX)):
                m.d.sync += bot.eq(bot + 1)

        bounce_dy = Signal(2)
        m.d.comb += bounce_dy.eq(dy)
        with m.If((ball_y == TOP) & (dy == UP)):
            m.d.comb += bounce_dy.eq(DOWN)
        with m.Elif((ball_y == BOTTOM) & (dy == DOWN)):
            m.d.comb += bounce_dy.eq(UP)

        player_hit = Signal()
        bot_hit = Signal()
        m.d.comb += [
            player_hit.eq((dx == LEFT) & (ball_x == 1) & (ball_y >= player) & (ball_y < player + PADDLE)),
            bot_hit.eq((dx == RIGHT) & (ball_x == WIDTH - 2) & (ball_y >= bot) & (ball_y < bot + PADDLE)),
        ]
        paddle_top = Signal(4)
        m.d.comb += paddle_top.eq(Mux(dx == LEFT, player, bot))

        english = Signal(2)
        with m.If(ball_y == paddle_top):
            m.d.comb += english.eq(Mux(ball_y == TOP, FLAT, UP))
        with m.Elif(ball_y == paddle_top + PADDLE - 1):
            m.d.comb += english.eq(Mux(ball_y == BOTTOM, FLAT, DOWN))
        with m.Else():
            m.d.comb += english.eq(FLAT)

        new_dx = Signal()
        new_dy = Signal(2)
        m.d.comb += [new_dx.eq(dx), new_dy.eq(bounce_dy)]
        with m.If(player_hit | bot_hit):
            m.d.comb += [new_dx.eq(~dx), new_dy.eq(english)]

        def reset_ball(direction):
            return [
                ball_x.eq(BALL_X),
                ball_y.eq(BALL_Y),
                dx.eq(direction),
                dy.eq(Mux(noise[0], UP, DOWN)),
                serve.eq(SERVE_FRAMES),
            ]

        with m.If(self.restart):
            m.d.sync += reset_ball(LEFT)
            m.d.sync += [player_points.eq(0), bot_points.eq(0), over.eq(0), player.eq(PADDLE_START), bot.eq(PADDLE_START)]
        with m.Elif(over):
            pass
        with m.Elif(serve != 0):
            m.d.sync += serve.eq(serve - 1)
        with m.Elif(ball_x == 0):
            m.d.sync += reset_ball(LEFT)
            m.d.sync += [bot_points.eq(Cat(1, bot_points[:-1])), over.eq(bot_points[WIN - 2])]
        with m.Elif(ball_x == WIDTH - 1):
            m.d.sync += reset_ball(RIGHT)
            m.d.sync += [player_points.eq(Cat(1, player_points[:-1])), over.eq(player_points[WIN - 2])]
        with m.Else():
            m.d.sync += [
                dx.eq(new_dx),
                dy.eq(new_dy),
                ball_x.eq(Mux(new_dx == LEFT, ball_x - 1, ball_x + 1)),
                ball_y.eq(ball_y + (new_dy == DOWN) - (new_dy == UP)),
            ]

        for y in range(HEIGHT):
            for x in range(WIDTH):
                if y == 0:
                    if x < WIN:
                        lit = player_points[x]
                    elif x >= WIDTH - WIN:
                        lit = bot_points[WIDTH - 1 - x]
                    else:
                        lit = 0
                else:
                    lit = (ball_x == x) & (ball_y == y)
                    if x == 0:
                        lit = lit | covers(player, y)
                    elif x == WIDTH - 1:
                        lit = lit | covers(bot, y)
                    elif x == NET and y % 2 == 1:
                        lit = 1
                m.d.comb += pixel(self.screen, x, y).eq(lit)
        return m


def top():
    return Pong()
