"""Extraction machines. Drill draws region, then the spinning -rotator, then the static -top."""
import math

import parts as K
import raster as R


def drill_head(n, cx, cy, r, count=4, ramp="steel", base=4, sweep=0.3, width=0.42, tips="rust"):
    """Cutting head for a -rotator region: thick curved blades with bright leading edges."""
    c = R.Canvas(n, n)
    disc = R.circle(cx, cy, r)
    c.fill(disc, "dark", 2)
    bl = K.blades(cx, cy, r, r * 0.28, count, width=width, sweep=sweep)
    c.plate(bl, ramp, base, bevel=1, hi=2, lo=2, rim=0)
    if tips:
        c.recolor(bl - R.circle(cx, cy, r - 2.2), tips, 0)
    c.dome(R.circle(cx, cy, r * 0.34), ramp, 2, 5, spec=False)
    c.fill(R.ring(cx, cy, r, r - 1), "dark", 1)
    return c


def collar(c, cx, cy, r_out, r_in, bolts=8, ramp="steel", base=3):
    """Bore collar around a rotor: raised ring with bolts, drawn in the base region."""
    ring = R.ring(cx, cy, r_out, r_in)
    K.raised(c, ring, ramp, base, bevel=1)
    for k in range(bolts):
        a = 2 * math.pi * (k + 0.5) / bolts
        rr = (r_out + r_in) / 2
        K.stud(c, int(cx + rr * math.cos(a)), int(cy + rr * math.sin(a)), ramp, base + 2)
    return ring


def survey_drill(alt="a"):
    """Perfuradora básica 2x2: compact frame, cutting head in a bolted collar."""
    n, cx, cy = 64, 32, 32
    base = R.Canvas(n, n)
    K.deck(base, n, 5, "steel", 4, inset=3, seed="survey" + alt, rust=0.08, layout="cross")

    if alt == "a":
        # motor housings left and right, joined by a gantry on the top layer
        for x in (3, n - 12):
            hous = R.chamfer(x, 14, 9, 36, 2)
            K.raised(base, hous, "steel", 3, bevel=1)
            K.vent(base, x + 2, 18, 5, 11, horizontal=True)
            K.lamp(base, x + 4, 41, "amber", size=1)
        well = R.circle(cx, cy, 20.5)
    else:
        # four hazard-marked corner clamps around an octagonal well
        for (x, y) in ((4, 4), (n - 14, 4), (4, n - 14), (n - 14, n - 14)):
            clamp = R.chamfer(x, y, 10, 10, 3)
            K.raised(base, clamp, "dark", 4, bevel=1)
            K.stripes(base, R.shrink(clamp, 2), ("amber", 4), ("dark", 2), period=4, width=2)
        well = R.chamfer(10, 10, 44, 44, 12)
        K.lamp(base, n - 8, cy - 1, "amber", size=1)
    K.well(base, well, "dark", 1, bevel=1, cast=2)
    collar(base, cx, cy, 20.5, 17.5, bolts=8)

    rot = drill_head(n, cx, cy, 17, count=4)

    top = R.Canvas(n, n)
    if alt == "a":
        gantry = R.rect(10, 29, 44, 7)
        K.plate(top, gantry, "steel", 4, bevel=1)
        top.groove(R.line(12, 32, 51, 32), depth=1, lip=1)
        K.plate(top, R.rect(9, 27, 4, 11), "steel", 3, bevel=1)
        K.plate(top, R.rect(51, 27, 4, 11), "steel", 3, bevel=1)
    hub = R.circle(cx, cy, 7.5)
    K.plate(top, hub - R.circle(cx, cy, 5), "steel", 3, bevel=1)
    top.dome(R.circle(cx, cy, 5), "amber", 1, 4)
    for a in range(4):
        ang = math.pi / 4 + a * math.pi / 2
        K.stud(top, int(cx + 6.4 * math.cos(ang)), int(cy + 6.4 * math.sin(ang)), "steel", 6)
    return {"": base, "-rotator": rot, "-top": top}


def reinforced_drill(alt="a"):
    """Perfuradora reforçada 3x3: heavy bore flanked by exposed gearboxes."""
    n, cx, cy = 96, 48, 48
    base = R.Canvas(n, n)
    K.deck(base, n, 7, "steel", 4, inset=3, seed="reinforced", rust=0.08, layout="x")
    for x in (4, n - 20):
        box = R.chamfer(x, 22, 16, 52, 3)
        K.raised(base, box, "steel", 4, bevel=2)
        paint = R.shrink(box, 3) - R.rect(0, 36, n, 24)
        base.recolor(paint, "amber", -1)
        g = K.gear(x + 8, 48, 7.5, 5.8, 10, 0.5, 9)
        base.cavity(R.circle(x + 8, 48, 9), "dark", 1, bevel=1)
        K.plate(base, g, "steel", 4, bevel=1, rim=1)
        base.fill(R.circle(x + 8, 48, 2.2), "dark", 2)
    for y in (4, n - 9):
        K.stripes(base, R.rect(30, y, 36, 5), ("amber", 4), ("dark", 2), period=6, width=3)
    well = R.chamfer(17, 17, 62, 62, 16)
    K.well(base, well, "dark", 1, bevel=1, cast=2)
    collar(base, cx, cy, 29.5, 25.5, bolts=12)
    K.lamp(base, n - 10, 8, "amber", size=2)

    rot = drill_head(n, cx, cy, 25, count=5, width=0.4, sweep=0.32)

    top = R.Canvas(n, n)
    for k in range(4):
        strut = R.rot90(R.rect(46, 20, 4, 18), n, k)
        K.plate(top, strut, "steel", 4, bevel=1)
    hub = R.circle(cx, cy, 11)
    K.plate(top, hub, "dark", 4, bevel=2)
    K.stripes(top, R.ring(cx, cy, 9, 6.5), ("amber", 4), ("dark", 2), period=4, width=2)
    top.dome(R.circle(cx, cy, 5), "steel", 3, 6)
    return {"": base, "-rotator": rot, "-top": top}


def scrap_collector(alt="a"):
    """Coletor de sucata 2x2: rotating electromagnet ring over a grated pit, claw hub on top."""
    n, cx, cy = 64, 32, 32
    base = R.Canvas(n, n)
    K.deck(base, n, 5, "steel", 4, inset=3, seed="collector", rust=0.18, layout="cross")
    bin_ = R.chamfer(3, 3, 14, 20, 2)
    K.raised(base, bin_, "rust", 4, bevel=1)
    K.well(base, R.rect(6, 6, 8, 14), "dark", 1, bevel=1, cast=1)
    for y in (9, 13, 17):
        base.fill(R.rect(7, y, 6, 1), "rust", 2)
    pit = R.circle(cx + 2, cy + 2, 21)
    K.well(base, pit, "dark", 2, bevel=1, cast=2)
    for p in R.shrink(pit, 2):
        if (p[0] + p[1]) % 4 == 0:
            base.set(p, "dark", 3)
    base.fill(R.ring(cx + 2, cy + 2, 21, 20), "steel", 2)
    K.lamp(base, n - 8, 6, "amber", size=1)

    rot = R.Canvas(n, n)
    disc = R.ring(cx + 2, cy + 2, 18, 8)
    rot.fill(disc, "dark", 3)
    for k in range(3):
        spoke = R.thick(cx + 2, cy + 2, cx + 2 + 17 * math.cos(k * 2.094), cy + 2 + 17 * math.sin(k * 2.094), 3)
        K.plate(rot, spoke & R.circle(cx + 2, cy + 2, 18), "steel", 3, bevel=1)
    for k in range(6):
        a = k * math.pi / 3 + 0.52
        px, py = cx + 2 + 14 * math.cos(a), cy + 2 + 14 * math.sin(a)
        pad = R.circle(px, py, 4.2)
        K.plate(rot, pad, "copper", 3, bevel=1)
        rot.fill(R.ring(px, py, 2.6, 1.6), "copper", 5)
        rot.set((int(px), int(py)), "dark", 1)

    top = R.Canvas(n, n)
    hub = R.circle(cx + 2, cy + 2, 7)
    K.plate(top, hub, "steel", 4, bevel=2)
    for k in range(3):
        a = k * 2.094 - 1.57
        claw = R.thick(cx + 2 + 5 * math.cos(a), cy + 2 + 5 * math.sin(a), cx + 2 + 10 * math.cos(a + 0.3),
                       cy + 2 + 10 * math.sin(a + 0.3), 3)
        K.plate(top, claw - hub, "steel", 3, bevel=1)
    top.dome(R.circle(cx + 2, cy + 2, 3.5), "amber", 2, 5)
    return {"": base, "-rotator": rot, "-top": top}


def crystal_extractor(alt="a"):
    """Extrator de cristal 2x2: recovered ancient pylons focus resonance into a slowly turning emitter ring."""
    n, cx, cy = 64, 32, 32
    base = R.Canvas(n, n)
    K.deck(base, n, 5, "steel", 4, inset=3, seed="extractor", rust=0.06, layout="x")
    for k in range(4):
        pyl = R.rot90(R.chamfer(4, 4, 15, 15, 5), n, k)
        K.raised(base, pyl, "dark", 4, bevel=2)
        K.trace(base, sorted(R.rot90({(9, 9), (14, 14)}, n, k)), node=True)
    well = R.circle(cx, cy, 18)
    K.well(base, well, "dark", 1, bevel=1, cast=1)
    base.fill(R.circle(cx, cy, 9), "teal", 1)
    base.fill(R.circle(cx, cy, 5), "teal", 2)
    base.fill(R.ring(cx, cy, 18, 17), "steel", 2)

    rot = R.Canvas(n, n)
    ring = R.ring(cx, cy, 16, 13)
    K.plate(rot, ring, "steel", 4, bevel=1)
    for k in range(3):
        a = k * 2.094
        ex, ey = cx + 14.5 * math.cos(a), cy + 14.5 * math.sin(a)
        node = R.circle(ex, ey, 4)
        K.plate(rot, node, "dark", 4, bevel=1)
        rot.fill(R.circle(ex, ey, 2), "teal", 5)
        rot.set((int(ex) + 1, int(ey) - 1), "teal", 6)

    top = R.Canvas(n, n)
    cage = R.ring(cx, cy, 8, 6)
    K.plate(top, cage, "steel", 4, bevel=1)
    crystal = R.regular(cx, cy, 5.5, 6, 30)
    top.facets([(crystal & R.rect(0, 0, int(cx), n), (-1, 0.3)), (crystal - R.rect(0, 0, int(cx), n), (1, 0.4))], "teal", 4, 1)
    top.set((int(cx), int(cy) - 3), "teal", 6)
    rim = R.Canvas(n, n)
    for rr, a in ((9.5, 140), (11.5, 70)):
        rim.fill(R.ring(cx, cy, rr, rr - 2), "teal", 5, a)
    return {"": base, "-rotator": rot, "-top": top, "-rim": rim}
