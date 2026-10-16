import { useMemo, useState } from 'react'
import { ArrowDownRight, ArrowRight, ArrowUpRight, MapPin, PartyPopper, Search, Users } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { rooms, quickPlaces } from '../data.js'
import FloorMap from '../components/FloorMap.jsx'
import { openDayVenues } from '../openDay.js'
import { useOpenDay } from '../useOpenDay.js'
import { Reveal, SectionLabel } from '../components/Primitives.jsx'

function MiniPlan() {
  const [floor, setFloor] = useState(1)
  const navigate = useNavigate()
  const openRoom = (room) => navigate(`/map?floor=${room.floor}&room=${encodeURIComponent(room.id)}`)

  return <div className="mini-plan-wrap" role="group" aria-label="План этажей колледжа">
    <div className="mini-plan-top"><span>План колледжа</span><div className="mini-plan-tabs" role="group" aria-label="Этаж на главной карте">{[1, 2].map((number) => <button key={number} type="button" aria-label={`${number} этаж`} aria-pressed={floor === number} className={floor === number ? 'mini-plan-tab-active' : ''} onClick={() => setFloor(number)}>0{number}</button>)}</div></div>
    <FloorMap floor={floor} compact showLabels={false} onSelect={openRoom} onFloorRequest={setFloor} />
    <div className="mini-plan-bottom"><span>Нажмите на помещение</span><Link to={`/map?floor=${floor}`} aria-label="Открыть полную карту">Открыть план <ArrowUpRight size={16} /></Link></div>
  </div>
}

function HomeSearch() {
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()
  const matches = useMemo(() => {
    const term = query.trim().toLocaleLowerCase('ru')
    if (!term) return []
    return rooms.filter((room) => `${room.name} ${room.number} ${room.aliases}`.toLocaleLowerCase('ru').includes(term)).slice(0, 4)
  }, [query])
  const goTo = (room) => navigate(`/map?floor=${room.floor}&room=${encodeURIComponent(room.id)}`)
  const submit = (event) => {
    event.preventDefault()
    if (matches[0]) goTo(matches[0])
    else navigate(`/rooms?q=${encodeURIComponent(query.trim())}`)
  }

  return <form className="home-search" onSubmit={submit} role="search"><Search size={19} /><input value={query} onFocus={() => setOpen(true)} onBlur={() => window.setTimeout(() => setOpen(false), 130)} onChange={(event) => setQuery(event.target.value)} placeholder="Например, 217 или библиотека" aria-label="Найти кабинет или пространство" autoComplete="off" /><button type="submit" aria-label="Искать"><ArrowRight size={18} /></button>{open && query.trim() && <div className="home-search-suggestions">{matches.length ? matches.map((room) => <button type="button" key={room.id} onMouseDown={(event) => event.preventDefault()} onClick={() => goTo(room)}><span>{room.name}{room.number && ` №${room.number}`}</span><small>{room.floor} этаж · {room.category}</small></button>) : <button type="submit" className="home-search-no-results"><span>Искать «{query}» в справочнике</span><small>Посмотреть все результаты</small></button>}</div>}</form>
}

function OpenDayBand() {
  const [openDay] = useOpenDay()
  if (!openDay) return null
  return <section className="open-day-band"><div className="container open-day-inner">
    <div className="open-day-head"><span className="open-day-chip"><PartyPopper size={14} /> Сегодня</span><h2>День открытых дверей:<br /><em>площадки и мастер-классы.</em></h2><p>Выберите площадку — покажем аудиторию, ведущих и предложим построить маршрут прямо по плану колледжа.</p></div>
    <div className="open-day-grid">{openDayVenues.map((venue, index) => {
      const room = rooms.find((item) => item.id === venue.roomId)
      return <Link key={venue.roomId} className="open-day-card" to={`/map?floor=${room.floor}&room=${encodeURIComponent(room.id)}`}><span className="open-day-card-index">0{index + 1}</span><span className="open-day-card-copy"><strong>{venue.title}</strong><small>{venue.highlights.slice(0, 3).join(' · ')}</small><span className="open-day-card-meta"><MapPin size={13} /> Аудитория {room.number} · {room.floor} этаж <i /> <Users size={13} /> {venue.audience}</span></span><ArrowUpRight size={17} /></Link>
    })}</div>
  </div></section>
}

export default function HomePage() {
  const [openDay] = useOpenDay()
  return <>
    <section className="home-hero" id="top"><div className="container home-hero-inner"><div className="home-hero-copy"><span className="home-eyebrow">{openDay ? 'День открытых дверей · путеводитель колледжа' : 'Путеводитель по колледжу · два этажа'}</span><h1>{openDay ? <>Куда идти<br /><em>сегодня?</em></> : <>Куда идти<br /><em>на следующую пару?</em></>}</h1><p>{openDay ? 'Найдите площадки дня открытых дверей, посмотрите расписание на карте и постройте маршрут до нужной аудитории.' : 'Введите номер кабинета или откройте план. Покажем, где находится нужное место и как к нему пройти.'}</p><HomeSearch /><div className="home-hero-actions"><Link className="button button-primary" to="/map">Смотреть карту <ArrowRight size={16} /></Link><Link className="home-plain-link" to="/route">Построить маршрут <ArrowUpRight size={15} /></Link></div></div><Reveal className="home-hero-visual" delay={100}><MiniPlan /></Reveal></div><div className="home-hero-edge container"><span>План корпуса · 1 и 2 этажи</span><a href="#quick-places" onClick={(event) => { event.preventDefault(); document.getElementById('quick-places')?.scrollIntoView({ behavior: 'smooth' }) }}>Часто ищут <ArrowDownRight size={14} /></a></div></section>

    <OpenDayBand />

    <section className="quick-places container section-pad" id="quick-places"><div className="section-top"><div><SectionLabel>Часто ищут</SectionLabel><h2>Начните <em>здесь.</em></h2></div><Link className="text-link" to="/rooms">Все помещения <span><ArrowRight size={15} /></span></Link></div><div className="quick-place-grid">{quickPlaces.map((place, index) => { const room = rooms.find((item) => item.id === place.roomId); return <Reveal key={place.roomId} delay={index * 40}><Link className={`quick-place-card quick-place-${place.tone}`} to={`/map?floor=${room.floor}&room=${room.id}`}><span className="quick-place-num">0{index + 1} / {room.floor} этаж</span><span className="quick-place-copy"><strong>{place.label}</strong><small>{place.meta}</small></span><span className="quick-place-arrow"><ArrowUpRight size={16} /></span></Link></Reveal> })}</div></section>

    <section className="floor-preview-band"><div className="container floor-preview-inner"><Reveal className="floor-preview-copy"><SectionLabel>Планы этажей</SectionLabel><h2>Первый<br />или <em>второй?</em></h2><p>Переключайтесь между этажами, выбирайте помещения и находите ближайшую лестницу.</p><Link className="button button-white" to="/map">Открыть карту <ArrowRight size={15} /></Link></Reveal><Reveal className="floor-switch-cards" delay={90}>{[1, 2].map((floor) => <Link key={floor} to={`/map?floor=${floor}`} className={`floor-switch-card floor-switch-${floor}`}><span className="floor-switch-number">0{floor}</span><span className="floor-switch-copy"><small>{floor === 1 ? 'ПЕРВЫЙ ЭТАЖ' : 'ВТОРОЙ ЭТАЖ'}</small><strong>{floor === 1 ? 'Столовая, актовый зал, учебная часть' : 'Библиотека, лаборатории, кабинеты'}</strong></span><ArrowUpRight size={17} /></Link>)}</Reveal></div></section>

    <section className="play-teaser container section-pad"><Reveal className="play-teaser-copy"><SectionLabel>На перемене</SectionLabel><h2>Проверьте себя<br /><em>по плану.</em></h2><p>В коротком квизе можно потренироваться узнавать кабинеты и этажи.</p><Link className="button button-primary" to="/games">Открыть игры <ArrowRight size={15} /></Link></Reveal><span className="play-teaser-note">5 вопросов<br />без оценок</span></section>
  </>
}
