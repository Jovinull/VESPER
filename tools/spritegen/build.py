"""Generate Vesper sprites.

    python tools/spritegen/build.py                      # approved sprites -> assets/sprites (replaces it)
    python tools/spritegen/build.py --alternatives DIR   # every explored alternative -> DIR/<name>/<alt>/
    python tools/spritegen/build.py --out DIR            # approved sprites into another folder (stale check)

Generation is deterministic: the same code always produces byte-identical PNGs.
"""
import argparse
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import catalog  # noqa: E402
from families import special  # noqa: E402

ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
SPRITES = os.path.join(ROOT, "assets", "sprites")


def write_layers(entry, layers, folder):
    os.makedirs(folder, exist_ok=True)
    paths = []
    for suffix, canvas in sorted(layers.items()):
        path = os.path.join(folder, entry.name + suffix + ".png")
        canvas.image().save(path, optimize=True)
        paths.append(path)
    return paths


def build_icon(path):
    """Mod icon (mod list): the Vesper emblem, drawn at 64 px and doubled with nearest-neighbour."""
    c = special.emblem()
    img = c.image()
    img.resize((img.width * 2, img.height * 2), resample=0).save(path, optimize=True)
    return path


def build(out_dir, only=None):
    if os.path.isdir(out_dir):
        shutil.rmtree(out_dir)
    count = 0
    for e in catalog.ENTRIES:
        if only and e.name not in only:
            continue
        count += len(write_layers(e, e.layers(), os.path.join(out_dir, *e.folder.split("/"))))
    return count


def build_alternatives(out_dir):
    count = 0
    for e in catalog.ENTRIES:
        for alt in e.alts:
            count += len(write_layers(e, e.layers(alt), os.path.join(out_dir, e.name, alt)))
    return count


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=SPRITES)
    ap.add_argument("--alternatives")
    ap.add_argument("--only", nargs="*")
    args = ap.parse_args()
    if args.alternatives:
        n = build_alternatives(args.alternatives)
        print("wrote %d alternative layers to %s" % (n, args.alternatives))
        return
    n = build(args.out, args.only)
    print("wrote %d sprite files to %s" % (n, args.out))
    if args.out == SPRITES and not args.only:
        print("wrote mod icon", build_icon(os.path.join(ROOT, "icon.png")))


if __name__ == "__main__":
    main()
