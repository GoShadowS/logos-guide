import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ArrowRight, ArrowUp, ArrowUpRight, Check, Footprints, MapPin, MoveDown, MoveUp, X } from 'lucide-react'
import { floors, rooms } from '../data.js'
import { FloorTag, PageHeading, Reveal } from '../components/Primitives.jsx'
import FloorMap from '../components/FloorMap.jsx'
import { buildIndoorRoute } from '../map-routing.js'
import { readManualLocation } from '../map-location.js'
import { storageGet } from '../storage.js'

function roomName(room) { return `${room.name}${room.number ? ` №${room.number}` : ''}` }
function stepLabel(count) { return `${count} ${count % 10 === 1 && count % 100 !== 11 ? 'шаг' : count % 10 >= 2 && count % 10 <= 4 && !(count % 100 >= 12 && count % 100 <= 14) ? 'шага' : 'шагов'}` }

export default function RoutePage() {
  const [params] = useSearchParams()
  const manualPoint = readManualLocation(storageGet('map-manual-point'))
  const [startId, setStartId] = useState(() => params.get('from') === 'manual' && manualPoint ? 'manual' : rooms.some((room) => room.id === params.get('from')) ? params.get('from') : 'entrance-1')
  const [targetId, setTargetId] = useState(() => rooms.some((room) => room.id === params.get('to')) ? params.get('to') : 'auditorium-104')
  const [transition, setTransition] = useState(() => params.get('mode') === 'stairs' ? 'stairs' : 'lift')
  const start = startId === 'manual' && manualPoint ? manualPoint : rooms.find((room) => room.id === startId) || rooms.find((room) => room.id === 'entrance-1')
  const target = rooms.find((room) => room.id === targetId) || rooms[0]
  const startLabel = startId === 'manual' ? 'Ваша отметка' : roomName(start)
  const crossFloor = start.floor !== target.floor
  const route = useMemo(() => buildIndoorRoute(startId === 'manual' && manualPoint ? manualPoint : start.id, target.id, transition), [startId, start, target.id, transition, manualPoint])
  const firstLeg = route.byFloor[start.floor]
  const lastLeg = route.byFloor[target.floor]
  const sameRoom = !crossFloor && route.length === 0
  const steps = [
    { title: sameRoom ? 'Вы уже на месте' : `Начало: ${startLabel}`, detail: `${start.floor} этаж`, icon: MapPin },
  ]
  if (!sameRoom) {
    firstLeg?.waypoints.forEach((place) => steps.push({ title: place, detail: `${start.floor} этаж`, icon: Footprints }))
    if (route.transfer) {
      const move = target.floor > start.floor ? 'Поднимитесь' : 'Спуститесь'
      steps.push({ title: `${move} на ${target.floor} этаж`, detail: route.transfer.kind === 'lift' ? 'Переход на лифте' : `Переход по лестнице (${route.transfer.kind === 'stairs-west' ? 'западное крыло' : route.transfer.kind === 'stairs-middle' ? 'центральная лестница' : 'северная лестница'})`, icon: target.floor > start.floor ? MoveUp : MoveDown })
      lastLeg?.waypoints.forEach((place) => steps.push({ title: place, detail: `${target.floor} этаж`, icon: Footprints }))
    }
    steps.push({ title: `Приход: ${roomName(target)}`, detail: `${target.floor} этаж · дверь помещения на плане`, icon: Check })
  }

  const planRoute = (event) => { event.preventDefault(); document.getElementById('route-result')?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }
  const resetRoute = () => { setStartId('entrance-1'); setTargetId('auditorium-104'); setTransition('lift') }
  const mapUrl = `/map?floor=${target.floor}&from=${encodeURIComponent(startId)}&to=${encodeURIComponent(target.id)}${crossFloor ? `&mode=${transition}` : ''}`

  return <>
    <PageHeading eyebrow="Маршрут по колледжу" title={<>Откуда идёте?<br /><em>Куда нужно попасть?</em></>} description="Выберите старт и кабинет. Покажем путь по коридорам и, если нужно, переход на другой этаж." />
    <section className="route-builder container">
      <Reveal className="route-form-panel"><div className="route-form-header"><span className="eyebrow">Точки маршрута</span><span className="route-form-step">1 / 2</span></div><form onSubmit={planRoute}>
        <label className="route-select-field"><span>Откуда</span><span className="route-select-wrap"><MapPin size={16} /><select value={startId} onChange={(event) => setStartId(event.target.value)}>{manualPoint && <option value="manual">Ваша отметка · {manualPoint.floor} этаж</option>}{floors.map((floor) => <optgroup key={floor.id} label={floor.label}>{rooms.filter((room) => room.floor === floor.id).map((room) => <option key={room.id} value={room.id}>{roomName(room)}</option>)}</optgroup>)}</select></span></label>
        <Link className="route-manual-link" to={`/map?floor=${start.floor}&manual=1`}><MapPin size={15} /> {manualPoint ? 'Изменить отметку на плане' : 'Отметить точку на плане'}</Link>
        <div className="route-connector"><span /><i /><span /></div>
        <label className="route-select-field"><span>Куда</span><span className="route-select-wrap"><MapPin size={16} /><select value={targetId} onChange={(event) => setTargetId(event.target.value)}>{floors.map((floor) => <optgroup key={floor.id} label={floor.label}>{rooms.filter((room) => room.floor === floor.id).map((room) => <option key={room.id} value={room.id}>{roomName(room)}</option>)}</optgroup>)}</select></span></label>
        {crossFloor && <div className="route-mode-select"><span>Переход между этажами</span><div role="group" aria-label="Способ перехода"><button type="button" className={transition === 'lift' ? 'route-mode-active' : ''} onClick={() => setTransition('lift')}><MoveUp size={15} /> Лифт</button><button type="button" className={transition === 'stairs' ? 'route-mode-active' : ''} onClick={() => setTransition('stairs')}><ArrowUp size={15} /> Ближайшая лестница</button></div></div>}
        <button className="button button-primary route-submit" type="submit">Посмотреть маршрут <ArrowRight size={16} /></button>
      </form></Reveal>
      <Reveal id="route-result" className="route-result-panel" delay={100} aria-live="polite"><div className="route-result-top"><span className="eyebrow">Ваш маршрут</span><button className="route-reset" type="button" onClick={resetRoute}><X size={14} /> Сбросить</button></div>
        <div className="route-endpoints"><div><span>Старт</span><strong>{startLabel}</strong><FloorTag floor={start.floor} /></div><div className="route-end-arrow"><ArrowRight size={19} /></div><div><span>Финиш</span><strong>{roomName(target)}</strong><FloorTag floor={target.floor} /></div></div>
        <div className="route-distance"><span><Footprints size={16} /> Пешком</span><span>{crossFloor ? `Переход: ${route.transfer?.label}` : 'Один этаж'}</span><span>{stepLabel(steps.length)}</span></div>
        <div className={`route-preview-grid ${crossFloor ? 'route-preview-two' : ''}`}>
          <div className="route-preview-floor"><div className="route-preview-caption"><span>{start.floor} этаж</span><small>{crossFloor ? `До ${route.transfer?.toward}` : 'Путь по этажу'}</small></div><div className="route-preview-panel"><FloorMap floor={start.floor} compact selectedId={crossFloor ? route.transfer?.startId : target.id} routeStart={startId === 'manual' ? manualPoint : start.id} routeTargetId={crossFloor ? route.transfer?.startId : target.id} transition={transition} manualPoint={startId === 'manual' ? manualPoint : null} /></div></div>
          {crossFloor && <div className="route-preview-floor"><div className="route-preview-caption"><span>{target.floor} этаж</span><small>От {route.transfer?.toward} до кабинета</small></div><div className="route-preview-panel"><FloorMap floor={target.floor} compact selectedId={target.id} routeStart={route.transfer?.endId} routeTargetId={target.id} transition={transition} /></div></div>}
        </div>
        <div className="route-steps">{steps.map((step, index) => { const Icon = step.icon; return <div className="route-step" key={`${step.title}-${index}`}><span className="route-step-index"><Icon size={15} /></span><div><strong>{step.title}</strong><small>{step.detail}</small></div><span className="route-step-count">{String(index + 1).padStart(2, '0')}</span></div> })}</div>
        <div className="route-result-actions"><Link className="button button-primary" to={mapUrl}>Показать на карте <ArrowUpRight size={15} /></Link><Link className="route-other-link" to="/rooms">Справочник кабинетов <ArrowRight size={14} /></Link></div>
      </Reveal>
    </section>
    <section className="route-hint-band"><div className="container route-hint-inner"><span className="route-hint-icon">i</span><div><strong>О маршруте</strong><p>Линия проходит по коридорам и отмеченным дверям, а не сквозь кабинеты. Схема не учитывает временно закрытые проходы; доступность лифта уточняйте в колледже.</p></div><Link to="/map">Карта этажей <ArrowRight size={14} /></Link></div></section>
  </>
}
