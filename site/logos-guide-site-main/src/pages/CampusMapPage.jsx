import { useState } from 'react'
import { ArrowLeft, ArrowRight, ArrowUp, Accessibility, MapPin, Navigation, Search, X } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'

const rooms = [
  { id: '118', name: 'Кабинет 118', kind: 'Аудитория', floor: 1, x: 13, y: 25, width: 27, height: 25 },
  { id: '119', name: 'Кабинет 119', kind: 'Аудитория', floor: 1, x: 42, y: 25, width: 25, height: 25 },
  { id: 'hall', name: 'Главный холл', kind: 'Общее пространство', floor: 1, x: 13, y: 54, width: 54, height: 28 },
  { id: 'library', name: 'Библиотека', kind: 'Полезное место', floor: 1, x: 70, y: 25, width: 18, height: 57 },
  { id: '204', name: 'Кабинет 204', kind: 'Аудитория', floor: 2, x: 13, y: 25, width: 27, height: 25 },
  { id: '219', name: 'Кабинет 219', kind: 'Аудитория', floor: 2, x: 42, y: 25, width: 25, height: 25 },
  { id: 'lab', name: 'Лаборатория', kind: 'Практика', floor: 2, x: 70, y: 25, width: 18, height: 57 },
  { id: '305', name: 'Кабинет 305', kind: 'Аудитория', floor: 3, x: 13, y: 25, width: 27, height: 25 },
  { id: 'studio', name: 'Мастерская', kind: 'Практика', floor: 3, x: 42, y: 25, width: 46, height: 57 },
]

export default function CampusMapPage() {
  const location = useLocation()
  const [query, setQuery] = useState(location.state?.search || '')
  const [floor, setFloor] = useState(1)
  const [selected, setSelected] = useState('118')
  const [routeStarted, setRouteStarted] = useState(false)
  const matches = rooms.filter((room) => `${room.id} ${room.name} ${room.kind}`.toLocaleLowerCase('ru').includes(query.trim().toLocaleLowerCase('ru')))
  const active = rooms.find((room) => room.id === selected) || matches[0] || rooms[0]
  const floorRooms = rooms.filter((room) => room.floor === floor)

  const searchRoom = (event) => {
    event.preventDefault()
    const result = matches[0]
    if (result) {
      setSelected(result.id)
      setFloor(result.floor)
      setRouteStarted(false)
    }
  }

  return (
    <div className="college-page campus-page">
      <div className="college-container campus-container">
        <header className="campus-topline"><Link to="/" className="campus-back" aria-label="На главную"><ArrowLeft size={18} /></Link><span className="campus-brand">Карта колледжа</span><button className="campus-locate" onClick={() => { setFloor(1); setSelected('hall'); setRouteStarted(false) }} aria-label="Показать главный вход"><Navigation size={17} /></button></header>
        <div className="campus-heading"><div><span className="college-overline">ПЛАН КОРПУСА</span><h1>Найди нужное<br />место.</h1></div><span className="campus-building-chip"><span className="status-dot" /> Главный корпус</span></div>
        <form className="college-search campus-search" onSubmit={searchRoom}><Search size={18} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Номер кабинета или место" aria-label="Поиск кабинета или места" />{query && <button type="button" className="clear-search" onClick={() => setQuery('')} aria-label="Очистить"><X size={16} /></button>}<button type="submit" aria-label="Найти"><ArrowRight size={18} /></button></form>

        {query && <div className="campus-results" role="status">{matches.length ? <>{matches.length} {matches.length === 1 ? 'место найдено' : 'места найдены'}{matches.slice(0, 3).map((room) => <button key={room.id} onClick={() => { setSelected(room.id); setFloor(room.floor); setRouteStarted(false) }}><MapPin size={15} /><span><strong>{room.name}</strong><small>{room.floor} этаж · {room.kind}</small></span><ArrowRight size={15} /></button>)}</> : <span>Ничего не найдено. Попробуй номер кабинета.</span>}</div>}

        <div className="floor-controls"><span>ЭТАЖ</span>{[1, 2, 3].map((number) => <button key={number} className={floor === number ? 'floor-active' : ''} onClick={() => { setFloor(number); const nextRoom = rooms.find((room) => room.floor === number); if (nextRoom) setSelected(nextRoom.id); setRouteStarted(false) }}>{number}<small>Й</small></button>)}<span className="floor-total">ИЗ 3</span></div>

        <section className="campus-map-board" aria-label={`План ${floor} этажа`}>
          <div className="map-board-meta"><span><i className="map-live-dot" /> {floor} ЭТАЖ · СХЕМА КОРПУСА</span><span>СЕВЕР <ArrowUp size={12} /></span></div>
          <div className="campus-map-grid">
            <div className="map-corridor corridor-horizontal" /><div className="map-corridor corridor-vertical" />
            {floorRooms.map((room) => <button key={room.id} className={`map-room ${room.id === selected ? 'map-room-active' : ''} ${room.id === 'hall' ? 'room-hall' : ''}`} style={{ left: `${room.x}%`, top: `${room.y}%`, width: `${room.width}%`, height: `${room.height}%` }} onClick={() => { setSelected(room.id); setRouteStarted(false) }} aria-pressed={room.id === selected}><span>{room.id === 'hall' ? 'ХОЛЛ' : room.name.toUpperCase()}</span><small>{room.id === selected && 'ВЫ ЗДЕСЬ'}</small></button>)}
            <span className="map-entrance-label">ВХОД</span><span className="map-you-are"><MapPin size={17} fill="currentColor" /> <small>ВЫ ЗДЕСЬ</small></span>
            <span className="map-stair-label">ЛЕСТНИЦА <Accessibility size={13} /></span>
          </div>
          <div className="map-board-footer"><span>Схема этажа</span><span>Коснись аудитории, чтобы выбрать</span></div>
        </section>

        <section className={`room-detail ${routeStarted ? 'route-active' : ''}`} aria-live="polite">
          <div className="room-detail-handle" />
          <div className="room-detail-top"><span><span className="college-overline">{routeStarted ? 'МАРШРУТ ПОСТРОЕН' : 'ВЫБРАННОЕ МЕСТО'}</span><button onClick={() => setRouteStarted(false)} aria-label="Сбросить маршрут"><X size={16} /></button></span><h2>{active.name}</h2><p><MapPin size={14} /> {active.floor} этаж <i /> {active.kind}</p></div>
          <div className="route-steps"><span className="route-line" /><div><b>1</b><span><strong>Главный вход</strong><small>Начало маршрута</small></span></div><div><b>2</b><span><strong>{active.name}</strong><small>{active.floor === 1 ? 'Прямо по коридору' : `Поднимитесь на ${active.floor} этаж`}</small></span></div></div>
          <button className="route-button" onClick={() => setRouteStarted((value) => !value)}>{routeStarted ? <><MapPin size={17} /> Маршрут начат</> : <><Navigation size={17} /> Начать маршрут</>}</button>
        </section>
        <Link className="campus-guide-link" to="/guide">Не знаешь, что ищешь? <span>Открыть путеводитель</span><ArrowRight size={15} /></Link>
      </div>
      <div className="mobile-tabbar" aria-label="Быстрая навигация"><Link to="/"><Search size={19} /><span>Поиск</span></Link><Link className="tab-active" to="/map"><MapPin size={19} /><span>Карта</span></Link><Link to="/guide"><BookOpenIcon /><span>Справочник</span></Link></div>
    </div>
  )
}

function BookOpenIcon() {
  return <ArrowRight size={19} />
}
