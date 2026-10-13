#!/usr/bin/env python3
"""Извлекает координаты room_*, door_* и restricted_* из SVG в системе viewBox.

Скрипт использует только стандартную библиотеку Python. Координаты элементов, вложенные
transform и популярные команды path преобразуются в абсолютные точки. Кривые Безье
аппроксимируются отрезками, поэтому для итоговой навигации точные проверенные полигоны
следует хранить в rooms.json / navigation_graph.json.
"""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

Number = float
Matrix = tuple[Number, Number, Number, Number, Number, Number]
IDENTITY: Matrix = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
NUMBER_RE = re.compile(r"[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?")
PATH_TOKEN_RE = re.compile(r"[A-Za-z]|[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?")
TRANSFORM_RE = re.compile(r"([A-Za-z]+)\s*\(([^)]*)\)")


@dataclass(frozen=True)
class Shape:
    element_id: str
    tag: str
    points: list[tuple[float, float]]
    closed: bool


def multiply(left: Matrix, right: Matrix) -> Matrix:
    """Складывает SVG affine-матрицы в порядке left * right."""
    a, b, c, d, e, f = left
    g, h, i, j, k, l = right
    return (
        a * g + c * h,
        b * g + d * h,
        a * i + c * j,
        b * i + d * j,
        a * k + c * l + e,
        b * k + d * l + f,
    )


def parse_transform(value: str | None) -> Matrix:
    matrix = IDENTITY
    if not value:
        return matrix
    for operation, raw_arguments in TRANSFORM_RE.findall(value):
        args = [float(number) for number in NUMBER_RE.findall(raw_arguments)]
        name = operation.lower()
        if name == "matrix" and len(args) == 6:
            local = tuple(args)  # type: ignore[assignment]
        elif name == "translate" and len(args) in (1, 2):
            local = (1.0, 0.0, 0.0, 1.0, args[0], args[1] if len(args) == 2 else 0.0)
        elif name == "scale" and len(args) in (1, 2):
            local = (args[0], 0.0, 0.0, args[1] if len(args) == 2 else args[0], 0.0, 0.0)
        elif name == "rotate" and len(args) in (1, 3):
            angle = math.radians(args[0])
            cosine, sine = math.cos(angle), math.sin(angle)
            rotation: Matrix = (cosine, sine, -sine, cosine, 0.0, 0.0)
            if len(args) == 3:
                cx, cy = args[1], args[2]
                local = multiply(multiply((1.0, 0.0, 0.0, 1.0, cx, cy), rotation),
                                 (1.0, 0.0, 0.0, 1.0, -cx, -cy))
            else:
                local = rotation
        elif name == "skewx" and len(args) == 1:
            local = (1.0, 0.0, math.tan(math.radians(args[0])), 1.0, 0.0, 0.0)
        elif name == "skewy" and len(args) == 1:
            local = (1.0, math.tan(math.radians(args[0])), 0.0, 1.0, 0.0, 0.0)
        else:
            raise ValueError(f"Неподдерживаемое SVG-преобразование: {operation}({raw_arguments})")
        matrix = multiply(matrix, local)
    return matrix


def apply_matrix(matrix: Matrix, point: tuple[float, float]) -> tuple[float, float]:
    a, b, c, d, e, f = matrix
    x, y = point
    return a * x + c * y + e, b * x + d * y + f


def parse_points(value: str) -> list[tuple[float, float]]:
    values = [float(number) for number in NUMBER_RE.findall(value)]
    if len(values) % 2:
        raise ValueError("У polygon должно быть чётное количество координат")
    return list(zip(values[::2], values[1::2]))


def _cubic(p0: tuple[float, float], p1: tuple[float, float], p2: tuple[float, float],
           p3: tuple[float, float], steps: int) -> list[tuple[float, float]]:
    result = []
    for step in range(1, steps + 1):
        t = step / steps
        u = 1.0 - t
        result.append((
            u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0],
            u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1],
        ))
    return result


def _quadratic(p0: tuple[float, float], p1: tuple[float, float], p2: tuple[float, float],
               steps: int) -> list[tuple[float, float]]:
    result = []
    for step in range(1, steps + 1):
        t = step / steps
        u = 1.0 - t
        result.append((u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0],
                       u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1]))
    return result


def parse_path(value: str, curve_steps: int = 8) -> tuple[list[tuple[float, float]], bool]:
    """Разбирает команды M/L/H/V/C/S/Q/T/Z; дуги A аппроксимирует хордой."""
    tokens = PATH_TOKEN_RE.findall(value)
    points: list[tuple[float, float]] = []
    current = (0.0, 0.0)
    subpath_start = current
    previous_control: tuple[float, float] | None = None
    previous_command = ""
    command = ""
    index = 0
    closed = False

    def is_command(token: str) -> bool:
        return len(token) == 1 and token.isalpha()

    def read_pair(position: int, relative: bool) -> tuple[tuple[float, float], int]:
        x, y = float(tokens[position]), float(tokens[position + 1])
        if relative:
            x += current[0]
            y += current[1]
        return (x, y), position + 2

    while index < len(tokens):
        if is_command(tokens[index]):
            command = tokens[index]
            index += 1
            if command in "Zz":
                current = subpath_start
                if points and points[-1] != current:
                    points.append(current)
                closed = True
                previous_control = None
                previous_command = command
                continue
        if not command:
            raise ValueError("SVG path начинается без команды")
        relative = command.islower()
        opcode = command.upper()
        if opcode == "M":
            current, index = read_pair(index, relative)
            points.append(current)
            subpath_start = current
            command = "l" if relative else "L"
            previous_control = None
        elif opcode == "L":
            current, index = read_pair(index, relative)
            points.append(current)
            previous_control = None
        elif opcode == "H":
            x = float(tokens[index]) + (current[0] if relative else 0.0)
            index += 1
            current = (x, current[1])
            points.append(current)
            previous_control = None
        elif opcode == "V":
            y = float(tokens[index]) + (current[1] if relative else 0.0)
            index += 1
            current = (current[0], y)
            points.append(current)
            previous_control = None
        elif opcode == "C":
            p0 = current
            control1, index = read_pair(index, relative)
            control2, index = read_pair(index, relative)
            endpoint, index = read_pair(index, relative)
            points.extend(_cubic(p0, control1, control2, endpoint, curve_steps))
            current, previous_control = endpoint, control2
        elif opcode == "S":
            p0 = current
            control1 = (2 * current[0] - previous_control[0], 2 * current[1] - previous_control[1]) \
                if previous_control is not None and previous_command.upper() in {"C", "S"} else current
            control2, index = read_pair(index, relative)
            endpoint, index = read_pair(index, relative)
            points.extend(_cubic(p0, control1, control2, endpoint, curve_steps))
            current, previous_control = endpoint, control2
        elif opcode == "Q":
            p0 = current
            control, index = read_pair(index, relative)
            endpoint, index = read_pair(index, relative)
            points.extend(_quadratic(p0, control, endpoint, curve_steps))
            current, previous_control = endpoint, control
        elif opcode == "T":
            p0 = current
            control = (2 * current[0] - previous_control[0], 2 * current[1] - previous_control[1]) \
                if previous_control is not None and previous_command.upper() in {"Q", "T"} else current
            endpoint, index = read_pair(index, relative)
            points.extend(_quadratic(p0, control, endpoint, curve_steps))
            current, previous_control = endpoint, control
        elif opcode == "A":
            # Дуга потребует отдельной эллиптической аппроксимации; её конец остаётся точным.
            if index + 7 > len(tokens):
                raise ValueError("В SVG-команде A недостаточно параметров")
            index += 5
            endpoint, index = read_pair(index, relative)
            points.append(endpoint)
            current, previous_control = endpoint, None
        else:
            raise ValueError(f"Неподдерживаемая команда SVG path: {command}")
        previous_command = command
    return points, closed


def element_points(element: ET.Element) -> tuple[list[tuple[float, float]], bool]:
    tag = element.tag.rsplit("}", 1)[-1]
    if tag == "rect":
        x, y = float(element.get("x", "0")), float(element.get("y", "0"))
        width, height = float(element.get("width", "0")), float(element.get("height", "0"))
        return [(x, y), (x + width, y), (x + width, y + height), (x, y + height)], True
    if tag == "polygon":
        return parse_points(element.get("points", "")), True
    if tag == "path":
        return parse_path(element.get("d", ""))
    return [], False


def walk_shapes(root: ET.Element) -> tuple[list[Shape], list[str]]:
    shapes: list[Shape] = []
    errors: list[str] = []

    def visit(element: ET.Element, parent_matrix: Matrix) -> None:
        try:
            matrix = multiply(parent_matrix, parse_transform(element.get("transform")))
            tag = element.tag.rsplit("}", 1)[-1]
            element_id = element.get("id", "")
            if element_id.startswith(("room_", "door_", "restricted_")) and tag in {"rect", "polygon", "path"}:
                points, closed = element_points(element)
                if len(points) < 3:
                    errors.append(f"{element_id}: контур пустой или содержит меньше трёх точек")
                else:
                    shapes.append(Shape(element_id, tag, [apply_matrix(matrix, point) for point in points], closed))
            for child in list(element):
                visit(child, matrix)
        except (ValueError, IndexError) as error:
            ident = element.get("id", "") or element.tag.rsplit("}", 1)[-1]
            errors.append(f"{ident}: {error}")

    visit(root, IDENTITY)
    return shapes, errors


def view_box(root: ET.Element) -> tuple[float, float, float, float] | None:
    raw = root.get("viewBox")
    if not raw:
        return None
    numbers = [float(item) for item in NUMBER_RE.findall(raw)]
    return tuple(numbers) if len(numbers) == 4 else None  # type: ignore[return-value]


def format_shape(shape: Shape) -> dict[str, object]:
    xs, ys = [point[0] for point in shape.points], [point[1] for point in shape.points]
    return {
        "id": shape.element_id,
        "tag": shape.tag,
        "closed": shape.closed,
        "bbox": {"minX": min(xs), "minY": min(ys), "maxX": max(xs), "maxY": max(ys)},
        "points": [{"x": x, "y": y} for x, y in shape.points],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("svg", type=Path, help="SVG-карта этажа")
    parser.add_argument("--prefix", action="append", default=None,
                        help="ID-префикс, который нужно выгрузить; можно указать несколько раз")
    parser.add_argument("--strict", action="store_true", help="завершиться с ошибкой при выходе за viewBox")
    args = parser.parse_args()
    prefixes = tuple(args.prefix or ["room_", "door_", "restricted_"])
    try:
        root = ET.parse(args.svg).getroot()
    except (OSError, ET.ParseError) as error:
        print(f"Не удалось прочитать SVG: {error}", file=sys.stderr)
        return 2
    shapes, errors = walk_shapes(root)
    selected = [shape for shape in shapes if shape.element_id.startswith(prefixes)]
    selected.sort(key=lambda shape: shape.element_id)
    box = view_box(root)
    out_of_box = []
    if box:
        x, y, width, height = box
        for shape in selected:
            for px, py in shape.points:
                if px < x - 0.01 or px > x + width + 0.01 or py < y - 0.01 or py > y + height + 0.01:
                    out_of_box.append(shape.element_id)
                    break
    result = {
        "source": str(args.svg),
        "viewBox": box,
        "shapeCount": len(selected),
        "shapes": [format_shape(shape) for shape in selected],
        "warnings": errors + [f"{ident}: координаты выходят за SVG viewBox" for ident in sorted(set(out_of_box))],
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if errors or (args.strict and out_of_box):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
