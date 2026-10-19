"""Industry and power. Crafters usually draw region, optional moving parts, -top, and an additive -glow."""
import math

import parts as K
import raster as R


def glow_layer(n, m, ramp, core_idx=5, falloff=2, alpha=(150, 80, 40), core_alpha=190):
    """Soft emissive halo for DrawGlowRegion (drawn additively by the game)."""
    c = R.Canvas(n, n)
    rings = [m]
    for _ in range(falloff):
        rings.append(R.grow(rings[-1], 1, diag=False))
    for k in range(len(rings) - 1, 0, -1):
        a = alpha[min(k - 1, len(alpha) - 1)]
        c.fill(rings[k] - rings[k - 1], ramp, core_idx - k, a)
    c.fill(m, ramp, core_idx, core_alpha)
    return c


def crucible(c, cx, cy, r_shell, r_pool, rivets=10):
    """Heavy refractory vessel: thick riveted dark shell around a molten pool."""
    shell = R.circle(cx, cy, r_shell)
    c.drop_shadow(shell, -1, 2)
    K.plate(c, shell, "dark", 4, bevel=2, rim=1)
    c.fill(R.ring(cx, cy, r_shell - 3, r_shell - 4), "dark", 2)
    for k in range(rivets):
        a = 2 * math.pi * (k + 0.5) / rivets
        rr = r_shell - 1.8
        K.stud(c, int(cx + rr * math.cos(a)), int(cy + rr * math.sin(a)), "steel", 5)
    lip = R.circle(cx, cy, r_pool + 2)
    K.well(c, lip, "dark", 1, bevel=1, cast=0)
    c.dome(R.circle(cx, cy, r_pool), "amber", 2, 5, spec=False)
    return shell


def segmented_lid(cx, cy, r, hole, segments=6, rot=0.0):
    """Radial lid plates with hairline seams and a central pour opening."""
    lid = R.circle(cx, cy, r) - R.circle(cx, cy, hole)
    seams = set()
    for k in range(segments):
        a = 2 * math.pi * k / segments + rot
        seams |= R.line(int(round(cx + hole * math.cos(a))), int(round(cy + hole * math.sin(a))),
                        int(round(cx + (r - 0.5) * math.cos(a))), int(round(cy + (r - 0.5) * math.sin(a))))
    return lid, seams & lid


def riveted_lid(top, cx, cy, r, hole, segments, rot):
    lid, seams = segmented_lid(cx, cy, r, hole, segments, rot)
    K.panels(top, lid, seams, "steel", 4, gap=("dark", 2))
    top.fill(R.ring(cx, cy, hole + 1.5, hole), "dark", 3)
    for k in range(segments):
        a = 2 * math.pi * (k + 0.5) / segments + rot
        K.stud(top, int(cx + (r - 3.5) * math.cos(a)), int(cy + (r - 3.5) * math.sin(a)), "steel", 6)


def alloy_refinery(alt="a"):
    """Refinaria de liga 3x3."""
    n = 96
    base = R.Canvas(n, n)
    top = R.Canvas(n, n)
    K.deck(base, n, 7, "steel", 4, inset=3, seed="refinery" + alt, rust=0.08, layout="cross")
    if alt == "a":
        # exhaust stack, top right
        stack = R.chamfer(62, 7, 27, 27, 4)
        K.raised(base, stack, "steel", 4, bevel=2)
        K.panels(base, R.shrink(stack, 2), R.rect(62, 20, 27, 1), "steel", 4)
        flue = R.circle(75.5, 20.5, 7)
        K.raised(base, R.grow(flue, 2), "dark", 4, bevel=1)
        K.well(base, flue, "dark", 0, bevel=1, cast=0)
        base.fill(R.ring(75.5, 20.5, 4, 3), "rust", 1)
        K.lamp(base, 85, 10, "amber", size=1)
        # feed hopper, bottom left
        hop = R.chamfer(7, 60, 30, 29, 4)
        K.raised(base, hop, "steel", 4, bevel=2)
        mouth = R.chamfer(11, 64, 22, 21, 3)
        K.well(base, mouth, "dark", 1, bevel=1, cast=2)
        for gx in range(13, 32, 3):
            base.fill(R.rect(gx, 66, 1, 17), "steel", 3)
        K.stripes(base, R.rect(9, 87, 26, 1) | R.rect(9, 61, 26, 1), ("amber", 4), ("dark", 1), period=4, width=2)
        # cooling fins, right
        fins = R.chamfer(66, 40, 23, 49, 3)
        K.raised(base, fins, "dark", 3, bevel=1)
        for y in range(43, 86, 4):
            K.plate(base, R.rect(68, y, 19, 2), "steel", 4, bevel=1, rim=0)
        # feed line from the hopper into the crucible
        K.pipe(base, 22, 44, 22, 59, width=5, collar=6)
        crucible(base, 38, 40, 25, 11)
        riveted_lid(top, 38, 40, 14.5, 4.5, 6, 0.3)
        glow = glow_layer(n, R.circle(38, 40, 4.5), "amber", 4, falloff=2, alpha=(90, 45), core_alpha=110)
    else:
        # alt b: twin crucibles bridged by a heavy transfer gantry
        for cx in (26, 70):
            crucible(base, cx, 48, 20, 9)
        bridge = R.chamfer(30, 38, 36, 20, 3)
        K.raised(top, bridge, "steel", 4, bevel=2)
        top.groove(R.line(33, 47, 62, 47), depth=1, lip=1)
        for x in (36, 59):
            K.lamp(top, x, 42, "amber", size=1)
        for cx in (26, 70):
            riveted_lid(top, cx, 48, 11.5, 3.5, 5, 0.2)
        K.pipe(base, 48, 70, 48, 88, width=6, collar=5)
        K.vent(base, 38, 8, 20, 10, horizontal=False)
        glow = glow_layer(n, R.circle(26, 48, 3.5) | R.circle(70, 48, 3.5), "amber", 4, falloff=2, alpha=(90, 45), core_alpha=110)
    return {"": base, "-top": top, "-glow": glow}


def structural_forge(alt="a"):
    """Fornalha estrutural 2x2: octagonal refractory furnace. A ring of firebrick around a riveted crucible
    cap; the firebox slot glows; the flue rises from the top-right shoulder."""
    n, cx, cy = 64, 32, 33
    base = R.Canvas(n, n)
    K.foundation(base, n)
    body = R.regular(32, 32, 30.5, 8, 22.5)
    base.drop_shadow(body, -1, 2)
    base.plate(body, "dark", 4, bevel=2, hi=1, lo=2, rim=1, edge_lo=1)
    bricks = R.ring(cx, cy, 25, 17)
    mortar = R.ring(cx, cy, 21.5, 20.5)
    for k in range(16):
        a = 2 * math.pi * (k + (0.5 if k % 2 else 0)) / 16
        r0, r1 = (17, 21) if k % 2 else (21, 25)
        mortar |= R.thick(cx + r0 * math.cos(a), cy + r0 * math.sin(a), cx + r1 * math.cos(a), cy + r1 * math.sin(a), 1)
    K.panels(base, bricks, mortar & bricks, "rust", 3, gap=("dark", 1))
    cap = R.circle(cx, cy, 16)
    base.drop_shadow(cap, -1, 2)
    K.plate(base, cap, "steel", 4, bevel=2, rim=1)
    for k in range(8):
        a = 2 * math.pi * (k + 0.5) / 8
        K.stud(base, int(cx + 13.5 * math.cos(a)), int(cy + 13.5 * math.sin(a)), "steel", 6)
    slot = R.chamfer(cx - 9, cy - 3, 18, 7, 2)
    K.well(base, slot, "dark", 0, bevel=1, cast=0)
    fire = R.rect(cx - 7, cy - 1, 14, 3)
    base.fill(fire, "amber", 3)
    base.fill(R.rect(cx - 7, cy - 1, 14, 1), "amber", 5)
    for x in range(cx - 5, cx + 7, 3):
        base.fill(R.rect(x, cy - 2, 1, 5), "dark", 2)
    flue = R.circle(50, 13, 6)
    K.raised(base, flue, "steel", 4, bevel=1, shadow=2)
    K.well(base, R.circle(50, 13, 3.5), "dark", 0, bevel=1, cast=0)
    hatch = R.chamfer(4, 26, 7, 12, 2)
    K.raised(base, hatch, "steel", 3, bevel=1)
    base.fill(R.rect(6, 29, 3, 6), "amber", 3)
    K.lamp(base, 14, 52, "amber", size=1)
    glow = glow_layer(n, fire, "amber", 4, falloff=2, alpha=(110, 50), core_alpha=140)
    return {"": base, "-glow": glow}


def scrap_recycler(alt="a"):
    """Recicladora industrial 2x2.

    a: flared intake hopper over twin counter-rotating shredders (the single -rotator region is drawn twice
       by the game at the two rotor positions, spinning opposite ways), narrow output chute below.
    b: magnetic sorting carousel: a turning table drops sorted scrap into four bins."""
    n = 64
    base = R.Canvas(n, n)
    if alt == "a":
        K.foundation(base, n)
        hopper = R.poly([(1, 4), (63, 4), (57, 42), (7, 42)])
        base.drop_shadow(hopper, -1, 2)
        base.plate(hopper, "steel", 3, bevel=2, hi=1, lo=2, rim=1, edge_lo=1)
        K.stripes(base, R.rect(6, 6, 52, 3), ("amber", 4), ("dark", 2), period=6, width=3)
        throat = R.poly([(7, 11), (57, 11), (53, 38), (11, 38)])
        K.well(base, throat, "dark", 1, bevel=1, cast=2)
        for x in (20, 44):
            base.fill(R.ring(x, 24, 11, 10), "dark", 0)
        chute = R.chamfer(16, 41, 32, 22, 3)
        base.drop_shadow(chute, -1, 1)
        K.plate(base, chute, "rust", 4, bevel=2, rim=1)
        K.well(base, R.rect(21, 46, 22, 12), "dark", 1, bevel=1, cast=1)
        for x in range(23, 42, 5):
            base.fill(R.rect(x, 50, 2, 4), "steel", 3)
        K.wear(base, hopper, "recycler-a", 0.08)
        K.lamp(base, 55, 8, "amber", size=1)
        rot = R.Canvas(24, 24)
        rot.fill(R.circle(12, 12, 10), "dark", 3)
        teeth = K.gear(12, 12, 10, 7, 8, 0.45)
        K.plate(rot, teeth, "steel", 4, bevel=1, rim=1)
        rot.recolor({p for p in teeth if (p[0] - 12) ** 2 + (p[1] - 12) ** 2 > 64}, "rust", 1)
        rot.fill(R.circle(12, 12, 3), "dark", 2)
        rot.set((13, 11), "steel", 6)
        # icon-only layer: both shredders at rest, footprint-sized (see on_base in defense.py for why)
        rotors = R.Canvas(n, n)
        for x in (20, 44):
            rotors.paste(rot, x - 12, 24 - 12)
        return {"": base, "-rotator": rot, "-rotors": rotors}
    cx = cy = 32
    K.deck(base, n, 5, "steel", 4, inset=3, seed="recycler-b", rust=0.12, layout="x")
    for k in range(4):
        bin_ = R.rot90(R.chamfer(3, 3, 17, 17, 4), n, k)
        K.raised(base, bin_, "rust", 4, bevel=1)
        K.well(base, R.rot90(R.chamfer(6, 6, 11, 11, 3), n, k), "dark", 1, bevel=1, cast=1)
    pit = R.circle(cx, cy, 20)
    K.well(base, pit, "dark", 1, bevel=1, cast=2)
    rot = R.Canvas(n, n)
    table = R.circle(cx, cy, 18)
    K.plate(rot, table, "steel", 3, bevel=1)
    for k in range(4):
        a = k * math.pi / 2 + math.pi / 4
        sector = R.thick(cx + 6 * math.cos(a), cy + 6 * math.sin(a), cx + 15 * math.cos(a), cy + 15 * math.sin(a), 6) & table
        rot.cavity(sector, "dark", 2, bevel=1)
        rot.fill(R.circle(cx + 11 * math.cos(a), cy + 11 * math.sin(a), 1.5), "copper", 4)
    for k in range(4):
        a = k * math.pi / 2
        rot.fill(R.thick(cx, cy, cx + 17 * math.cos(a), cy + 17 * math.sin(a), 2) & table, "steel", 5)
    top = R.Canvas(n, n)
    hub = R.circle(cx, cy, 6)
    K.plate(top, hub, "dark", 4, bevel=1)
    top.dome(R.circle(cx, cy, 3), "copper", 2, 5)
    return {"": base, "-rotator": rot, "-top": top}


def mineral_compressor(alt="a"):
    """Compressor mineral 2x2: cross-shaped press. Four hydraulic rams on the sides drive a ribbed head into
    the central die; the empty corners give it a silhouette no other machine shares."""
    n = 64
    base = R.Canvas(n, n)
    K.foundation(base, n)
    cross = R.chamfer(12, 1, 40, 62, 4) | R.chamfer(1, 12, 62, 40, 4)
    base.drop_shadow(cross, -1, 2)
    base.plate(cross, "steel", 3, bevel=2, hi=1, lo=2, rim=1, edge_lo=1)
    die = R.chamfer(15, 15, 34, 34, 4)
    K.well(base, die, "dark", 1, bevel=1, cast=2)
    for k in range(4):
        ram = R.rot90(R.chamfer(20, 1, 24, 12, 3), n, k)
        K.raised(base, ram, "steel", 4, bevel=1)
        cyl = R.rot90(R.rect(26, 3, 12, 8), n, k)
        base.cylinder(cyl, "steel", 2, 6, axis="h" if k % 2 == 0 else "v")
        rod = R.rot90(R.rect(30, 11, 4, 5), n, k)
        base.fill(rod, "steel", 6)
    for (x, y) in ((14, 14), (49, 14), (14, 49), (49, 49)):
        K.stud(base, x, y, "steel", 5)
    gauge = R.circle(56, 20, 3)
    K.raised(base, gauge, "dark", 3, bevel=1)
    base.set((56, 19), "amber", 5)
    top = R.Canvas(n, n)
    head = R.chamfer(18, 18, 28, 28, 4)
    K.panels(top, head, R.rect(31, 0, 2, n) | R.rect(0, 31, n, 2), "steel", 4, gap=("dark", 2))
    for k in range(4):
        top.fill(R.rot90(R.rect(24, 20, 2, 8), n, k), "steel", 6)
    K.plate(top, R.chamfer(27, 27, 10, 10, 2), "amber", 3, bevel=1)
    return {"": base, "-top": top}


def combustion_engine(alt="a"):
    """Gerador de combustão 2x2: four-cylinder engine block with an exhaust manifold whose two stacks stand
    proud of the right edge; glowing intake grilles between them."""
    n = 64
    base = R.Canvas(n, n)
    K.foundation(base, n)
    block = R.chamfer(2, 4, 46, 56, 5)
    base.drop_shadow(block, -1, 2)
    base.plate(block, "steel", 3, bevel=2, hi=1, lo=2, rim=1, edge_lo=1)
    base.recolor(R.chamfer(6, 7, 38, 6, 1), "amber", -1)
    for (x, y) in ((5, 15), (25, 15), (5, 36), (25, 36)):
        head = R.chamfer(x, y, 18, 18, 4)
        K.raised(base, head, "dark", 4, bevel=1)
        base.dome(R.circle(x + 9, y + 9, 5.5), "steel", 2, 6)
        for q in ((x + 2, y + 2), (x + 15, y + 2), (x + 2, y + 15), (x + 15, y + 15)):
            K.stud(base, q[0], q[1], "steel", 5)
    manifold = R.chamfer(46, 10, 10, 44, 3)
    K.raised(base, manifold, "steel", 4, bevel=1)
    for y in (16, 30, 44):
        K.pipe(base, 44, y, 50, y, width=4)
    glow_m = set()
    for sy in (14, 40):
        stack = R.circle(57, sy + 4, 6)
        K.raised(base, stack, "dark", 4, bevel=1, shadow=2)
        K.well(base, R.circle(57, sy + 4, 3.5), "dark", 0, bevel=1, cast=0)
        g = R.circle(57, sy + 4, 2)
        base.fill(g, "amber", 3)
        glow_m |= g
    base.fill(R.rect(48, 27, 6, 8), "dark", 1)
    for y in range(28, 34, 2):
        base.fill(R.rect(49, y, 4, 1), "amber", 3)
        glow_m |= R.rect(49, y, 4, 1)
    glow = glow_layer(n, glow_m, "amber", 4, falloff=2, alpha=(90, 40), core_alpha=120)
    return {"": base, "-glow": glow}


def power_accumulator(alt="a"):
    """Acumulador de energia 2x2 (Battery): four capacitor cells on a cross frame (clover silhouette).

    region = housing floor, -power = white window mask the game tints with the charge color (DrawPower with
    mixcol), -top = cell covers. Supplying -power stops the game from filling the whole square."""
    n = 64
    centers = ((16, 16), (47, 16), (16, 47), (47, 47))
    shape = R.chamfer(20, 2, 24, 60, 3) | R.chamfer(2, 20, 60, 24, 3)
    for (x, y) in centers:
        shape |= R.circle(x + 0.5, y + 0.5, 14.5)
    base = R.Canvas(n, n)
    base.fill(K.body(n, 3), "dark", 2)
    top = R.Canvas(n, n)
    K.foundation(top, n)
    top.drop_shadow(shape, -1, 2)
    top.plate(shape, "steel", 3, bevel=2, hi=1, lo=2, rim=1, edge_lo=1)
    windows = set()
    for (x, y) in centers:
        cell = R.circle(x + 0.5, y + 0.5, 11.5)
        K.raised(top, cell, "steel", 4, bevel=2)
        top.fill(R.ring(x + 0.5, y + 0.5, 9.5, 8.5), "dark", 2)
        windows |= R.ring(x + 0.5, y + 0.5, 7.5, 4.5)
        top.dome(R.circle(x + 0.5, y + 0.5, 4.5), "steel", 3, 6)
    bus = R.chamfer(25, 25, 14, 14, 3)
    K.raised(top, bus, "amber", 3, bevel=1)
    top.fill(R.rect(30, 30, 4, 4), "dark", 2)
    top.erase(windows)
    power = R.Canvas(n, n)
    power.fill(windows, "team", 2)
    return {"": base, "-power": power, "-top": top}


def resonator_spire(n, cx, cy):
    """Alternative b for the resonator: a stepped spire of concentric rings over four corner coils."""
    base = R.Canvas(n, n)
    K.deck(base, n, 7, "steel", 4, inset=3, seed="resonator-b", rust=0.06, layout="cross")
    for k in range(4):
        coil = R.rot90(R.circle(16, 16, 10), n, k)
        K.raised(base, coil, "dark", 4, bevel=2)
        cc = sorted(R.rot90({(16, 16)}, n, k))[0]
        base.fill(R.ring(cc[0] + 0.5, cc[1] + 0.5, 7, 5), "copper", 3)
        base.dome(R.circle(cc[0] + 0.5, cc[1] + 0.5, 3.5), "teal", 2, 5)
    for r, ramp, idx in ((34, "dark", 4), (27, "dark", 5)):
        K.raised(base, R.circle(cx, cy, r), ramp, idx, bevel=2, shadow=2)
    K.glyph_ring(base, cx, cy, 30.5, "teal", 3, gaps=10, gap_len=0.3)
    rot = R.Canvas(n, n)
    ring = R.ring(cx, cy, 22, 17)
    K.plate(rot, ring, "steel", 4, bevel=1)
    for k in range(6):
        a = k * math.pi / 3
        rot.fill(R.circle(cx + 19.5 * math.cos(a), cy + 19.5 * math.sin(a), 1.6), "teal", 5)
    top = R.Canvas(n, n)
    K.raised(top, R.circle(cx, cy, 13), "dark", 5, bevel=2)
    core = R.regular(cx, cy, 8, 6, 30)
    left, upper = R.rect(0, 0, cx, n), R.rect(0, 0, n, cy)
    top.facets([(core & left - upper, (-1, -0.6)), (core & left & upper, (-0.6, 1)),
                (core & upper - left, (0.7, 1)), (core - left - upper, (1, -0.5))], "teal", 4, 1)
    glow = glow_layer(n, R.circle(cx, cy, 3), "teal", 5, falloff=3, alpha=(110, 60, 30), core_alpha=150)
    return {"": base, "-rotator": rot, "-top": top, "-glow": glow}


def experimental_resonator(alt="a"):
    """Ressonador experimental 3x3: player frame clamped around a recovered ancient resonance core;
    three emitter pylons and a slowly turning glyph ring. Alternative b is a stepped ring spire."""
    n, cx, cy = 96, 48, 48
    if alt == "b":
        return resonator_spire(n, cx, cy)
    base = R.Canvas(n, n)
    K.deck(base, n, 7, "steel", 4, inset=3, seed="resonator", rust=0.06, layout="x")
    plinth = R.regular(cx, cy, 40, 8, 22.5)
    K.raised(base, plinth, "dark", 4, bevel=2, rim=1)
    K.wear(base, {p for p in plinth if p[0] - p[1] < -30}, "resonator-ancient", 0.14)
    for k in range(3):
        a = math.radians(90 + k * 120)
        px, py = cx + 31 * math.cos(a), cy - 31 * math.sin(a)
        pyl = R.regular(px, py, 7.5, 6, 0)
        K.raised(base, pyl, "steel", 4, bevel=1)
        base.dome(R.circle(px, py, 3.5), "teal", 2, 5)
        K.trace(base, [(int(px - 7 * math.cos(a)), int(py + 7 * math.sin(a))),
                       (int(cx + 18 * math.cos(a)), int(cy - 18 * math.sin(a)))], node=False)
    pit = R.circle(cx, cy, 22)
    K.well(base, pit, "dark", 1, bevel=1, cast=1)
    base.fill(R.circle(cx, cy, 13), "teal", 1)
    for k in range(4):
        clamp = R.rot90(R.chamfer(8, 44, 12, 8, 2), n, k)
        K.raised(base, clamp, "steel", 4, bevel=1)
        base.fill(R.rot90(R.rect(10, 47, 8, 2), n, k), "amber", 4)

    rot = R.Canvas(n, n)
    ring = R.ring(cx, cy, 20, 16)
    K.plate(rot, ring, "dark", 5, bevel=1)
    K.glyph_ring(rot, cx, cy, 18, "teal", 4, gaps=7, gap_len=0.3)

    top = R.Canvas(n, n)
    core = R.regular(cx, cy, 10, 6, 30)
    left, upper = R.rect(0, 0, cx, n), R.rect(0, 0, n, cy)
    top.facets([(core & left - upper, (-1, -0.6)), (core & left & upper, (-0.6, 1)),
                (core & upper - left, (0.7, 1)), (core - left - upper, (1, -0.5))], "teal", 4, 1)
    top.fill(R.circle(cx, cy, 3), "teal", 6)
    glow = glow_layer(n, R.circle(cx, cy, 4) | R.ring(cx, cy, 12.5, 11.5), "teal", 5, falloff=3,
                      alpha=(110, 60, 30), core_alpha=150)
    return {"": base, "-rotator": rot, "-top": top, "-glow": glow}


def component_assembler(alt="a"):
    """Montadora de componentes 3x3: assembly bed fed from two sides, worked by twin gantry arms."""
    n = 96
    base = R.Canvas(n, n)
    K.deck(base, n, 7, "steel", 4, inset=3, seed="assembler", rust=0.08, layout="cross")
    bed = R.chamfer(22, 22, 52, 52, 6)
    K.well(base, bed, "dark", 2, bevel=1, cast=2)
    for x in range(26, 70, 6):
        base.fill(R.rect(x, 26, 1, 44), "dark", 3)
    jig = R.chamfer(38, 38, 20, 20, 3)
    K.raised(base, jig, "steel", 3, bevel=1)
    K.plate(base, R.rect(43, 43, 10, 10), "dark", 4, bevel=1)
    K.trace(base, [(44, 48), (48, 48), (48, 45), (51, 45)], node=False)
    for side in (0, 2):
        feed = R.rot90(R.rect(6, 40, 16, 16), n, side)
        base.cavity(feed, "dark", 2, bevel=1)
        for off in (8, 13):
            base.fill(R.rot90(R.path([(off, 43), (off + 3, 48), (off, 52)]), n, side), "steel", 3)
    for (x, y) in ((8, 8), (76, 8), (8, 76), (76, 76)):
        K.raised(base, R.chamfer(x, y, 12, 12, 3), "steel", 3, bevel=1)
        K.lamp(base, x + 5, y + 5, "amber", size=2)
    top = R.Canvas(n, n)
    rail = R.rect(10, 16, 76, 6)
    K.plate(top, rail, "steel", 4, bevel=1)
    top.groove(R.line(12, 18, 83, 18), depth=1, lip=1)
    for x0, tip in ((26, (40, 40)), (70, (56, 42))):
        carriage = R.chamfer(x0 - 5, 13, 10, 12, 2)
        K.plate(top, carriage, "amber", 3, bevel=1)
        arm = R.thick(x0, 24, tip[0], tip[1], 4)
        K.plate(top, arm, "steel", 4, bevel=1)
        joint = R.circle(tip[0], tip[1], 3)
        K.plate(top, joint, "dark", 4, bevel=1)
        top.set(tip, "amber", 6)
    return {"": base, "-top": top}


def resonance_dynamo(alt="a"):
    """Dínamo de ressonância 3x3: a heavy player frame around a sealed core chamber (the resonance core shows
    through its window), two copper transformer stacks on the flanks, a core feed hopper and an output bus;
    the energy ring (-ring) turns around the chamber with the load, the chamber and ring light up (-glow)."""
    n, cx, cy = 96, 48, 48
    base = R.Canvas(n, n)
    K.deck(base, n, 7, "steel", 4, inset=3, seed="dynamo", rust=0.07, layout="cross")
    # transformer stacks: copper windings in dark housings, left and right
    for side in (0, 2):
        house = R.rot90(R.chamfer(6, 26, 16, 44, 3), n, side)
        K.raised(base, house, "dark", 4, bevel=1, shadow=2)
        for k in range(6):
            coil = R.rot90(R.rect(9, 30 + k * 6, 10, 4), n, side)
            base.cylinder(coil, "copper", 2, 5, axis="h" if side in (0, 2) else "v")
    # core feed hopper (bottom) and output bus (top)
    hopper = R.chamfer(36, 78, 24, 13, 3)
    K.raised(base, hopper, "dark", 4, bevel=1)
    base.cavity(R.rect(41, 81, 14, 7), "dark", 1, bevel=1)
    base.fill(R.rect(44, 83, 8, 3), "teal", 2)
    K.stripes(base, R.rect(28, 90, 40, 3), ("amber", 4), ("dark", 1), period=6, width=3)
    bus = R.chamfer(30, 5, 36, 10, 2)
    K.raised(base, bus, "amber", 3, bevel=1)
    for x in (36, 46, 56):
        base.fill(R.rect(x, 7, 4, 6), "dark", 2)
        base.fill(R.rect(x + 1, 8, 2, 4), "copper", 4)
    # the chamber: a deep round well with the core inside
    collar_m = R.circle(cx, cy, 30)
    K.raised(base, collar_m, "dark", 4, bevel=2, shadow=2)
    K.well(base, R.circle(cx, cy, 19), "dark", 1, bevel=1, cast=1)
    core = R.regular(cx, cy, 10, 6, 30)
    left, upper = R.rect(0, 0, cx, n), R.rect(0, 0, n, cy)
    base.facets([(core & left - upper, (-1, -0.6)), (core & left & upper, (-0.6, 1)),
                 (core & upper - left, (0.7, 1)), (core - left - upper, (1, -0.5))], "teal", 2, 1)
    for (x, y) in ((10, 10), (84, 10), (10, 84), (84, 84)):
        K.lamp(base, x, y, "amber", size=2)

    ring = R.Canvas(n, n)
    band = R.ring(cx, cy, 28, 22)
    K.plate(ring, band, "steel", 3, bevel=1)
    for k in range(8):
        a = 2 * math.pi * k / 8
        px, py = cx + 25 * math.cos(a), cy + 25 * math.sin(a)
        node = R.circle(px, py, 3.2)
        K.plate(ring, node, "copper", 3, bevel=1)
        ring.set((int(px), int(py)), "teal", 5)
    ring.fill(R.ring(cx, cy, 22.8, 22), "dark", 2)

    top = R.Canvas(n, n)
    window = R.ring(cx, cy, 19, 16.5)
    K.plate(top, window, "steel", 4, bevel=1)
    for k in range(4):
        clamp = R.rot90(R.chamfer(44, 27, 8, 6, 1), n, k)
        K.plate(top, clamp, "steel", 5, bevel=1)
        top.fill(R.rot90(R.rect(46, 28, 4, 2), n, k), "amber", 4)
    top.fill(R.circle(cx, cy, 2.5), "teal", 6)

    glow = glow_layer(n, R.circle(cx, cy, 7) | R.ring(cx, cy, 25.5, 24.5) | R.rect(44, 83, 8, 3), "teal", 5, falloff=3,
                      alpha=(110, 60, 30), core_alpha=160)
    return {"": base, "-ring": ring, "-top": top, "-glow": glow}
