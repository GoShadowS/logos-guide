import { useState } from 'react'
import { ArrowRight, BookOpen, Bus, CalendarDays, Coffee, GraduationCap, MapPin, Search, Sparkles, Utensils, Wifi } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'

const shortcuts = [
  { label: 'Кабинеты', icon: MapPin, query: '118', tone: 'blue' },
  { label: 'Расписание', icon: CalendarDays, query: 'расписание', tone: 'yellow' },
  { label: 'Преподаватели', icon: GraduationCap, query: 'преподаватель', tone: 'pink' },
  { label: 'Столовая', icon: Utensils, query: 'столовая', tone: 'green' },
  { label: 'Библиотека', icon: BookOpen, query: 'библиотека', tone: 'orange' },
  { label: 'Как добраться', icon: Bus, query: 'вход', tone: 'lilac' },
]

const recent = [
  { title: 'Кабинет 118', detail: '1 этаж · учебный корпус', query: '118' },
  { title: 'Кабинет 119', detail: '1 этаж · рядом с кабинетом 118', query: '119' },
  { title: 'Столовая', detail: '1 этаж · центральный холл', query: 'столовая' },
]

export default function CollegeHomePage() {
  const [query, setQuery] = useState('')
  const navigate = useNavigate()
  const find = (value = query) => navigate('/map', { state: { search: value } })

  return (
    <div className="college-page college-home">
      <section className="college-welcome">
        <div className="college-container welcome-inner">
          <div className="welcome-copy">
            <span className="college-eyebrow"><span className="status-dot" /> ТВОЙ КОЛЛЕДЖ, ПОНЯТНЫЙ С ПЕРВОГО ДНЯ</span>
            <h1>Найди своё<br /><em>место.</em></h1>
            <p>Аудитории, полезные места и маршруты по колледжу — всё рядом.</p>
            <form className="college-search" onSubmit={(event) => { event.preventDefault(); find() }}>
              <Search size={19} aria-hidden="true" />
              <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Куда тебе сегодня? Например, 118" aria-label="Поиск по колледжу" />
              <button type="submit" aria-label="Найти"><ArrowRight size={19} /></button>
            </form>
            <div className="welcome-hint"><Sparkles size={14} /> Можно искать аудиторию или место</div>
          </div>
          <div className="welcome-art" aria-label="Иллюстрация здания колледжа">
            <div className="art-sun" />
            <div className="art-label art-label-top">ТВОЯ ТОЧКА СТАРТА <span>01</span></div>
            <div className="college-building">
              <div className="building-roof" />
              <div className="building-front">
                <div className="building-sign">КОЛЛЕДЖ <span>●</span></div>
                <div className="building-windows"><i /><i /><i /><i /></div>
                <div className="building-entry"><i /><i /><i /></div>
              </div>
              <div className="building-step" />
            </div>
            <div className="art-label art-label-bottom"><MapPin size={14} /> ГЛАВНЫЙ ВХОД <span>→</span></div>
            <div className="art-sticker">Здесь<br /><strong>всё</strong><br />начинается</div>
          </div>
        </div>
      </section>

      <section className="college-container college-content">
        <div className="college-section-head">
          <div><span className="college-overline">БЫСТРЫЙ ДОСТУП</span><h2>Что ищем?</h2></div>
          <Link className="college-text-link" to="/guide">Все разделы <ArrowRight size={15} /></Link>
        </div>
        <div className="shortcut-grid">
          {shortcuts.map(({ label, icon: Icon, query: target, tone }) => (
            <button className={`shortcut-item shortcut-${tone}`} key={label} onClick={() => find(target)}>
              <span className="shortcut-icon"><Icon size={21} strokeWidth={2.1} /></span>
              <span>{label}</span><ArrowRight className="shortcut-arrow" size={15} />
            </button>
          ))}
        </div>

        <div className="college-lower-grid">
          <section className="recent-section">
            <div className="college-section-head"><div><span className="college-overline">ТВОЯ ИСТОРИЯ</span><h2>Недавно искали</h2></div><span className="recent-count">03</span></div>
            <div className="recent-list">
              {recent.map((item, index) => <button className="recent-row" key={item.title} onClick={() => find(item.query)}><span className="recent-index">0{index + 1}</span><span className="recent-copy"><strong>{item.title}</strong><small>{item.detail}</small></span><ArrowRight size={17} /></button>)}
            </div>
          </section>
          <Link className="map-promo" to="/map">
            <div className="promo-heading"><span className="college-overline">НЕ ЗАБЛУДИСЬ</span><span className="promo-icon"><MapPin size={18} /></span></div>
            <div className="mini-map" aria-hidden="true"><i /><i /><i /><i /><span>118</span><b /></div>
            <div className="promo-bottom"><span><strong>Карта колледжа</strong><small>Выбери этаж и построй маршрут</small></span><span className="promo-arrow"><ArrowRight size={17} /></span></div>
          </Link>
        </div>
        <div className="college-notice"><span className="notice-icon"><Coffee size={19} /></span><span><strong>Перерыв между парами?</strong><small>Найди столовую, библиотеку или место, где можно отдохнуть.</small></span><Link to="/guide" aria-label="Места для отдыха"><ArrowRight size={17} /></Link><Wifi className="notice-wifi" size={17} /></div>
      </section>
      <div className="mobile-tabbar" aria-label="Быстрая навигация"><Link className="tab-active" to="/"><Search size={19} /><span>Поиск</span></Link><Link to="/map"><MapPin size={19} /><span>Карта</span></Link><Link to="/guide"><BookOpen size={19} /><span>Справочник</span></Link></div>
    </div>
  )
}
