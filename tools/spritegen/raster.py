"""Indexed-material raster used by every Vesper sprite generator.

Pixels store (ramp, shade, alpha) instead of RGBA. Lighting, cast shadows and wear only move a pixel along
its own ramp, so a sprite can never drift off-palette. Masks are plain sets of (x, y) pixels in image
coordinates (y grows downward); shading math converts to y-up so "light from the top right" reads naturally.
"""
import math
import random
import zlib

from PIL import Image

import palette

# ----------------------------------------------------------------------------------------------- masks

N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
N8 = N4 + ((1, 1), (1, -1), (-1, 1), (-1, -1))


def rect(x, y, w, h):
    return {(i, j) for i in range(x, x + w) for j in range(y, y + h)}


def chamfer(x, y, w, h, c, corners=("tl", "tr", "bl", "br")):
    """Rectangle with 45-degree corner cuts of c pixels: the core Vesper silhouette."""
    out = set()
    for j in range(y, y + h):
        for i in range(x, x + w):
            lx, ly, rx, ry = i - x, j - y, x + w - 1 - i, y + h - 1 - j
            if "tl" in corners and lx + ly < c:
                continue
            if "tr" in corners and rx + ly < c:
                continue
            if "bl" in corners and lx + ry < c:
                continue
            if "br" in corners and rx + ry < c:
                continue
            out.add((i, j))
    return out


def circle(cx, cy, r):
    out = set()
    for j in range(int(math.floor(cy - r)) - 1, int(math.ceil(cy + r)) + 1):
        for i in range(int(math.floor(cx - r)) - 1, int(math.ceil(cx + r)) + 1):
            if (i + 0.5 - cx) ** 2 + (j + 0.5 - cy) ** 2 <= r * r:
                out.add((i, j))
    return out


def ring(cx, cy, r_out, r_in):
    return circle(cx, cy, r_out) - circle(cx, cy, r_in)


def ellipse(cx, cy, rx, ry):
    out = set()
    for j in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for i in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if ((i + 0.5 - cx) / rx) ** 2 + ((j + 0.5 - cy) / ry) ** 2 <= 1.0:
                out.add((i, j))
    return out


def poly(points):
    """Even-odd fill tested at pixel centers."""
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    out = set()
    n = len(points)
    for j in range(int(math.floor(min(ys))), int(math.ceil(max(ys))) + 1):
        py = j + 0.5
        for i in range(int(math.floor(min(xs))), int(math.ceil(max(xs))) + 1):
            px = i + 0.5
            inside = False
            for k in range(n):
                x0, y0 = points[k]
                x1, y1 = points[(k + 1) % n]
                if (y0 > py) != (y1 > py):
                    if px < x0 + (py - y0) * (x1 - x0) / (y1 - y0):
                        inside = not inside
            if inside:
                out.add((i, j))
    return out


def regular(cx, cy, r, sides, angle=0.0):
    pts = []
    for k in range(sides):
        a = math.radians(angle) + 2 * math.pi * k / sides
        pts.append((cx + r * math.cos(a), cy - r * math.sin(a)))
    return poly(pts)


def line(x0, y0, x1, y1):
    """1px Bresenham line between integer pixels."""
    out = set()
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        out.add((x0, y0))
        if x0 == x1 and y0 == y1:
            return out
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def path(points):
    out = set()
    for a, b in zip(points, points[1:]):
        out |= line(a[0], a[1], b[0], b[1])
    return out


def thick(x0, y0, x1, y1, w):
    """Capsule of width w between two points (pixel-center test)."""
    out = set()
    r = w / 2.0
    vx, vy = x1 - x0, y1 - y0
    ll = vx * vx + vy * vy or 1e-9
    for j in range(int(min(y0, y1) - r) - 1, int(max(y0, y1) + r) + 2):
        for i in range(int(min(x0, x1) - r) - 1, int(max(x0, x1) + r) + 2):
            px, py = i + 0.5, j + 0.5
            t = max(0.0, min(1.0, ((px - x0) * vx + (py - y0) * vy) / ll))
            if (px - x0 - t * vx) ** 2 + (py - y0 - t * vy) ** 2 <= r * r:
                out.add((i, j))
    return out


def grow(m, n=1, diag=True):
    out = set(m)
    nb = N8 if diag else N4
    for _ in range(n):
        out |= {(x + dx, y + dy) for x, y in out for dx, dy in nb}
    return out


def shrink(m, n=1, diag=True):
    out = set(m)
    nb = N8 if diag else N4
    for _ in range(n):
        out = {(x, y) for x, y in out if all((x + dx, y + dy) in out for dx, dy in nb)}
    return out


def edge(m, diag=False):
    nb = N8 if diag else N4
    return {(x, y) for x, y in m if any((x + dx, y + dy) not in m for dx, dy in nb)}


def halo(m, diag=True):
    return grow(m, 1, diag) - m


def shift(m, dx, dy):
    return {(x + dx, y + dy) for x, y in m}


def flip_x(m, w):
    return {(w - 1 - x, y) for x, y in m}


def flip_y(m, h):
    return {(x, h - 1 - y) for x, y in m}


def rot90(m, n, k=1):
    """Rotate clockwise k quarter turns inside an n x n square."""
    out = set(m)
    for _ in range(k % 4):
        out = {(n - 1 - y, x) for x, y in out}
    return out


def sym4(m, n):
    return m | rot90(m, n, 1) | rot90(m, n, 2) | rot90(m, n, 3)


def mirror4(m, w, h):
    return m | flip_x(m, w) | flip_y(m, h) | flip_y(flip_x(m, w), h)


def clip(m, w, h):
    return {(x, y) for x, y in m if 0 <= x < w and 0 <= y < h}


def bbox(m):
    xs = [p[0] for p in m]
    ys = [p[1] for p in m]
    return min(xs), min(ys), max(xs), max(ys)


def distance(m, diag=True):
    """Steps from each pixel to the nearest pixel outside the mask (boundary pixels are 0)."""
    nb = N8 if diag else N4
    dist = {}
    frontier = [p for p in m if any((p[0] + dx, p[1] + dy) not in m for dx, dy in nb)]
    for p in frontier:
        dist[p] = 0
    d = 0
    while frontier:
        d += 1
        nxt = []
        for x, y in frontier:
            for dx, dy in nb:
                q = (x + dx, y + dy)
                if q in m and q not in dist:
                    dist[q] = d
                    nxt.append(q)
        frontier = nxt
    return dist


_offset_cache = {}


def _offsets(radius):
    if radius not in _offset_cache:
        r = int(math.ceil(radius))
        _offset_cache[radius] = [(dx, dy, 1.0 / (dx * dx + dy * dy)) for dx in range(-r, r + 1)
                                 for dy in range(-r, r + 1) if 0 < dx * dx + dy * dy <= radius * radius]
    return _offset_cache[radius]


def normal(m, p, radius=2.5):
    """Outward surface normal at p, estimated from nearby empty pixels; returned y-up."""
    nx = ny = 0.0
    x, y = p
    for dx, dy, w in _offsets(radius):
        if (x + dx, y + dy) not in m:
            nx += dx * w
            ny += dy * w
    ll = math.hypot(nx, ny)
    return (nx / ll, -ny / ll) if ll > 0 else (0.0, 0.0)


# ----------------------------------------------------------------------------------------------- noise

def seed_of(*parts):
    return zlib.crc32("/".join(str(p) for p in parts).encode())


def rng(*parts):
    return random.Random(seed_of(*parts))


def hash01(x, y, seed):
    if not isinstance(seed, int):
        seed = seed_of(seed)
    h =(x * 374761393 + y * 668265263 + seed * 362437) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def value_noise(x, y, cell, period, seed):
    """Smooth value noise that tiles every `period` pixels (period must be a multiple of cell)."""
    gp = period // cell
    fx, fy = x / cell, y / cell
    x0, y0 = int(math.floor(fx)), int(math.floor(fy))
    tx, ty = fx - x0, fy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)

    def v(ix, iy):
        return hash01(ix % gp, iy % gp, seed)

    a = v(x0, y0) + (v(x0 + 1, y0) - v(x0, y0)) * tx
    b = v(x0, y0 + 1) + (v(x0 + 1, y0 + 1) - v(x0, y0 + 1)) * tx
    return a + (b - a) * ty


def fbm(x, y, period, seed, cells=(16, 8, 4), weights=(0.55, 0.3, 0.15)):
    return sum(w * value_noise(x, y, c, period, seed + i * 101) for i, (c, w) in enumerate(zip(cells, weights)))


# ----------------------------------------------------------------------------------------------- canvas

class Canvas:
    def __init__(self, w, h, light=(1.0, 1.0)):
        self.w, self.h = w, h
        self.px = {}
        ll = math.hypot(*light)
        self.light = (light[0] / ll, light[1] / ll)

    # --- raw access
    def inside(self, p):
        return 0 <= p[0] < self.w and 0 <= p[1] < self.h

    def set(self, p, ramp, idx, alpha=255):
        if self.inside(p):
            n = palette.size(ramp)
            self.px[p] = (ramp, max(0, min(n - 1, idx)), alpha)

    def fill(self, m, ramp, idx, alpha=255):
        for p in m:
            self.set(p, ramp, idx, alpha)
        return self

    def erase(self, m):
        for p in m:
            self.px.pop(p, None)
        return self

    def occupied(self):
        return set(self.px)

    def shade(self, m, delta):
        """Move existing pixels along their own ramp."""
        for p in m:
            v = self.px.get(p)
            if v:
                self.set(p, v[0], v[1] + delta, v[2])
        return self

    def recolor(self, m, ramp, delta=0):
        for p in m:
            v = self.px.get(p)
            if v:
                src_n, dst_n = palette.size(v[0]), palette.size(ramp)
                idx = round(v[1] * (dst_n - 1) / max(1, src_n - 1))
                self.set(p, ramp, idx + delta, v[2])
        return self

    def lit(self, n):
        return n[0] * self.light[0] + n[1] * self.light[1]

    # --- volumetric fills
    def plate(self, m, ramp, base, bevel=1, hi=1, lo=1, rim=0, edge_lo=0, threshold=0.3):
        """Raised plate: faces toward the light brighten, faces away darken, the top stays flat."""
        if not m:
            return self
        dist = distance(m)
        radius = bevel + 1.5
        for p in m:
            idx = base
            d = dist.get(p, 0)
            if d < bevel:
                s = self.lit(normal(m, p, radius))
                if s > threshold:
                    idx = base + hi + (rim if d == 0 else 0)
                elif s < -threshold:
                    idx = base - lo - (edge_lo if d == 0 else 0)
            self.set(p, ramp, idx)
        return self

    def cavity(self, m, ramp, base, bevel=1, hi=1, lo=1, cast=0, threshold=0.3):
        """Recess: inverted bevel, plus an optional shadow cast from the top-right rim onto the floor."""
        if not m:
            return self
        dist = distance(m)
        radius = bevel + 1.5
        lx, ly = self.light
        for p in m:
            idx = base
            d = dist.get(p, 0)
            if d < bevel:
                s = self.lit(normal(m, p, radius))
                if s > threshold:
                    idx = base - lo
                elif s < -threshold:
                    idx = base + hi
            elif cast:
                for k in range(1, cast + 1):
                    q = (p[0] + round(lx * k), p[1] - round(ly * k))
                    if q not in m:
                        idx = base - 1
                        break
            self.set(p, ramp, idx)
        return self

    def dome(self, m, ramp, lo, hi, cx=None, cy=None, r=None, spec=True, ambient=0.15, height=1.0):
        """Spherical shading; the specular pixel lands on the top-right of the dome."""
        if not m:
            return self
        x0, y0, x1, y1 = bbox(m)
        cx = (x0 + x1 + 1) / 2.0 if cx is None else cx
        cy = (y0 + y1 + 1) / 2.0 if cy is None else cy
        r = max(x1 - x0 + 1, y1 - y0 + 1) / 2.0 if r is None else r
        lx, ly = self.light
        l3 = (lx, ly, 1.1)
        ln = math.sqrt(sum(c * c for c in l3))
        l3 = tuple(c / ln for c in l3)
        best, best_p = -9, None
        for p in m:
            dx = (p[0] + 0.5 - cx) / r
            dy = -(p[1] + 0.5 - cy) / r
            dz = math.sqrt(max(0.0, 1.0 - dx * dx - dy * dy)) * height
            nl = math.sqrt(dx * dx + dy * dy + dz * dz) or 1.0
            i = (dx * l3[0] + dy * l3[1] + dz * l3[2]) / nl
            t = max(0.0, min(1.0, (i + ambient) / (1 + ambient)))
            self.set(p, ramp, lo + int(round(t * (hi - lo))))
            if i > best:
                best, best_p = i, p
        if spec and best_p:
            self.set(best_p, ramp, hi + 1)
        return self

    def cylinder(self, m, ramp, lo, hi, axis="h"):
        """Shade across a pipe: for a horizontal pipe the top rows catch light."""
        if not m:
            return self
        x0, y0, x1, y1 = bbox(m)
        for p in m:
            if axis == "h":
                t = 1.0 - (p[1] - y0 + 0.5) / (y1 - y0 + 1)
            else:
                t = (p[0] - x0 + 0.5) / (x1 - x0 + 1)
            t = math.sin(t * math.pi * 0.5 + 0.35) if t < 0.85 else 0.7
            self.set(p, ramp, lo + int(round(max(0.0, min(1.0, t)) * (hi - lo))))
        return self

    def facets(self, faces, ramp, base, spread=2):
        """Flat-shaded facets; each face is (mask, normal_xy) and gets one shade from its normal."""
        for m, n in faces:
            ll = math.hypot(*n) or 1.0
            s = self.lit((n[0] / ll, n[1] / ll)) if (n[0] or n[1]) else 0.25
            self.fill(m, ramp, base + int(round(s * spread)))
        return self

    def drop_shadow(self, m, delta=-1, dist=1):
        """Darken what lies just below-left of a raised part (the side away from the light)."""
        lx, ly = self.light
        target = set()
        for k in range(1, dist + 1):
            target |= shift(m, -round(lx * k), round(ly * k))
        return self.shade(target - m, delta)

    def groove(self, m, depth=2, lip=1):
        """Engraved line: dark channel plus a lit lip on the side that faces the light."""
        lx, ly = self.light
        self.shade(m, -depth)
        lip_m = (shift(m, 0, 1) if ly > 0.2 else set()) | (shift(m, -1, 0) if lx > 0.2 else set())
        return self.shade(lip_m - m, lip)

    def paste(self, other, ox=0, oy=0):
        for (x, y), v in other.px.items():
            self.set((x + ox, y + oy), v[0], v[1], v[2])
        return self

    # --- output
    def image(self):
        img = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
        put = img.putpixel
        for (x, y), (ramp, idx, alpha) in self.px.items():
            put((x, y), palette.color(ramp, idx, alpha))
        return img

    def save(self, path):
        self.image().save(path)
        return path
