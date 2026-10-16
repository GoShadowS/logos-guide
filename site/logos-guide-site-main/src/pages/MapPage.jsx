import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ArrowRight, ArrowUpRight, Building2, DoorOpen, Info, LocateFixed, MapPin, Search, Settings2, Sparkles, X, ZoomIn, ZoomOut } from 'lucide-react'
import { categoryLabels, floors, rooms } from '../data.js'
import FloorMap from '../components/FloorMap.jsx'
import VenueModal from '../components/VenueModal.jsx'
import { isVenueRoom, openDayVenues, venueByRoomId } from '../openDay.js'
import { useOpenDay } from '../useOpenDay.js'
import { FloorTag, PageHeading, Reveal } from '../components/Primitives.jsx'
import { isInsidePlan, planRoomBounds } from '../map-geometry.js'
import { addGpsAnchor, gpsPointOnPlan, readGpsAnchors, readManualLocation } from '../map-location.js'
import { storageGet, storageRemove, storageSet } from '../storage.js'

const otherFloor = (floor) => floor === 1 ? 2 : 1

export default function MapPage() {
  const [params, setParams] = useSearchParams()
  const floor = Number(params.get('floor')) === 2 ? 2 : 1
  const selectedId = params.get('room') || params.get('to') || ''
  const selected = rooms.find((room) => room.id === selectedId && room.floor === floor)
  const floorRooms = useMemo(() => rooms.filter((room) => room.floor === floor), [floor])
  const [filter, setFilter] = useState('Все места')
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [showLabels, setShowLabels] = useState(() => storageGet('map-show-labels') !== 'false')
  const [showDoorMarks, setShowDoorMarks] = useState(() => storageGet('map-show-doors') !== 'false')
  const [zoomed, setZoomed] = useState(() => window.matchMedia('(max-width: 680px)').matches)
  const [gpsEnabled, setGpsEnabled] = useState(false)
  const [gpsStatus, setGpsStatus] = useState('idle')
  const [gpsCoordinates, setGpsCoordinates] = useState(null)
  const [gpsAnchors, setGpsAnchors] = useState(() => readGpsAnchors(storageGet('map-gps-anchors')))
  const [manualPoint, setManualPoint] = useState(() => {
    const saved = readManualLocation(storageGet('map-manual-point'))
    if (saved) return saved
    const oldRoom = rooms.find((room) => room.id === storageGet('map-manual-location'))
    if (!oldRoom) return null
    const [x, y, width, height] = planRoomBounds[oldRoom.id]
    return { floor: oldRoom.floor, x: x + width / 2, y: y + height / 2 }
  })
  const [picking, setPicking] = useState(() => params.get('manual') === '1' ? 'manual' : '')
  const [pointFeedback, setPointFeedback] = useState('')
  const [openDay] = useOpenDay()
  const [venueModalId, setVenueModalId] = useState('')
  const autoShownVenue = useRef('')

  useEffect(() => {
    if (!openDay) { setVenueModalId(''); autoShownVenue.current = ''; return }
    if (!selectedId || venueModalId || autoShownVenue.current === selectedId) return
    if (isVenueRoom(selectedId)) {
      autoShownVenue.current = selectedId
      setVenueModalId(selectedId)
    }
  }, [openDay, selectedId, venueModalId])

  const fromParam = params.get('from') || ''
  const routeStart = fromParam === 'manual' ? manualPoint : fromParam || (selected && manualPoint ? manualPoint : '')
  const routeTargetId = params.get('to') || selected?.id || ''
  const anchorsHere = gpsAnchors[floor] || []
  const gpsMarker = gpsEnabled && gpsStatus === 'active' ? gpsPointOnPlan(floor, gpsCoordinates, gpsAnchors) : null
  const stair = selected?.id.match(/^(stairs-(?:north|middle|west))-[12]$/)

  useEffect(() => {
    storageSet('map-show-labels', String(showLabels))
    storageSet('map-show-doors', String(showDoorMarks))
  }, [showLabels, showDoorMarks])
  useEffect(() => {
    if (manualPoint) storageSet('map-manual-point', JSON.stringify(manualPoint))
    else storageRemove('map-manual-point')
    storageRemove('map-manual-location')
  }, [manualPoint])
  useEffect(() => { storageSet('map-gps-anchors', JSON.stringify(gpsAnchors)) }, [gpsAnchors])
  useEffect(() => {
    if (!gpsEnabled) return undefined
    if (!window.isSecureContext || !navigator.geolocation) {
      setGpsStatus('unsupported')
      setGpsEnabled(false)
      return undefined
    }
    setGpsStatus('loading')
    const watchId = navigator.geolocation.watchPosition(
      ({ coords }) => {
        setGpsCoordinates({ latitude: coords.latitude, longitude: coords.longitude, accuracy: coords.accuracy })
        setGpsStatus('active')
      },
      (error) => { setGpsStatus(error.code === 1 ? 'denied' : 'unavailable'); setGpsEnabled(false) },
      { enableHighAccuracy: true, maximumAge: 5000, timeout: 15000 },
    )
    return () => navigator.geolocation.clearWatch(watchId)
  }, [gpsEnabled])

  const chooseFloor = (nextFloor, room = null, clear = false) => {
    setParams((previous) => {
      const next = new URLSearchParams(previous)
      next.set('floor', String(nextFloor))
      if (room) next.set('room', room.id)
      else next.delete('room')
      if (clear) { next.delete('from'); next.delete('to') }
      next.delete('manual')
      return next
    }, { replace: true })
  }
  const selectRoom = (room) => {
    if (picking) {
      const [x, y, width, height] = planRoomBounds[room.id]
      placePoint({ floor: room.floor, x: x + width / 2, y: y + height / 2 })
      return
    }
    if (openDay && isVenueRoom(room.id)) {
      autoShownVenue.current = room.id
      setVenueModalId(room.id)
    }
    setParams((previous) => {
      const next = new URLSearchParams(previous)
      next.set('floor', String(room.floor))
      next.set('room', room.id)
      if (next.has('from') || next.has('to')) next.set('to', room.id)
      return next
    }, { replace: true })
  }
  const placePoint = (point) => {
    if (!isInsidePlan(point.floor, [point.x, point.y], rooms)) {
      setPointFeedback('Эта точка вне помещений и проходов. Коснитесь плана внутри здания.')
      return
    }
    if (picking === 'gps') {
      if (!gpsCoordinates) return
      const next = addGpsAnchor(gpsAnchors, point.floor, point, gpsCoordinates)
      if (next === gpsAnchors) {
        setPointFeedback('Для второй точки привязки отойдите хотя бы на 3 метра и попробуйте снова.')
        return
      }
      setGpsAnchors(next)
      setPointFeedback(next[point.floor].length === 1 ? 'Первая точка привязана. Отойдите к другому известному месту и повторите привязку.' : 'Две точки привязаны. Синяя метка приблизительно следует за координатами GPS.')
    } else {
      setManualPoint(point)
      setPointFeedback('Метка поставлена. Теперь выберите место назначения.')
    }
    setPicking('')
    if (params.has('manual')) setParams((previous) => {
      const next = new URLSearchParams(previous)
      next.delete('manual')
      return next
    }, { replace: true })
  }
  const toggleGps = () => {
    if (gpsEnabled) {
      setGpsEnabled(false)
      setGpsCoordinates(null)
      setGpsStatus('idle')
      if (picking === 'gps') setPicking('')
    } else {
      setGpsStatus('loading')
      setGpsEnabled(true)
    }
  }
  const gpsMessage = () => {
    if (gpsStatus === 'loading') return 'Определяем координаты… Разрешите доступ к геолокации в браузере.'
    if (gpsStatus === 'denied') return 'Доступ к геолокации запрещён. Разрешите его в настройках браузера или поставьте метку вручную.'
    if (gpsStatus === 'unsupported') return 'GPS доступен только через HTTPS в браузере с геолокацией. Используйте ручную отметку.'
    if (gpsStatus === 'unavailable') return 'Координаты не получены. Попробуйте ещё раз или поставьте метку вручную.'
    if (gpsStatus !== 'active') return ''
    const accuracy = Math.round(gpsCoordinates?.accuracy || 0)
    if (!anchorsHere.length) return `Координаты получены (точность около ${accuracy} м), но план не привязан к местности. Привяжите GPS к двум известным точкам на этом этаже или поставьте метку вручную.`
    if (anchorsHere.length === 1) return `Показана первая точка привязки, а не отслеживаемое положение. Для движения метки нужна вторая точка. Точность GPS около ${accuracy} м.`
    if (!gpsMarker) return 'Положение оказалось вне плана этажа. Метка скрыта; проверьте привязку или используйте ручную отметку.'
    return `Положение на схеме приблизительное (GPS ±${accuracy} м). Этаж выбран вручную: спутники не определяют этаж и кабинет.${accuracy > 25 ? ' В помещении лучше поставить метку вручную.' : ''}`
  }

  return <>
    <PageHeading eyebrow="Карта колледжа" title={<>Найти нужный<br /><em>кабинет.</em></>} description="Выберите этаж и коснитесь помещения на плане. Покажем номер, вход и путь по коридорам." />
    <section className="map-page-section container">
      <Reveal className="map-control-row"><div className="floor-tabs" role="group" aria-label="Выбор этажа">{floors.map((item) => <button key={item.id} type="button" aria-pressed={floor === item.id} className={`floor-tab ${floor === item.id ? 'floor-tab-active' : ''}`} onClick={() => chooseFloor(item.id)}><span className="floor-tab-index">{item.short}</span><span><strong>{item.label}</strong><small>{item.hint}</small></span><i aria-hidden="true">{floor === item.id && <ArrowUpRight size={16} />}</i></button>)}</div><Link className="map-directory-link" to="/rooms"><Search size={16} /> Не знаете номер? <strong>Открыть справочник</strong><ArrowRight size={14} /></Link></Reveal>

      <div className={`map-tools ${picking ? 'map-tools-picking' : ''}`}>
        <div className="map-tools-top"><div className="map-tool-label"><span>План · {floor} этаж</span><span>{floorRooms.length} помещений</span></div><div className="map-navigation-actions">
          <button className={`map-gps-button ${gpsStatus === 'active' ? 'map-gps-active' : ''}`} onClick={toggleGps} aria-pressed={gpsEnabled}><LocateFixed size={16} />{gpsEnabled ? 'Выключить GPS' : 'Включить GPS'}</button>
          <button className={`map-manual-button ${picking === 'manual' ? 'map-manual-active' : ''}`} onClick={() => { setPicking(picking === 'manual' ? '' : 'manual'); setPointFeedback('') }} aria-pressed={picking === 'manual'}><MapPin size={15} />{picking === 'manual' ? 'Отмена' : 'Поставить метку'}</button>
          <button className="map-settings-button" onClick={() => setSettingsOpen((value) => !value)} aria-expanded={settingsOpen} aria-label="Настройки карты"><Settings2 size={17} /></button>
        </div></div>
        {gpsStatus !== 'idle' && <div className={`map-gps-status map-gps-${gpsStatus}`} role="status"><LocateFixed size={16} /><span>{gpsMessage()}</span>{gpsStatus === 'active' && <button type="button" className="map-anchor-button" onClick={() => { setPicking(picking === 'gps' ? '' : 'gps'); setPointFeedback('') }}>{picking === 'gps' ? 'Отмена' : anchorsHere.length === 2 ? 'Перепривязать GPS' : 'Привязать GPS к плану'}</button>}</div>}
        {picking && <div className="map-manual-hint" role="status">{picking === 'gps' ? 'Коснитесь известной точки на плане, где вы сейчас стоите. Для отслеживания нужны две разные точки на одном этаже.' : 'Коснитесь любого помещения или прохода внутри здания. Ваша точка будет отмечена точно там, где вы нажмёте.'}</div>}
        {pointFeedback && <div className="map-point-feedback" role="status">{pointFeedback}</div>}
        {manualPoint && <div className="map-manual-summary"><MapPin size={16} /><span>Ваша отметка · {manualPoint.floor} этаж. Её можно использовать как начало маршрута.</span>{manualPoint.floor !== floor && <button type="button" className="map-pin-show" onClick={() => chooseFloor(manualPoint.floor)}>Показать</button>}<button type="button" onClick={() => { setManualPoint(null); setPointFeedback('') }} aria-label="Удалить ручную отметку"><X size={16} /></button></div>}
        {settingsOpen && <div className="map-settings-panel" aria-label="Настройки карты"><label><input type="checkbox" checked={showLabels} onChange={(event) => setShowLabels(event.target.checked)} /><span><strong>Подписи помещений</strong><small>Названия и номера кабинетов</small></span></label><label><input type="checkbox" checked={showDoorMarks} onChange={(event) => setShowDoorMarks(event.target.checked)} /><span><strong>Отметки дверей</strong><small>Показывать входы в помещения</small></span></label>{anchorsHere.length > 0 && <button type="button" className="map-reset-anchors" onClick={() => setGpsAnchors((current) => ({ ...current, [floor]: [] }))}>Сбросить GPS-привязку {floor} этажа</button>}</div>}
        <div className="map-category-filters" role="group" aria-label="Фильтр помещений">{categoryLabels.map((category) => <button key={category} className={filter === category ? 'map-filter map-filter-active' : 'map-filter'} onClick={() => setFilter(category)} aria-pressed={filter === category}>{category}</button>)}</div>
      </div>

      <div className="map-workspace"><Reveal className="map-plan-panel"><div className="map-panel-heading"><span><strong>{floor === 1 ? 'Первый этаж' : 'Второй этаж'}</strong><small>{picking ? 'Коснитесь точки внутри здания' : 'Нажмите на помещение · дважды на лестницу для смены этажа'}</small></span><button type="button" className="map-zoom-button" onClick={() => setZoomed((value) => !value)} aria-label={zoomed ? 'Показать весь план' : 'Увеличить план'}>{zoomed ? <ZoomOut size={17} /> : <ZoomIn size={17} />}<span>{zoomed ? 'Весь план' : 'Крупнее'}</span></button></div>
        <FloorMap floor={floor} selectedId={selected?.id || ''} onSelect={selectRoom} onFloorRequest={picking ? undefined : chooseFloor} onPointSelect={placePoint} picking={Boolean(picking)} zoomed={zoomed} filterCategory={filter} showLabels={showLabels} showDoorMarks={showDoorMarks} manualPoint={manualPoint} gpsMarker={gpsMarker} routeStart={routeStart} routeTargetId={routeTargetId} transition={params.get('mode') === 'stairs' ? 'stairs' : 'lift'} venueRoomIds={openDay ? openDayVenues.map((venue) => venue.roomId) : []} />
        <div className="map-panel-foot">{showDoorMarks && <span><i className="door-legend" /> Двери</span>}<span><i className="route-legend" /> Маршрут по коридору</span><span>На телефоне перемещайте план пальцем</span></div></Reveal>
        <Reveal className={`room-detail-panel ${selected ? 'room-detail-selected' : ''}`} delay={100} aria-live="polite">{selected ? <><div className="room-detail-top"><span className="eyebrow">{selected.category}</span><span className="room-detail-number">{selected.floor} этаж</span></div>{openDay && venueByRoomId(selected.id) && <button className="room-venue-chip" type="button" onClick={() => { autoShownVenue.current = selected.id; setVenueModalId(selected.id) }}><Sparkles size={13} /> Площадка дня открытых дверей · нажмите</button>}<div className="room-detail-icon"><Building2 size={21} strokeWidth={1.5} /></div><FloorTag floor={selected.floor} /><h2>{selected.name}</h2>{selected.number && <div className="room-number-line">Кабинет <strong>{selected.number}</strong></div>}<p>{selected.description}</p><div className="room-detail-rule" /><div className="room-detail-info"><span><DoorOpen size={15} />{showDoorMarks ? 'Синяя черта на плане — дверь' : 'Двери можно включить в настройках'}</span><span><MapPin size={15} /> {selected.floor} этаж</span></div>{stair && <button className="room-floor-button" onClick={() => chooseFloor(otherFloor(floor), rooms.find((room) => room.id === `${stair[1]}-${otherFloor(floor)}`))}>Перейти по лестнице на {otherFloor(floor)} этаж <ArrowRight size={15} /></button>}<Link className="button button-primary room-route-button" to={`/route?${fromParam ? `from=${encodeURIComponent(fromParam)}&` : manualPoint ? 'from=manual&' : ''}to=${encodeURIComponent(selected.id)}${params.get('mode') === 'stairs' ? '&mode=stairs' : ''}`}>Построить маршрут <ArrowRight size={15} /></Link><button className="room-clear" onClick={() => chooseFloor(floor, null, true)}>Снять выделение</button></> : <><div className="room-empty-illustration"><MapPin size={24} /></div><span className="eyebrow">На плане</span><h2>Куда идём?</h2><p>Выберите кабинет на схеме. Здесь появятся его номер, назначение и кнопка маршрута.</p><div className="room-detail-rule" /><div className="room-quick-stat"><strong>{floorRooms.length}</strong><span>мест на этом этаже</span></div><Link className="room-browse-link" to="/rooms">Открыть справочник <ArrowUpRight size={14} /></Link></>}</Reveal>
      </div>
      <div className="map-under-note"><span><Info size={15} /> О схеме</span><p>Маршрут проходит по отмеченным коридорам и дверям. Схема ориентировочная: если проход закрыт, следуйте указателям в здании.</p><Link to="/route">Построить маршрут <ArrowRight size={14} /></Link></div>
    </section>
    {openDay && venueModalId && <VenueModal roomId={venueModalId} onClose={() => setVenueModalId('')} fromParam={fromParam} manualPoint={manualPoint} mode={params.get('mode') === 'stairs' ? 'stairs' : ''} />}
  </>
}
