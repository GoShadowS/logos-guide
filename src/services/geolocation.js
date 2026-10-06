/**
 * Локальная геопривязка планов к GPS (WGS84).
 *
 * Для каждого этажа используется аффинное преобразование, рассчитанное методом
 * наименьших квадратов по отмеченным пользователем опорным точкам. География
 * сначала переводится в локальные метры (восток/север), поэтому функция
 * не требует сети или картографического SDK.
 */
import calibrationData from '../data/gps-calibration.json';
import floorsData from '../data/floors.json';

const EARTH_RADIUS_METERS = 6371008.8;
const PLAN_EDGE_TOLERANCE_UNITS = 20; // ~2 м: компенсирует округление пользовательских GPS-точек
const radians = (degrees) => (degrees * Math.PI) / 180;
const clamp = (value, min, max) => Math.min(max, Math.max(min, value));
const floorsById = new Map(floorsData.floors.map((floor) => [floor.id, floor]));

function projectToLocalMeters(latitude, longitude, origin) {
  const latitudeRadians = radians((latitude + origin.latitude) / 2);
  return {
    east: EARTH_RADIUS_METERS * radians(longitude - origin.longitude) * Math.cos(latitudeRadians),
    north: EARTH_RADIUS_METERS * radians(latitude - origin.latitude),
  };
}

/** Решает небольшую плотную линейную систему методом Гаусса с выбором ведущего элемента. */
function solveLinearSystem(matrix, vector) {
  const size = vector.length;
  const rows = matrix.map((row, index) => [...row, vector[index]]);

  for (let column = 0; column < size; column += 1) {
    let pivotRow = column;
    for (let row = column + 1; row < size; row += 1) {
      if (Math.abs(rows[row][column]) > Math.abs(rows[pivotRow][column])) pivotRow = row;
    }
    if (Math.abs(rows[pivotRow][column]) < 1e-12) return null;

    [rows[column], rows[pivotRow]] = [rows[pivotRow], rows[column]];
    const pivot = rows[column][column];
    for (let cell = column; cell <= size; cell += 1) rows[column][cell] /= pivot;

    for (let row = 0; row < size; row += 1) {
      if (row === column) continue;
      const factor = rows[row][column];
      for (let cell = column; cell <= size; cell += 1) {
        rows[row][cell] -= factor * rows[column][cell];
      }
    }
  }

  return rows.map((row) => row[size]);
}

function fitAffineTransform(floorId, points) {
  if (!Array.isArray(points) || points.length < 3) return null;
  const origin = {
    latitude: points.reduce((sum, point) => sum + point.latitude, 0) / points.length,
    longitude: points.reduce((sum, point) => sum + point.longitude, 0) / points.length,
  };
  const projected = points.map((point) => ({
    ...projectToLocalMeters(point.latitude, point.longitude, origin),
    x: point.x,
    y: point.y,
  }));

  // x = a*east + b*north + c; y = d*east + e*north + f.
  const normal = [
    [0, 0, 0],
    [0, 0, 0],
    [0, 0, 0],
  ];
  const rightX = [0, 0, 0];
  const rightY = [0, 0, 0];
  for (const point of projected) {
    const row = [point.east, point.north, 1];
    for (let i = 0; i < 3; i += 1) {
      rightX[i] += row[i] * point.x;
      rightY[i] += row[i] * point.y;
      for (let j = 0; j < 3; j += 1) normal[i][j] += row[i] * row[j];
    }
  }

  const xCoefficients = solveLinearSystem(normal, rightX);
  const yCoefficients = solveLinearSystem(normal, rightY);
  if (!xCoefficients || !yCoefficients) return null;

  const floor = floorsById.get(floorId);
  if (!floor) return null;
  const residuals = projected.map((point) => {
    const x = xCoefficients[0] * point.east + xCoefficients[1] * point.north + xCoefficients[2];
    const y = yCoefficients[0] * point.east + yCoefficients[1] * point.north + yCoefficients[2];
    return Math.hypot(x - point.x, y - point.y) / floor.unitsPerMeter;
  });

  const eastScale = Math.hypot(xCoefficients[0], yCoefficients[0]);
  const northScale = Math.hypot(xCoefficients[1], yCoefficients[1]);
  return {
    origin,
    xCoefficients,
    yCoefficients,
    unitsPerMeter: (eastScale + northScale) / 2,
    rmsErrorMeters: Math.sqrt(residuals.reduce((sum, value) => sum + value * value, 0) / residuals.length),
    maxErrorMeters: Math.max(...residuals),
    pointCount: points.length,
  };
}

const transformsByFloor = new Map(
  Object.entries(calibrationData.floors).map(([floorId, value]) => [
    floorId,
    fitAffineTransform(floorId, value.controlPoints),
  ])
);

/**
 * Преобразует GPS-координату в координату плана указанного этажа.
 * Возвращает null для некорректной позиции или этажа без калибровки.
 */
export function gpsToPlanLocation(floorId, latitude, longitude, accuracyMeters = null) {
  const transform = transformsByFloor.get(floorId);
  if (
    !transform ||
    !Number.isFinite(latitude) ||
    latitude < -90 ||
    latitude > 90 ||
    !Number.isFinite(longitude) ||
    longitude < -180 ||
    longitude > 180
  ) {
    return null;
  }

  const { east, north } = projectToLocalMeters(latitude, longitude, transform.origin);
  const [xEast, xNorth, xOffset] = transform.xCoefficients;
  const [yEast, yNorth, yOffset] = transform.yCoefficients;
  const rawX = xEast * east + xNorth * north + xOffset;
  const rawY = yEast * east + yNorth * north + yOffset;
  if (!Number.isFinite(rawX) || !Number.isFinite(rawY)) return null;

  const floor = floorsById.get(floorId);
  const withinPlan =
    rawX >= -PLAN_EDGE_TOLERANCE_UNITS && rawX <= floor.width + PLAN_EDGE_TOLERANCE_UNITS &&
    rawY >= -PLAN_EDGE_TOLERANCE_UNITS && rawY <= floor.height + PLAN_EDGE_TOLERANCE_UNITS;
  // Небольшой выход за край возникает из-за округления исходных координат;
  // при таком пограничном случае фиксируем точку на границе холста.
  const x = withinPlan ? clamp(rawX, 0, floor.width) : rawX;
  const y = withinPlan ? clamp(rawY, 0, floor.height) : rawY;
  const accuracy = Number.isFinite(accuracyMeters) && accuracyMeters >= 0 ? accuracyMeters : null;
  return {
    floorId,
    x,
    y,
    latitude,
    longitude,
    accuracyMeters: accuracy,
    accuracyUnits: accuracy == null ? null : accuracy * transform.unitsPerMeter,
    calibrationRmsErrorMeters: transform.rmsErrorMeters,
    calibrationMaxErrorMeters: transform.maxErrorMeters,
    calibrationPointCount: transform.pointCount,
    withinPlan,
  };
}

/** Диагностика геопривязки для тестов и экранов «О приложении». */
export function getGpsCalibrationDiagnostics(floorId) {
  const transform = transformsByFloor.get(floorId);
  if (!transform) return null;
  return {
    pointCount: transform.pointCount,
    rmsErrorMeters: transform.rmsErrorMeters,
    maxErrorMeters: transform.maxErrorMeters,
  };
}

export default gpsToPlanLocation;
