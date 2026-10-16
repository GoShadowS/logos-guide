import test from 'node:test'
import assert from 'node:assert/strict'
import { addGpsAnchor, gpsPointOnPlan, projectGpsPoint, readGpsAnchors, readManualLocation } from './map-location.js'

test('saved locations are checked against the building footprint', () => {
  assert.deepEqual(readManualLocation('{"floor":2,"x":210,"y":168}'), { floor: 2, x: 210, y: 168 })
  assert.deepEqual(readManualLocation('{"floor":2,"x":73,"y":300}'), { floor: 2, x: 73, y: 300 }, 'the west corridor is a valid location')
  assert.deepEqual(readManualLocation('{"floor":2,"x":100,"y":530}'), { floor: 2, x: 100, y: 530 }, 'room 225 is inside the plan')
  assert.equal(readManualLocation('{"floor":2,"x":160,"y":500}'), null, 'the void beside room 225 is outside the plan')
  assert.equal(readManualLocation('{"floor":2,"x":700,"y":560}'), null)
  assert.equal(readManualLocation('not json'), null)
  assert.deepEqual(readGpsAnchors('not json'), { 1: [], 2: [] })
})

test('GPS is not converted to a fake indoor position without two measured anchors', () => {
  const first = { floor: 1, x: 200, y: 168, latitude: 59.3, longitude: 18.05 }
  const second = { floor: 1, x: 280, y: 168, latitude: 59.3, longitude: 18.0502 }
  let anchors = { 1: [], 2: [] }
  assert.equal(projectGpsPoint(first, anchors[1]), null)
  anchors = addGpsAnchor(anchors, 1, first, first)
  assert.deepEqual(projectGpsPoint(second, anchors[1]).point, [200, 168])
  assert.equal(projectGpsPoint(second, anchors[1]).mode, 'anchor')
  anchors = addGpsAnchor(anchors, 1, second, second)
  const current = { latitude: 59.3, longitude: 18.0501, accuracy: 8 }
  const result = gpsPointOnPlan(1, current, anchors)
  assert.equal(result.mode, 'gps')
  assert.ok(Math.abs(result.point[0] - 240) < .001)
  assert.ok(Math.abs(result.point[1] - 168) < .001)
  assert.ok(result.radius > 0)
  assert.equal(gpsPointOnPlan(2, current, anchors), null, 'a browser cannot detect the floor')
  assert.equal(gpsPointOnPlan(1, { ...current, longitude: 18.1 }, anchors), null, 'no pin outside the building')
})
