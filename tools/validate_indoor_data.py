#!/usr/bin/env python3
"""Проверяет assets JSON, SVG viewBox/ID, полигоны, граф и список кабинетов."""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
import xml.etree.ElementTree as ET
from collections import deque
from pathlib import Path

NUMBER_RE = re.compile(r"[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?")
# Эти зоны являются открытыми проходами либо частью узла перехода, а не закрытой комнатой.
OPEN_ROUTE_ROLES = {"hall", "entrance", "stairs", "elevator", "restroom", "restricted"}
# На исходном плане лестничный узел 1 этажа частично совпадает с контуром кабинета 149.
# Исключение оставлено явным и требует проверки по фактическому плану/на месте.
KNOWN_ROOM_EDGE_OVERLAPS = {
    ("room_149", frozenset(("1_corridor_central", "1_stairs_central"))),
}
EXPECTED_OFFICIAL_NUMBERS = {
    "104", "113", "114", "115", "118", "119", "120", "126", "138", "139",
    "141", "142", "144", "147", "148", "149", "159", "160", "161",
    "202", "207", "209", "211", "213", "214", "217", "222", "224", "225",
    "228", "229", "230", "232", "233",
}


def inside_box(x: float, y: float, floor: dict, tolerance: float = 0.01) -> bool:
    return (
        floor["viewBoxX"] - tolerance <= x <= floor["viewBoxX"] + floor["width"] + tolerance
        and floor["viewBoxY"] - tolerance <= y <= floor["viewBoxY"] + floor["height"] + tolerance
    )


def point_in_polygon(x: float, y: float, polygon: list[dict]) -> bool:
    inside = False
    previous = polygon[-1]
    for current in polygon:
        cross = (x - previous["x"]) * (current["y"] - previous["y"]) - (y - previous["y"]) * (current["x"] - previous["x"])
        on_segment = (
            abs(cross) <= 1e-7
            and min(previous["x"], current["x"]) <= x <= max(previous["x"], current["x"])
            and min(previous["y"], current["y"]) <= y <= max(previous["y"], current["y"])
        )
        if on_segment:
            return True
        if (current["y"] > y) != (previous["y"] > y):
            crossing_x = (previous["x"] - current["x"]) * (y - current["y"]) / (previous["y"] - current["y"]) + current["x"]
            if x < crossing_x:
                inside = not inside
        previous = current
    return inside


def segment_crosses_polygon(start: dict, end: dict, polygon: list[dict]) -> bool:
    length = math.hypot(end["x"] - start["x"], end["y"] - start["y"])
    samples = max(2, math.ceil(length * 4))
    return any(
        point_in_polygon(
            start["x"] + (end["x"] - start["x"]) * step / samples,
            start["y"] + (end["y"] - start["y"]) * step / samples,
            polygon,
        )
        for step in range(1, samples)
    )


def reachable(start: str, edges: list[dict], node_ids: set[str], include_accessible: bool) -> set[str]:
    adjacency: dict[str, list[str]] = {node_id: [] for node_id in node_ids}
    for edge in edges:
        is_accessible = edge.get("requiresAccessibility") is True or edge.get("type", "").lower() == "elevator"
        if is_accessible and not include_accessible:
            continue
        adjacency[edge["from"]].append(edge["to"])
        if edge.get("bidirectional") is not False:
            adjacency[edge["to"]].append(edge["from"])
    seen = {start}
    queue = deque([start])
    while queue:
        current = queue.popleft()
        for target in adjacency[current]:
            if target not in seen:
                seen.add(target)
                queue.append(target)
    return seen


def fail(message: str) -> None:
    raise ValueError(message)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets", type=Path, default=Path("app/src/main/assets"), help="Каталог Android assets")
    args = parser.parse_args()
    try:
        graph = json.loads((args.assets / "navigation_graph.json").read_text(encoding="utf-8"))
        catalog = json.loads((args.assets / "rooms.json").read_text(encoding="utf-8"))
        floors = graph["floors"]
        floor_by_number = {floor["number"]: floor for floor in floors}
        if len(floor_by_number) != len(floors):
            fail("Повторяются номера этажей")

        svg_ids: dict[int, set[str]] = {}
        for floor in floors:
            svg_path = args.assets / floor["mapAsset"]
            root = ET.parse(svg_path).getroot()
            raw_view_box = root.get("viewBox", "")
            svg_view_box = [float(value) for value in NUMBER_RE.findall(raw_view_box)]
            expected_view_box = [floor["viewBoxX"], floor["viewBoxY"], floor["width"], floor["height"]]
            if len(svg_view_box) != 4 or any(abs(a - b) > 0.01 for a, b in zip(svg_view_box, expected_view_box)):
                fail(f"{svg_path}: viewBox={raw_view_box!r}, JSON={expected_view_box}")
            ids = [element.get("id") for element in root.iter() if element.get("id")]
            if len(ids) != len(set(ids)):
                fail(f"{svg_path}: повторяются SVG ID")
            svg_ids[floor["number"]] = set(ids)

        nodes = graph["nodes"]
        node_by_id = {node["id"]: node for node in nodes}
        if len(node_by_id) != len(nodes):
            fail("Повторяются ID узлов")
        if not nodes:
            fail("Граф пуст")
        for node in nodes:
            floor = floor_by_number.get(node["floor"])
            if floor is None or not node.get("name") or not node.get("type"):
                fail(f"Некорректный узел {node.get('id')}")
            if not inside_box(node["x"], node["y"], floor):
                fail(f"Узел {node['id']} вне viewBox")

        edge_pairs: set[tuple[str, str, str]] = set()
        for edge in graph["edges"]:
            if edge["from"] not in node_by_id or edge["to"] not in node_by_id or edge["from"] == edge["to"]:
                fail(f"Неверная ссылка ребра {edge}")
            if not math.isfinite(edge["weight"]) or edge["weight"] <= 0:
                fail(f"Некорректный вес ребра {edge}")
            edge_pairs.add((edge["from"], edge["to"], edge.get("type", "")))

        areas = graph.get("restrictedAreas", [])
        for area in areas:
            floor = floor_by_number.get(area["floor"])
            if floor is None or not area.get("polygons"):
                fail(f"Некорректная закрытая область {area.get('id')}")
            for polygon in area["polygons"]:
                if len(polygon) < 3:
                    fail(f"У области {area['id']} менее трёх точек")
                for point in polygon:
                    if not inside_box(point["x"], point["y"], floor):
                        fail(f"Область {area['id']} выходит за viewBox")
                for node in nodes:
                    if node["floor"] == area["floor"] and point_in_polygon(node["x"], node["y"], polygon):
                        fail(f"Узел {node['id']} расположен внутри {area['id']}")
                for edge in graph["edges"]:
                    start, end = node_by_id[edge["from"]], node_by_id[edge["to"]]
                    if start["floor"] != area["floor"] or end["floor"] != area["floor"]:
                        continue
                    for step in range(1, 50):
                        ratio = step / 50
                        x = start["x"] + (end["x"] - start["x"]) * ratio
                        y = start["y"] + (end["y"] - start["y"]) * ratio
                        if point_in_polygon(x, y, polygon):
                            fail(f"Ребро {edge['from']}—{edge['to']} пересекает закрытую область {area['id']}")

        rooms = catalog["rooms"]
        room_by_id = {room["id"]: room for room in rooms}
        if len(room_by_id) != len(rooms):
            fail("Повторяются ID помещений")
        allowed_categories = {"classroom", "laboratory", "administrative", "medical", "common", "entrance"}
        official_numbers: set[str] = set()
        for room in rooms:
            floor = floor_by_number.get(room["floor"])
            if floor is None or any(not room.get(field) for field in ("name_ru", "name_en", "description_ru", "description_en")):
                fail(f"Нет этажа и полного RU/EN имени/описания у {room.get('id')}")
            if room.get("category") not in allowed_categories:
                fail(f"Неизвестная категория у {room['id']}: {room.get('category')}")
            for field in ("aliases_ru", "aliases_en"):
                if not isinstance(room.get(field), list):
                    fail(f"Поле {field} отсутствует/не список у {room['id']}")
            node_id = room.get("nodeId")
            if room.get("route_available"):
                if node_id not in node_by_id or node_by_id[node_id]["floor"] != room["floor"]:
                    fail(f"Маршрутизируемая комната {room['id']} не имеет узла на своём этаже")
                if room.get("x") is None or room.get("y") is None or not inside_box(room["x"], room["y"], floor):
                    fail(f"У маршрутизируемой комнаты {room['id']} нет корректных координат SVG")
            elif node_id is not None and (node_id not in node_by_id or node_by_id[node_id]["floor"] != room["floor"]):
                fail(f"Неверный необязательный узел у {room['id']}")
            x, y = room.get("x"), room.get("y")
            if (x is None) != (y is None):
                fail(f"Только одна координата задана у {room['id']}")
            if x is not None and not inside_box(x, y, floor):
                fail(f"Координата комнаты {room['id']} вне viewBox")
            polygons = room.get("polygons", [])
            legacy_polygon = room.get("polygon", [])
            if legacy_polygon:
                polygons = [*polygons, legacy_polygon]
            for polygon in polygons:
                if len(polygon) < 3:
                    fail(f"Контур {room['id']} содержит меньше трёх точек")
                for point in polygon:
                    if not inside_box(point["x"], point["y"], floor):
                        fail(f"Контур {room['id']} выходит за viewBox")
            element_ids = list(room.get("svgElementIds", []))
            if room.get("svgElementId"):
                element_ids.append(room["svgElementId"])
            missing_ids = [ident for ident in element_ids if ident not in svg_ids[room["floor"]]]
            if missing_ids:
                fail(f"У {room['id']} отсутствуют SVG ID: {missing_ids}")
            door_ids = list(room.get("doorNodeIds", []))
            if room.get("doorNodeId"):
                door_ids.append(room["doorNodeId"])
            for door_id in door_ids:
                if door_id not in node_by_id:
                    fail(f"У {room['id']} указан неизвестный door node {door_id}")
            raw_number = room.get("number")
            if raw_number:
                official_numbers.update(value for value in re.findall(r"\d+", raw_number) if value in EXPECTED_OFFICIAL_NUMBERS)
            official_numbers.update(value for value in room.get("aliases_ru", []) if value in EXPECTED_OFFICIAL_NUMBERS)

        # Проверяем рёбра коридора по контурам помещений: дверь — отдельный тип ребра,
        # поэтому обычный проход не должен срезать путь через учебную или служебную комнату.
        for edge in graph["edges"]:
            if edge.get("type", "").lower() != "corridor":
                continue
            start = node_by_id[edge["from"]]
            end = node_by_id[edge["to"]]
            if start["floor"] != end["floor"]:
                continue
            edge_nodes = frozenset((start["id"], end["id"]))
            for room in rooms:
                if room["floor"] != start["floor"] or room.get("role") in OPEN_ROUTE_ROLES:
                    continue
                if (room["id"], edge_nodes) in KNOWN_ROOM_EDGE_OVERLAPS:
                    continue
                polygons = list(room.get("polygons", []))
                if room.get("polygon"):
                    polygons.append(room["polygon"])
                for polygon in polygons:
                    if segment_crosses_polygon(start, end, polygon):
                        fail(f"Ребро коридора {start['id']}—{end['id']} проходит через помещение {room['id']}")

        missing_numbers = EXPECTED_OFFICIAL_NUMBERS - official_numbers
        extra_numbers = official_numbers - EXPECTED_OFFICIAL_NUMBERS
        if missing_numbers or extra_numbers:
            fail(f"Несовпадение утверждённых номеров: missing={sorted(missing_numbers)}, extra={sorted(extra_numbers)}")

        starts = [node["id"] for node in nodes if node.get("type") == "entrance"]
        if not starts:
            fail("Нет входного узла для проверки связности")
        for include_accessible in (False, True):
            all_reachable: set[str] = set()
            for start in starts:
                all_reachable |= reachable(start, graph["edges"], set(node_by_id), include_accessible)
            unreachable_rooms = [room["id"] for room in rooms if room.get("route_available") and room["nodeId"] not in all_reachable]
            if unreachable_rooms:
                mode = "доступный" if include_accessible else "обычный"
                fail(f"Недостижимы комнаты ({mode}): {unreachable_rooms}")

        print(f"OK: этажей={len(floors)}, помещений={len(rooms)}, узлов={len(nodes)}, рёбер={len(graph['edges'])}, закрытых областей={len(areas)}")
        print(f"Официальные номера проверены: {len(EXPECTED_OFFICIAL_NUMBERS)}")
        print("Неразмеченные/недоступные по геометрии:", ", ".join(room["id"] for room in rooms if not room.get("route_available")))
        return 0
    except (OSError, ET.ParseError, KeyError, TypeError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
