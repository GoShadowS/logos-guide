import { useMemo, useState } from 'react'
import { ArrowRight, ArrowUpRight, Building2, Search, Sparkles, X } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { rooms, categoryLabels } from '../data'
import { isVenueRoom } from '../openDay'
import { useOpenDay } from '../useOpenDay'
import { FloorTag, PageHeading, Reveal, SectionLabel } from '../components/Primitives'

function formatRoomCount(count) {
  const lastTwo = count % 100
  const last = count % 10
  const noun = lastTwo >= 11 && lastTwo <= 14 ? 'мест' : last === 1 ? 'место' : last >= 2 && last <= 4 ? 'места' : 'мест'
  return noun
}

export default function RoomsPage() {
  const [params] = useSearchParams()
  const [query, setQuery] = useState(params.get('q') || '')
  const [floor, setFloor] = useState('all')
  const [category, setCategory] = useState('Все места')
  const [openDay] = useOpenDay()
  const visibleRooms = useMemo(() => {
    const term = query.trim().toLocaleLowerCase('ru')
    return rooms.filter((room) => {
      const text = `${room.name} ${room.number} ${room.category} ${room.aliases}`.toLocaleLowerCase('ru')
      return (!term || text.includes(term)) && (floor === 'all' || room.floor === Number(floor)) && (category === 'Все места' || room.category === category)
    })
  }, [query, floor, category])
  const resetFilters = () => { setQuery(''); setFloor('all'); setCategory('Все места') }

  return <>
    <PageHeading eyebrow="Справочник помещений" title={<>Кабинеты<br /><em>и другие места.</em></>} description="Ищите по номеру или названию. Нажмите на помещение, чтобы увидеть его на карте и построить маршрут." />

    <section className="directory-section container"><Reveal className="directory-search-bar"><Search size={19} /><input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Например: 217, физика, библиотека…" aria-label="Поиск кабинета" />{query && <button onClick={() => setQuery('')} aria-label="Очистить поиск"><X size={17} /></button>}<kbd>⌕</kbd></Reveal>
      <Reveal className="directory-filters"><div className="directory-floor-filters" role="group" aria-label="Фильтр по этажу"><span>ЭТАЖ</span>{[['all', 'Все'], ['1', '1 этаж'], ['2', '2 этаж']].map(([id, label]) => <button key={id} onClick={() => setFloor(id)} className={floor === id ? 'directory-chip directory-chip-active' : 'directory-chip'} aria-pressed={floor === id}>{label}</button>)}</div><div className="directory-category-filters" role="group" aria-label="Категория места">{categoryLabels.map((item) => <button key={item} onClick={() => setCategory(item)} className={category === item ? 'directory-chip directory-chip-active' : 'directory-chip'} aria-pressed={category === item}>{item}</button>)}</div></Reveal>
      <div className="directory-results-head"><span><strong>{visibleRooms.length}</strong> {formatRoomCount(visibleRooms.length)}{query && <> по запросу «{query}»</>}</span><Link to="/map">Смотреть на карте <ArrowUpRight size={14} /></Link></div>
      {visibleRooms.length ? <div className="directory-grid" aria-live="polite">{visibleRooms.map((room, index) => <Reveal key={room.id} delay={index % 8 * 30}><Link className="directory-card" to={`/map?floor=${room.floor}&room=${room.id}`}><span className="directory-card-icon"><Building2 size={18} strokeWidth={1.6} /></span><span className="directory-card-copy"><small>{room.category}</small>{openDay && isVenueRoom(room.id) && <span className="directory-venue-chip"><Sparkles size={11} /> Площадка дня открытых дверей</span>}<strong>{room.name}</strong><span>{room.number ? `№ ${room.number}` : 'Общее пространство'}</span></span><FloorTag floor={room.floor} /><ArrowUpRight className="directory-card-arrow" size={16} /></Link></Reveal>)}</div> : <div className="directory-empty"><span className="directory-empty-icon"><Search size={20} /></span><h2>Пока не нашли.</h2><p>Попробуйте другой номер или снимите часть фильтров.</p><button className="button button-primary" onClick={resetFilters}>Сбросить фильтры <ArrowRight size={15} /></button></div>}
    </section>

    <section className="directory-help container"><div className="directory-help-line" /><SectionLabel index="01">НЕ ЗНАЕТЕ НОМЕР?</SectionLabel><p>Введите часть названия или назначение кабинета — например, «лаборатория» или «туалет».</p><Link to="/route">Нужен маршрут? <ArrowRight size={14} /></Link></section>
  </>
}
