#!/usr/bin/env python3
"""Boss gear: item and armour textures, models, equipment, recipes, tags, boss drops and names.

Standard library only. Run from anywhere; writes under src/main/resources. The numbers live in
com.bossmode.gear.BossTier; this only makes the resources.

The textures keep the vanilla gear's shapes and shading and only change the colours: each one is a vanilla
texture (the diamond tools and spear, the diamond or netherite armour, a few vanilla materials) with every
shade swapped for the same step of the tier's own colour ramp. The vanilla textures are read from the
Minecraft client jar on this machine when this runs (Loom's cache, or --client-jar / MINECRAFT_CLIENT_JAR);
they are not kept in this repository. Without the jar the textures are left as they are and only the JSON
is written.
"""
from __future__ import annotations

import json
import os
import struct
import sys
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src" / "main" / "resources"
NS = "boss_mode"
ASSETS = RES / "assets" / NS
DATA = RES / "data" / NS
VANILLA = "assets/minecraft/textures/"


def rgb(*colours: str):
    return [tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) for c in colours]


# Each tier's colours, darkest to lightest: "metal" for the blade, head or plate (outline first, shine last),
# "handle" for the wood of a tool, "accent" for the binding of a spear. "armour" is the vanilla armour whose
# shape the tier wears.
TIERS = {
    "shard": dict(
        material="crystal_scale", boss="shard_dragon", drops=(6, 10), armour="diamond",
        metal=rgb("#160a2a", "#35186a", "#6a34b4", "#a468e6", "#d7a6f8", "#fff0ff"),
        handle=rgb("#281e0b", "#493615", "#684e1e", "#896727"),
        accent=rgb("#5e1548", "#b0307f", "#ff74c4", "#ffc2e6")),
    "tempest": dict(
        material="tempest_scale", boss="tempest_wyvern", drops=(6, 10), armour="diamond",
        metal=rgb("#070f1c", "#152e4e", "#2a5b8c", "#4f93c8", "#96d0ee", "#f4fdff"),
        handle=rgb("#10141a", "#232b36", "#394554", "#536274"),
        accent=rgb("#6e5208", "#c79a16", "#ffe256", "#fff8b8")),
    "behemoth": dict(
        material="behemoth_plate", boss="bastion_behemoth", drops=(7, 11), armour="netherite",
        metal=rgb("#0d0807", "#2a1610", "#562616", "#96441a", "#de8426", "#ffdc82"),
        handle=rgb("#0c0908", "#1e1512", "#33241d", "#4b362b"),
        accent=rgb("#4f1006", "#a0260c", "#e4441e", "#ff9050")),
    "void": dict(
        material="void_ingot", boss="void_sovereign", drops=(8, 12), armour="netherite",
        metal=rgb("#040207", "#100820", "#22103e", "#3e1c70", "#7636ba", "#e692ff"),
        handle=rgb("#06040a", "#120c1c", "#20162e", "#322446"),
        accent=rgb("#560a44", "#a81a86", "#ff3cc8", "#ffa4e6")),
    "ultimate": dict(
        material="ultimate_alloy", boss=None, drops=None, armour="diamond",
        metal=rgb("#2a1604", "#70460e", "#bd8420", "#f2c440", "#fdec98", "#f0ffff"),
        handle=rgb("#5a4a2c", "#9c8858", "#d6c79a", "#f8f1da"),
        accent=rgb("#0a4f62", "#149cb8", "#5ae6ff", "#d0faff")),
}
BOSS_TIERS = ["shard", "tempest", "behemoth", "void"]
KINDS = ["sword", "pickaxe", "axe", "shovel", "hoe", "spear", "helmet", "chestplate", "leggings", "boots"]
TOOLS = KINDS[:6]
ARMOUR = KINDS[6:]
# The vanilla item each material's shape comes from, and how much of the tier's ramp it uses.
MATERIAL_SHAPES = {
    "crystal_scale": ("armadillo_scute", (0.18, 1.0)),
    "tempest_scale": ("turtle_scute", (0.2, 0.92)),
    "behemoth_plate": ("netherite_scrap", (0.08, 0.95)),
    "void_ingot": ("netherite_ingot", (0.04, 1.0)),
    "ultimate_alloy": ("netherite_ingot", (0.1, 1.0)),
}
# How much of the ramp the armour as worn uses. Diamond's is drawn without dark shades; it gets some depth here.
WORN_SPAN = {"diamond": (0.36, 1.0), "netherite": (0.06, 0.9)}
ICON_SPAN = {"diamond": (0.0, 1.0), "netherite": (0.0, 0.88)}
WORN_LAYERS = ["humanoid", "humanoid_leggings", "humanoid_baby"]


def write_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def write_png(path: Path, pixels) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = b"".join(b"\x00" + bytes(v for px in row for v in px) for row in pixels)

    def chunk(kind: bytes, body: bytes) -> bytes:
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", len(pixels[0]), len(pixels), 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def read_png(data: bytes):
    """Rows of (r, g, b, a) from a PNG of any colour type, up to 8 bits a sample, not interlaced."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    pos, idat, plte, trns = 8, b"", None, None
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, depth, colour, _, _, interlace = struct.unpack(">IIBBBBB", body)
        elif kind == b"PLTE":
            plte = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b"tRNS":
            trns = body
        elif kind == b"IDAT":
            idat += body
    if interlace or depth > 8:
        raise ValueError("interlaced or 16-bit PNGs are not read")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    step = max(1, channels * depth // 8)
    stride = (width * channels * depth + 7) // 8
    raw = zlib.decompress(idat)
    rows, before, at = [], bytearray(stride), 0
    for _ in range(height):
        kind, line = raw[at], bytearray(raw[at + 1:at + 1 + stride])
        at += 1 + stride
        for x in range(stride):
            a = line[x - step] if x >= step else 0
            b = before[x]
            c = before[x - step] if x >= step else 0
            if kind == 1:
                line[x] = (line[x] + a) & 255
            elif kind == 2:
                line[x] = (line[x] + b) & 255
            elif kind == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif kind == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        before = line
        if depth < 8:
            per, mask = 8 // depth, (1 << depth) - 1
            samples = [(line[k // per] >> (8 - depth * (k % per + 1))) & mask for k in range(width * channels)]
        else:
            samples = line
        row = []
        for x in range(width):
            s = samples[x * channels:(x + 1) * channels]
            if colour == 6:
                row.append((s[0], s[1], s[2], s[3]))
            elif colour == 2:
                clear = trns is not None and len(trns) >= 6 and (s[0], s[1], s[2]) == (trns[1], trns[3], trns[5])
                row.append((s[0], s[1], s[2], 0 if clear else 255))
            elif colour == 3:
                row.append((*plte[s[0]], trns[s[0]] if trns is not None and s[0] < len(trns) else 255))
            else:
                grey = s[0] * 255 // ((1 << depth) - 1)
                if colour == 4:
                    row.append((grey, grey, grey, s[1]))
                else:
                    clear = trns is not None and len(trns) >= 2 and s[0] == ((trns[0] << 8) | trns[1])
                    row.append((grey, grey, grey, 0 if clear else 255))
        rows.append(row)
    return rows


# ---- Recolouring the vanilla shapes ------------------------------------------------------------------

def find_client_jar() -> Path | None:
    """The Minecraft client jar: --client-jar, MINECRAFT_CLIENT_JAR, or the one Loom downloaded for this project."""
    named = [sys.argv[i + 1] for i, a in enumerate(sys.argv[:-1]) if a == "--client-jar"]
    if os.environ.get("MINECRAFT_CLIENT_JAR"):
        named.append(os.environ["MINECRAFT_CLIENT_JAR"])
    version = None
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("minecraft_version="):
            version = line.split("=", 1)[1].strip()
    candidates = [Path(p) for p in named]
    if version:
        home = Path(os.environ.get("GRADLE_USER_HOME") or Path.home() / ".gradle")
        candidates.append(home / "caches" / "fabric-loom" / version / "minecraft-client.jar")
        candidates += sorted((ROOT / ".gradle" / "loom-cache" / "minecraftMaven" / "net" / "minecraft").glob(f"minecraft-merged-*/{version}/*.jar"))
    for jar in candidates:
        if jar.is_file():
            with zipfile.ZipFile(jar) as archive:
                if VANILLA + "item/diamond_sword.png" in archive.namelist():
                    return jar
    return None


def lum(colour) -> float:
    return 0.299 * colour[0] + 0.587 * colour[1] + 0.114 * colour[2]


def ramp(stops, t: float):
    """The colour a share t (0 dark, 1 light) of the way up a ramp of evenly spaced stops."""
    t = min(1.0, max(0.0, t)) * (len(stops) - 1)
    i = min(int(t), len(stops) - 2)
    return tuple(round(a + (b - a) * (t - i)) for a, b in zip(stops[i], stops[i + 1]))


def part_of_tool(colour) -> str:
    """Which part of a vanilla diamond tool a pixel belongs to, by its hue: teal metal, brown wood, red binding."""
    r, g, _b = colour[:3]
    if g >= r:
        return "metal"
    return "handle" if g > 0.62 * r else "accent"


def all_metal(_colour) -> str:
    return "metal"


def spread(shapes, part_of):
    """The darkest and lightest shade of each part across a family of textures, which all share one mapping."""
    found = {}
    for rows in shapes:
        for row in rows:
            for colour in row:
                if colour[3]:
                    part, light = part_of(colour), lum(colour)
                    low, high = found.get(part, (light, light))
                    found[part] = (min(low, light), max(high, light))
    return found


def recolour(rows, colours, part_of, shades, span=(0.0, 1.0)):
    """
    The same picture in a tier's colours: each pixel keeps its place between its part's darkest and lightest shade,
    and takes the colour that far up the tier's ramp for that part (within span for the metal).
    """
    out = []
    for row in rows:
        line = []
        for colour in row:
            if not colour[3]:
                line.append((0, 0, 0, 0))
                continue
            part = part_of(colour)
            low, high = shades[part]
            t = (lum(colour) - low) / (high - low) if high > low else 1.0
            if part == "metal":
                t = span[0] + (span[1] - span[0]) * t
            line.append((*ramp(colours[part], t), colour[3]))
        out.append(line)
    return out


def alloy(rows, shades, span):
    """The ultimate alloy: an ingot that runs through the four bosses' colours from one end to the other, edged in gold."""
    order = ["behemoth", "shard", "void", "tempest"]    # by hue, so each colour runs cleanly into the next
    xs = [x for row in rows for x, colour in enumerate(row) if colour[3]]
    left, right = min(xs), max(xs)
    out = []
    for row in rows:
        line = []
        for x, colour in enumerate(row):
            if not colour[3]:
                line.append((0, 0, 0, 0))
                continue
            low, high = shades["metal"]
            t = span[0] + (span[1] - span[0]) * ((lum(colour) - low) / (high - low))
            along = (x - left) / (right - left) * (len(order) - 1)
            i = min(int(along), len(order) - 2)
            a, b = (ramp(TIERS[order[k]]["metal"], 0.45 + 0.55 * t) for k in (i, i + 1))
            mixed = tuple(round(p + (q - p) * (along - i)) for p, q in zip(a, b))
            gold = ramp(TIERS["ultimate"]["metal"], t)
            # The darkest shades (the outline) and the brightest (the shine) are the ultimate gold.
            weight = 1.0 if t < 0.3 else 0.0 if t < 0.8 else (t - 0.8) / 0.2
            line.append((*(round(m + (g - m) * weight) for m, g in zip(mixed, gold)), colour[3]))
        out.append(line)
    return out


def write_textures(jar: Path) -> None:
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())

        def vanilla(path: str):
            return read_png(archive.read(VANILLA + path + ".png"))

        tools = {kind: vanilla(f"item/diamond_{kind}") for kind in TOOLS}
        tools["spear_in_hand"] = vanilla("item/diamond_spear_in_hand")
        icons = {shape: {kind: vanilla(f"item/{shape}_{kind}") for kind in ARMOUR} for shape in ("diamond", "netherite")}
        worn = {shape: {layer: vanilla(f"entity/equipment/{layer}/{shape}") for layer in WORN_LAYERS
                        if VANILLA + f"entity/equipment/{layer}/{shape}.png" in names} for shape in ("diamond", "netherite")}
        materials = {name: vanilla(f"item/{shape}") for name, (shape, _span) in MATERIAL_SHAPES.items()}

    # The diamond tools and armour icons share one palette in vanilla, so they share one mapping here.
    diamond_shades = spread(list(tools.values()) + list(icons["diamond"].values()), part_of_tool)
    icon_shades = {"diamond": diamond_shades, "netherite": spread(icons["netherite"].values(), all_metal)}
    icon_part = {"diamond": part_of_tool, "netherite": all_metal}
    worn_shades = {shape: spread(layers.values(), all_metal) for shape, layers in worn.items()}

    items = ASSETS / "textures" / "item"
    for tier, colours in TIERS.items():
        for kind in TOOLS:
            write_png(items / f"{tier}_{kind}.png", recolour(tools[kind], colours, part_of_tool, diamond_shades))
        write_png(items / f"{tier}_spear_in_hand.png", recolour(tools["spear_in_hand"], colours, part_of_tool, diamond_shades))
        shape = colours["armour"]
        for kind in ARMOUR:
            write_png(items / f"{tier}_{kind}.png",
                      recolour(icons[shape][kind], colours, icon_part[shape], icon_shades[shape], ICON_SPAN[shape]))
        for layer, rows in worn[shape].items():
            write_png(ASSETS / "textures" / "entity" / "equipment" / layer / f"{tier}.png",
                      recolour(rows, colours, all_metal, worn_shades[shape], WORN_SPAN[shape]))
        material = colours["material"]
        rows, span = materials[material], MATERIAL_SHAPES[material][1]
        shades = spread([rows], all_metal)
        write_png(items / f"{material}.png", alloy(rows, shades, span) if tier == "ultimate" else recolour(rows, colours, all_metal, shades, span))


def main() -> None:
    jar = find_client_jar()
    if jar:
        write_textures(jar)
    else:
        print("no Minecraft client jar found (run a Gradle build once, or pass --client-jar): textures left as they are")
    worn_layers = [layer for layer in WORN_LAYERS
                   if (ASSETS / "textures" / "entity" / "equipment" / layer / "shard.png").is_file()]

    lang_ko, lang_en = {}, {}
    ko_tier = {"shard": "파편", "tempest": "폭풍", "behemoth": "거수", "void": "공허", "ultimate": "최종"}
    en_tier = {"shard": "Shard", "tempest": "Tempest", "behemoth": "Behemoth", "void": "Void", "ultimate": "Ultimate"}
    ko_kind = {"sword": "검", "pickaxe": "곡괭이", "axe": "도끼", "shovel": "삽", "hoe": "괭이", "spear": "창",
               "helmet": "투구", "chestplate": "흉갑", "leggings": "레깅스", "boots": "부츠"}
    en_kind = {"sword": "Sword", "pickaxe": "Pickaxe", "axe": "Axe", "shovel": "Shovel", "hoe": "Hoe", "spear": "Spear",
               "helmet": "Helmet", "chestplate": "Chestplate", "leggings": "Leggings", "boots": "Boots"}
    ko_material = {"crystal_scale": "수정 비늘", "tempest_scale": "폭풍 비늘", "behemoth_plate": "거수 장갑판", "void_ingot": "공허 주괴",
                   "ultimate_alloy": "최종 합금"}
    en_material = {"crystal_scale": "Crystal Scale", "tempest_scale": "Tempest Scale", "behemoth_plate": "Behemoth Plate", "void_ingot": "Void Ingot",
                   "ultimate_alloy": "Ultimate Alloy"}

    tool_tags = {"sword": "swords", "pickaxe": "pickaxes", "axe": "axes", "shovel": "shovels", "hoe": "hoes", "spear": "spears",
                 "helmet": "head_armor", "chestplate": "chest_armor", "leggings": "leg_armor", "boots": "foot_armor"}
    tag_values = {tag: [] for tag in tool_tags.values()}

    for tier, colours in TIERS.items():
        material = colours["material"]
        write_json(ASSETS / "models" / "item" / f"{material}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{material}"}})
        write_json(ASSETS / "items" / f"{material}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{material}"}})
        lang_ko[f"item.{NS}.{material}"] = ko_material[material]
        lang_en[f"item.{NS}.{material}"] = en_material[material]
        write_json(DATA / "tags" / "item" / f"repairs_{tier}_gear.json", {"values": [f"{NS}:{material}"]})
        for kind in KINDS:
            name = f"{tier}_{kind}"
            parent = "minecraft:item/handheld" if kind in TOOLS and kind != "spear" else "minecraft:item/generated"
            write_json(ASSETS / "models" / "item" / f"{name}.json", {"parent": parent, "textures": {"layer0": f"{NS}:item/{name}"}})
            if kind == "spear":
                # Like the vanilla spears: the small icon in menus, a long one in the hand.
                write_json(ASSETS / "models" / "item" / f"{name}_in_hand.json",
                           {"parent": "minecraft:item/spear_in_hand", "textures": {"layer0": f"{NS}:item/{name}_in_hand"}})
                write_json(ASSETS / "items" / f"{name}.json", {"model": {
                    "type": "minecraft:select", "property": "minecraft:display_context",
                    "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"],
                               "model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}}],
                    "fallback": {"type": "minecraft:model", "model": f"{NS}:item/{name}_in_hand"}},
                    "swap_animation_scale": 1.95})
            else:
                write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
            lang_ko[f"item.{NS}.{name}"] = f"{ko_tier[tier]} {ko_kind[kind]}"
            lang_en[f"item.{NS}.{name}"] = f"{en_tier[tier]} {en_kind[kind]}"
            tag_values[tool_tags[kind]].append(f"{NS}:{name}")
        # Armour as worn.
        write_json(ASSETS / "equipment" / f"{tier}.json",
                   {"layers": {layer: [{"texture": f"{NS}:{tier}"}] for layer in worn_layers}})

    # Vanilla tags: what makes each piece a sword, pickaxe, helmet... for enchanting and for what tools do.
    for tag, values in tag_values.items():
        path = RES / "data" / "minecraft" / "tags" / "item" / f"{tag}.json"
        existing = json.loads(path.read_text(encoding="utf-8")).get("values", []) if path.exists() else []
        write_json(path, {"replace": False, "values": existing + [v for v in values if v not in existing]})

    # Recipes: the vanilla shapes with the tier's material; the ultimate alloy from one of each boss's material.
    shapes = {
        "sword": ["X", "X", "#"], "pickaxe": ["XXX", " # ", " # "], "axe": ["XX", "X#", " #"], "shovel": ["X", "#", "#"],
        "hoe": ["XX", " #", " #"], "spear": ["  X", " # ", "#  "],
        "helmet": ["XXX", "X X"], "chestplate": ["X X", "XXX", "XXX"], "leggings": ["XXX", "X X", "X X"], "boots": ["X X", "X X"],
    }
    for tier in TIERS:
        material = TIERS[tier]["material"]
        for kind, pattern in shapes.items():
            key = {"X": f"{NS}:{material}"}
            if "#" in "".join(pattern):
                key["#"] = "minecraft:stick"
            write_json(DATA / "recipe" / f"{tier}_{kind}.json", {
                "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern, "key": key,
                "result": {"id": f"{NS}:{tier}_{kind}", "count": 1}})
    write_json(DATA / "recipe" / "ultimate_alloy.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [f"{NS}:{TIERS[tier]['material']}" for tier in BOSS_TIERS],
        "result": {"id": f"{NS}:ultimate_alloy", "count": 1}})

    # Each boss drops its material, a few at a time.
    for tier in BOSS_TIERS:
        material, boss, (low, high) = TIERS[tier]["material"], TIERS[tier]["boss"], TIERS[tier]["drops"]
        path = DATA / "loot_table" / "entities" / f"{boss}.json"
        table = json.loads(path.read_text(encoding="utf-8"))
        table["pools"] = [p for p in table["pools"] if not any(e.get("name") == f"{NS}:{material}" for e in p.get("entries", []))]
        table["pools"].append({"rolls": 1, "entries": [{
            "type": "minecraft:item", "name": f"{NS}:{material}",
            "modifier": {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}}}]})
        write_json(path, table)

    lang_ko.update({
        "tooltip.boss_mode.gear.set": "4부위 세트 효과:",
        "tooltip.boss_mode.gear.set.shard": " 화염 저항, 근접 공격자에게 피해 2 반사",
        "tooltip.boss_mode.gear.set.tempest": " 이동 속도 +10%, 낙하 피해 절반",
        "tooltip.boss_mode.gear.set.behemoth": " 폭발 피해 -30%",
        "tooltip.boss_mode.gear.set.void": " 최대 체력 +4, 위더 면역",
        "tooltip.boss_mode.gear.set.ultimate": " 네 보스 세트 효과 모두, 최대 체력 +8",
        "tooltip.boss_mode.gear.hit.shard": "적중 시: 대상에 불 3초",
        "tooltip.boss_mode.gear.hit.tempest": "적중 시: 10% 확률로 번개",
        "tooltip.boss_mode.gear.hit.behemoth": "적중 시: 강하게 밀쳐냄",
        "tooltip.boss_mode.gear.hit.void": "적중 시: 위더 2초",
        "tooltip.boss_mode.gear.hit.ultimate": "적중 시: 불, 번개, 밀쳐내기, 위더 모두",
    })
    lang_en.update({
        "tooltip.boss_mode.gear.set": "Full set of four:",
        "tooltip.boss_mode.gear.set.shard": " Fire resistance, 2 damage back to melee attackers",
        "tooltip.boss_mode.gear.set.tempest": " 10% faster, half fall damage",
        "tooltip.boss_mode.gear.set.behemoth": " 30% less explosion damage",
        "tooltip.boss_mode.gear.set.void": " 4 more max health, immune to wither",
        "tooltip.boss_mode.gear.set.ultimate": " All four bosses' set effects, 8 more max health",
        "tooltip.boss_mode.gear.hit.shard": "On hit: sets the target on fire for 3 s",
        "tooltip.boss_mode.gear.hit.tempest": "On hit: 10% chance of lightning",
        "tooltip.boss_mode.gear.hit.behemoth": "On hit: knocks the target far back",
        "tooltip.boss_mode.gear.hit.void": "On hit: wither for 2 s",
        "tooltip.boss_mode.gear.hit.ultimate": "On hit: fire, lightning, knockback and wither",
    })
    for name, entries in (("ko_kr", lang_ko), ("en_us", lang_en)):
        path = ASSETS / "lang" / f"{name}.json"
        language = json.loads(path.read_text(encoding="utf-8"))
        language.update(entries)
        for retired in ("tooltip.boss_mode.gear.forge", "tooltip.boss_mode.gear.ultimate"):
            language.pop(retired, None)
        write_json(path, language)
    print("boss gear generated")


if __name__ == "__main__":
    main()
