"""The Silent Basin floors.

Rules that keep variants tileable side by side:
- every variant of a family shares one periodic base layer (same seed), so tile borders always match;
- variant-specific features (stones, debris, hatches) stay at least two pixels away from the border;
- floors use two main tones plus sparse accents, because they repeat across the whole map;
- at least one variant per family is calm, so random variant picks do not form a visible grid.
"""
import math

import raster as R

N = 32


def _base(c, ramp, seed, lo, hi, cells=(16, 8, 4), threshold=0.5, accent=None):
    """Two-tone periodic mottling shared by all variants of a family."""
    for y in range(N):
        for x in range(N):
            v = R.fbm(x, y, N, seed, cells)
            idx = hi if v > threshold else lo
            if accent and v < accent[0]:
                idx = accent[1]
            c.set((x, y), ramp, idx)


def _interior(m, margin=2):
    return {(x, y) for x, y in m if margin <= x < N - margin and margin <= y < N - margin}


# ------------------------------------------------------------------------------------------ basalt

def machine_basalt(variant):
    """Mechanical basalt: dark stone whose cooled columns look almost machined (hexagonal tops)."""
    c = R.Canvas(N, N)
    _base(c, "basalt", 11, 2, 3, threshold=0.52, accent=(0.36, 1))
    rnd = R.rng("basalt", variant)
    layouts = {
        1: [(10, 11, 5.4, 0), (23, 22, 3.6, 30)],
        2: [],
        3: [(19, 14, 6.4, 15), (8, 24, 2.8, 0)],
    }
    for cx, cy, r, ang in layouts[variant]:
        top = _interior(R.regular(cx, cy, r, 6, ang))
        joint = R.halo(top, diag=False) & _interior(R.rect(0, 0, N, N), 1)
        c.fill({p for p in joint if p[0] - cx + (cy - p[1]) < r * 0.6}, "basalt", 1)
        c.plate(top, "basalt", 3, bevel=1, hi=1, lo=1)
        if r > 5:
            x0, y0 = int(cx - r * 0.5), int(cy + r * 0.2)
            c.shade(R.line(x0, y0, x0 + int(r * 0.8), y0 - int(r * 0.5)) & top, -1)
    if variant == 2:
        # calm variant: one hairline fracture with a lit lip
        crack = _interior(R.path([(7, 20), (12, 17), (15, 18), (21, 13), (25, 12)]), 3)
        c.groove(crack, depth=1, lip=1)
    # sparse metallic glints: the "mechanical" in mechanical basalt
    for _ in range(1 + variant % 2):
        x, y = rnd.randint(4, N - 5), rnd.randint(4, N - 5)
        if c.px.get((x, y), ("", 0))[1] <= 2:
            c.set((x, y), "steel", 3)
            c.set((x + 1, y - 1), "steel", 4)
    return c


# ------------------------------------------------------------------------------------------ scrap soil

def scrap_soil(variant):
    """Compacted scrap soil: brown packed ground full of buried debris (plate shards, bolts, wire)."""
    c = R.Canvas(N, N)
    _base(c, "scrapsoil", 23, 2, 3, cells=(8, 4, 2), threshold=0.55, accent=(0.33, 1))
    rnd = R.rng("scrapsoil", variant)
    shards = {1: 2, 2: 3, 3: 1}[variant]
    for k in range(shards):
        cx, cy = rnd.randint(7, N - 8), rnd.randint(7, N - 8)
        w, h = rnd.randint(3, 6), rnd.randint(2, 4)
        a = rnd.random() * math.pi
        pts = []
        for sx, sy in ((-w, -h), (w, -h * 0.6), (w * 0.8, h), (-w * 0.7, h * 0.8)):
            pts.append((cx + sx * math.cos(a) / 2 - sy * math.sin(a) / 2, cy + sx * math.sin(a) / 2 + sy * math.cos(a) / 2))
        shard = _interior(R.poly(pts), 3)
        if not shard:
            continue
        ramp = "steel" if k % 2 == 0 else "rust"
        c.fill(R.halo(shard, diag=False) - shard, "scrapsoil", 1)
        c.plate(shard, ramp, 2, bevel=1, hi=1, lo=1)
    for _ in range(2):
        x, y = rnd.randint(3, N - 4), rnd.randint(3, N - 4)
        c.set((x, y), "rust", 3)
        c.set((x - 1, y + 1), "scrapsoil", 1)
    if variant == 3:
        wire = _interior(R.path([(6, 23), (10, 21), (14, 22), (17, 19)]), 3)
        c.fill(wire, "copper", 2)
        c.shade(R.shift(wire, -1, 1) - wire, -1)
    return c


# ------------------------------------------------------------------------------------------ rusted plating

def rusted_plating(variant):
    """Oxidized deck plating: the planet's original floor, one plate per tile, seams on the grid."""
    c = R.Canvas(N, N)
    for y in range(N):
        for x in range(N):
            v = R.fbm(x, y, N, 37, (8, 4, 2))
            c.set((x, y), "rustplate", 3 if v > 0.56 else 2)
    deck = R.rect(0, 0, N, N)
    # seams: lit top/right lip, shadowed bottom/left lip -> each tile reads as a slightly raised plate
    for x in range(N):
        c.shade({(x, 0)}, 1)
        c.shade({(x, N - 1)}, -1)
    for y in range(N):
        c.shade({(N - 1, y)}, 1)
        c.shade({(0, y)}, -1)
    for p in ((2, 2), (N - 3, 2), (2, N - 3), (N - 3, N - 3)):
        c.set(p, "rustplate", 4)
        c.set((p[0] - 1, p[1] + 1), "rustplate", 1)
    rnd = R.rng("plating", variant)
    if variant == 1:
        # rust run-off streaking down from a corroded rivet
        streak = R.rect(N - 4, 4, 1, 9) | R.rect(N - 5, 6, 1, 5)
        c.fill(streak, "rust", 1)
        c.fill(R.rect(N - 4, 4, 1, 3), "rust", 2)
        c.set((N - 3, 2), "rust", 3)
    elif variant == 2:
        # inspection hatch, slightly recessed
        hatch = R.chamfer(11, 11, 10, 10, 2)
        c.cavity(R.edge(hatch, diag=True), "rustplate", 2, bevel=1)
        c.plate(R.shrink(hatch, 1), "rustplate", 2, bevel=1, hi=1, lo=1)
        c.groove(R.line(13, 16, 18, 16), depth=1, lip=1)
    else:
        # hairline crack exposing the dark under-structure
        crack = R.path([(8, 10), (11, 13), (13, 12), (17, 17), (21, 18), (23, 22)])
        c.fill(crack, "rustplate", 0)
        c.shade(R.shift(crack, 0, 1) - crack, 1)
    for _ in range(2):
        x, y = rnd.randint(5, N - 6), rnd.randint(5, N - 6)
        c.set((x, y), "rust", 3)
    return c


# ------------------------------------------------------------------------------------------ vitrified dust

def vitrified_dust(variant):
    """Vitrified mineral dust: pale glassy grit swept into ripples, with sharp glass specks."""
    c = R.Canvas(N, N)
    for y in range(N):
        for x in range(N):
            ripple = math.sin((x * 2 + y) * 2 * math.pi / 32 * 2 + R.value_noise(x, y, 8, N, 3) * 3.0)
            v = R.fbm(x, y, N, 51, (16, 8, 4)) + ripple * 0.1
            c.set((x, y), "vitdust", 3 if v > 0.52 else 2)
    rnd = R.rng("vitdust", variant)
    specks = {1: 2, 2: 3, 3: 1}[variant]
    for _ in range(specks):
        x, y = rnd.randint(4, N - 5), rnd.randint(4, N - 5)
        c.set((x, y), "pale", 3)
        c.set((x - 1, y + 1), "vitdust", 1)
    if variant == 2:
        peb = R.ellipse(20.5, 12.5, 3.0, 2.2)
        c.dome(peb, "vitdust", 3, 5)
        c.shade(R.shift(peb, -1, 1) - peb, -1)
    if variant == 3:
        shard = R.poly([(9, 22), (14, 18), (16, 20), (11, 24)])
        c.plate(shard, "glass", 4, bevel=1, hi=1, lo=1)
        c.shade(R.shift(shard, -1, 1) - shard, -1)
    return c


# ------------------------------------------------------------------------------------------ basalt ridge

_RIDGE_PERIOD = 32
# column centres of one 32 px period (two rows, the second offset by half a column); jitter keeps them irregular
_RIDGE_LATTICE = [(1, 2), (17, -1), (8, 15), (25, 17)]


def _ridge_cells(n):
    """Periodic Voronoi cells over an n x n canvas plus a one-period margin, so tops crossing the border are
    shaded exactly as in the neighbouring tile. Returns {cell key: pixel set} in canvas coordinates."""
    P = _RIDGE_PERIOD
    pts = []
    for oy in range(-P, n + P, P):
        for ox in range(-P, n + P, P):
            for k, (x, y) in enumerate(_RIDGE_LATTICE):
                pts.append(((k, ox, oy), x + ox, y + oy))
    cells, joints = {}, set()
    for y in range(-P // 2, n + P // 2):
        for x in range(-P // 2, n + P // 2):
            best, second, key = 1e9, 1e9, None
            for kk, px, py in pts:
                d = (x - px) ** 2 + (y - py) ** 2
                if d < best:
                    best, second, key = d, best, kk
                elif d < second:
                    second = d
            if second ** 0.5 - best ** 0.5 < 1.1:
                joints.add((x, y))
            else:
                cells.setdefault(key, set()).add((x, y))
    return cells, joints


def _ridge(key):
    """Basalt ridge: a mass of packed column tops, brighter than the basalt floor so impassable rock reads at a
    glance. Every variant shares the same periodic columns (borders always match); variants differ only in
    details kept away from the border. The game darkens the interior of large wall masses on its own."""
    n = 64 if key == "-large" else 32
    m = _RIDGE_PERIOD // 2
    big = R.Canvas(n + 2 * m, n + 2 * m)
    cells, joints = _ridge_cells(n)
    sh = lambda pts: {(x + m, y + m) for x, y in pts}  # noqa: E731
    big.fill(sh(joints), "basalt", 1)
    for (k, ox, oy), pix in sorted(cells.items()):
        height = (3, 5, 4, 5)[k]                       # periodic: depends only on the column, not the tile
        big.plate(sh(pix), "basalt", height, bevel=2, hi=1, lo=2, rim=0)
    if key == "-large":
        # one massive column in the middle of the 2x2 group, over the regular ones
        top = sh(R.regular(31.5, 31.5, 23, 6, 0))
        big.drop_shadow(top, -1, 2)
        big.plate(top, "basalt", 5, bevel=2, hi=1, lo=2, rim=1)
        big.shade(sh(R.line(19, 36, 35, 22)) & R.shrink(top, 3), 1)
    c = R.Canvas(n, n)
    for (x, y), v in big.px.items():
        if m <= x < n + m and m <= y < n + m:
            c.px[(x - m, y - m)] = v
    rnd = R.rng("ridge", key)
    inner = R.rect(4, 4, n - 8, n - 8)
    if key == "1":
        crack = R.path([(9, 20), (13, 17), (16, 18), (21, 13)]) & inner
        c.fill(crack, "basalt", 1)
        c.shade(R.shift(crack, 0, 1) - crack, 1)
    for _ in range(2 if n == 32 else 4):
        x, y = rnd.randint(5, n - 6), rnd.randint(5, n - 6)
        if c.px.get((x, y), ("", 0))[1] >= 4:
            c.set((x, y), "steel", 4)
            c.set((x + 1, y - 1), "steel", 5)
    return c


def machine_basalt_wall():
    return {key: _ridge(key) for key in ("1", "2", "-large")}


FAMILIES = {
    "machine-basalt": machine_basalt,
    "scrap-soil": scrap_soil,
    "rusted-plating": rusted_plating,
    "vitrified-dust": vitrified_dust,
}
