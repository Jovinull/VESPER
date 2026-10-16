"""Walls and turrets.

Turret heads face up in the image and are lit from the right (Mindustry turret convention). The game adds a
4 px #404049 outline to turret heads at load time, so heads keep a 4 px empty margin and carry no outline.
Bases are ordinary block sprites named <turret>-base.
"""
import math

import parts as K
import raster as R

TURRET_LIGHT = (1.0, 0.35)


def on_base(layer, n):
    """Centre a turret layer on an n x n canvas. Mindustry composes block icons by drawing every icon layer at
    the top-left corner (Block.createIcons), so a head smaller than the base would sit off-centre in the
    build menu and tooltips. In the world the game draws regions centred, so this changes nothing there."""
    out = R.Canvas(n, n, light=TURRET_LIGHT)
    out.paste(layer, (n - layer.w) // 2, (n - layer.h) // 2)
    return out


def turret_base(n, chamfer, ring_r, alt="a", ramp="steel", seed="tbase"):
    """Deck with a raised, bolted turntable; the head covers its middle."""
    c = R.Canvas(n, n)
    K.deck(c, n, chamfer, ramp, 4, inset=3, seed="%s%d%s" % (seed, n, alt), rust=0.08, layout="x" if alt == "a" else "cross")
    cx = cy = n / 2
    table = R.circle(cx, cy, ring_r)
    c.drop_shadow(table, -1, 2)
    K.plate(c, table, "dark", 4, bevel=2, rim=1)
    c.fill(R.ring(cx, cy, ring_r - 3, ring_r - 4), "dark", 2)
    c.cavity(R.circle(cx, cy, ring_r - 6), "dark", 2, bevel=1)
    for k in range(10):
        a = 2 * math.pi * (k + 0.5) / 10
        K.stud(c, int(cx + (ring_r - 1.6) * math.cos(a)), int(cy + (ring_r - 1.6) * math.sin(a)), "steel", 5)
    return c


# ------------------------------------------------------------------------------------------ walls

def reinforced_wall():
    """Muralha reforçada 1x1: layered armor block with bolted face plate."""
    n = 32
    c = R.Canvas(n, n)
    body = R.chamfer(0, 0, n, n, 2)
    c.plate(body, "steel", 3, bevel=2, hi=1, lo=2, rim=1, edge_lo=1)
    face = R.chamfer(4, 4, 24, 24, 3)
    c.drop_shadow(face, -1, 1)
    K.panels(c, face, R.rect(4, 15, 24, 1), "steel", 4)
    for p in ((6, 6), (24, 6), (6, 24), (24, 24)):
        K.rivet(c, p[0], p[1], "steel", 3)
    K.wear(c, {p for p in body if p[0] - p[1] < -14}, "wall1", 0.12)
    return {"": c}


def heavy_wall():
    """Muralha pesada 2x2: thick armored slab, recessed center rib and hazard-marked corners."""
    n = 64
    c = R.Canvas(n, n)
    body = R.chamfer(0, 0, n, n, 4)
    c.plate(body, "steel", 3, bevel=2, hi=1, lo=2, rim=1, edge_lo=1)
    cuts = R.rect(31, 0, 2, n) | R.rect(0, 31, n, 2)
    face = R.chamfer(5, 5, 54, 54, 5)
    c.drop_shadow(face, -1, 2)
    K.panels(c, face, cuts, "steel", 4, gap=("dark", 1))
    for (x, y) in ((8, 8), (44, 8), (8, 44), (44, 44)):
        K.stripes(c, R.chamfer(x, y, 12, 12, 2) & face, ("amber", 3), ("steel", 3), period=6, width=3)
    hub = R.chamfer(25, 25, 14, 14, 3)
    K.raised(c, hub, "dark", 4, bevel=1)
    for p in ((27, 27), (35, 27), (27, 35), (35, 35)):
        K.stud(c, p[0], p[1], "steel", 6)
    K.wear(c, {p for p in body if p[0] - p[1] < -26}, "wall2", 0.12)
    return {"": c}


# ------------------------------------------------------------------------------------------ turrets

def kinetic_turret(alt="b"):
    """Torre cinética 2x2: heavy single-barrel autocannon with recoil sleeve and shoulder plates."""
    base = turret_base(64, 5, 23, "a")
    w, h = 52, 64
    head = R.Canvas(w, h, light=TURRET_LIGHT)
    cx = w // 2
    if alt == "a":
        breech = R.chamfer(cx - 15, 28, 30, 28, 6)
        for bx in (cx - 8, cx + 3):
            barrel = R.rect(bx, 5, 5, 28)
            head.cylinder(barrel, "steel", 2, 6, axis="v")
            K.plate(head, R.rect(bx - 1, 5, 7, 5), "dark", 4, bevel=1)
            head.fill(R.rect(bx + 1, 5, 3, 1), "dark", 0)
            K.plate(head, R.rect(bx - 1, 20, 7, 3), "steel", 3, bevel=1)
        K.plate(head, breech, "steel", 4, bevel=2, rim=1)
        K.panels(head, R.shrink(breech, 3), R.rect(0, 40, w, 1), "steel", 4)
        drum = R.circle(cx - 15, 46, 8)
        K.plate(head, drum, "dark", 4, bevel=2)
        head.fill(R.ring(cx - 15, 46, 4.5, 3.5), "amber", 3)
        K.stripes(head, R.rect(cx - 6, 49, 14, 4), ("amber", 4), ("dark", 2), period=4, width=2)
        K.lamp(head, cx + 9, 32, "amber", size=1)
    else:
        barrel = R.rect(cx - 3, 4, 7, 32)
        head.cylinder(barrel, "steel", 2, 6, axis="v")
        head.cylinder(R.rect(cx - 4, 22, 9, 10), "steel", 1, 5, axis="v")
        K.plate(head, R.rect(cx - 5, 4, 11, 5), "dark", 4, bevel=1)
        head.fill(R.rect(cx - 1, 4, 3, 1), "dark", 0)
        for sx in (cx - 19, cx + 9):
            sh = R.chamfer(sx, 30, 11, 24, 3)
            K.plate(head, sh, "steel", 3, bevel=1)
            head.recolor(R.shrink(sh, 2) & R.rect(0, 33, w, 8), "amber", -1)
        breech = R.chamfer(cx - 11, 30, 23, 28, 5)
        K.plate(head, breech, "steel", 4, bevel=2, rim=1)
        head.groove(R.line(cx - 8, 44, cx + 8, 44), depth=1, lip=1)
        K.stripes(head, R.rect(cx - 7, 50, 15, 4), ("amber", 4), ("dark", 2), period=4, width=2)
        K.lamp(head, cx - 1, 36, "amber", size=2)
    heat = R.Canvas(w, h, light=TURRET_LIGHT)
    heat.fill(R.rect(cx - 2, 5, 5, 16), "red", 4, 200)
    heat.fill(R.rect(cx - 1, 5, 3, 10), "red", 5, 230)
    return {"-base": base, "": on_base(head, base.w), "-heat": on_base(heat, base.w)}


def pulse_turret(alt="a"):
    """Torre de pulso 2x2: resonance emitter, twin forked prongs around a charged coil."""
    base = turret_base(64, 5, 23, "b", seed="pulse")
    w, h = 52, 60
    head = R.Canvas(w, h, light=TURRET_LIGHT)
    cx = w // 2
    for side in (-1, 1):
        pts = [(cx + side * 4, 40), (cx + side * 13, 30), (cx + side * 11, 6), (cx + side * 7, 6), (cx + side * 8, 28), (cx + side * 3, 32)]
        prong = R.poly(pts)
        K.plate(head, prong, "steel", 4, bevel=1, rim=1)
        tip = R.rect(min(cx + side * 7, cx + side * 11), 5, 5, 3)
        head.fill(tip, "teal", 4)
    coil = R.rect(cx - 5, 14, 10, 18)
    head.cylinder(coil, "copper", 2, 5, axis="v")
    for y in range(15, 31, 3):
        head.fill(R.rect(cx - 5, y, 10, 1), "copper", 1)
    head.fill(R.rect(cx - 1, 10, 2, 22), "teal", 5)
    body = R.chamfer(cx - 13, 30, 26, 24, 6)
    K.plate(head, body, "dark", 4, bevel=2, rim=1)
    lens = R.circle(cx, 41, 6)
    head.dome(lens, "teal", 2, 5)
    K.trace(head, [(cx - 9, 50), (cx - 4, 50)], node=False)
    K.trace(head, [(cx + 4, 50), (cx + 9, 50)], node=False)
    heat = R.Canvas(w, h, light=TURRET_LIGHT)
    heat.fill(R.rect(cx - 2, 8, 4, 24), "teal", 5, 180)
    heat.fill(R.circle(cx, 41, 3), "teal", 6, 220)
    return {"-base": base, "": on_base(head, base.w), "-heat": on_base(heat, base.w)}


def ancient_platform(alt="a"):
    """Plataforma defensiva antiga 3x3: recovered ancient emplacement; long lance, crystal capacitor."""
    n = 96
    base = R.Canvas(n, n)
    K.foundation(base, n, 4, "dark", 4)
    body = R.regular(48, 48, 49, 8, 22.5) & R.rect(1, 1, n - 2, n - 2)
    base.drop_shadow(body, -1, 2)
    base.plate(body, "dark", 4, bevel=2, hi=1, lo=2, rim=1)
    K.wear(base, {p for p in body if p[0] - p[1] < -20}, "platform", 0.12)
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        K.trace(base, [(int(48 + 30 * math.cos(a)), int(48 + 30 * math.sin(a))), (int(48 + 41 * math.cos(a)), int(48 + 41 * math.sin(a)))], node=True)
    table = R.circle(48, 48, 28)
    base.drop_shadow(table, -1, 2)
    K.plate(base, table, "dark", 5, bevel=2)
    K.glyph_ring(base, 48, 48, 25, "teal", 3, gaps=8, gap_len=0.25)
    base.cavity(R.circle(48, 48, 20), "dark", 2, bevel=1)
    K.cracks(base, "platform-crack", (20, 70), 14)

    w, h = 72, 96
    head = R.Canvas(w, h, light=TURRET_LIGHT)
    cx = w // 2
    for side in (-1, 1):
        wing = R.poly([(cx + side * 10, 42), (cx + side * 30, 54), (cx + side * 29, 80), (cx + side * 12, 74)])
        K.plate(head, wing, "dark", 4, bevel=2, rim=1)
        K.trace(head, [(cx + side * 14, 51), (cx + side * 24, 58), (cx + side * 24, 72)], node=True)
        K.wear(head, {p for p in wing if p[1] > 66}, "wing%d" % side, 0.2)
    lance = R.poly([(cx - 6, 54), (cx - 5, 9), (cx, 4), (cx + 5, 9), (cx + 6, 54)])
    K.plate(head, lance, "dark", 5, bevel=2, rim=1)
    head.fill(R.line(cx, 8, cx, 50), "teal", 4)
    head.fill(R.line(cx + 1, 12, cx + 1, 46), "teal", 3)
    for y in (16, 27, 38):
        ringm = R.chamfer(cx - 8, y, 17, 4, 1)
        K.plate(head, ringm, "steel", 4, bevel=1)
        K.wear(head, ringm, "lance%d" % y, 0.3)
    core = R.chamfer(cx - 15, 46, 30, 38, 8)
    K.plate(head, core, "dark", 4, bevel=2, rim=1)
    gem = R.regular(cx, 63, 9, 6, 30)
    head.facets([(gem & R.rect(0, 0, cx, h), (-1, 0.4)), (gem - R.rect(0, 0, cx, h), (1, 0.6))], "teal", 4, 1)
    head.set((cx + 2, 58), "teal", 6)
    head.fill(R.rect(cx - 6, 77, 12, 3), "dark", 1)
    heat = R.Canvas(w, h, light=TURRET_LIGHT)
    heat.fill(R.line(cx, 6, cx, 50) | R.line(cx - 1, 10, cx - 1, 44) | R.line(cx + 1, 10, cx + 1, 44), "teal", 5, 200)
    heat.fill(gem, "teal", 6, 160)
    return {"-base": base, "": on_base(head, base.w), "-heat": on_base(heat, base.w)}
