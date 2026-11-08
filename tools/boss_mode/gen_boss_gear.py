#!/usr/bin/env python3
"""Boss gear: item and armour textures, models, equipment, recipes, tags, boss drops and names.

Standard library only. Run from anywhere; writes under src/main/resources. The numbers live in
com.bossmode.gear.BossTier; this only makes the resources.
"""
from __future__ import annotations

import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src" / "main" / "resources"
NS = "boss_mode"
ASSETS = RES / "assets" / NS
DATA = RES / "data" / NS

# Tier: (material id, dark, mid, light, highlight, accent), boss loot table, drop range
TIERS = {
    "shard": ("crystal_scale", (64, 30, 96), (150, 86, 204), (206, 150, 240), (250, 228, 255), (255, 116, 196), "shard_dragon", (6, 10)),
    "tempest": ("tempest_scale", (30, 52, 74), (68, 128, 170), (128, 198, 228), (222, 248, 255), (255, 226, 86), "tempest_wyvern", (6, 10)),
    "behemoth": ("behemoth_plate", (38, 28, 26), (112, 62, 30), (206, 118, 40), (255, 200, 96), (228, 68, 30), "bastion_behemoth", (7, 11)),
    "void": ("void_ingot", (16, 10, 26), (58, 26, 84), (112, 52, 150), (198, 122, 255), (255, 60, 200), "void_sovereign", (8, 12)),
    "ultimate": (None, (92, 62, 12), (196, 148, 40), (248, 210, 90), (255, 250, 222), (90, 230, 255), None, None),
}
BOSS_TIERS = ["shard", "tempest", "behemoth", "void"]
KINDS = ["sword", "pickaxe", "axe", "shovel", "hoe", "spear", "helmet", "chestplate", "leggings", "boots"]
TOOLS = KINDS[:6]
OUTLINE = (20, 14, 22)
HANDLE = (112, 78, 44)
HANDLE_DARK = (74, 50, 28)
GOLD_HANDLE = (214, 168, 60)
GOLD_HANDLE_DARK = (150, 108, 30)

ICONS = {
    "sword": [
        "..............OO",
        ".............O4O",
        "............O43O",
        "...........O432O",
        "..........O432O.",
        ".........O432O..",
        "........O432O...",
        ".......O432O....",
        "..OO..O432O.....",
        "..OAOO432O......",
        "...OAO32O.......",
        "....OAOO........",
        "...OHOAO........",
        "..OHO.OAO.......",
        ".OhO...OO.......",
        ".OO.............",
    ],
    "pickaxe": [
        "................",
        "....OOOOOO......",
        "...O344433OO....",
        "..O32OOOO2443O..",
        "..O2O...OOH233O.",
        "..OO....OHO.O32O",
        ".......OHO...O2O",
        "......OHO....O2O",
        ".....OHO.....OO.",
        "....OHO.........",
        "...OHO..........",
        "..OHO...........",
        ".OhO............",
        "OhO.............",
        "OO..............",
        "................",
    ],
    "axe": [
        "................",
        ".....OOOO.......",
        "....O3443O......",
        "...O344432O.OO..",
        "...O34432OOOHO..",
        "...O3432OOOHO...",
        "...O332O.OHO....",
        "....OOO.OHO.....",
        ".......OHO......",
        "......OHO.......",
        ".....OHO........",
        "....OHO.........",
        "...OHO..........",
        "..OhO...........",
        ".OhO............",
        ".OO.............",
    ],
    "shovel": [
        "............OOO.",
        "...........O443O",
        "..........O4332O",
        "..........O3322O",
        "...........O22O.",
        "..........OHOO..",
        ".........OHO....",
        "........OHO.....",
        ".......OHO......",
        "......OHO.......",
        ".....OHO........",
        "....OHO.........",
        "...OhO..........",
        "..OhO...........",
        "..OO............",
        "................",
    ],
    "hoe": [
        "................",
        ".......OOOOO....",
        "......O34432O...",
        "......OOOOHO2O..",
        ".........OHO.OO.",
        "........OHO.....",
        ".......OHO......",
        "......OHO.......",
        ".....OHO........",
        "....OHO.........",
        "...OHO..........",
        "..OHO...........",
        ".OhO............",
        "OhO.............",
        "OO..............",
        "................",
    ],
    "spear": [
        "..............OO",
        ".............O4O",
        "............O43O",
        "...........O32O.",
        "..........OAOO..",
        ".........OHO....",
        "........OHO.....",
        ".......OHO......",
        "......OHO.......",
        ".....OHO........",
        "....OHO.........",
        "...OHO..........",
        "..OHO...........",
        ".OhO............",
        "OhO.............",
        "OO..............",
    ],
    "helmet": [
        "................",
        "................",
        "................",
        "....OOOOOOOO....",
        "...O34444433O...",
        "..O3443AA3322O..",
        "..O3OOOOOOOO2O..",
        "..O3O......O2O..",
        "..O2O......O1O..",
        "..OOO......OOO..",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    "chestplate": [
        "................",
        "..OOO......OOO..",
        ".O343O....O332O.",
        ".O3443OOOO3332O.",
        ".O344433333332O.",
        ".OO3443AA3332OO.",
        "..OO3433333322O.",
        "...O34433332O...",
        "...O34333322O...",
        "...O33AA3322O...",
        "...O33333222O...",
        "...O33332221O...",
        "...O32222211O...",
        "...OOOOOOOOOO...",
        "................",
        "................",
    ],
    "leggings": [
        "................",
        "...OOOOOOOOOO...",
        "...O34444332O...",
        "...O3AA33AA2O...",
        "...O34433322O...",
        "...O343OO322O...",
        "...O343OO322O...",
        "...O343OO322O...",
        "...O343OO322O...",
        "...O343OO321O...",
        "...O332OO221O...",
        "...O332OO221O...",
        "...OOOOOOOOOO...",
        "................",
        "................",
        "................",
    ],
    "boots": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "..OOOO....OOOO..",
        "..O34O....O32O..",
        "..O34O....O32O..",
        "..O34O....O32O..",
        "..OA3O....OA2O..",
        ".O3443O..O3322O.",
        "O344433OO3332221",
        "OOOOOOOOOOOOOOOO",
        "................",
        "................",
        "................",
    ],
    # Materials
    "crystal_scale": [
        "................",
        "......OOO.......",
        ".....O444O......",
        "....O34443O.....",
        "...O3444332O....",
        "...O344A332O....",
        "..O34443A322O...",
        "..O3443332221O..",
        "..O3433332221O..",
        "...O333322211O..",
        "...O33222111O...",
        "....O222111O....",
        ".....O2111O.....",
        "......OOOO......",
        "................",
        "................",
    ],
    "tempest_scale": [
        "................",
        ".....OOOOOO.....",
        "...OO344443OO...",
        "..O3444AA4433O..",
        "..O344AA44332O..",
        ".O3444AA443322O.",
        ".O344AAAA43322O.",
        ".O34443AA33322O.",
        ".O3443AA333222O.",
        "..O343A3332221O.",
        "..O33333322211O.",
        "...O332222211O..",
        "....OO22221OO...",
        "......OOOO......",
        "................",
        "................",
    ],
    "behemoth_plate": [
        "................",
        "................",
        ".OOOOOOOOOOOOOO.",
        ".O4443333333322O",
        ".O4A433333332A2O",
        ".O4433333333222O",
        ".O3333333332222O",
        ".O3333333322222O",
        ".O3333333222222O",
        ".O3333332222221O",
        ".O3A33322222A21O",
        ".O2222222222111O",
        ".OOOOOOOOOOOOOOO",
        "................",
        "................",
        "................",
    ],
    "void_ingot": [
        "................",
        "................",
        "................",
        "................",
        ".....OOOOOOOO...",
        "....O44443333O..",
        "...O444AA33332O.",
        "..O4443AA33322O.",
        ".OOOOOOOOO3322O.",
        ".O44333333O221O.",
        ".O3333AA332O1O..",
        ".O22222222211O..",
        ".OOOOOOOOOOOO...",
        "................",
        "................",
        "................",
    ],
}


def write_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def write_png(path: Path, width: int, height: int, pixels) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = b"".join(b"\x00" + bytes(v for px in row for v in px) for row in pixels)

    def chunk(kind: bytes, body: bytes) -> bytes:
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def palette(tier: str) -> dict:
    _, dark, mid, light, high, accent, _, _ = TIERS[tier]
    gold = tier == "ultimate"
    return {"O": OUTLINE, "1": dark, "2": mid, "3": light, "4": high, "A": accent,
            "H": GOLD_HANDLE if gold else HANDLE, "h": GOLD_HANDLE_DARK if gold else HANDLE_DARK}


def icon(rows, colors):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), rows
    return [[(*colors[c], 255) if c in colors else (0, 0, 0, 0) for c in row] for row in rows]


# ---- Armour as worn: 64 x 32, the vanilla humanoid layout ------------------------------------------

def spear_in_hand(colors):
    """32 x 32: a long shaft from the bottom left, the head at the top right, outlined."""
    grid = [[None] * 32 for _ in range(32)]
    for t in range(22):
        x, y = 2 + t, 29 - t
        grid[y][x] = "h" if t < 4 else "H"
        grid[y][x + 1] = "h"
    grid[8][23], grid[8][24], grid[7][23] = "A", "A", "A"
    for k in range(7):
        x, y = 24 + k, 7 - k
        if 0 <= y < 32 and x < 32:
            grid[y][x] = "3" if k < 5 else "4"
            if 1 <= k <= 4:
                grid[y - 1][x] = "4"
                grid[y][x + 1] = "2"
            if 2 <= k <= 3:
                grid[y - 1][x - 1] = "4"
                grid[y + 1][x + 1] = "1"
    out = [[(0, 0, 0, 0)] * 32 for _ in range(32)]
    for y in range(32):
        for x in range(32):
            if grid[y][x]:
                out[y][x] = (*colors[grid[y][x]], 255)
            elif any(0 <= y + dy < 32 and 0 <= x + dx < 32 and grid[y + dy][x + dx]
                     for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y][x] = (*OUTLINE, 255)
    return out


def armour_layer(tier: str, leggings: bool):
    _, dark, mid, light, high, accent, _, _ = TIERS[tier]
    px = [[(0, 0, 0, 0)] * 64 for _ in range(32)]

    def box(x0, y0, x1, y1, base):
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[y][x] = (*base, 255)

    def shade_box(x0, y0, x1, y1):
        """A face: light at the top, dark at the bottom, an outline, and an accent stripe through it."""
        height = y1 - y0
        for y in range(y0, y1):
            t = (y - y0) / max(1, height - 1)
            base = high if t < 0.15 else light if t < 0.45 else mid if t < 0.8 else dark
            for x in range(x0, x1):
                px[y][x] = (*base, 255)
        for x in range(x0, x1):
            px[y0][x] = (*OUTLINE, 255) if (x - x0) % 7 == 0 else px[y0][x]
            px[y1 - 1][x] = (*dark, 255)
        for y in range(y0, y1):
            px[y][x0] = (*dark, 255)
            px[y][x1 - 1] = (*dark, 255)

    if not leggings:
        # Head: every face of the helmet, the front open for the face.
        for x0, y0, x1, y1 in ((8, 0, 16, 8), (16, 0, 24, 8), (0, 8, 8, 16), (8, 8, 16, 16), (16, 8, 24, 16), (24, 8, 32, 16)):
            shade_box(x0, y0, x1, y1)
        for y in range(11, 16):
            for x in range(10, 14):
                px[y][x] = (0, 0, 0, 0)
        for x in range(9, 15):
            px[9][x] = (*accent, 255)
        # Body and arms: the chestplate.
        for x0, y0, x1, y1 in ((20, 16, 28, 20), (16, 20, 20, 32), (20, 20, 28, 32), (28, 20, 32, 32), (32, 20, 40, 32),
                               (44, 16, 48, 20), (48, 16, 52, 20), (40, 20, 44, 32), (44, 20, 48, 32), (48, 20, 52, 32), (52, 20, 56, 32)):
            shade_box(x0, y0, x1, y1)
        for y in range(22, 28):
            px[y][23] = (*accent, 255)
            px[y][24] = (*accent, 255)
        # Legs: only the boots, at the bottom.
        for x0, x1 in ((0, 4), (4, 8), (8, 12), (12, 16)):
            shade_box(x0, 26, x1, 32)
    else:
        # Leggings: the waist of the body and the whole of the legs.
        for x0, y0, x1, y1 in ((16, 26, 20, 32), (20, 26, 28, 32), (28, 26, 32, 32), (32, 26, 40, 32),
                               (4, 16, 8, 20), (8, 16, 12, 20), (0, 20, 4, 32), (4, 20, 8, 32), (8, 20, 12, 32), (12, 20, 16, 32)):
            shade_box(x0, y0, x1, y1)
        for x in range(20, 28):
            px[26][x] = (*accent, 255)
    return px


def main() -> None:
    lang_ko, lang_en = {}, {}
    ko_tier = {"shard": "파편", "tempest": "폭풍", "behemoth": "거수", "void": "공허", "ultimate": "최종"}
    en_tier = {"shard": "Shard", "tempest": "Tempest", "behemoth": "Behemoth", "void": "Void", "ultimate": "Ultimate"}
    ko_kind = {"sword": "검", "pickaxe": "곡괭이", "axe": "도끼", "shovel": "삽", "hoe": "괭이", "spear": "창",
               "helmet": "투구", "chestplate": "흉갑", "leggings": "레깅스", "boots": "부츠"}
    en_kind = {"sword": "Sword", "pickaxe": "Pickaxe", "axe": "Axe", "shovel": "Shovel", "hoe": "Hoe", "spear": "Spear",
               "helmet": "Helmet", "chestplate": "Chestplate", "leggings": "Leggings", "boots": "Boots"}
    ko_material = {"crystal_scale": "수정 비늘", "tempest_scale": "폭풍 비늘", "behemoth_plate": "거수 장갑판", "void_ingot": "공허 주괴"}
    en_material = {"crystal_scale": "Crystal Scale", "tempest_scale": "Tempest Scale", "behemoth_plate": "Behemoth Plate", "void_ingot": "Void Ingot"}

    tool_tags = {"sword": "swords", "pickaxe": "pickaxes", "axe": "axes", "shovel": "shovels", "hoe": "hoes", "spear": "spears",
                 "helmet": "head_armor", "chestplate": "chest_armor", "leggings": "leg_armor", "boots": "foot_armor"}
    tag_values = {tag: [] for tag in tool_tags.values()}

    for tier, (material, *_rest) in TIERS.items():
        colors = palette(tier)
        if material:
            write_png(ASSETS / "textures" / "item" / f"{material}.png", 16, 16, icon(ICONS[material], colors))
            write_json(ASSETS / "models" / "item" / f"{material}.json",
                       {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{material}"}})
            write_json(ASSETS / "items" / f"{material}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{material}"}})
            lang_ko[f"item.{NS}.{material}"] = ko_material[material]
            lang_en[f"item.{NS}.{material}"] = en_material[material]
            write_json(DATA / "tags" / "item" / f"repairs_{tier}_gear.json", {"values": [f"{NS}:{material}"]})
        else:
            write_json(DATA / "tags" / "item" / f"repairs_{tier}_gear.json", {"values": [f"{NS}:void_ingot"]})
        for kind in KINDS:
            name = f"{tier}_{kind}"
            write_png(ASSETS / "textures" / "item" / f"{name}.png", 16, 16, icon(ICONS[kind], colors))
            parent = "minecraft:item/handheld" if kind in TOOLS and kind != "spear" else "minecraft:item/generated"
            write_json(ASSETS / "models" / "item" / f"{name}.json", {"parent": parent, "textures": {"layer0": f"{NS}:item/{name}"}})
            if kind == "spear":
                # Like the vanilla spears: the small icon in menus, a long one in the hand.
                write_png(ASSETS / "textures" / "item" / f"{name}_in_hand.png", 32, 32, spear_in_hand(colors))
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
        write_png(ASSETS / "textures" / "entity" / "equipment" / "humanoid" / f"{tier}.png", 64, 32, armour_layer(tier, False))
        write_png(ASSETS / "textures" / "entity" / "equipment" / "humanoid_leggings" / f"{tier}.png", 64, 32, armour_layer(tier, True))
        write_json(ASSETS / "equipment" / f"{tier}.json", {"layers": {
            "humanoid": [{"texture": f"{NS}:{tier}"}],
            "humanoid_leggings": [{"texture": f"{NS}:{tier}"}],
        }})

    # Vanilla tags: what makes each piece a sword, pickaxe, helmet... for enchanting and for what tools do.
    for tag, values in tag_values.items():
        path = RES / "data" / "minecraft" / "tags" / "item" / f"{tag}.json"
        existing = json.loads(path.read_text(encoding="utf-8")).get("values", []) if path.exists() else []
        write_json(path, {"replace": False, "values": existing + [v for v in values if v not in existing]})

    # Recipes: the vanilla shapes with the boss's material; the ultimate piece from one of each boss's.
    shapes = {
        "sword": ["X", "X", "#"], "pickaxe": ["XXX", " # ", " # "], "axe": ["XX", "X#", " #"], "shovel": ["X", "#", "#"],
        "hoe": ["XX", " #", " #"], "spear": ["  X", " # ", "#  "],
        "helmet": ["XXX", "X X"], "chestplate": ["X X", "XXX", "XXX"], "leggings": ["XXX", "X X", "X X"], "boots": ["X X", "X X"],
    }
    for tier in BOSS_TIERS:
        material = TIERS[tier][0]
        for kind, pattern in shapes.items():
            key = {"X": f"{NS}:{material}"}
            if "#" in "".join(pattern):
                key["#"] = "minecraft:stick"
            write_json(DATA / "recipe" / f"{tier}_{kind}.json", {
                "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern, "key": key,
                "result": {"id": f"{NS}:{tier}_{kind}", "count": 1}})
    for kind in KINDS:
        write_json(DATA / "recipe" / f"ultimate_{kind}.json", {
            "type": "minecraft:crafting_shapeless", "category": "equipment",
            "ingredients": [f"{NS}:{tier}_{kind}" for tier in BOSS_TIERS],
            "result": {"id": f"{NS}:ultimate_{kind}", "count": 1}})

    # Each boss drops its material, a few at a time.
    for tier in BOSS_TIERS:
        material, *_c, boss, (low, high) = TIERS[tier]
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
        "tooltip.boss_mode.gear.forge": "네 보스의 같은 장비를 합치면 최종 장비가 됩니다",
        "tooltip.boss_mode.gear.ultimate": "넣은 장비의 인챈트 중 가장 높은 레벨을 이어받습니다",
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
        "tooltip.boss_mode.gear.forge": "One of each boss's pieces of this kind forges the ultimate one",
        "tooltip.boss_mode.gear.ultimate": "Keeps the highest level of each enchantment on the pieces it was forged from",
    })
    for name, entries in (("ko_kr", lang_ko), ("en_us", lang_en)):
        path = ASSETS / "lang" / f"{name}.json"
        language = json.loads(path.read_text(encoding="utf-8"))
        language.update(entries)
        write_json(path, language)
    print("boss gear generated")


if __name__ == "__main__":
    main()
