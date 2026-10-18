"""Automated checks for the shipped sprites in assets/sprites.

    python tools/spritegen/validate.py            # validate, exit 1 on any error
    python tools/spritegen/validate.py --stale    # also regenerate into a temp dir and require identical pixels

Checks: valid RGBA PNGs, expected files per catalog entry, dimensions, opacity rules per kind, outline
margins for runtime-outlined regions, team-color-only cells, palette adherence, stray pixels, color budget,
naming, orphans, file size and atlas page budget.
"""
import argparse
import filecmp
import os
import re
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from PIL import Image  # noqa: E402

import build  # noqa: E402
import catalog  # noqa: E402
import palette  # noqa: E402

NAME_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*\.png$")
PALETTE = {c[:3] for ramp in palette.RGBA.values() for c in ramp}
TEAM = {palette.hex_to_rgba(h)[:3] for h in palette.RAMPS["team"]}
TRANSLUCENT_OK = catalog.GLOW_SUFFIXES
MAX_FILE = 64 * 1024
PAGE = 4096


class Report:
    def __init__(self):
        self.errors, self.warnings, self.checked = [], [], 0

    def err(self, path, msg):
        self.errors.append("%s: %s" % (path, msg))

    def warn(self, path, msg):
        self.warnings.append("%s: %s" % (path, msg))


def expected_files(e):
    folder = os.path.join(build.SPRITES, *e.folder.split("/"))
    return {suffix: os.path.join(folder, e.name + suffix + ".png") for suffix in e.layers()}


def opaque_pixels(img):
    px = img.load()
    return [(x, y) for y in range(img.height) for x in range(img.width) if px[x, y][3] > 0]


def check_file(rep, path, e, suffix):
    rel = os.path.relpath(path, build.ROOT)
    if not os.path.isfile(path):
        rep.err(rel, "missing")
        return None
    rep.checked += 1
    if not NAME_RE.match(os.path.basename(path)):
        rep.err(rel, "file name is not lowercase kebab-case")
    if os.path.getsize(path) > MAX_FILE:
        rep.err(rel, "file larger than %d bytes" % MAX_FILE)
    try:
        with Image.open(path) as probe:
            probe.verify()
        img = Image.open(path)
        img.load()
    except Exception as ex:  # noqa: BLE001
        rep.err(rel, "not a valid PNG (%s)" % ex)
        return None
    if img.mode != "RGBA":
        rep.err(rel, "mode %s, expected RGBA" % img.mode)
        img = img.convert("RGBA")
    px = img.load()
    w, h = img.size
    solid = opaque_pixels(img)
    if not solid:
        rep.err(rel, "image is empty")
        return img

    translucent_ok = e.kind == "effect" or suffix.endswith(TRANSLUCENT_OK) or suffix == "-laser-end"
    partial = [p for p in solid if px[p][3] < 255]
    if partial and not translucent_ok:
        rep.err(rel, "%d semi-transparent pixels in a solid sprite" % len(partial))

    colors = {px[p][:3] for p in solid}
    off = colors - PALETTE
    if off:
        rep.err(rel, "%d colors outside the Vesper palette" % len(off))
    if len(colors) > 40:
        rep.warn(rel, "%d colors (budget 40)" % len(colors))

    if suffix in catalog.TEAM_SUFFIXES and colors - TEAM:
        rep.err(rel, "team region uses colors other than #ffffff/#dcc6c6/#9d7f7f")

    # per-kind geometry
    n = e.size * 32
    coverage = len(solid) / float(w * h)
    if e.kind == "floor":
        if (w, h) != (32, 32):
            rep.err(rel, "floor must be 32x32, is %dx%d" % (w, h))
        if coverage < 1.0:
            rep.err(rel, "floor must be fully opaque")
    elif e.kind == "wall":
        size = 64 if suffix == "-large" else 32
        if (w, h) != (size, size):
            rep.err(rel, "static wall region must be %dx%d, is %dx%d" % (size, size, w, h))
        if coverage < 1.0:
            rep.err(rel, "static wall must be fully opaque")
    elif e.kind == "ore":
        if (w, h) != (32, 32):
            rep.err(rel, "ore must be 32x32")
        if not 0.08 <= coverage <= 0.6:
            rep.err(rel, "ore coverage %.0f%% outside 8-60%%" % (coverage * 100))
    elif e.kind == "item":
        if (w, h) != (32, 32):
            rep.err(rel, "item icon must be 32x32")
        if not 0.25 <= coverage <= 0.9:
            rep.warn(rel, "item coverage %.0f%%" % (coverage * 100))
    elif e.kind in ("block", "turret"):
        is_base = (suffix == "" and e.kind == "block") or (suffix == "-base" and e.kind == "turret")
        if is_base and (w, h) != (n, n):
            rep.err(rel, "base region must be %dx%d for a %dx%d block, is %dx%d" % (n, n, e.size, e.size, w, h))
        if e.kind == "turret" and suffix in ("", "-heat"):
            margin = min(min(x for x, _ in solid), min(y for _, y in solid), w - 1 - max(x for x, _ in solid), h - 1 - max(y for _, y in solid))
            if suffix == "" and margin < 4:
                rep.err(rel, "turret head needs a 4 px margin for the runtime outline (has %d)" % margin)
        # Block.createIcons draws every icon layer at the top-left corner, so layers must match the footprint
        if (w, h) != (n, n) and suffix not in ("-laser", "-laser-end") and suffix not in e.free_layers:
            rep.err(rel, "layer is %dx%d; block layers must be %dx%d or icons compose off-centre" % (w, h, n, n))
    elif e.kind == "icon":
        # UI emblems (sector icons): the game draws a 5 px outline around them
        if (w, h) != (64, 64):
            rep.err(rel, "icon must be 64x64, is %dx%d" % (w, h))
        margin = min(min(x for x, _ in solid), min(y for _, y in solid), w - 1 - max(x for x, _ in solid), h - 1 - max(y for _, y in solid))
        if margin < 5:
            rep.err(rel, "icon needs a 5 px margin for the runtime outline (has %d)" % margin)
    elif e.kind == "unit" and suffix not in catalog.TEAM_SUFFIXES:
        margin = min(min(x for x, _ in solid), min(y for _, y in solid), w - 1 - max(x for x, _ in solid), h - 1 - max(y for _, y in solid))
        if margin < 3:
            rep.err(rel, "unit region needs a 3 px margin for the runtime outline (has %d)" % margin)

    # stray pixels: tiny isolated islands in solid sprites usually mean a generator bug
    if not translucent_ok and e.kind in ("ore", "item", "block", "turret", "unit", "icon"):
        left = set(solid)
        while left:
            seed = left.pop()
            comp, stack = 1, [seed]
            while stack:
                x, y = stack.pop()
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        q = (x + dx, y + dy)
                        if q in left:
                            left.discard(q)
                            comp += 1
                            stack.append(q)
            if comp <= 1 and len(solid) > 40:
                rep.warn(rel, "isolated single pixel at %s" % (seed,))
    return img


def same_pixels(a, b):
    with Image.open(a) as ia, Image.open(b) as ib:
        return ia.size == ib.size and ia.convert("RGBA").tobytes() == ib.convert("RGBA").tobytes()


def atlas_budget(rep):
    pages = {"environment": 0, "main": 0}
    for root, _, files in os.walk(build.SPRITES):
        for f in files:
            with Image.open(os.path.join(root, f)) as img:
                area = (img.width + 4) * (img.height + 4)  # include packer padding / bleed
            page = "environment" if "environment" in root.replace("\\", "/") else "main"
            pages[page] += area
    for page, area in pages.items():
        share = area / float(PAGE * PAGE)
        if share > 0.85:
            rep.err("atlas:" + page, "uses %.0f%% of a %dpx page" % (share * 100, PAGE))
    return pages


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--stale", action="store_true", help="regenerate and compare with committed sprites")
    args = ap.parse_args()
    rep = Report()

    expected = set()
    for e in catalog.ENTRIES:
        for suffix, path in expected_files(e).items():
            expected.add(os.path.normcase(os.path.abspath(path)))
            check_file(rep, path, e, suffix)

    for root, _, files in os.walk(build.SPRITES):
        for f in files:
            p = os.path.normcase(os.path.abspath(os.path.join(root, f)))
            if p not in expected:
                rep.err(os.path.relpath(p, build.ROOT), "orphan sprite (not produced by the catalog)")

    pages = atlas_budget(rep)

    if args.stale:
        with tempfile.TemporaryDirectory() as tmp:
            build.build(tmp)
            cmp = filecmp.dircmp(tmp, build.SPRITES)
            stale = []

            def walk(d, prefix=""):
                stale.extend(prefix + f for f in d.left_only + d.right_only)
                # PNG bytes depend on the zlib build (Windows and Linux differ); compare decoded pixels instead
                stale.extend(prefix + f for f in d.diff_files if not same_pixels(os.path.join(d.left, f), os.path.join(d.right, f)))
                for name, sub in d.subdirs.items():
                    walk(sub, prefix + name + "/")
            walk(cmp)
            for f in stale:
                rep.err("assets/sprites/" + f, "differs from a fresh generation (run build.py)")

    print("checked %d sprite files across %d catalog entries" % (rep.checked, len(catalog.ENTRIES)))
    for page, area in pages.items():
        print("atlas page %-11s %6.1f%% of %dx%d" % (page, 100.0 * area / (PAGE * PAGE), PAGE, PAGE))
    for w in rep.warnings:
        print("WARN  " + w)
    for e in rep.errors:
        print("ERROR " + e)
    print("%d errors, %d warnings" % (len(rep.errors), len(rep.warnings)))
    sys.exit(1 if rep.errors else 0)


if __name__ == "__main__":
    main()
