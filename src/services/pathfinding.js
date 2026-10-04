/**
 * src/services/pathfinding.js — поиск маршрута (алгоритм A*) и формирование
 * пошаговых инструкций.
 *
 * Поток данных:
 *   1. Точки начала/конца привязываются к проходимым ячейкам (NavGraph).
 *   2. A* ищет путь по сетке с учётом межэтажных связей.
 *   3. Путь упрощается (алгоритм Дугласа — Пекера), чтобы убрать «лесенку» сетки.
 *   4. По точкам строятся шаги: «Идите прямо 60 м», «Поверните налево»,
 *      «Поднимитесь на 2 этаж по лестнице» и т.д.
 */
import graphConfig from '../data/graph.json';
import { getFloor } from '../data';
import { getNavGraph, parseCellKey, unitsPerMeter } from './graph';

// ---------------------------------------------------------------------------
// Бинарная куча (min-heap) для приоритетной очереди A*
// ---------------------------------------------------------------------------

class MinHeap {
  constructor() {
    this.items = []; // [{key, priority}]
  }

  get size() {
    return this.items.length;
  }

  push(key, priority) {
    this.items.push({ key, priority });
    let i = this.items.length - 1;
    while (i > 0) {
      const parent = (i - 1) >> 1;
      if (this.items[parent].priority <= this.items[i].priority) break;
      [this.items[parent], this.items[i]] = [this.items[i], this.items[parent]];
      i = parent;
    }
  }

  pop() {
    if (this.items.length === 0) return null;
    const top = this.items[0];
    const last = this.items.pop();
    if (this.items.length > 0) {
      this.items[0] = last;
      let i = 0;
      for (;;) {
        const left = i * 2 + 1;
        const right = left + 1;
        let smallest = i;
        if (left < this.items.length && this.items[left].priority < this.items[smallest].priority) smallest = left;
        if (right < this.items.length && this.items[right].priority < this.items[smallest].priority) smallest = right;
        if (smallest === i) break;
        [this.items[smallest], this.items[i]] = [this.items[i], this.items[smallest]];
        i = smallest;
      }
    }
    return top.key;
  }
}

// ---------------------------------------------------------------------------
// A*
// ---------------------------------------------------------------------------

/**
 * Поиск пути между двумя ячейками.
 * @returns {Array<{key: string, kind: string}>|null} — путь с типом ребра
 */
export function findPath(startKey, goalKey) {
  const graph = getNavGraph();
  if (!startKey || !goalKey) return null;
  if (startKey === goalKey) return [{ key: startKey, kind: 'walk' }];

  const gScore = new Map([[startKey, 0]]);
  const cameFrom = new Map(); // key → {key, kind}
  const open = new MinHeap();
  const closed = new Set();

  const heuristicOf = (key) => {
    const a = parseCellKey(key);
    const b = parseCellKey(goalKey);
    return Math.hypot(a.cx - b.cx, a.cy - b.cy) * graphConfig.cellSize;
  };

  open.push(startKey, heuristicOf(startKey));

  while (open.size > 0) {
    const current = open.pop();
    if (current === goalKey) {
      // Восстановление пути: собираем ключи от старта к цели,
      // а тип ребра привязываем к node, В КОТОРУЮ оно ведёт.
      const keys = [goalKey];
      let cursor = goalKey;
      while (cameFrom.has(cursor)) {
        cursor = cameFrom.get(cursor).key;
        keys.unshift(cursor);
      }
      return keys.map((key, index) => ({
        key,
        kind: index === 0 ? 'walk' : cameFrom.get(key).kind,
      }));
    }
    if (closed.has(current)) continue;
    closed.add(current);

    const currentG = gScore.get(current) ?? Infinity;

    for (const edge of graph.neighbors(current)) {
      if (closed.has(edge.to)) continue;
      const tentative = currentG + edge.cost;
      if (tentative < (gScore.get(edge.to) ?? Infinity)) {
        gScore.set(edge.to, tentative);
        cameFrom.set(edge.to, { key: current, kind: edge.kind || 'walk' });
        open.push(edge.to, tentative + heuristicOf(edge.to));
      }
    }
  }
  return null;
}

// ---------------------------------------------------------------------------
// Упрощение пути (Дуглас — Пекер)
// ---------------------------------------------------------------------------

function perpendicularDistance(point, lineStart, lineEnd) {
  const dx = lineEnd.x - lineStart.x;
  const dy = lineEnd.y - lineStart.y;
  const lengthSq = dx * dx + dy * dy;
  if (lengthSq === 0) return Math.hypot(point.x - lineStart.x, point.y - lineStart.y);
  let t = ((point.x - lineStart.x) * dx + (point.y - lineStart.y) * dy) / lengthSq;
  t = Math.max(0, Math.min(1, t));
  const projX = lineStart.x + t * dx;
  const projY = lineStart.y + t * dy;
  return Math.hypot(point.x - projX, point.y - projY);
}

function rdp(points, epsilon) {
  if (points.length < 3) return points.slice();
  let maxDist = 0;
  let index = 0;
  for (let i = 1; i < points.length - 1; i += 1) {
    const dist = perpendicularDistance(points[i], points[0], points[points.length - 1]);
    if (dist > maxDist) {
      maxDist = dist;
      index = i;
    }
  }
  if (maxDist <= epsilon) {
    return [points[0], points[points.length - 1]];
  }
  const left = rdp(points.slice(0, index + 1), epsilon);
  const right = rdp(points.slice(index), epsilon);
  return left.slice(0, -1).concat(right);
}

// ---------------------------------------------------------------------------
// Формирование шагов
// ---------------------------------------------------------------------------

/** Тип поворота по изменению направления (экранные координаты, y вниз) */
function turnKind(prevAngle, angle) {
  let delta = angle - prevAngle;
  while (delta > Math.PI) delta -= Math.PI * 2;
  while (delta < -Math.PI) delta += Math.PI * 2;
  const abs = Math.abs(delta);
  const isLeft = delta < 0; // в экранных координатах отрицательная дельта = поворот налево
  if (abs < (20 * Math.PI) / 180) return 'straight';
  if (abs < (60 * Math.PI) / 180) return isLeft ? 'slightLeft' : 'slightRight';
  if (abs < (150 * Math.PI) / 180) return isLeft ? 'left' : 'right';
  return 'around';
}

/** Инструкция перехода между этажами по типу ребра */
function transitionKind(edgeKind, toFloorId, fromFloorId) {
  const toLevel = getFloor(toFloorId)?.level ?? 1;
  const fromLevel = getFloor(fromFloorId)?.level ?? 1;
  const goingUp = toLevel > fromLevel;
  if (edgeKind === 'elevator') return goingUp ? 'elevatorUp' : 'elevatorDown';
  return goingUp ? 'stairsUp' : 'stairsDown';
}

/**
 * Разбивает путь на «участки» (legs): непрерывные отрезки на одном этаже,
 * разделённые переходами (лестница/лифт/вход).
 */
function splitIntoSegments(points, kinds) {
  const segments = [];
  let current = { floorId: points[0].floorId, points: [points[0]], transition: null };

  for (let i = 1; i < points.length; i += 1) {
    const kind = kinds[i] || 'walk';
    if (kind === 'stairs' || kind === 'elevator') {
      // Завершаем участок, фиксируем переход, начинаем новый
      current.points.push(points[i - 1]);
      segments.push(current);
      current = {
        floorId: points[i].floorId,
        points: [points[i]],
        transition: {
          kind: transitionKind(kind, points[i].floorId, points[i - 1].floorId),
          level: getFloor(points[i].floorId)?.level ?? 1,
          floorId: points[i].floorId,
        },
      };
    } else {
      current.points.push(points[i]);
    }
  }
  segments.push(current);
  return segments;
}

/**
 * Строит пошаговые инструкции по участкам маршрута.
 * @param {Array} segments — результат splitIntoSegments
 */
function buildSteps(segments) {
  const steps = [];
  let pending = null; // текущий прямой участок

  const flush = () => {
    if (pending && pending.distanceM > 0.5) steps.push(pending);
    pending = null;
  };

  for (const segment of segments) {
    if (segment.transition) {
      flush();
      steps.push({
        kind: segment.transition.kind,
        distanceM: 0,
        floorId: segment.transition.floorId,
        level: segment.transition.level,
        x: segment.points[0].x,
        y: segment.points[0].y,
      });
    }

    for (let i = 1; i < segment.points.length; i += 1) {
      const prev = segment.points[i - 1];
      const cur = segment.points[i];
      const units = Math.hypot(cur.x - prev.x, cur.y - prev.y);
      const distanceM = units / unitsPerMeter(cur.floorId);
      const angle = Math.atan2(cur.y - prev.y, cur.x - prev.x);

      if (!pending) {
        pending = {
          kind: 'straight',
          distanceM,
          floorId: cur.floorId,
          x: cur.x,
          y: cur.y,
          angle,
        };
        continue;
      }

      const kind = turnKind(pending.angle, angle);
      if (kind === 'straight') {
        pending.distanceM += distanceM;
        pending.x = cur.x;
        pending.y = cur.y;
      } else {
        flush();
        steps.push({ kind, distanceM: 0, floorId: cur.floorId, x: cur.x, y: cur.y, angle });
        pending = {
          kind: 'straight',
          distanceM: 0,
          floorId: cur.floorId,
          x: cur.x,
          y: cur.y,
          angle,
        };
      }
    }
  }
  flush();
  return steps;
}

// ---------------------------------------------------------------------------
// Публичный API
// ---------------------------------------------------------------------------

/**
 * Строит маршрут между двумя точками.
 * @param {{floorId: string, x: number, y: number}} start
 * @param {{floorId: string, x: number, y: number}} end
 * @returns {{ok: boolean, points?: Array, steps?: Array, distanceM?: number,
 *            durationSec?: number, floors?: string[], error?: string}}
 */
export function buildRoute(start, end) {
  const graph = getNavGraph();
  const startKey = graph.snapToWalkable(start.floorId, start.x, start.y);
  const goalKey = graph.snapToWalkable(end.floorId, end.x, end.y);

  if (!startKey || !goalKey) {
    return { ok: false, error: 'routeNotFound' };
  }

  if (startKey === goalKey) {
    return {
      ok: true,
      points: [
        { floorId: start.floorId, x: start.x, y: start.y },
        { floorId: end.floorId, x: end.x, y: end.y },
      ],
      steps: [{ kind: 'arrive', distanceM: 0, floorId: end.floorId, x: end.x, y: end.y }],
      distanceM: 0,
      durationSec: 0,
      floors: [start.floorId, end.floorId].filter((v, i, a) => a.indexOf(v) === i),
    };
  }

  const path = findPath(startKey, goalKey);
  if (!path || path.length < 2) {
    return { ok: false, error: 'routeNotFound' };
  }

  // Точки маршрута (первая и последняя — точные координаты пользователя)
  const points = path.map((entry) => {
    const center = graph.centerOf(entry.key) || { x: 0, y: 0, floorId: start.floorId };
    return { floorId: center.floorId, x: center.x, y: center.y };
  });
  points[0] = { floorId: start.floorId, x: start.x, y: start.y };
  points[points.length - 1] = { floorId: end.floorId, x: end.x, y: end.y };
  const kinds = path.map((entry) => entry.kind);

  // Разбивка на участки + упрощение «лесенки» сетки
  const segments = splitIntoSegments(points, kinds);
  const epsilon = graphConfig.cellSize * 0.9;
  for (const segment of segments) {
    if (segment.points.length > 2) {
      segment.points = rdp(segment.points, epsilon);
    }
  }

  // Метрики маршрута
  let distanceM = 0;
  let transitions = 0;
  const floorIds = [];
  for (const segment of segments) {
    if (segment.transition) transitions += 1;
    for (let i = 1; i < segment.points.length; i += 1) {
      distanceM +=
        Math.hypot(
          segment.points[i].x - segment.points[i - 1].x,
          segment.points[i].y - segment.points[i - 1].y
        ) / unitsPerMeter(segment.floorId);
    }
    if (!floorIds.includes(segment.floorId)) floorIds.push(segment.floorId);
  }

  const durationSec = distanceM / graphConfig.walkSpeedMps + transitions * graphConfig.stairSeconds;
  const steps = buildSteps(segments);

  return {
    ok: true,
    points: segments.flatMap((s) => s.points),
    steps,
    distanceM: Math.round(distanceM),
    durationSec: Math.round(durationSec),
    floors: floorIds,
  };
}

/** Переводит секунды в минутки (округление, минимум 1) */
export function secondsToMinutes(seconds) {
  if (!seconds || seconds <= 0) return 0;
  return Math.max(1, Math.round(seconds / 60));
}
