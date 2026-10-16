import test from 'node:test'
import assert from 'node:assert/strict'
import {
  isVenueRoom,
  openDayVenues,
  readOpenDayEnabled,
  venueByRoomId,
  OPEN_DAY_STORAGE_KEY,
} from './openDay.js'

function fakeStorage(initial = {}) {
  const data = { ...initial }
  return {
    getItem: (key) => (key in data ? data[key] : null),
    setItem: (key, value) => { data[key] = String(value) },
    removeItem: (key) => { delete data[key] },
  }
}

test('every open day venue points at a real room and has details', () => {
  assert.equal(openDayVenues.length, 5)
  const expectedRooms = ['class-138', 'language-148', 'simulator-114', 'training-centre-113', 'radio-lab-228']
  assert.deepEqual(openDayVenues.map((venue) => venue.roomId), expectedRooms)
  for (const venue of openDayVenues) {
    assert.ok(venue.title.length > 0, venue.roomId)
    assert.ok(venue.description.length > 30, venue.roomId)
    assert.ok(venue.leader.length > 0, venue.roomId)
    assert.ok(venue.audience.length > 0, venue.roomId)
    assert.ok(venue.highlights.length >= 3, venue.roomId)
  }
})

test('venue lookup is exact and misses cleanly', () => {
  assert.equal(venueByRoomId('class-138')?.title, 'Кибербез разума')
  assert.equal(venueByRoomId('class-139'), null)
  assert.equal(isVenueRoom('radio-lab-228'), true)
  assert.equal(isVenueRoom('canteen'), false)
})

test('open day flag can be switched off in one place', () => {
  const storage = fakeStorage()
  assert.equal(readOpenDayEnabled(storage, undefined), true, 'default is on')
  storage.setItem(OPEN_DAY_STORAGE_KEY, 'false')
  assert.equal(readOpenDayEnabled(storage, undefined), false, 'switched off in the moment')
  storage.setItem(OPEN_DAY_STORAGE_KEY, 'true')
  assert.equal(readOpenDayEnabled(storage, undefined), true, 'switched back on')
  assert.equal(readOpenDayEnabled(fakeStorage(), 'false'), false, 'build can default it off via VITE_OPEN_DAY=false')
  assert.equal(readOpenDayEnabled(fakeStorage(), 'true'), true)
  assert.equal(readOpenDayEnabled(null, 'false'), false, 'works without localStorage')
})
