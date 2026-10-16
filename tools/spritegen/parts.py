"""Reusable Vesper components.

These are the shared vocabulary of the art direction: chamfered frames, recessed wells, rivets, lamps,
vents, pipes, hazard bands and resonance traces. Families compose them; they should rarely draw raw pixels.
"""
import math

import raster as R


# ------------------------------------------------------------------------------------------ frames

def body(n, chamfer, inset=0):
    """Chamfered footprint for an n-pixel block."""
    return R.chamfer(inset, inset, n - 2 * inset, n - 2 * inset, chamfer)


def frame(c, n, ramp="steel", base=4, chamfer=3, bevel=2, rim=1, inset=0):
    m = body(n, chamfer, inset)
    c.plate(m, ramp, base, bevel=bevel, hi=1, lo=2, rim=rim, edge_lo=1)
    return m


def foundation(c, n, chamfer=None, ramp="steel", base=3):
    """Machine base covering the whole footprint (small chamfer only), drawn like the standard machine frame.
    Machines with a shaped silhouette stand on one, so a belt or conduit meeting any edge tile always reaches
    the same solid, lit frame edge as every other block instead of a cut corner or a dark gap."""
    chamfer = chamfer if chamfer is not None else max(3, n // 16)
    m = frame(c, n, ramp, base, chamfer=chamfer, bevel=2)
    k = chamfer + 2
    for (x, y) in ((k, k), (n - 1 - k, k), (k, n - 1 - k), (n - 1 - k, n - 1 - k)):
        stud(c, x, y, "steel", 5)
    return m


def well(c, m, ramp="dark", base=2, bevel=1, cast=2):
    c.cavity(m, ramp, base, bevel=bevel, hi=1, lo=1, cast=cast)
    return m


def plate(c, m, ramp="steel", base=4, bevel=1, rim=1):
    c.plate(m, ramp, base, bevel=bevel, hi=1, lo=1, rim=rim)
    return m


def raised(c, m, ramp="steel", base=4, bevel=1, rim=1, shadow=1):
    """Plate that casts a one-pixel shadow onto whatever is under it."""
    if shadow:
        c.drop_shadow(m, -1, shadow)
    return plate(c, m, ramp, base, bevel, rim)


# ------------------------------------------------------------------------------------------ small parts

def rivet(c, x, y, ramp="steel", base=4):
    """2x2 rivet: lit top-right pixel, shadowed bottom-left."""
    c.plate(R.rect(x, y, 2, 2), ramp, base, bevel=1, hi=2, lo=1)


def stud(c, x, y, ramp="steel", base=5):
    """Single-pixel bolt head with a shadow pixel below-left."""
    c.shade({(x - 1, y + 1)}, -1)
    c.set((x, y), ramp, base)


def lamp(c, x, y, ramp="amber", size=1, lit=True):
    """Indicator light. Size 1 is a single bright pixel in a dark socket; size 2 is a 2x2 lens."""
    hot = 6 if lit else 2
    if size == 1:
        c.shade(R.halo({(x, y)}, diag=False), -1)
        c.set((x, y), ramp, hot)
    else:
        c.fill(R.halo(R.rect(x, y, 2, 2), diag=False), "dark", 1)
        c.fill(R.rect(x, y, 2, 2), ramp, hot - 2)
        c.set((x + 1, y), ramp, hot)


def vent(c, x, y, w, h, horizontal=True, ramp="dark", slat="steel", slat_base=3, step=2):
    """Recessed grille with lit slats."""
    box = R.rect(x, y, w, h)
    c.cavity(box, ramp, 1, bevel=1, cast=0)
    inner = R.shrink(box, 1, diag=False)
    for p in inner:
        k = (p[1] - y) if horizontal else (p[0] - x)
        if k % step == 0:
            c.set(p, slat, slat_base + (1 if (horizontal and p[0] > x + w // 2) or (not horizontal and p[1] < y + h // 2) else 0))
    return box


def stripes(c, m, a=("amber", 4), b=("dark", 2), period=4, width=2, direction=1):
    """Diagonal hazard band inside a mask."""
    for p in m:
        k = (p[0] + direction * p[1]) % period
        ramp, idx = a if k < width else b
        c.set(p, ramp, idx)
    # re-light the band so it keeps the volume of the plate it sits on
    dist = R.distance(m)
    for p in m:
        if dist.get(p, 0) == 0:
            s = c.lit(R.normal(m, p, 1.5))
            if s > 0.3:
                c.shade({p}, 1)
            elif s < -0.3:
                c.shade({p}, -1)


def pipe(c, x0, y0, x1, y1, width=3, ramp="steel", lo=1, hi=5, collar=None):
    """Straight horizontal or vertical pipe with cylinder shading and optional collars every N px."""
    if y0 == y1:
        m = R.rect(min(x0, x1), y0 - width // 2, abs(x1 - x0) + 1, width)
        c.cylinder(m, ramp, lo, hi, axis="h")
        if collar:
            for x in range(min(x0, x1) + collar // 2, max(x0, x1), collar):
                c.cylinder(R.rect(x, y0 - width // 2 - 1, 1, width + 2), ramp, lo + 1, hi + 1, axis="h")
    else:
        m = R.rect(x0 - width // 2, min(y0, y1), width, abs(y1 - y0) + 1)
        c.cylinder(m, ramp, lo, hi, axis="v")
        if collar:
            for y in range(min(y0, y1) + collar // 2, max(y0, y1), collar):
                c.cylinder(R.rect(x0 - width // 2 - 1, y, width + 2, 1), ramp, lo + 1, hi + 1, axis="v")
    return m


def trace(c, points, ramp="teal", base=4, node=True, groove=True):
    """Resonance trace: an engraved channel carrying a thin teal line, with bright nodes at the ends.

    This is the signature motif of ancient Vesper technology.
    """
    m = R.path(points)
    if groove:
        c.shade(R.grow(m, 1, diag=False) - m, -1)
    c.fill(m, ramp, base)
    if node:
        for x, y in (points[0], points[-1]):
            c.fill(R.rect(x - 1, y - 1, 2, 2), ramp, base + 1)
            c.set((x, y - 1), ramp, base + 2)
    return m


def glyph_ring(c, cx, cy, r, ramp="teal", base=3, gaps=6, gap_len=0.35, phase=0.0):
    """Broken circular inlay: arcs separated by gaps, used on ancient machines."""
    m = R.ring(cx, cy, r + 0.5, r - 0.5)
    keep = set()
    for p in m:
        a = (math.atan2(-(p[1] + 0.5 - cy), p[0] + 0.5 - cx) / (2 * math.pi) + phase) % 1.0
        if (a * gaps) % 1.0 > gap_len:
            keep.add(p)
    c.fill(keep, ramp, base)
    return keep


def gear(cx, cy, r_out, r_in, teeth, tooth=0.5, angle=0.0):
    """Gear silhouette mask."""
    out = set()
    for j in range(int(cy - r_out) - 1, int(cy + r_out) + 2):
        for i in range(int(cx - r_out) - 1, int(cx + r_out) + 2):
            dx, dy = i + 0.5 - cx, j + 0.5 - cy
            d = math.hypot(dx, dy)
            a = (math.atan2(dy, dx) - math.radians(angle)) / (2 * math.pi) * teeth
            lim = r_out if (a % 1.0) < tooth else r_in
            if d <= lim:
                out.add((i, j))
    return out


def blades(cx, cy, r_out, r_hub, count, width=0.5, sweep=0.35, angle=0.0):
    """Curved rotor/drill blades as one mask; width is the fraction of each sector that is blade."""
    out = set()
    for j in range(int(cy - r_out) - 1, int(cy + r_out) + 2):
        for i in range(int(cx - r_out) - 1, int(cx + r_out) + 2):
            dx, dy = i + 0.5 - cx, j + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > r_out or d < r_hub:
                continue
            a = (math.atan2(dy, dx) - math.radians(angle)) / (2 * math.pi) - sweep * (d / r_out)
            if (a * count) % 1.0 < width:
                out.add((i, j))
    return out


def wear(c, m, seed, amount=0.1, lo=2, cell=5):
    """Oxidation patches on existing metal.

    Low-frequency noise picks clumped patches covering exactly `amount` of the eligible area (quantile
    threshold), each with a darker rim, so wear reads as corrosion rather than speckle noise."""
    pts = sorted(p for p in m if p in c.px and c.px[p][0] in ("steel", "dark"))
    if not pts or amount <= 0:
        return
    seed2 = str(seed) + "/fine"
    vals = {p: 0.75 * R.value_noise(p[0], p[1], cell, cell * 128, seed) + 0.25 * R.value_noise(p[0], p[1], 2, 256, seed2)
            for p in pts}
    ordered = sorted(vals.values())
    threshold = ordered[min(len(ordered) - 1, int(len(ordered) * (1.0 - amount)))]
    patch = {p for p in pts if vals[p] >= threshold}
    for p in patch:
        v = c.px[p]
        c.set(p, "rust", lo + (1 if v[1] >= 5 else 0) + (1 if vals[p] > threshold + 0.06 else 0))
    for p in R.edge(patch) & set(pts):
        if c.lit(R.normal(patch, p, 1.5)) < -0.2:
            c.set(p, "rust", lo - 1)


def components(m):
    """4-connected pieces of a mask."""
    left = set(m)
    out = []
    while left:
        seed = left.pop()
        comp, stack = {seed}, [seed]
        while stack:
            x, y = stack.pop()
            for dx, dy in R.N4:
                q = (x + dx, y + dy)
                if q in left:
                    left.discard(q)
                    comp.add(q)
                    stack.append(q)
        out.append(comp)
    return out


def panels(c, m, cuts, ramp="steel", base=4, gap=("dark", 1), bevel=1, rim=1):
    """Stacked plating: the cut lines become dark gaps and every piece is lit as its own raised plate."""
    c.fill(m & cuts, gap[0], gap[1])
    pieces = [p for p in components(m - cuts) if len(p) > 2]
    for piece in pieces:
        c.plate(piece, ramp, base, bevel=bevel, hi=1, lo=1, rim=rim)
    return pieces


def deck(c, n, chamfer, ramp="steel", base=4, inset=3, seed=None, rust=0.08, layout="cross"):
    """Standard player-tech machine frame.

    A darker beveled outer frame carries an inset deck of raised plates separated by dark seams. The
    layout decides how the deck is cut ("cross", "h", "v", "x" or "solid"); machines then sit on top.
    """
    m = frame(c, n, ramp, base - 1, chamfer=chamfer, bevel=2)
    inner = R.chamfer(inset, inset, n - 2 * inset, n - 2 * inset, max(1, chamfer - inset // 2))
    cuts = R.edge(inner, diag=True)
    a, b = n // 2 - 1, n // 2
    if layout in ("cross", "v"):
        cuts |= R.rect(a, 0, 1, n)
    if layout in ("cross", "h"):
        cuts |= R.rect(0, b, n, 1)
    if layout == "x":
        cuts |= R.line(0, 0, n - 1, n - 1) | R.line(n - 1, 0, 0, n - 1)
    panels(c, inner, cuts, ramp, base, gap=("dark", 1))
    if seed is not None and rust > 0:
        lower_left = {p for p in m if p[0] - p[1] < -n * 0.3}
        wear(c, lower_left, seed, rust)
    return m, R.shrink(inner, 1)


def corner_pockets(c, n, size, inset, ramp="dark", rivets=True):
    """Recessed square pockets in each corner of a frame, each holding a rivet."""
    out = []
    for (x, y) in ((inset, inset), (n - inset - size, inset), (inset, n - inset - size), (n - inset - size, n - inset - size)):
        pocket = R.chamfer(x, y, size, size, 1)
        c.cavity(pocket, ramp, 2, bevel=1, cast=1)
        if rivets:
            rivet(c, x + size // 2 - 1, y + size // 2 - 1, "steel", 4)
        out.append(pocket)
    return out


def cracks(c, seed, start, steps, ramp="dark", idx=0, spread=0.6):
    """Random-walk fracture line, darkened, with a lit lower lip."""
    rnd = R.rng("crack", seed)
    x, y = start
    pts = set()
    dx, dy = rnd.choice([(1, 0), (0, 1), (1, 1), (-1, 1)])
    for _ in range(steps):
        pts.add((x, y))
        if rnd.random() < spread:
            dx, dy = rnd.choice([(1, 0), (0, 1), (1, 1), (-1, 1), (1, -1)])
        x, y = x + dx, y + dy
    c.groove(pts, depth=2, lip=1)
    return pts
