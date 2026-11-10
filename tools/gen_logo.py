#!/usr/bin/env python3
"""The Modernization World logo: a 32 x 32 pixel-art picture, scaled to 128 x 128 for the mod list.

A copper gear (the factories and machines) with a lightning bolt in its heart (electricity), a boss's crystal shard
above it, over a grass block's top under a night-to-dusk sky. Standard library only.
"""
from __future__ import annotations

import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src" / "main" / "resources" / "assets" / "modernization_world" / "icon.png"
SIZE, SCALE = 32, 4


def hexc(c: str):
    return tuple(int(c[i:i + 2], 16) for i in (1, 3, 5))


def mix(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


SKY_TOP, SKY_LOW = hexc("#10142e"), hexc("#2c4f7c")
STAR = hexc("#cfe6ff")
GRASS = [hexc("#2f6b1f"), hexc("#4f9a2e"), hexc("#74c442")]
DIRT = [hexc("#4a2f1a"), hexc("#6b4425"), hexc("#86592f")]
COPPER = [hexc("#3a1a0c"), hexc("#7a3a18"), hexc("#b8622a"), hexc("#e08a3c"), hexc("#ffc27a")]
CORE = [hexc("#2a0606"), hexc("#7a0d0d"), hexc("#d42222")]
BOLT = [hexc("#c98a00"), hexc("#ffd53d"), hexc("#fff7c2")]
SHARD = [hexc("#35186a"), hexc("#6a34b4"), hexc("#a468e6"), hexc("#f0c8ff")]
FRAME = hexc("#06070f")


def draw():
    px = [[None] * SIZE for _ in range(SIZE)]

    # Sky, darker at the top, and a few stars.
    for y in range(SIZE):
        for x in range(SIZE):
            px[y][x] = mix(SKY_TOP, SKY_LOW, y / 22)
    for x, y in ((3, 3), (7, 5), (20, 3), (3, 13), (11, 2), (27, 12)):
        px[y][x] = STAR

    # A grass block's top along the bottom: a jagged grass edge over dirt.
    for x in range(SIZE):
        edge = 24 + (1 if x % 5 in (1, 3) else 0)
        for y in range(edge, SIZE):
            if y < edge + 2:
                px[y][x] = GRASS[2] if y == edge else GRASS[1]
            elif y == edge + 2 and x % 3 == 0:
                px[y][x] = GRASS[0]
            else:
                px[y][x] = DIRT[(x * 7 + y * 3) % 5 % 3]

    # The gear: a ring with eight square teeth, lit from the top left and outlined, a red-hot core in its hole.
    cx, cy = 15.5, 14.5
    gear = {}
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x - cx, y - cy
            r = math.hypot(dx, dy)
            body = 4.2 <= r <= 7.6
            tooth = False
            for k in range(8):
                ux, uy = math.cos(k * math.pi / 4), math.sin(k * math.pi / 4)
                u, v = dx * ux + dy * uy, -dx * uy + dy * ux
                if 6.5 <= u <= 10.2 and abs(v) <= 1.9:
                    tooth = True
            if body or tooth:
                light = (-dx - dy) / (max(r, 0.1) * math.sqrt(2))
                rim = r > 7.6 or abs(r - 6.0) < 0.5
                shade = 2.2 + light * 1.6 - (0.8 if r < 5.0 else 0) - (0.6 if rim and r <= 7.6 else 0)
                gear[(x, y)] = COPPER[max(1, min(4, round(shade)))]
            elif r < 4.2:
                gear[(x, y)] = CORE[2] if r < 1.8 else CORE[1] if r < 3.2 else CORE[0]
    for (x, y), c in gear.items():
        px[y][x] = c
    for y in range(SIZE):
        for x in range(SIZE):
            if (x, y) not in gear and any((x + dx, y + dy) in gear for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[y][x] = COPPER[0]

    # The bolt across the core.
    bolt = ["..2",
            ".21",
            "21.",
            "2221",
            ".12.",
            "12..",
            "2...",
            "1..."]
    for j, row in enumerate(bolt):
        for i, ch in enumerate(row):
            if ch != ".":
                px[10 + j][14 + i] = BOLT[int(ch)]

    # A boss's crystal shard, top right, glinting.
    shard = ["....3", "...32", "..321", ".321.", "321..", "21...", "1...."]
    for j, row in enumerate(shard):
        for i, ch in enumerate(row):
            if ch != ".":
                px[2 + j][24 + i] = SHARD[int(ch)]
    for x, y in ((23, 3), (29, 8), (27, 1)):
        px[y][x] = SHARD[3]

    # A dark frame with rounded corners.
    for y in range(SIZE):
        for x in range(SIZE):
            if x in (0, SIZE - 1) or y in (0, SIZE - 1):
                px[y][x] = FRAME
    for x, y in ((0, 0), (1, 0), (0, 1), (SIZE - 1, 0), (SIZE - 2, 0), (SIZE - 1, 1), (0, SIZE - 1), (1, SIZE - 1),
                 (0, SIZE - 2), (SIZE - 1, SIZE - 1), (SIZE - 2, SIZE - 1), (SIZE - 1, SIZE - 2)):
        px[y][x] = None
    for x, y in ((1, 1), (SIZE - 2, 1), (1, SIZE - 2), (SIZE - 2, SIZE - 2)):
        px[y][x] = FRAME
    return px


def write_png(path: Path, px, scale: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    rows = []
    for row in px:
        line = b"".join(bytes((*c, 255)) if c else b"\0\0\0\0" for c in row for _ in range(scale))
        rows += [b"\x00" + line] * scale

    def chunk(kind: bytes, body: bytes) -> bytes:
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    side = len(px) * scale
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", side, side, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(b"".join(rows), 9)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    picture = draw()
    write_png(OUT, picture, SCALE)
    write_png(ROOT / "docs" / "previews" / "logo.png", picture, 16)
    print(OUT)
