import { useEffect, useRef } from 'react'
import { rooms } from '../data.js'
import floorOnePlan from '../../map1lower.svg?raw'
import floorTwoPlan from '../../map2lower.svg?raw'
import { roomOutline, planSizes } from '../map-geometry.js'
import { calculateIndoorRoute } from '../map-routing.js'

function FloorRoom({ room, selected, onSelect, onFloorRequest, gameTargetId, gameFeedback, filterCategory, venue }) {
  const isTarget = gameTargetId === room.id
  const className = [
    'plan-room',
    selected ? 'plan-room-selected' : '',
    isTarget ? 'plan-room-target' : '',
    gameFeedback === room.id ? 'plan-room-correct' : '',
    filterCategory !== 'Все места' && room.category !== filterCategory ? 'plan-room-muted' : '',
  ].filter(Boolean).join(' ')
  const stair = room.id.match(/^(stairs-(?:north|middle|west))-[12]$/)
  const touchStart = useRef(null)
  const lastTap = useRef({ time: 0, x: 0, y: 0 })
  const switchedAt = useRef(0)
  const skipClick = useRef(false)
  const activate = () => {
    if (skipClick.current) { skipClick.current = false; return }
    onSelect?.(room)
  }
  const switchFloor = () => {
    const nextFloor = room.floor === 1 ? 2 : 1
    onFloorRequest(nextFloor, rooms.find((item) => item.id === `${stair[1]}-${nextFloor}`))
  }
  const onKeyDown = (event) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      activate()
    }
  }
  const onDoubleClick = (event) => {
    if (!stair || !onFloorRequest) return
    event.preventDefault()
    event.stopPropagation()
    if (Date.now() - switchedAt.current > 600) switchFloor()
  }
  const onPointerDown = (event) => {
    if (event.pointerType === 'touch') touchStart.current = [event.clientX, event.clientY]
  }
  const onPointerUp = (event) => {
    if (!stair || !onFloorRequest || event.pointerType !== 'touch' || !touchStart.current) return
    const [x, y] = touchStart.current
    touchStart.current = null
    if (Math.hypot(event.clientX - x, event.clientY - y) > 16) { lastTap.current.time = 0; return }
    const now = Date.now()
    const prior = lastTap.current
    if (now - prior.time < 450 && Math.hypot(event.clientX - prior.x, event.clientY - prior.y) < 36) {
      lastTap.current.time = 0
      switchedAt.current = now
      skipClick.current = true // Touch browsers may still dispatch click after pointerup.
      event.preventDefault()
      event.stopPropagation()
      switchFloor()
    } else lastTap.current = { time: now, x: event.clientX, y: event.clientY }
  }

  return <g className={className} role={onSelect ? 'button' : 'presentation'} tabIndex={onSelect ? 0 : undefined} aria-label={onSelect ? `${room.name}${room.number ? `, кабинет ${room.number}` : ''}, ${room.floor} этаж${stair ? '. Дважды нажмите, чтобы перейти на другой этаж' : ''}` : undefined} aria-pressed={onSelect ? selected : undefined} onClick={onSelect ? activate : undefined} onDoubleClick={onFloorRequest ? onDoubleClick : undefined} onKeyDown={onSelect ? onKeyDown : undefined} onPointerDown={stair && onFloorRequest ? onPointerDown : undefined} onPointerUp={stair && onFloorRequest ? onPointerUp : undefined}>
    <title>{`${room.name}${room.number ? ` · ${room.number}` : ''} · ${room.floor} этаж`}</title>
    <polygon className="plan-room-shape" points={roomOutline(room.id).map((point) => point.join(',')).join(' ')} />
    {venue && (() => {
      const outline = roomOutline(room.id)
      const cx = outline.reduce((sum, point) => sum + point[0], 0) / outline.length
      const top = Math.min(...outline.map((point) => point[1]))
      const cy = top + 15
      return <g className="plan-venue-badge" aria-hidden="true"><circle className="plan-venue-dot" cx={cx} cy={cy} r="11" /><text className="plan-venue-star" x={cx} y={cy + 4.5} textAnchor="middle">★</text></g>
    })()}
    {isTarget && <circle className="plan-target-halo" cx={roomOutline(room.id).reduce((sum, point) => sum + point[0], 0) / roomOutline(room.id).length} cy={roomOutline(room.id).reduce((sum, point) => sum + point[1], 0) / roomOutline(room.id).length} r="18" />}
  </g>
}

function LocationMarker({ point, kind, radius }) {
  const [x, y] = point
  return <g className={`plan-position plan-position-${kind}`} aria-hidden="true">
    {kind === 'gps' && radius && <circle className="plan-position-accuracy" cx={x} cy={y} r={Math.min(radius, 130)} />}
    <circle className="plan-position-halo" cx={x} cy={y} r="13" />
    <circle className="plan-position-core" cx={x} cy={y} r="6.5" />
    <circle className="plan-position-center" cx={x} cy={y} r="2" />
    <text x={x + 12} y={y - 13}>{kind === 'manual' ? 'Ваша отметка' : kind === 'anchor' ? 'Точка привязки' : 'GPS · примерно'}</text>
  </g>
}

export default function FloorMap({ floor = 1, selectedId = '', onSelect, onFloorRequest, onPointSelect, picking = false, gameTargetId = '', gameFeedback = '', filterCategory = 'Все места', compact = false, zoomed = false, showLabels = true, showDoorMarks = true, routeStart = '', routeTargetId = '', manualPoint = null, gpsMarker = null, transition = 'lift', venueRoomIds = [] }) {
  const floorRooms = rooms.filter((room) => room.floor === floor)
  const plan = floor === 1 ? floorOnePlan : floorTwoPlan
  const route = routeStart && routeTargetId ? calculateIndoorRoute(routeStart, routeTargetId, floor, transition) : null
  const { width, height } = planSizes[floor]
  const viewportRef = useRef(null)

  useEffect(() => {
    const viewport = viewportRef.current
    if (!viewport || compact) return
    const room = rooms.find((item) => item.floor === floor && item.id === selectedId)
    const outline = room ? roomOutline(room.id) : null
    const focusPoint = outline ? [outline.reduce((sum, point) => sum + point[0], 0) / outline.length, outline.reduce((sum, point) => sum + point[1], 0) / outline.length] :
      manualPoint?.floor === floor ? [manualPoint.x, manualPoint.y] : null
    if (!focusPoint) return
    const stage = viewport.querySelector('.floor-map-stage')
    if (!stage) return
    const scale = stage.getBoundingClientRect().width / width
    // Don't move a staircase between the first and second tap on a phone.
    const timer = window.setTimeout(() => {
      viewport.scrollLeft = Math.max(0, focusPoint[0] * scale - viewport.clientWidth / 2)
      viewport.scrollTop = Math.max(0, focusPoint[1] * scale - viewport.clientHeight / 2)
    }, room?.id.startsWith('stairs-') ? 540 : 340)
    return () => window.clearTimeout(timer)
  }, [compact, floor, manualPoint?.floor, manualPoint?.x, manualPoint?.y, selectedId, width, zoomed])

  const showLowerFloor = () => onFloorRequest?.(1, rooms.find((room) => room.id === 'auditorium-104'))
  const onGhostKeyDown = (event) => {
    if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); showLowerFloor() }
  }
  const onMapClickCapture = (event) => {
    if (!picking || !onPointSelect) return
    const svg = event.currentTarget
    const matrix = svg.getScreenCTM()
    const coordinates = matrix && typeof DOMPoint !== 'undefined' ? new DOMPoint(event.clientX, event.clientY).matrixTransform(matrix.inverse()) : null
    const rect = svg.getBoundingClientRect()
    const point = coordinates ? [coordinates.x, coordinates.y] : [((event.clientX - rect.left) / rect.width) * width, ((event.clientY - rect.top) / rect.height) * height]
    event.preventDefault()
    event.stopPropagation() // Do not select a room while placing a precise pin.
    onPointSelect({ floor, x: Math.round(point[0] * 10) / 10, y: Math.round(point[1] * 10) / 10 })
  }

  return <div ref={viewportRef} className={`floor-map-viewport ${compact ? 'floor-map-compact' : ''} ${zoomed ? 'floor-map-zoomed' : ''} ${picking ? 'floor-map-picking' : ''} ${showLabels ? '' : 'floor-map-labels-hidden'} ${showDoorMarks ? '' : 'floor-map-doors-hidden'}`}>
    <div className={`floor-map-stage floor-map-stage-floor-${floor}`}>
      <div className="floor-map-art" aria-hidden="true" dangerouslySetInnerHTML={{ __html: plan }} />
      <svg className="floor-plan-svg" viewBox={`0 0 ${width} ${height}`} role="group" aria-label={`Интерактивная карта, ${floor} этаж`} onClickCapture={onMapClickCapture}>
        <title>{`${floor} этаж колледжа`}</title>
        <desc>{picking ? 'Коснитесь точки на плане, чтобы установить отметку.' : 'Выберите помещение, чтобы открыть описание. Двойной щелчок по лестнице переключает этаж.'}</desc>
        {floorRooms.map((room) => <FloorRoom key={room.id} room={room} selected={selectedId === room.id} onSelect={onSelect} onFloorRequest={onFloorRequest} gameTargetId={gameTargetId} gameFeedback={gameFeedback} filterCategory={filterCategory} venue={venueRoomIds.includes(room.id)} />)}
        {route?.points.length > 1 && <g className="plan-route"><polyline points={route.points.map(([x, y]) => `${x},${y}`).join(' ')} /><circle cx={route.points[0][0]} cy={route.points[0][1]} r="6" /><circle className="plan-route-destination" cx={route.points.at(-1)[0]} cy={route.points.at(-1)[1]} r="7" /></g>}
        {floor === 2 && onFloorRequest && <g className="plan-downstairs-link" role="button" tabIndex={0} aria-label="Актовый зал на первом этаже. Открыть первый этаж" onClick={showLowerFloor} onKeyDown={onGhostKeyDown}><rect x="610" y="94" width="156" height="150" rx="2" /></g>}
        {manualPoint?.floor === floor && <LocationMarker point={[manualPoint.x, manualPoint.y]} kind="manual" />}
        {gpsMarker?.floor === floor && <LocationMarker point={gpsMarker.point} radius={gpsMarker.radius} kind={gpsMarker.mode} />}
      </svg>
    </div>
  </div>
}
