#!/usr/bin/env python3
"""Генерирует Kotlin Canvas/Path-представление двух исходных планов.

SVG остаются эталонными исходниками и используются только этим генератором/валидаторами;
Android-приложение не загружает и не разбирает SVG во время работы.
"""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOLS_DIR.parent
sys.path.insert(0, str(TOOLS_DIR))

from extract_svg_geometry import IDENTITY, Matrix, multiply, parse_transform, view_box, walk_shapes  # noqa: E402
SVG_TAGS_THAT_ARE_DEFINITIONS = {"defs", "mask", "clipPath", "linearGradient", "metadata", "title", "desc"}
INHERITED_PAINT_ATTRIBUTES = {
    "fill", "fill-opacity", "fill-rule", "stroke", "stroke-opacity", "stroke-width",
    "stroke-linecap", "stroke-linejoin", "stroke-miterlimit",
}
URL_REFERENCE = re.compile(r"^url\(#([^)]+)\)$")

FLOORS = (
    (1, REPO_ROOT / "app/src/main/assets/floors/floor_1.svg"),
    (2, REPO_ROOT / "app/src/main/assets/floors/floor_2.svg"),
)
OUTPUT = REPO_ROOT / "app/src/main/java/com/logos/navigator/ui/map/GeneratedFloorMaps.kt"
REFERENCE_SVGS = {
    1: REPO_ROOT / "maps_and_design/map1lower.svg",
    2: REPO_ROOT / "maps_and_design/map2lower.svg",
}


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def validate_svg_attributes(root: ET.Element) -> None:
    group_style = INHERITED_PAINT_ATTRIBUTES | {"id", "opacity", "transform", "clip-path"}
    shape_style = group_style | {"mask", "clip-rule"}
    allowed_attributes = {
        "svg": {"width", "height", "viewBox", "fill", "opacity"},
        "defs": {"id"},
        "g": group_style,
        "rect": shape_style | {"x", "y", "width", "height", "rx", "ry"},
        "path": shape_style | {"d"},
        "mask": {"id", "fill", "maskUnits", "maskContentUnits"},
        "clipPath": {"id", "clipPathUnits"},
        "linearGradient": {"id", "x1", "y1", "x2", "y2", "gradientUnits", "gradientTransform", "spreadMethod"},
        "stop": {"offset", "stop-color", "stop-opacity"},
        "metadata": set(),
        "title": set(),
        "desc": set(),
    }
    for element in root.iter():
        tag = local_name(element.tag)
        allowed = allowed_attributes.get(tag)
        if allowed is None:
            raise ValueError(f"Неизвестный SVG-элемент, который нельзя безопасно отрисовать: <{tag}>")
        unsupported = set(element.attrib) - allowed
        if unsupported:
            raise ValueError(f"Неподдерживаемые атрибуты <{tag}>: {', '.join(sorted(unsupported))}")
        if tag == "clipPath" and element.get("clipPathUnits", "userSpaceOnUse") != "userSpaceOnUse":
            raise ValueError(f"clipPath {element.get('id')} должен использовать userSpaceOnUse")
        if tag == "mask":
            if element.get("maskContentUnits", "userSpaceOnUse") != "userSpaceOnUse":
                raise ValueError(f"mask {element.get('id')} должен использовать userSpaceOnUse")
            if element.get("fill", "black").lower() != "white":
                raise ValueError(f"mask {element.get('id')} должен состоять из белых непрозрачных контуров")


def reference_signature(element: ET.Element) -> tuple[object, ...]:
    attributes = tuple(sorted(
        (name, value)
        for name, value in element.attrib.items()
        if not (name == "id" and value.startswith(("room_", "door_", "restricted_")))
    ))
    text = (element.text or "").strip()
    return (
        element.tag,
        attributes,
        text,
        tuple(reference_signature(child) for child in list(element)),
    )


def validate_reference_copy(floor_number: int, runtime_source: Path) -> None:
    reference = REFERENCE_SVGS[floor_number]
    source_root = ET.parse(runtime_source).getroot()
    reference_root = ET.parse(reference).getroot()
    if reference_signature(source_root) != reference_signature(reference_root):
        raise ValueError(
            f"{runtime_source.relative_to(REPO_ROOT)} отличается от эталона "
            f"{reference.relative_to(REPO_ROOT)} не только добавленными ID комнат"
        )


def f32(value: float) -> str:
    if not math.isfinite(value):
        raise ValueError(f"Неконечное число нельзя записать в Kotlin: {value}")
    text = f"{value:.8f}".rstrip("0").rstrip(".")
    if text in {"", "-0"}:
        text = "0"
    if "." not in text and "e" not in text.lower():
        text += ".0"
    return text + "f"


def kotlin_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def matrix_expression(matrix: Matrix) -> str:
    a, b, c, d, e, f = matrix
    # Android Matrix хранит [scaleX, skewX, tx, skewY, scaleY, ty, ...].
    values = (a, c, e, b, d, f, 0.0, 0.0, 1.0)
    return "floatArrayOf(" + ", ".join(f32(value) for value in values) + ")"


def parse_number_value(raw: str | None, name: str, default: float = 0.0) -> float:
    if raw is None:
        return default
    match = re.fullmatch(r"\s*([-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?)(?:px)?\s*", raw)
    if not match:
        raise ValueError(f"Неподдерживаемое значение {name}={raw!r}")
    return float(match.group(1))


def read_number(element: ET.Element, name: str, default: float = 0.0) -> float:
    return parse_number_value(element.get(name), name, default)


def path_expression(element: ET.Element, matrix: Matrix, fill_rule: str = "nonzero") -> str:
    tag = local_name(element.tag)
    transform = "null" if matrix == IDENTITY else matrix_expression(matrix)
    rule = "Path.FillType.EVEN_ODD" if fill_rule == "evenodd" else "Path.FillType.WINDING"
    if tag == "path":
        data = element.get("d")
        if not data:
            raise ValueError("У path отсутствует атрибут d")
        return f"svgPath({kotlin_string(data)}, {transform}, {rule})"
    if tag == "rect":
        x = read_number(element, "x")
        y = read_number(element, "y")
        width = read_number(element, "width")
        height = read_number(element, "height")
        raw_rx = element.get("rx")
        raw_ry = element.get("ry")
        rx = read_number(element, "rx")
        ry = read_number(element, "ry", rx)
        if raw_rx is None and raw_ry is not None:
            rx = ry
        if width < 0 or height < 0:
            raise ValueError("SVG rect не может иметь отрицательную ширину или высоту")
        rx = min(rx, width / 2.0)
        ry = min(ry, height / 2.0)
        return (
            f"svgRect({f32(x)}, {f32(y)}, {f32(width)}, {f32(height)}, "
            f"{f32(rx)}, {f32(ry)}, {transform}, {rule})"
        )
    raise ValueError(f"Неподдерживаемый SVG-элемент в контуре: {tag}")


def parse_color(value: str, opacity: float = 1.0) -> tuple[int, int, int, int]:
    raw = value.strip().lower()
    named = {"black": "#000000", "white": "#ffffff", "red": "#ff0000", "blue": "#0000ff"}
    raw = named.get(raw, raw)
    if not raw.startswith("#"):
        raise ValueError(f"Неподдерживаемый цвет SVG: {value!r}")
    digits = raw[1:]
    if len(digits) == 3:
        red, green, blue = (int(char * 2, 16) for char in digits)
        alpha = 255
    elif len(digits) == 4:
        red, green, blue, alpha = (int(char * 2, 16) for char in digits)
    elif len(digits) == 6:
        red, green, blue = (int(digits[index:index + 2], 16) for index in (0, 2, 4))
        alpha = 255
    elif len(digits) == 8:
        red, green, blue, alpha = (int(digits[index:index + 2], 16) for index in (0, 2, 4, 6))
    else:
        raise ValueError(f"Неподдерживаемый формат цвета SVG: {value!r}")
    alpha = max(0, min(255, round(alpha * opacity)))
    return alpha, red, green, blue


def argb_literal(color: tuple[int, int, int, int]) -> str:
    alpha, red, green, blue = color
    value = (alpha << 24) | (red << 16) | (green << 8) | blue
    return f"0x{value:08X}.toInt()"


def parse_opacity(value: str | None, default: float = 1.0) -> float:
    if value is None:
        return default
    result = float(value)
    if not math.isfinite(result):
        raise ValueError(f"Некорректная opacity: {value!r}")
    return max(0.0, min(1.0, result))


def stop_offset(value: str | None, default: float) -> float:
    if value is None:
        return default
    raw = value.strip()
    if raw.endswith("%"):
        return max(0.0, min(1.0, float(raw[:-1]) / 100.0))
    return max(0.0, min(1.0, float(raw)))


def reference_id(value: str | None, attribute: str) -> str | None:
    if value is None or value == "none":
        return None
    match = URL_REFERENCE.fullmatch(value.strip())
    if not match:
        raise ValueError(f"Неподдерживаемая ссылка {attribute}={value!r}")
    return match.group(1)


def compose(parent: Matrix, element: ET.Element) -> Matrix:
    return multiply(parent, parse_transform(element.get("transform")))


def uniform_scale(matrix: Matrix) -> float | None:
    a, b, c, d, _, _ = matrix
    scale_x = math.hypot(a, b)
    scale_y = math.hypot(c, d)
    if abs(scale_x - scale_y) > 1e-6 or abs(a * c + b * d) > 1e-6:
        return None
    return (scale_x + scale_y) / 2.0


def gradient_expression(gradient: ET.Element) -> str:
    if gradient.get("gradientUnits", "objectBoundingBox") != "userSpaceOnUse":
        raise ValueError(f"Для gradient {gradient.get('id')} ожидался userSpaceOnUse")
    if gradient.get("spreadMethod", "pad") != "pad":
        raise ValueError(f"Неподдерживаемый spreadMethod в {gradient.get('id')}")
    if gradient.get("gradientTransform"):
        matrix = parse_transform(gradient.get("gradientTransform"))
        # Для текущих карт преобразования градиентов нет. Не принимать выражения,
        # которые нельзя без потери смысла представить координатами LinearGradient.
        if matrix != IDENTITY:
            raise ValueError(f"Преобразование градиента {gradient.get('id')} требует отдельной обработки")
    x1 = read_number(gradient, "x1")
    y1 = read_number(gradient, "y1")
    x2 = read_number(gradient, "x2")
    y2 = read_number(gradient, "y2")
    stops: list[tuple[float, str]] = []
    previous_offset = 0.0
    for stop in list(gradient):
        if local_name(stop.tag) != "stop":
            raise ValueError(f"Неподдерживаемый элемент в gradient {gradient.get('id')}")
        offset = stop_offset(stop.get("offset"), previous_offset)
        if offset < previous_offset:
            offset = previous_offset
        previous_offset = offset
        color = stop.get("stop-color", "black")
        alpha = parse_opacity(stop.get("stop-opacity"))
        stops.append((offset, argb_literal(parse_color(color, alpha))))
    if len(stops) < 2:
        raise ValueError(f"В gradient {gradient.get('id')} меньше двух color stops")
    # Android LinearGradient требует позиции, если их длина совпадает с цветами;
    # позиции с равными значениями допускаются и сохраняют SVG-переходы.
    colors = ", ".join(color for _, color in stops)
    positions = ", ".join(f32(offset) for offset, _ in stops)
    return (
        "LinearGradient("
        f"{f32(x1)}, {f32(y1)}, {f32(x2)}, {f32(y2)}, "
        f"intArrayOf({colors}), floatArrayOf({positions}), Shader.TileMode.CLAMP)"
    )


def definition_paths(element: ET.Element, parent_matrix: Matrix) -> list[str]:
    result: list[str] = []
    default_rule = element.get("clip-rule", element.get("fill-rule", "nonzero"))
    children = list(element)
    if local_name(element.tag) == "mask":
        if len(children) != 1:
            raise ValueError(f"Mask {element.get('id')} должна содержать один непрозрачный белый контур")
        child = children[0]
        if child.get("fill", element.get("fill", "black")).lower() != "white" or \
                child.get("stroke", "none") != "none" or parse_opacity(child.get("opacity")) != 1.0 or \
                parse_opacity(child.get("fill-opacity")) != 1.0:
            raise ValueError(f"Mask {element.get('id')} не эквивалентна clipPath из-за прозрачности или цвета")
    for child in children:
        tag = local_name(child.tag)
        if tag not in {"path", "rect"}:
            raise ValueError(f"Неподдерживаемый {tag} внутри {local_name(element.tag)} {element.get('id')}")
        result.append(path_expression(child, multiply(parent_matrix, parse_transform(child.get("transform"))),
                                      child.get("clip-rule", child.get("fill-rule", default_rule))))
    if not result:
        raise ValueError(f"Пустое определение {local_name(element.tag)} {element.get('id')}")
    return result


def paint_expression(
    color: str | None,
    opacity: float,
    style: str,
    stroke_width: float = 0.0,
    stroke_cap: str = "butt",
    stroke_join: str = "miter",
    shader_name: str | None = None,
    miter_limit: float = 4.0,
) -> str:
    if shader_name is not None:
        color_literal = "Color.WHITE"
    elif color is None:
        raise ValueError("Внутренняя ошибка генератора: отсутствует цвет")
    else:
        color_literal = argb_literal(parse_color(color))
    shader = shader_name or "null"
    return (
        "mapPaint("
        f"color = {color_literal}, style = Paint.Style.{style}, opacity = {f32(opacity)}, "
        f"strokeWidth = {f32(stroke_width)}, strokeCap = Paint.Cap.{stroke_cap.upper()}, "
        f"strokeJoin = Paint.Join.{stroke_join.upper()}, strokeMiter = {f32(miter_limit)}, shader = {shader})"
    )


def generate_floor(floor_number: int, svg_path: Path) -> tuple[str, dict[str, object]]:
    validate_reference_copy(floor_number, svg_path)
    root = ET.parse(svg_path).getroot()
    validate_svg_attributes(root)
    view_box_value = view_box(root)
    if view_box_value is None:
        raise ValueError(f"В {svg_path} отсутствует viewBox")
    view_x, view_y, width, height = view_box_value
    if width <= 0 or height <= 0:
        raise ValueError(f"Некорректный viewBox в {svg_path}: {view_box_value}")
    if view_x != 0 or view_y != 0 or not width.is_integer() or not height.is_integer():
        raise ValueError(f"Текущий Picture renderer требует целочисленный viewBox с началом 0,0: {view_box_value}")

    definitions: dict[str, ET.Element] = {}
    gradients: dict[str, ET.Element] = {}
    for element in root.iter():
        tag = local_name(element.tag)
        element_id = element.get("id")
        if tag in {"mask", "clipPath"} and element_id:
            definitions[element_id] = element
        elif tag == "linearGradient" and element_id:
            gradients[element_id] = element

    prefix = f"f{floor_number}"
    lines: list[str] = [f"    private const val {prefix.upper()}_WIDTH = {f32(width)}",
                        f"    private const val {prefix.upper()}_HEIGHT = {f32(height)}"]

    gradient_names: dict[str, str] = {}
    for index, (gradient_id, gradient) in enumerate(gradients.items()):
        name = f"{prefix}Gradient{index}"
        gradient_names[gradient_id] = name
        lines.append(f"    private val {name} = {gradient_expression(gradient)}")

    draw_operations: list[str] = []
    emitted_paths = 0

    def get_definition_expressions(identifier: str, parent_matrix: Matrix, expected_tag: str) -> list[str]:
        definition = definitions.get(identifier)
        if definition is None:
            raise ValueError(f"Ссылка на отсутствующее SVG-определение #{identifier}")
        if local_name(definition.tag) != expected_tag:
            raise ValueError(f"Ссылка #{identifier} должна указывать на {expected_tag}, найден {local_name(definition.tag)}")
        return definition_paths(definition, parent_matrix)

    def walk(
        element: ET.Element,
        parent_matrix: Matrix,
        inherited_clips: list[list[str]],
        inherited_opacity: float,
        inherited_paint: dict[str, str],
    ) -> None:
        nonlocal emitted_paths
        tag = local_name(element.tag)
        if tag in SVG_TAGS_THAT_ARE_DEFINITIONS:
            return
        paint_style = dict(inherited_paint)
        paint_style.update({key: element.attrib[key] for key in INHERITED_PAINT_ATTRIBUTES if key in element.attrib})
        if tag == "g":
            group_matrix = compose(parent_matrix, element)
            own_group_opacity = parse_opacity(element.get("opacity"))
            if own_group_opacity != 1.0:
                raise ValueError(f"Групповая opacity у <g> требует compositing и не поддерживается: {element.get('id')}")
            group_opacity = inherited_opacity * own_group_opacity
            group_clips = list(inherited_clips)
            clip_ref = reference_id(element.get("clip-path"), "clip-path")
            if clip_ref:
                group_clips.append(get_definition_expressions(clip_ref, group_matrix, "clipPath"))
            for child in list(element):
                walk(child, group_matrix, group_clips, group_opacity, paint_style)
            return
        if tag not in {"rect", "path"}:
            raise ValueError(f"Неизвестный SVG-элемент в области рисования: <{tag}>")
        if element.get("style"):
            raise ValueError(f"Атрибут style пока не поддерживается: {element.get('id')}")

        element_matrix = compose(parent_matrix, element)
        path_rule = paint_style.get("fill-rule", "nonzero")
        path_name = f"{prefix}Path{emitted_paths}"
        path_expr = path_expression(element, element_matrix, path_rule)
        lines.append(f"    private val {path_name} = {path_expr}")

        opacity = inherited_opacity * parse_opacity(element.get("opacity"))
        fill_value = paint_style.get("fill", "black")
        fill_ref = reference_id(fill_value, "fill") if fill_value.startswith("url(") else None
        fill_opacity = opacity * parse_opacity(paint_style.get("fill-opacity"))
        if fill_value == "none":
            fill_paint = "null"
        elif fill_ref:
            gradient_name = gradient_names.get(fill_ref)
            if gradient_name is None:
                raise ValueError(f"Ссылка fill указывает на неподдерживаемый градиент #{fill_ref}")
            if element_matrix != IDENTITY:
                raise ValueError(f"Преобразованный элемент с градиентом требует shader local matrix: {element.get('id')}")
            fill_paint = paint_expression(None, fill_opacity, "FILL", shader_name=gradient_name)
        else:
            fill_paint = paint_expression(fill_value, fill_opacity, "FILL")

        stroke_value = paint_style.get("stroke", "none")
        if stroke_value == "none":
            stroke_paint = "null"
        else:
            stroke_ref = reference_id(stroke_value, "stroke") if stroke_value.startswith("url(") else None
            if stroke_ref:
                raise ValueError(f"SVG-градиентная обводка не поддерживается: #{stroke_ref}")
            stroke_cap = paint_style.get("stroke-linecap", "butt")
            stroke_join = paint_style.get("stroke-linejoin", "miter")
            cap_map = {"butt": "BUTT", "round": "ROUND", "square": "SQUARE"}
            join_map = {"miter": "MITER", "round": "ROUND", "bevel": "BEVEL"}
            if stroke_cap not in cap_map or stroke_join not in join_map:
                raise ValueError(f"Неизвестный стиль обводки у {element.get('id')}")
            scale = uniform_scale(element_matrix)
            if scale is None:
                raise ValueError(f"Анизотропная трансформация обводки не поддерживается: {element.get('id')}")
            stroke_width = parse_number_value(paint_style.get("stroke-width"), "stroke-width", 1.0) * scale
            stroke_paint = paint_expression(
                stroke_value,
                opacity * parse_opacity(paint_style.get("stroke-opacity")),
                "STROKE",
                stroke_width,
                cap_map[stroke_cap],
                join_map[stroke_join],
                miter_limit=parse_number_value(paint_style.get("stroke-miterlimit"), "stroke-miterlimit", 4.0),
            )

        if opacity != 1.0 and fill_paint != "null" and stroke_paint != "null":
            raise ValueError(f"Opacity элемента с fill и stroke требует compositing: {element.get('id')}")

        clips = list(inherited_clips)
        own_clip = reference_id(element.get("clip-path"), "clip-path")
        if own_clip:
            clips.append(get_definition_expressions(own_clip, parent_matrix, "clipPath"))
        masks: list[list[str]] = []
        mask_ref = reference_id(element.get("mask"), "mask")
        if mask_ref:
            masks.extend(get_definition_expressions(mask_ref, parent_matrix, "mask"))

        clip_expression = "listOf(" + ", ".join("listOf(" + ", ".join(paths) + ")" for paths in clips) + ")"
        mask_expression = "listOf(" + ", ".join(masks) + ")"
        draw_operations.append(
            "MapDrawOp(\n"
            f"            path = {path_name},\n"
            f"            fillPaint = {fill_paint},\n"
            f"            strokePaint = {stroke_paint},\n"
            f"            clipGroups = {clip_expression},\n"
            f"            maskPaths = {mask_expression},\n"
            f"            sourceId = {kotlin_string(element.get('id', ''))},\n"
            "        )"
        )
        emitted_paths += 1

    root_paint = {key: root.attrib[key] for key in INHERITED_PAINT_ATTRIBUTES if key in root.attrib}
    root_opacity = parse_opacity(root.get("opacity"))
    if root_opacity != 1.0:
        raise ValueError("Root-level opacity требует group compositing и не поддерживается")
    for child in list(root):
        walk(child, IDENTITY, [], root_opacity, root_paint)

    operations_name = f"{prefix}Operations"
    lines.append(f"    private val {operations_name} = listOf(")
    for operation in draw_operations:
        lines.extend("        " + line if index == 0 else "            " + line
                     for index, line in enumerate(operation.splitlines()))
        lines[-1] += ","
    lines.append("    )")

    shapes, geometry_errors = walk_shapes(root)
    if geometry_errors:
        raise ValueError("; ".join(geometry_errors))
    polygons: dict[str, list[list[tuple[float, float]]]] = defaultdict(list)
    for shape in shapes:
        if shape.closed and len(shape.points) >= 3:
            polygons[shape.element_id].append(shape.points)
    polygon_name = f"{prefix}RoomPolygons"
    lines.append(f"    private val {polygon_name}: Map<String, List<List<PointF>>> = mapOf(")
    for element_id in sorted(polygons):
        contour_expressions = []
        for contour in polygons[element_id]:
            points = ", ".join(f"PointF({f32(x)}, {f32(y)})" for x, y in contour)
            contour_expressions.append(f"listOf({points})")
        lines.append(f"        {kotlin_string(element_id)} to listOf({', '.join(contour_expressions)}),")
    lines.append("    )")

    metadata = {
        "floor": floor_number,
        "viewBox": [view_x, view_y, width, height],
        "drawOperations": emitted_paths,
        "roomPolygons": len(polygons),
        "source": str(svg_path.relative_to(REPO_ROOT)),
    }
    return "\n".join(lines), metadata


def generate_source() -> tuple[str, list[dict[str, object]]]:
    per_floor = [generate_floor(floor_number, path) for floor_number, path in FLOORS]
    floor_blocks = [block for block, _ in per_floor]
    metadata = [item for _, item in per_floor]
    code = '''// Автоматически создано tools/generate_kotlin_floor_maps.py. Не редактировать вручную.
package com.logos.navigator.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.PathParser
import kotlin.math.roundToInt

/** Векторные планы здания, собранные из исходных SVG для нативной отрисовки Canvas/Path. */
internal object GeneratedFloorMaps {
    private data class MapDrawOp(
        val path: Path,
        val fillPaint: Paint?,
        val strokePaint: Paint?,
        /** Каждый список — объединённая фигура одного SVG clipPath. */
        val clipGroups: List<List<Path>>,
        /** SVG-маски этих планов содержат белые контуры и эквивалентны обрезке по Path. */
        val maskPaths: List<Path>,
        val sourceId: String,
    )

    private fun svgPath(data: String, matrixValues: FloatArray?, fillType: Path.FillType): Path {
        val path = PathParser.createPathFromPathData(data)
            ?: error("Некорректный SVG path в сгенерированной карте")
        if (matrixValues != null) {
            path.transform(Matrix().apply { setValues(matrixValues) })
        }
        path.fillType = fillType
        return path
    }

    private fun svgRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        rx: Float,
        ry: Float,
        matrixValues: FloatArray?,
        fillType: Path.FillType,
    ): Path {
        val path = Path()
        if (rx <= 0f || ry <= 0f) {
            path.addRect(x, y, x + width, y + height, Path.Direction.CW)
        } else {
            path.addRoundRect(
                RectF(x, y, x + width, y + height),
                floatArrayOf(rx, ry, rx, ry, rx, ry, rx, ry),
                Path.Direction.CW,
            )
        }
        if (matrixValues != null) {
            path.transform(Matrix().apply { setValues(matrixValues) })
        }
        path.fillType = fillType
        return path
    }

    private fun mapPaint(
        color: Int,
        style: Paint.Style,
        opacity: Float,
        strokeWidth: Float,
        strokeCap: Paint.Cap,
        strokeJoin: Paint.Join,
        strokeMiter: Float,
        shader: Shader?,
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        alpha = (Color.alpha(color) * opacity).roundToInt().coerceIn(0, 255)
        this.style = style
        this.strokeWidth = strokeWidth
        this.strokeCap = strokeCap
        this.strokeJoin = strokeJoin
        this.strokeMiter = strokeMiter
        this.shader = shader
    }

    private fun drawOperations(canvas: Canvas, operations: List<MapDrawOp>) {
        operations.forEach { operation ->
            val saveCount = canvas.save()
            operation.clipGroups.forEach { group ->
                // Фигуры одного clipPath объединяются, а разные clipPath пересекаются.
                val union = Path()
                group.forEach { union.addPath(it) }
                canvas.clipPath(union)
            }
            operation.maskPaths.forEach { canvas.clipPath(it) }
            operation.fillPaint?.let { canvas.drawPath(operation.path, it) }
            operation.strokePaint?.let { canvas.drawPath(operation.path, it) }
            canvas.restoreToCount(saveCount)
        }
    }

'''
    code += "\n\n".join(floor_blocks)
    code += '''

    private val floor1Picture by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        recordPicture(F1_WIDTH, F1_HEIGHT, f1Operations)
    }
    private val floor2Picture by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        recordPicture(F2_WIDTH, F2_HEIGHT, f2Operations)
    }

    private fun recordPicture(width: Float, height: Float, operations: List<MapDrawOp>): android.graphics.Picture {
        val picture = android.graphics.Picture()
        val recordingCanvas = picture.beginRecording(width.toInt(), height.toInt())
        drawOperations(recordingCanvas, operations)
        picture.endRecording()
        return picture
    }

    /** Рисует в координатах исходного viewBox; масштабирование выполняется матрицей карты. */
    fun draw(canvas: Canvas, floorNumber: Int) {
        when (floorNumber) {
            1 -> floor1Picture.draw(canvas)
            2 -> floor2Picture.draw(canvas)
        }
    }

    /** Возвращает полигон SVG-элемента для прежней логики hit-test и редких fallback-геометрий. */
    fun roomPolygons(floorNumber: Int, elementIds: List<String>): List<List<PointF>> {
        val source = when (floorNumber) {
            1 -> f1RoomPolygons
            2 -> f2RoomPolygons
            else -> return emptyList()
        }
        return elementIds.distinct().flatMap { source[it].orEmpty() }
    }
}
'''
    return code, metadata


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="проверить, что Kotlin-файл совпадает с исходными SVG")
    args = parser.parse_args()
    try:
        content, metadata = generate_source()
    except (OSError, ET.ParseError, ValueError, KeyError) as error:
        print(f"Ошибка генерации Kotlin-карт: {error}", file=sys.stderr)
        return 2

    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != content:
            print(f"{OUTPUT.relative_to(REPO_ROOT)} устарел; запустите python3 tools/generate_kotlin_floor_maps.py", file=sys.stderr)
            return 1
        print("Kotlin Canvas/Path-карты синхронизированы с исходными SVG:")
        for item in metadata:
            print(f"  этаж {item['floor']}: {item['drawOperations']} контуров, {item['roomPolygons']} room-полигонов, viewBox={item['viewBox']}")
        return 0

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(content, encoding="utf-8")
    print(f"Создан {OUTPUT.relative_to(REPO_ROOT)}")
    for item in metadata:
        print(f"  этаж {item['floor']}: {item['drawOperations']} контуров, {item['roomPolygons']} room-полигонов, viewBox={item['viewBox']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
