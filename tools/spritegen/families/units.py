"""Units. Sprites face up and are lit from the top; the game adds a 3 px #565666 outline at load time, so
every region keeps a 4 px empty margin. Cells use only the team colors (#ffffff / #dcc6c6) and are drawn on
top of the body, tinted by team.

Allied (player-built) units: structural steel, amber lights, team cells.
Hostile (ancient) units: dark hull metal, rust, red instability lights.
"""
import math

import parts as K
import raster as R

UNIT_LIGHT = (0.35, 1.0)


def _cell(w, h, light_m, dark_m=None):
    c = R.Canvas(w, h, light=UNIT_LIGHT)
    if dark_m:
        c.fill(dark_m, "team", 1)
    c.fill(light_m, "team", 2)
    return c


def scout_drone():
    """Drone de reconhecimento: fast delta-wing scout; long nose sensor, swept wings, twin rear engines."""
    w = h = 48
    c = R.Canvas(w, h, light=UNIT_LIGHT)
    cx = 24
    wings = R.poly([(cx, 14), (cx + 19, 34), (cx + 17, 38), (cx + 5, 33), (cx - 5, 33), (cx - 17, 38), (cx - 19, 34)])
    K.plate(c, wings, "steel", 3, bevel=1, rim=1)
    for side in (-1, 1):
        tip = R.poly([(cx + side * 13, 30), (cx + side * 19, 34), (cx + side * 17, 38), (cx + side * 12, 35)])
        c.fill(tip & wings, "amber", 3)
        c.groove(R.line(cx + side * 4, 22, cx + side * 14, 32) & wings, depth=1, lip=1)
    body = R.poly([(cx, 5), (cx + 5, 16), (cx + 5, 36), (cx + 3, 41), (cx - 3, 41), (cx - 5, 36), (cx - 5, 16)])
    K.plate(c, body, "steel", 4, bevel=1, rim=1)
    canopy = R.ellipse(cx, 20, 2.6, 5)
    c.dome(canopy, "teal", 2, 5)
    for side in (-1, 1):
        eng = R.rect(cx + side * 7 - (2 if side > 0 else 1), 34, 3, 7)
        c.cylinder(eng, "dark", 2, 5, axis="v")
        c.fill(R.rect(cx + side * 7 - (2 if side > 0 else 1), 40, 3, 1), "amber", 4)
    cell = _cell(w, h, R.poly([(cx - 2, 27), (cx + 2, 27), (cx + 3, 33), (cx - 3, 33)]),
                 R.line(cx - 10, 31, cx - 6, 27) | R.line(cx + 6, 27, cx + 10, 31))
    return {"": c, "-cell": cell}


def maintenance_drone():
    """Drone de manutenção: boxy quad-rotor utility drone with a forward welding arm."""
    w = h = 56
    c = R.Canvas(w, h, light=UNIT_LIGHT)
    cx = cy = 28
    for (rx, ry) in ((13, 14), (43, 14), (13, 42), (43, 42)):
        boom = R.thick(cx, cy, rx, ry, 4)
        K.plate(c, boom, "steel", 3, bevel=1)
    for (rx, ry) in ((13, 14), (43, 14), (13, 42), (43, 42)):
        duct = R.ring(rx, ry, 9, 6.6)
        K.plate(c, duct, "steel", 4, bevel=1, rim=1)
        disc = R.circle(rx, ry, 6.6)
        c.fill(disc, "dark", 2)
        for k in range(3):
            a = k * 2.094 + (0.3 if rx > cx else -0.3)
            c.fill(R.thick(rx, ry, rx + 6 * math.cos(a), ry + 6 * math.sin(a), 1.6) & disc, "steel", 2)
        c.fill(R.circle(rx, ry, 1.6), "steel", 5)
    chassis = R.chamfer(cx - 10, cy - 11, 20, 24, 4)
    K.plate(c, chassis, "steel", 4, bevel=2, rim=1)
    K.panels(c, R.shrink(chassis, 3), R.rect(0, cy + 2, w, 1), "steel", 4)
    arm = R.rect(cx - 1, cy - 21, 3, 11)
    c.cylinder(arm, "steel", 2, 6, axis="v")
    tool = R.chamfer(cx - 3, cy - 24, 7, 4, 1)
    K.plate(c, tool, "dark", 4, bevel=1)
    c.set((cx, cy - 25), "amber", 6)
    K.lamp(c, cx + 6, cy + 9, "amber", size=1)
    cell = _cell(w, h, R.rect(cx - 6, cy - 6, 12, 3), R.rect(cx - 6, cy - 3, 12, 1))
    return {"": c, "-cell": cell}


def ancient_sentinel():
    """Sentinela antiga: hostile walking guardian; heavy angular pauldrons, armored head with a single red
    eye, reactor pack on the back."""
    w = h = 64
    c = R.Canvas(w, h, light=UNIT_LIGHT)
    cx, cy = 32, 33
    for side in (-1, 1):
        x0 = cx + side * 10
        pauldron = R.poly([(x0, cy - 12), (x0 + side * 14, cy - 8), (x0 + side * 16, cy + 6), (x0 + side * 6, cy + 10), (x0, cy + 6)])
        K.plate(c, pauldron, "dark", 4, bevel=2, rim=1)
        K.wear(c, {p for p in pauldron if p[1] > cy + 2}, "sentinel%d" % side, 0.3)
        c.groove(R.line(x0 + side * 3, cy - 6, x0 + side * 12, cy - 3) & pauldron, depth=1, lip=1)
    torso = R.chamfer(cx - 12, cy - 14, 24, 28, 6)
    K.plate(c, torso, "dark", 5, bevel=2, rim=1)
    pack = R.chamfer(cx - 8, cy + 6, 16, 11, 3)
    K.plate(c, pack, "dark", 3, bevel=1)
    for x in (cx - 5, cx - 1, cx + 3):
        c.fill(R.rect(x, cy + 9, 2, 5), "dark", 1)
        c.set((x, cy + 13), "red", 3)
    head = R.chamfer(cx - 7, cy - 20, 14, 12, 4)
    K.plate(c, head, "dark", 5, bevel=1, rim=1)
    visor = R.rect(cx - 5, cy - 16, 10, 3)
    c.fill(visor, "dark", 0)
    c.fill(R.rect(cx - 1, cy - 16, 3, 3), "red", 4)
    c.set((cx + 1, cy - 16), "red", 6)
    K.trace(c, [(cx - 8, cy - 4), (cx - 8, cy + 3)], "red", 3, node=False)
    K.trace(c, [(cx + 8, cy - 4), (cx + 8, cy + 3)], "red", 3, node=False)
    cell = _cell(w, h, R.rect(cx - 5, cy - 4, 10, 2), R.rect(cx - 5, cy - 2, 10, 1))

    leg = R.Canvas(w, h, light=UNIT_LIGHT)
    foot = R.chamfer(cx + 6, cy - 9, 10, 18, 3)
    K.plate(leg, foot, "dark", 4, bevel=1, rim=1)
    leg.fill(R.rect(cx + 8, cy - 7, 6, 2), "rust", 3)

    base = R.Canvas(w, h, light=UNIT_LIGHT)
    hip = R.regular(cx, cy, 12, 8, 22.5)
    K.plate(base, hip, "dark", 3, bevel=1)
    base.fill(R.circle(cx, cy, 4), "dark", 2)

    gun = R.Canvas(20, 38, light=UNIT_LIGHT)
    barrel = R.rect(7, 4, 6, 20)
    gun.cylinder(barrel, "dark", 2, 6, axis="v")
    K.plate(gun, R.rect(6, 4, 8, 3), "dark", 4, bevel=1)
    housing = R.chamfer(4, 18, 12, 15, 3)
    K.plate(gun, housing, "dark", 4, bevel=1, rim=1)
    gun.fill(R.rect(9, 23, 2, 6), "red", 3)
    return {"": c, "-cell": cell, "-leg": leg, "-base": base, "-weapon": gun}


def rust_crawler():
    """Aranha mecânica hostil: low hexagonal carapace, cluster of red sensor eyes, four jointed legs."""
    w, h = 72, 72
    c = R.Canvas(w, h, light=UNIT_LIGHT)
    cx, cy = 36, 38
    shell = R.regular(cx, cy, 20, 6, 90)
    K.plate(c, shell, "dark", 4, bevel=2, rim=1)
    plates = R.regular(cx, cy + 3, 13, 6, 90)
    K.panels(c, plates, R.line(cx, cy - 10, cx, cy + 16) | R.line(cx - 11, cy + 3, cx + 11, cy + 3), "dark", 5)
    K.wear(c, {p for p in shell if p[1] > cy + 6}, "crawler", 0.25)
    core = R.rect(cx - 1, cy + 5, 3, 8)
    c.fill(core, "red", 3)
    c.fill(R.rect(cx, cy + 6, 1, 6), "red", 5)
    head = R.chamfer(cx - 9, cy - 21, 18, 11, 4)
    K.plate(c, head, "dark", 5, bevel=1, rim=1)
    for (ex, ey, s) in ((cx - 4, cy - 17, 2), (cx + 2, cy - 17, 2), (cx - 6, cy - 13, 1), (cx + 5, cy - 13, 1)):
        c.fill(R.rect(ex, ey, s, s), "red", 4)
        c.set((ex + s - 1, ey), "red", 6)
    for side in (-1, 1):
        mand = R.poly([(cx + side * 3, cy - 21), (cx + side * 7, cy - 27), (cx + side * 5, cy - 21)])
        c.fill(mand, "steel", 3)
    cell = _cell(w, h, R.line(cx - 6, cy + 13, cx + 6, cy + 13) | R.line(cx - 4, cy + 14, cx + 4, cy + 14))

    leg = R.Canvas(44, 24, light=UNIT_LIGHT)
    seg = R.poly([(4, 9), (38, 10), (40, 12), (38, 14), (4, 15)])
    K.plate(leg, seg, "dark", 4, bevel=1, rim=1)
    leg.fill(R.rect(10, 11, 22, 2), "rust", 3)
    leg.fill(R.rect(20, 9, 2, 6), "dark", 2)

    legbase = R.Canvas(44, 24, light=UNIT_LIGHT)
    seg2 = R.poly([(4, 7), (36, 9), (40, 12), (36, 15), (4, 17)])
    K.plate(legbase, seg2, "dark", 4, bevel=1, rim=1)
    legbase.fill(R.line(8, 12, 32, 12), "red", 2)

    foot = R.Canvas(24, 24, light=UNIT_LIGHT)
    claw = R.poly([(12, 4), (17, 12), (12, 20), (7, 12)])
    K.plate(foot, claw, "dark", 4, bevel=1, rim=1)
    foot.fill(R.rect(11, 6, 2, 3), "steel", 4)

    joint = R.Canvas(16, 16, light=UNIT_LIGHT)
    jm = R.circle(8, 8, 5)
    joint.dome(jm, "dark", 2, 5)
    joint.set((9, 6), "rust", 4)
    return {"": c, "-cell": cell, "-leg": leg, "-leg-base": legbase, "-foot": foot, "-joint": joint}
