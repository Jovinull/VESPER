"""Items (32x32 icons) and ore overlays (32x32, drawn over floors; the game adds the drop shadow).

Each raw material owns a distinct shape so it reads without color:
ferroprism = hexagonal prisms, conductive scrap = flat flakes and wire, resonance crystal = upright spikes,
vitreous carbon = rounded glossy nodules.
"""
import math

import parts as K
import raster as R

N = 32


# ------------------------------------------------------------------------------------------ shape helpers

def prism(c, x0, y0, x1, y1, w, ramp, base, tip=0.0, cap=True):
    """Hexagonal prism/crystal seen from the side: three lit strips along its axis, optional pointed tip at
    (x1, y1) and a hexagonal end cap at (x0, y0)."""
    ax, ay = x1 - x0, y1 - y0
    ll = math.hypot(ax, ay)
    ux, uy = ax / ll, ay / ll
    px, py = -uy, ux
    hw = w / 2.0
    end = ll - tip
    pts = [(x0 + px * hw, y0 + py * hw), (x0 + ux * end + px * hw, y0 + uy * end + py * hw)]
    if tip > 0:
        pts.append((x1, y1))
    else:
        pts.append((x0 + ux * ll + px * hw, y0 + uy * ll + py * hw))
        pts.append((x0 + ux * ll - px * hw, y0 + uy * ll - py * hw))
    pts.append((x0 + ux * end - px * hw, y0 + uy * end - py * hw))
    pts.append((x0 - px * hw, y0 - py * hw))
    shape = R.poly(pts)
    # strips: classify by perpendicular offset; the strip whose normal faces the light is brightest
    faces = {1: set(), 0: set(), -1: set()}
    for p in shape:
        t = ((p[0] + 0.5 - x0) * px + (p[1] + 0.5 - y0) * py) / hw
        faces[1 if t > 0.34 else (-1 if t < -0.34 else 0)].add(p)
    lx, ly = c.light
    side_lit = (px * lx - py * ly)  # +perp side normal, converted to y-up
    hi_side = 1 if side_lit > 0 else -1
    c.fill(faces[0], ramp, base)
    c.fill(faces[hi_side], ramp, base + 2)
    c.fill(faces[-hi_side], ramp, base - 2)
    if cap:
        capm = R.circle(x0 + 0.5, y0 + 0.5, hw * 0.9) & shape
        c.fill(capm, ramp, base + 1)
        c.fill(R.edge(capm) & R.edge(shape), ramp, base - 1)
    c.shade(R.edge(shape) & faces[-hi_side], -1)
    return shape


def column(c, cx, cy, r, height, ramp, base=3, angle=0.0):
    """Hexagonal crystal column seen from above: lit hexagonal top face, extruded side toward the
    bottom left (away from the light)."""
    top = R.regular(cx, cy, r, 6, angle)
    side = set()
    for k in range(1, height + 1):
        side |= R.shift(top, -k, k)
    side -= top
    c.fill(side, ramp, base - 1)
    c.fill({p for p in side if (p[0] - cx) + (p[1] - cy) > r * 0.9}, ramp, base - 2)
    c.fill(R.edge(side) - R.grow(top, 1), ramp, base - 2)
    c.plate(top, ramp, base + 1, bevel=1, hi=2, lo=1)
    c.set((int(cx + r * 0.3), int(cy - r * 0.45)), ramp, base + 4)
    return top | side


def kite(c, x0, y0, x1, y1, w, ramp, base=3, widest=0.38):
    """Elongated rhombic prism lying on the ground: a bright ridge splits a lit face from a dark face."""
    ax, ay = x1 - x0, y1 - y0
    ll = math.hypot(ax, ay)
    ux, uy = ax / ll, ay / ll
    px, py = -uy, ux
    mx, my = x0 + ax * widest, y0 + ay * widest
    pts = [(x0, y0), (mx + px * w / 2, my + py * w / 2), (x1, y1), (mx - px * w / 2, my - py * w / 2)]
    m = R.poly(pts)
    lx, ly = c.light
    plus_lit = (px * lx - py * ly) > 0
    for p in m:
        t = (p[0] + 0.5 - x0) * px + (p[1] + 0.5 - y0) * py
        lit_side = (t > 0) == plus_lit
        c.set(p, ramp, base + 1 if lit_side else base - 1)
    ridge = R.line(int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1))) & m
    c.fill(ridge, ramp, base + 3)
    dark_edge = {p for p in R.edge(m) if c.px[p][1] == base - 1}
    c.fill(dark_edge, ramp, base - 2)
    return m


def shard(c, pts, ramp="glass", base=2, edge_hi=7, inner=None):
    """Angular glassy shard: dark body, one bright lit edge, optional inner facet."""
    m = R.poly(pts)
    c.plate(m, ramp, base, bevel=1, hi=3, lo=1)
    if inner:
        c.fill(R.poly(inner) & R.shrink(m, 1), ramp, base + 2)
    lit = {p for p in R.edge(m) if c.lit(R.normal(m, p, 1.8)) > 0.55}
    c.fill(lit, ramp, edge_hi)
    return m


def nodule(c, cx, cy, rx, ry, ramp="glass", lo=1, hi=6, rim=True):
    m = R.ellipse(cx, cy, rx, ry)
    c.dome(m, ramp, lo, hi, cx=cx, cy=cy, r=max(rx, ry))
    if rim:
        # cool reflected light along the lower-left rim sells the glassy surface
        rimp = {p for p in R.edge(m) if (p[0] + 0.5 - cx) < -rx * 0.3 and (p[1] + 0.5 - cy) > ry * 0.1}
        c.fill(rimp, ramp, lo + 3)
    return m


def flake(c, pts, ramp, base):
    m = R.poly(pts)
    c.plate(m, ramp, base, bevel=1, hi=2, lo=2)
    return m


def spike(c, x, y_base, h, w, ramp="teal", base=3, lean=0.0):
    """Upright crystal spike: hexagonal column with a pointed top, light facet on the right."""
    top = (x + lean, y_base - h)
    pts = [(x - w / 2, y_base), (x - w / 2 + lean * 0.7, y_base - h * 0.62), top,
           (x + w / 2 + lean * 0.7, y_base - h * 0.62), (x + w / 2, y_base)]
    m = R.poly(pts)
    mid = x + lean * 0.5
    left = {p for p in m if p[0] + 0.5 < mid - w * 0.12}
    right = {p for p in m if p[0] + 0.5 > mid + w * 0.18}
    c.fill(m, ramp, base)
    c.fill(left, ramp, base - 1)
    c.fill(right, ramp, base + 1)
    tipm = {p for p in m if p[1] < y_base - h * 0.62}
    c.shade(tipm & right, 1)
    c.shade(tipm - right - left, 1)
    c.fill(R.edge(m) & left, ramp, base - 2)
    # inner light vein
    c.fill(R.line(int(mid), int(y_base - 2), int(mid + lean * 0.4), int(y_base - h * 0.55)) & m, ramp, base + 2)
    return m


# ------------------------------------------------------------------------------------------ items

def item_ferroprism():
    c = R.Canvas(N, N)
    kite(c, 6, 26, 26, 5, 9, "ferro", 3)
    kite(c, 6, 27, 15, 7, 6, "ferro", 3)
    kite(c, 7, 27, 27, 19, 6, "ferro", 3)
    return c


def item_conductive_scrap():
    """A torn length of armored conduit with its copper core spilling out."""
    c = R.Canvas(N, N)
    tube = prism(c, 6, 26, 19, 13, 9, "steel", 4, cap=False)
    mouth = R.ellipse(19.5, 12.5, 3.2, 3.2) & R.grow(tube, 1)
    c.fill(mouth, "dark", 1)
    for k, pts in enumerate(([(19, 12), (22, 9), (25, 8), (27, 5)],
                             [(20, 14), (24, 13), (27, 14), (28, 11)],
                             [(18, 11), (18, 7), (21, 4), (23, 4)])):
        wire = R.path(pts)
        c.fill(R.shift(wire, -1, 1) - wire - tube, "copper", 1)
        c.fill(wire, "copper", 4 + (k == 0))
        c.set(pts[-1], "copper", 6)
    for t in (0.35, 0.65):
        x, y = 6 + (19 - 6) * t, 26 + (13 - 26) * t
        band = R.thick(x - 3.2, y - 3.2, x + 3.2, y + 3.2, 2) & tube
        c.fill(band, "copper", 3)
    return c


def item_resonance_crystal():
    c = R.Canvas(N, N)
    spike(c, 15, 28, 25, 11, "teal", 3, lean=1.5)
    spike(c, 24, 28, 12, 6, "teal", 3, lean=1.0)
    spike(c, 8, 28, 10, 5, "teal", 3, lean=-1.0)
    c.set((17, 8), "teal", 6)
    return c


def item_vitreous_carbon():
    c = R.Canvas(N, N)
    shard(c, [(5, 20), (12, 6), (22, 4), (27, 13), (21, 27), (10, 27)], "glass", 2,
          inner=[(12, 8), (21, 6), (24, 13), (15, 16)])
    c.fill(R.line(12, 18, 17, 23), "glass", 1)
    c.fill(R.line(15, 17, 20, 15), "glass", 4)
    c.set((21, 7), "glass", 7)
    return c


def item_structural_alloy():
    c = R.Canvas(N, N)
    for oy, ox in ((9, 2), (0, -2)):
        top = R.poly([(6 + ox, 14 + oy), (18 + ox, 6 + oy), (27 + ox, 11 + oy), (15 + ox, 19 + oy)])
        front = R.poly([(6 + ox, 14 + oy), (15 + ox, 19 + oy), (15 + ox, 23 + oy), (6 + ox, 18 + oy)])
        side = R.poly([(15 + ox, 19 + oy), (27 + ox, 11 + oy), (27 + ox, 15 + oy), (15 + ox, 23 + oy)])
        c.fill(front, "steel", 3)
        c.fill(side, "steel", 5)
        c.fill(top, "steel", 6)
        c.fill(R.edge(top) & R.shift(side, 0, -1), "steel", 7)
        c.fill(R.edge(front) & R.shift(front, 0, 1) - R.shift(front, 0, -1), "steel", 2)
        c.fill(R.rect(14 + ox, 11 + oy, 4, 2), "amber", 4)
    return c


def item_recovered_circuit():
    c = R.Canvas(N, N)
    board = R.chamfer(6, 6, 20, 20, 3)
    for k in range(4):
        y = 9 + k * 4
        c.fill(R.rect(3, y, 3, 2), "copper", 4)
        c.fill(R.rect(26, y, 3, 2), "copper", 5)
    c.plate(board, "dark", 4, bevel=1, hi=2, lo=2)
    K.wear(c, {p for p in board if p[0] + (31 - p[1]) < 18}, "circuit", 0.25)
    die = R.chamfer(12, 12, 8, 8, 1)
    c.plate(die, "dark", 2, bevel=1, hi=2, lo=1)
    K.trace(c, [(8, 10), (11, 10), (12, 12)], node=False)
    K.trace(c, [(23, 22), (20, 22), (19, 19)], node=False)
    K.trace(c, [(9, 22), (12, 19)], node=False)
    c.fill(R.rect(15, 15, 2, 2), "teal", 5)
    c.set((16, 15), "teal", 6)
    return c


def item_resonance_core():
    c = R.Canvas(N, N)
    core = R.circle(16, 16, 8.5)
    c.dome(core, "teal", 2, 5)
    cage = R.ring(16, 16, 11, 9.4)
    c.plate(cage, "steel", 4, bevel=1, hi=2, lo=2)
    for band in (R.rect(15, 5, 3, 22), R.rect(5, 15, 22, 3)):
        b = band & R.circle(16, 16, 11)
        c.plate(b, "steel", 4, bevel=1, hi=2, lo=2)
    c.fill(R.rect(15, 15, 3, 3), "steel", 6)
    c.fill(R.rect(19, 10, 2, 2), "teal", 6)
    return c


def item_vitrified_compound():
    c = R.Canvas(N, N)
    top = R.poly([(5, 11), (16, 5), (27, 11), (16, 17)])
    left = R.poly([(5, 11), (16, 17), (16, 28), (5, 22)])
    right = R.poly([(16, 17), (27, 11), (27, 22), (16, 28)])
    c.fill(left, "pale", 2)
    c.fill(right, "pale", 4)
    c.fill(top, "pale", 5)
    c.fill(R.line(16, 17, 16, 27), "pale", 6)
    c.fill(R.line(6, 11, 15, 6), "pale", 6)
    for p, ramp, i in (((9, 18), "teal", 4), ((20, 21), "rust", 4), ((12, 10), "teal", 5), ((22, 16), "teal", 3)):
        c.set(p, ramp, i)
    return c


ITEMS = {
    "ferroprism": item_ferroprism,
    "conductive-scrap": item_conductive_scrap,
    "resonance-crystal": item_resonance_crystal,
    "vitreous-carbon": item_vitreous_carbon,
    "structural-alloy": item_structural_alloy,
    "recovered-circuit": item_recovered_circuit,
    "resonance-core": item_resonance_core,
    "vitrified-compound": item_vitrified_compound,
}


# ------------------------------------------------------------------------------------------ ores

def ore_ferroprism(v):
    """Prism clusters grow in different directions so a deposit never reads as a field of arrows."""
    c = R.Canvas(N, N)
    sets = {
        1: [(4, 13, 15, 6, 6), (19, 4, 27, 13, 5), (8, 27, 16, 17, 6), (20, 27, 27, 23, 4)],
        2: [(5, 6, 14, 15, 6), (5, 15, 14, 6, 4), (18, 26, 28, 17, 6), (16, 13, 21, 4, 4)],
    }[v]
    for x0, y0, x1, y1, w in sets:
        kite(c, x0, y0, x1, y1, w, "ferro", 3)
    return c


def ore_conductive_scrap(v):
    c = R.Canvas(N, N)
    if v == 1:
        flake(c, [(5, 9), (12, 6), (14, 12), (8, 15)], "copper", 4)
        flake(c, [(17, 14), (25, 12), (27, 19), (19, 21)], "steel", 4)
        flake(c, [(8, 21), (14, 19), (15, 26), (9, 27)], "copper", 3)
        c.fill(R.path([(18, 6), (20, 5), (22, 7), (24, 6)]), "copper", 5)
    else:
        flake(c, [(15, 5), (23, 7), (21, 13), (14, 11)], "steel", 4)
        flake(c, [(5, 15), (12, 13), (13, 20), (6, 21)], "copper", 4)
        flake(c, [(18, 19), (26, 18), (25, 26), (17, 25)], "copper", 3)
        c.fill(R.path([(7, 25), (9, 27), (11, 25), (13, 27)]), "copper", 5)
    return c


def ore_resonance_crystal(v):
    c = R.Canvas(N, N)
    clusters = {
        1: [(10, 17, 12, 5, -0.6), (14, 18, 8, 4, 0.8), (22, 27, 11, 5, 0.5), (26, 27, 6, 3, 1.0)],
        2: [(20, 15, 11, 5, 0.6), (16, 15, 6, 3, -0.6), (9, 28, 10, 5, -0.4), (13, 28, 7, 3, 0.8)],
    }[v]
    for x, yb, h, w, lean in clusters:
        spike(c, x, yb, h, w, "teal", 3, lean)
    return c


def ore_vitreous_carbon(v):
    c = R.Canvas(N, N)
    shards = {
        1: [[(5, 12), (9, 5), (15, 7), (14, 14), (8, 16)], [(17, 12), (24, 10), (27, 17), (21, 21)],
            [(7, 22), (13, 19), (15, 26), (9, 28)], [(20, 23), (25, 22), (26, 27), (21, 28)]],
        2: [[(16, 5), (23, 4), (26, 10), (19, 13)], [(4, 15), (10, 11), (14, 17), (8, 21)],
            [(15, 18), (22, 16), (25, 23), (18, 26)], [(6, 25), (10, 23), (12, 28), (7, 29)]],
    }[v]
    for pts in shards:
        shard(c, pts, "glass", 2)
    return c


ORES = {
    "ore-ferroprism": ore_ferroprism,
    "ore-conductive-scrap": ore_conductive_scrap,
    "ore-resonance-crystal": ore_resonance_crystal,
    "ore-vitreous-carbon": ore_vitreous_carbon,
}
