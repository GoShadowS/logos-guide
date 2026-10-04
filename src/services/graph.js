/**
 * src/services/graph.js — построение графа навигации из геометрии планировок.
 *
 * Идея: внутри этажа проходимость описывается сеткой (grid) с шагом cellSize
 * единиц viewBounds (по умолчанию 10 units = 0.4 метра). Ячейка «проходима»,
 * если её центр попадает в коридор, дорожку, парковку, площадку или в
 * помещение, через которое можно пройти (лестница, лифт, вестибюль).
 *
 * Аудитории проходимы ТОЛЬКО в двух точках: дверь и центр помещения —
 * между ними добавляется явное ребро. Поэтому маршрут не «прорежет» класс
 * насквозь, а аккуратно зайдёт в него через дверь.
 *
 * Межэтажные связи (лестницы/лифты) описаны в graph.json (verticalLinks)
 * и добавляются как явные рёбра между ячейками разных этажей.
 */
import { floors, rooms, pois, getFloorUnitsPerMeter } from '../data';
import graphConfig from '../data/graph.json';

/** Виды областей планировки, по которым можно ходить */
const WALKABLE_AREA_KINDS = new Set(['corridor', 'path', 'parking', 'plaza']);

/** Типы помещений, через которые можно проходить */
const WALKABLE_ROOM_TYPES = new Set(['stairs', 'elevator', 'entrance']);

function pointInRect(px, py, r) {
  return px >= r.x && px <= r.x + r.w && py >= r.y && py <= r.y + r.h;
}

/** Ключ ячейки: floorId:cx,cy */
export function cellKey(floorId, cx, cy) {
  return `${floorId}:${cx},${cy}`;
}

/** Разбор ключа ячейки */
export function parseCellKey(key) {
  const [floorId, coords] = key.split(':');
  const [cx, cy] = coords.split(',').map(Number);
  return { floorId, cx, cy };
}

// ---------------------------------------------------------------------------
// Построение сетки одного этажа
// ---------------------------------------------------------------------------

function buildFloorGrid(floor) {
  const cs = graphConfig.cellSize;
  const cols = Math.ceil(floor.width / cs);
  const rows = Math.ceil(floor.height / cs);
  const walkable = new Uint8Array(cols * rows);
  const areas = floor.areas || [];
  const floorRooms = rooms.filter((r) => r.floorId === floor.id);
  const floorPois = pois.filter((p) => p.floorId === floor.id);

  const isWalkablePoint = (px, py) => {
    for (const area of areas) {
      if (WALKABLE_AREA_KINDS.has(area.kind) && pointInRect(px, py, area)) return true;
    }
    for (const room of floorRooms) {
      if (WALKABLE_ROOM_TYPES.has(room.type) && pointInRect(px, py, room)) return true;
    }
    return false;
  };

  for (let cy = 0; cy < rows; cy += 1) {
    for (let cx = 0; cx < cols; cx += 1) {
      const px = cx * cs + cs / 2;
      const py = cy * cs + cs / 2;
      walkable[cy * cols + cx] = isWalkablePoint(px, py) ? 1 : 0;
    }
  }

  return { floor, cs, cols, rows, walkable, areas, rooms: floorRooms, pois: floorPois };
}

// ---------------------------------------------------------------------------
// Граф навигации
// ---------------------------------------------------------------------------

export class NavGraph {
  constructor() {
    /** @type {Map<string, object>} floorId → сетка этажа */
    this.grids = new Map();
    /** @type {Map<string, Array<{to: string, cost: number}>>} доп. рёбра */
    this.extraEdges = new Map();
    /** @type {Map<string, {floorId: string, x: number, y: number}>} ключ ячейки → центр в единицах viewBox */
    this.cellCenters = new Map();
    this.built = false;
  }

  /** Строит (и кэширует) сетки всех этажей и межэтажные связи */
  build() {
    if (this.built) return;
    for (const floor of floors) {
      this.grids.set(floor.id, buildFloorGrid(floor));
    }
    this._addRoomPortals();
    this._addPoiPortals();
    this._addVerticalLinks();
    this.built = true;
  }

  getGrid(floorId) {
    this.build();
    return this.grids.get(floorId) || null;
  }

  // --- Внутренние методы ---------------------------------------------------

  _cellCenter(grid, cx, cy) {
    return {
      floorId: grid.floor.id,
      x: cx * grid.cs + grid.cs / 2,
      y: cy * grid.cs + grid.cs / 2,
    };
  }

  _setWalkable(grid, cx, cy) {
    if (cx < 0 || cy < 0 || cx >= grid.cols || cy >= grid.rows) return;
    grid.walkable[cy * grid.cols + cx] = 1;
  }

  _isWalkable(grid, cx, cy) {
    if (cx < 0 || cy < 0 || cx >= grid.cols || cy >= grid.rows) return false;
    return grid.walkable[cy * grid.cols + cx] === 1;
  }

  /**
   * Добавляет двустороннее ребро между ячейками.
   * @param {string} kind — 'walk' | 'stairs' | 'elevator'
   *        (используется для генерации инструкций маршрута)
   */
  _addEdge(fromKey, toKey, cost, kind = 'walk') {
    if (!this.extraEdges.has(fromKey)) this.extraEdges.set(fromKey, []);
    if (!this.extraEdges.has(toKey)) this.extraEdges.set(toKey, []);
    this.extraEdges.get(fromKey).push({ to: toKey, cost, kind });
    this.extraEdges.get(toKey).push({ to: fromKey, cost, kind });
  }

  /** Помечает порталы: дверь и центр каждой аудитории */
  _addRoomPortals() {
    for (const room of rooms) {
      const grid = this.grids.get(room.floorId);
      if (!grid) continue;

      // Дверь
      if (room.door) {
        const dcx = Math.floor(room.door.x / grid.cs);
        const dcy = Math.floor(room.door.y / grid.cs);
        this._setWalkable(grid, dcx, dcy);
        const doorKey = cellKey(room.floorId, dcx, dcy);
        this.cellCenters.set(doorKey, this._cellCenter(grid, dcx, dcy));

        // Центр помещения
        const ccx = Math.floor((room.x + room.w / 2) / grid.cs);
        const ccy = Math.floor((room.y + room.h / 2) / grid.cs);
        this._setWalkable(grid, ccx, ccy);
        const centerKey = cellKey(room.floorId, ccx, ccy);
        this.cellCenters.set(centerKey, this._cellCenter(grid, ccx, ccy));

        // Ребро дверь ↔ центр (стоимость = расстояние в единицах)
        const dx = Math.abs(dcx - ccx);
        const dy = Math.abs(dcy - ccy);
        const cost = Math.hypot(dx, dy) * grid.cs;
        this._addEdge(doorKey, centerKey, cost);
      }
    }
  }

  /** Помечает позиции POI как проходимые (на случай, если точка внутри помещения) */
  _addPoiPortals() {
    for (const poi of pois) {
      const grid = this.grids.get(poi.floorId);
      if (!grid) continue;
      const cx = Math.floor(poi.x / grid.cs);
      const cy = Math.floor(poi.y / grid.cs);
      this._setWalkable(grid, cx, cy);
      this.cellCenters.set(cellKey(poi.floorId, cx, cy), this._cellCenter(grid, cx, cy));
    }
  }

  /** Межэтажные связи (лестницы / лифты) из graph.json */
  _addVerticalLinks() {
    const cost = graphConfig.stairSeconds * graphConfig.walkSpeedMps;
    for (const link of graphConfig.verticalLinks) {
      const gridA = this.grids.get(link.a.floorId);
      const gridB = this.grids.get(link.b.floorId);
      if (!gridA || !gridB) continue;
      const keyA = this._markPoint(gridA, link.a.x, link.a.y);
      const keyB = this._markPoint(gridB, link.b.x, link.b.y);
      this._addEdge(keyA, keyB, cost, link.kind || 'stairs');
    }
  }

  /** Помечает точку как проходимую и возвращает ключ её ячейки */
  _markPoint(grid, x, y) {
    const cx = Math.max(0, Math.min(grid.cols - 1, Math.floor(x / grid.cs)));
    const cy = Math.max(0, Math.min(grid.rows - 1, Math.floor(y / grid.cs)));
    this._setWalkable(grid, cx, cy);
    const key = cellKey(grid.floor.id, cx, cy);
    this.cellCenters.set(key, this._cellCenter(grid, cx, cy));
    return key;
  }

  // --- Публичный API --------------------------------------------------------

  /** Центр ячейки в единицах viewBox */
  centerOf(key) {
    this.build();
    if (this.cellCenters.has(key)) return this.cellCenters.get(key);
    const { floorId, cx, cy } = parseCellKey(key);
    const grid = this.grids.get(floorId);
    if (!grid) return null;
    return this._cellCenter(grid, cx, cy);
  }

  /** Соседи ячейки с учётом доп. рёбер (дороги между этажами) */
  neighbors(key) {
    const result = [];
    const { floorId, cx, cy } = parseCellKey(key);
    const grid = this.grids.get(floorId);
    if (grid) {
      const cs = grid.cs;
      const penalty = graphConfig.wallPenalty;
      for (let dy = -1; dy <= 1; dy += 1) {
        for (let dx = -1; dx <= 1; dx += 1) {
          if (dx === 0 && dy === 0) continue;
          const nx = cx + dx;
          const ny = cy + dy;
          if (!this._isWalkable(grid, nx, ny)) continue;
          let cost = (dx === 0 || dy === 0 ? 1 : Math.SQRT2) * cs;
          // Штраф «у стены»: маршрут стремится идти по центру коридора
          const nearWall =
            !this._isWalkable(grid, nx + 1, ny) ||
            !this._isWalkable(grid, nx - 1, ny) ||
            !this._isWalkable(grid, nx, ny + 1) ||
            !this._isWalkable(grid, nx, ny - 1);
          if (nearWall) cost += penalty;
          result.push({ to: cellKey(floorId, nx, ny), cost });
        }
      }
    }
    const extra = this.extraEdges.get(key);
    if (extra) {
      for (const edge of extra) result.push(edge);
    }
    return result;
  }

  /**
   * Привязка произвольной точки к ближайшей проходимой ячейке.
   * Если точка внутри аудитории — возвращает ячейку её двери.
   */
  snapToWalkable(floorId, x, y, maxRadiusCells = 40) {
    this.build();
    const grid = this.grids.get(floorId);
    if (!grid) return null;

    // Если точка внутри непроходимого помещения — берём его дверь
    for (const room of grid.rooms) {
      if (WALKABLE_ROOM_TYPES.has(room.type)) continue;
      if (pointInRect(x, y, room)) {
        if (room.door) {
          const dcx = Math.floor(room.door.x / grid.cs);
          const dcy = Math.floor(room.door.y / grid.cs);
          this._setWalkable(grid, dcx, dcy);
          return cellKey(floorId, dcx, dcy);
        }
      }
    }

    const startCx = Math.floor(x / grid.cs);
    const startCy = Math.floor(y / grid.cs);
    if (this._isWalkable(grid, startCx, startCy)) return cellKey(floorId, startCx, startCy);

    // Спиральный поиск ближайшей проходимой ячейки
    for (let r = 1; r <= maxRadiusCells; r += 1) {
      for (let dy = -r; dy <= r; dy += 1) {
        for (let dx = -r; dx <= r; dx += 1) {
          if (Math.max(Math.abs(dx), Math.abs(dy)) !== r) continue;
          if (this._isWalkable(grid, startCx + dx, startCy + dy)) {
            return cellKey(floorId, startCx + dx, startCy + dy);
          }
        }
      }
    }
    return null;
  }

  /** Есть ли на этаже проходимые ячейки (для проверок) */
  hasWalkable(floorId) {
    this.build();
    const grid = this.grids.get(floorId);
    if (!grid) return false;
    return grid.walkable.some((v) => v === 1);
  }
}

/** Единственный экземпляр графа (ленивая инициализация) */
let instance = null;

export function getNavGraph() {
  if (!instance) {
    instance = new NavGraph();
    instance.build();
  }
  return instance;
}

/** Расстояние между двумя ячейками в единицах viewBox (эвристика A*) */
export function heuristic(keyA, keyB) {
  const a = parseCellKey(keyA);
  const b = parseCellKey(keyB);
  if (a.floorId !== b.floorId) {
    // Между этажами эвристика не учитывает «плату» за переход — остаётся допустимой
    return Math.hypot(a.cx - b.cx, a.cy - b.cy) * graphConfig.cellSize;
  }
  return Math.hypot(a.cx - b.cx, a.cy - b.cy) * graphConfig.cellSize;
}

/** Масштаб этажа (единицы viewBox на метр) */
export function unitsPerMeter(floorId) {
  return getFloorUnitsPerMeter(floorId);
}
