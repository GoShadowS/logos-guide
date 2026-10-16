// Coordinates are in the viewBox of the original floor SVGs. Keep these bounds,
// the clickable areas and the routing doorways in sync when changing the plan.
export const planSizes = {
  1: { width: 758, height: 552 },
  2: { width: 767, height: 561 },
}

export const planRoomBounds = {
  canteen: [0, 0, 147, 156],
  'workshop-115': [148, 94, 109, 62],
  'simulator-114': [257, 94, 84, 62],
  'academic-office': [341, 94, 184, 62],
  'lift-1': [525, 94, 24, 62],
  'stairs-north-1': [549, 94, 61, 62],
  'auditorium-104': [610, 94, 147, 150],
  'class-119': [0, 179, 62, 74],
  'class-118': [0, 253, 62, 73],
  'toilet-men-1': [86, 179, 84, 32],
  'toilet-women-1': [86, 210, 84, 32],
  'language-148': [168, 179, 77, 63],
  'aerodynamics-120': [244, 179, 74, 63],
  'stairs-middle-1': [318, 179, 64, 63],
  'director-149': [382, 179, 68, 63],
  'changing-room': [449, 179, 99, 63],
  'hall-1': [548, 179, 62, 63],
  'entrance-1': [548, 240, 62, 24],
  'stairs-west-1': [0, 322, 62, 64],
  'class-139': [86, 306, 63, 69],
  'class-138': [86, 375, 63, 68],
  'class-162': [0, 419, 62, 49],
  'training-centre-113': [0, 468, 149, 84],

  'library-214': [0, 0, 144, 114],
  'radio-lab-228': [144, 94, 115, 63],
  'electro-radio-230': [259, 94, 98, 63],
  'assistant-230': [357, 94, 66, 63],
  'uav-lab-209': [423, 94, 112, 63],
  'lift-2': [535, 94, 26, 63],
  'stairs-north-2': [561, 94, 49, 63],
  'meteorology-236': [0, 182, 62, 97],
  'toilet-men-2': [86, 182, 62, 32],
  'toilet-women-2': [86, 214, 62, 34],
  'physics-211': [168, 182, 154, 64],
  'stairs-middle-2': [322, 182, 70, 64],
  'psychologist-224': [392, 182, 33, 64],
  'class-217': [425, 182, 55, 64],
  'class-229': [480, 182, 31, 64],
  'informatics-222': [511, 182, 50, 64],
  'stairs-west-2': [0, 279, 62, 138],
  'chemistry-biology-202': [86, 245, 62, 83],
  'assistant-202': [86, 328, 62, 55],
  'practical-225': [86, 383, 62, 34],
  // Room 207 wraps around the corridor's lower end; room 225 is rectangular.
  'electrical-lab-207': [0, 417, 86, 143],
  'biotech-lab-225': [86, 417, 62, 143],
}

const irregularRooms = {
  'electrical-lab-207': [[0, 417], [62, 417], [62, 477], [86, 477], [86, 560], [0, 560]],
}

export function roomOutline(id) {
  if (irregularRooms[id]) return irregularRooms[id]
  const bounds = planRoomBounds[id]
  if (!bounds) return []
  const [x, y, width, height] = bounds
  return [[x, y], [x + width, y], [x + width, y + height], [x, y + height]]
}

// Main corridor: y=168, west wing: x=73. The other two branches are the
// first-floor entrance hall and the second-floor lobby under the library.
// Only these surveyed circulation lines are used for routing; no straight
// line is drawn through rooms to reach a 'nearest node'.
export const circulation = {
  1: [
    [[0, 156], [610, 156], [610, 179], [0, 179]],
    [[62, 179], [86, 179], [86, 468], [62, 468]],
    [[548, 179], [610, 179], [610, 242], [548, 242]],
  ],
  2: [
    [[0, 114], [144, 114], [144, 182], [0, 182]],
    [[144, 156], [610, 156], [610, 182], [144, 182]],
    [[62, 182], [86, 182], [86, 477], [62, 477]],
  ],
}

// The point on each wall where the room meets a real corridor. 'west' doors
// face x=73; 'main' doors face y=168. Special branches have explicit paths.
export const roomAccess = {
  canteen: { door: [94, 156], branch: 'main' },
  'workshop-115': { door: [230, 156], branch: 'main' },
  'simulator-114': { door: [279, 156], branch: 'main' },
  'academic-office': { door: [412, 156], branch: 'main' },
  'lift-1': { door: [537, 156], branch: 'main' },
  'stairs-north-1': { door: [579, 156], branch: 'main' },
  // The auditorium opens off the entrance hall, not straight through its
  // north wall at the far end of the main corridor.
  'auditorium-104': { door: [610, 205], branch: 'auditorium' },
  'class-119': { door: [62, 246], branch: 'west' },
  'class-118': { door: [62, 294], branch: 'west' },
  'toilet-men-1': { door: [86, 205], branch: 'west' },
  'toilet-women-1': { door: [86, 237], branch: 'west' },
  'language-148': { door: [183, 181], branch: 'main' },
  'aerodynamics-120': { door: [261, 181], branch: 'main' },
  'stairs-middle-1': { door: [359, 181], branch: 'main' },
  'director-149': { door: [416, 181], branch: 'main' },
  'changing-room': { door: [505, 181], branch: 'main' },
  'hall-1': { door: [579, 179], branch: 'hall' },
  'entrance-1': { door: [579, 241], branch: 'hall' },
  'stairs-west-1': { door: [62, 354], branch: 'west' },
  'class-139': { door: [86, 367], branch: 'west' },
  'class-138': { door: [86, 434], branch: 'west' },
  'class-162': { door: [62, 450], branch: 'west' },
  'training-centre-113': { door: [73, 468], branch: 'west' },

  'library-214': { door: [116, 114], branch: 'library' },
  'radio-lab-228': { door: [217, 156], branch: 'main' },
  'electro-radio-230': { door: [317, 156], branch: 'main' },
  'assistant-230': { door: [390, 156], branch: 'main' },
  'uav-lab-209': { door: [459, 156], branch: 'main' },
  'lift-2': { door: [548, 156], branch: 'main' },
  'stairs-north-2': { door: [585, 156], branch: 'main' },
  'meteorology-236': { door: [62, 246], branch: 'west' },
  'toilet-men-2': { door: [86, 193], branch: 'west' },
  'toilet-women-2': { door: [86, 224], branch: 'west' },
  'physics-211': { door: [249, 182], branch: 'main' },
  'stairs-middle-2': { door: [355, 182], branch: 'main' },
  'psychologist-224': { door: [409, 182], branch: 'main' },
  'class-217': { door: [440, 182], branch: 'main' },
  'class-229': { door: [495, 182], branch: 'main' },
  'informatics-222': { door: [526, 182], branch: 'main' },
  'stairs-west-2': { door: [62, 360], branch: 'west' },
  'chemistry-biology-202': { door: [86, 263], branch: 'west' },
  'assistant-202': { door: [86, 355], branch: 'west' },
  'practical-225': { door: [86, 398], branch: 'west' },
  'electrical-lab-207': { door: [62, 450], branch: 'west' },
  'biotech-lab-225': { door: [86, 450], branch: 'west' },
}

export function pointInPolygon(point, polygon) {
  if (!point || !Array.isArray(polygon) || polygon.length < 3) return false
  const [x, y] = point
  let inside = false
  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
    const [xi, yi] = polygon[i]
    const [xj, yj] = polygon[j]
    const cross = (x - xi) * (yj - yi) - (y - yi) * (xj - xi)
    if (Math.abs(cross) < 1e-6 && x >= Math.min(xi, xj) && x <= Math.max(xi, xj) && y >= Math.min(yi, yj) && y <= Math.max(yi, yj)) return true
    if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside
  }
  return inside
}

export function roomAtPoint(floor, point, floorRooms) {
  return floorRooms.find((room) => room.floor === floor && pointInPolygon(point, roomOutline(room.id))) || null
}

export function isInsidePlan(floor, point, floorRooms) {
  const size = planSizes[floor]
  if (!size || !point || !point.every(Number.isFinite) || point[0] < 0 || point[1] < 0 || point[0] > size.width || point[1] > size.height) return false
  return Boolean(roomAtPoint(floor, point, floorRooms)) || circulation[floor].some((polygon) => pointInPolygon(point, polygon))
}

export function isManualPoint(value, floorRooms) {
  return value && (value.floor === 1 || value.floor === 2) && isInsidePlan(value.floor, [value.x, value.y], floorRooms)
}
