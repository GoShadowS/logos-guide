import { rooms } from './data.js'
import { isInsidePlan, isManualPoint } from './map-geometry.js'

const EARTH_METRES_PER_DEGREE = 111_320

export function readManualLocation(raw) {
  try {
    const value = JSON.parse(raw)
    return isManualPoint(value, rooms) ? value : null
  } catch { return null }
}

export function validAnchor(value, floor) {
  return value && value.floor === floor && isManualPoint(value, rooms) &&
    Number.isFinite(value.latitude) && Math.abs(value.latitude) <= 90 &&
    Number.isFinite(value.longitude) && Math.abs(value.longitude) <= 180
}

export function readGpsAnchors(raw) {
  try {
    const saved = JSON.parse(raw)
    return Object.fromEntries([1, 2].map((floor) => [floor, Array.isArray(saved?.[floor]) ? saved[floor].filter((item) => validAnchor(item, floor)).slice(0, 2) : []]))
  } catch { return { 1: [], 2: [] } }
}

function geoDelta(origin, point) {
  const latitude = origin.latitude * Math.PI / 180
  return [
    (point.longitude - origin.longitude) * EARTH_METRES_PER_DEGREE * Math.cos(latitude),
    -(point.latitude - origin.latitude) * EARTH_METRES_PER_DEGREE,
  ]
}

export function addGpsAnchor(anchors, floor, point, coords) {
  const current = anchors[floor] || []
  const next = { ...point, floor, latitude: coords.latitude, longitude: coords.longitude }
  if (!validAnchor(next, floor)) return anchors
  if (current.length === 1 && Math.hypot(...geoDelta(current[0], next)) < 3) return anchors
  return { ...anchors, [floor]: current.length === 1 ? [current[0], next] : [next] }
}

// A pair of user-recorded GPS ↔ plan points gives a 2D similarity transform.
// One point is only an anchor, not indoor positioning: never pretend it moves.
export function projectGpsPoint(coords, anchors) {
  if (!coords || !anchors?.length) return null
  const first = anchors[0]
  if (anchors.length === 1) return { point: [first.x, first.y], mode: 'anchor', radius: null }
  const second = anchors[1]
  const [dx, dy] = geoDelta(first, second)
  const metresSquared = dx * dx + dy * dy
  if (metresSquared < 9) return { point: [first.x, first.y], mode: 'anchor', radius: null }
  const planX = second.x - first.x
  const planY = second.y - first.y
  const [east, south] = geoDelta(first, coords)
  // [a -b; b a], where a,b express scale and rotation of the plan.
  const a = (planX * dx + planY * dy) / metresSquared
  const b = (planY * dx - planX * dy) / metresSquared
  return {
    point: [first.x + a * east - b * south, first.y + b * east + a * south],
    radius: Math.max(0, coords.accuracy || 0) * Math.hypot(a, b),
    mode: 'gps',
  }
}

export function gpsPointOnPlan(floor, coords, anchors) {
  const projection = projectGpsPoint(coords, anchors[floor])
  if (!projection || !isInsidePlan(floor, projection.point, rooms)) return null
  return { ...projection, floor }
}
