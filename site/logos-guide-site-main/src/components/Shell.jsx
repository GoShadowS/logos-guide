import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { ArrowRight, ArrowUpRight, Check, MapPin, Moon, PartyPopper, Search, Sun, X } from 'lucide-react'
import { navigation, rooms } from '../data'
import { lockBodyScroll, storageGet, storageSet } from '../storage'
import { useOpenDay } from '../useOpenDay'

const ToastContext = createContext(() => {})

export function ToastProvider({ children }) {
  const [toast, setToast] = useState('')
  const timer = useRef(null)
  const notify = useCallback((message) => {
    setToast(message)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setToast(''), 2500)
  }, [])

  useEffect(() => () => window.clearTimeout(timer.current), [])

  return (
    <ToastContext.Provider value={notify}>
      {children}
      <div className={`toast ${toast ? 'toast-visible' : ''}`} role="status" aria-live="polite"><span className="toast-icon"><Check size={15} /></span>{toast}</div>
    </ToastContext.Provider>
  )
}

export function useToast() { return useContext(ToastContext) }

function Brand() {
  return <Link className="brand" to="/" aria-label="Колледж — на главную"><img className="brand-mark" src="/logo.svg" alt="" /><span className="brand-copy"><strong>ЛОГОС</strong><small>ПУТЕВОДИТЕЛЬ</small></span></Link>
}

function resultPath(room) {
  return `/map?floor=${room.floor}&room=${encodeURIComponent(room.id)}`
}

function SearchDialog({ open, onClose }) {
  const [query, setQuery] = useState('')
  const inputRef = useRef(null)
  const location = useLocation()

  useEffect(() => {
    if (open) {
      setQuery('')
      const timer = window.setTimeout(() => inputRef.current?.focus(), 40)
      const unlock = lockBodyScroll()
      return () => { window.clearTimeout(timer); unlock() }
    }
    return undefined
  }, [open])

  useEffect(() => {
    if (!open) return undefined
    const onKeyDown = (event) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open, onClose])

  useEffect(() => onClose(), [location.pathname, onClose])
  if (!open) return null

  const normalized = query.trim().toLocaleLowerCase('ru')
  const matches = rooms.filter((room) => {
    const text = `${room.name} ${room.number} ${room.category} ${room.aliases}`.toLocaleLowerCase('ru')
    return !normalized || text.includes(normalized)
  }).slice(0, 7)
  const countLabel = matches.length === 1 ? 'место' : matches.length > 1 && matches.length < 5 ? 'места' : 'мест'

  return (
    <div className="search-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className="search-dialog" role="dialog" aria-modal="true" aria-labelledby="search-title">
        <div className="search-dialog-top"><span className="eyebrow"><span className="eyebrow-dot" />ПОИСК ПО КОЛЛЕДЖУ</span><button className="icon-button" onClick={onClose} aria-label="Закрыть поиск"><X size={18} /></button></div>
        <h2 id="search-title" className="visually-hidden">Найти кабинет или пространство</h2>
        <label className="search-input-wrap"><Search size={19} /><input ref={inputRef} type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Номер кабинета, название, место…" /><kbd>ESC</kbd></label>
        <div className="search-results" aria-live="polite">
          {matches.length ? matches.map((room) => <Link key={room.id} className="search-result" to={resultPath(room)} onClick={onClose}><span className="search-result-icon"><MapPin size={16} /></span><span className="search-result-copy"><small>{room.category} · {room.floor} этаж</small><strong>{room.name}{room.number && ` №${room.number}`}</strong></span><ArrowUpRight className="search-result-arrow" size={17} /></Link>) : <div className="search-empty"><span>Такого места не нашли.</span><small>Проверьте номер или попробуйте «библиотека», «столовая».</small></div>}
        </div>
        <div className="search-foot"><span><kbd>⌘ / Ctrl K</kbd> быстрый поиск</span><span>{matches.length} {countLabel}</span></div>
      </section>
    </div>
  )
}

export function Header() {
  const [searchOpen, setSearchOpen] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [theme, setTheme] = useState(() => storageGet('site-theme') || 'light')
  const [openDay, toggleOpenDay] = useOpenDay()
  const location = useLocation()
  const closeSearch = useCallback(() => setSearchOpen(false), [])

  useEffect(() => setMobileOpen(false), [location.pathname])
  useEffect(() => {
    document.documentElement.dataset.theme = theme
    storageSet('site-theme', theme)
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#07172e' : '#245ce5')
  }, [theme])
  useEffect(() => {
    const onKeyDown = (event) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); setSearchOpen(true) }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])

  return <>
    <header className="site-header">
      <div className="header-inner"><Brand />
        <nav className="desktop-nav" aria-label="Основная навигация">{navigation.map((item) => <NavLink end key={item.to} to={item.to} className={({ isActive }) => `nav-link ${isActive ? 'nav-link-active' : ''}`}>{item.label}</NavLink>)}</nav>
        <div className="header-actions"><button className={`open-day-toggle ${openDay ? 'open-day-toggle-on' : ''}`} onClick={() => toggleOpenDay()} aria-pressed={openDay} title={openDay ? 'Режим дня открытых дверей включён — нажмите, чтобы выключить' : 'Режим дня открытых дверей выключен — нажмите, чтобы включить'}><PartyPopper size={15} /><span>День открытых дверей</span><i className="open-day-switch" aria-hidden="true"><b /></i></button><button className="theme-toggle" onClick={() => setTheme((value) => value === 'dark' ? 'light' : 'dark')} aria-label={theme === 'dark' ? 'Включить светлую тему' : 'Включить тёмную тему'} title={theme === 'dark' ? 'Светлая тема' : 'Тёмная тема'}>{theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}</button><button className="search-trigger" onClick={() => setSearchOpen(true)} aria-label="Найти кабинет"><Search size={16} /><span>Найти место</span><kbd>⌘ K</kbd></button><Link className="header-cta" to="/map">Открыть карту <ArrowUpRight size={15} /></Link><button className={`mobile-menu-toggle ${mobileOpen ? 'is-open' : ''}`} onClick={() => setMobileOpen((value) => !value)} aria-label={mobileOpen ? 'Закрыть меню' : 'Открыть меню'} aria-expanded={mobileOpen}>{mobileOpen ? <X size={21} /> : <span className="menu-lines"><i /><i /></span>}</button></div>
      </div>
      {mobileOpen && <div className="mobile-nav-panel"><div className="mobile-nav-links">{navigation.map((item, index) => <NavLink key={item.to} to={item.to} className={({ isActive }) => `mobile-nav-link ${isActive ? 'mobile-nav-link-active' : ''}`}><span className="mobile-nav-index">0{index + 1}</span>{item.label}<ArrowRight size={17} /></NavLink>)}</div><div className="mobile-nav-bottom"><span>Найдите нужное место</span><button onClick={() => { setMobileOpen(false); setSearchOpen(true) }}><Search size={15} /> Поиск кабинета</button></div><div className="mobile-nav-bottom"><span>Режим мероприятия</span><button className={`open-day-toggle ${openDay ? 'open-day-toggle-on' : ''}`} onClick={() => toggleOpenDay()} aria-pressed={openDay}><PartyPopper size={15} /> День открытых дверей <i className="open-day-switch" aria-hidden="true"><b /></i></button></div></div>}
    </header>
    <SearchDialog open={searchOpen} onClose={closeSearch} />
  </>
}

export function Footer() {
  return <footer className="site-footer"><div className="footer-top container"><div className="footer-brand-block"><Brand /><p>План двух этажей,<br />кабинеты и маршруты.</p></div><div className="footer-links-group"><span className="footer-label">НАВИГАЦИЯ</span><Link to="/map">Карта этажей</Link><Link to="/rooms">Кабинеты</Link><Link to="/route">Маршрут</Link></div><div className="footer-links-group"><span className="footer-label">ПОЛЕЗНОЕ</span><Link to="/services">Пространства</Link><Link to="/games">Игры</Link><Link to="/about">О путеводителе</Link></div><div className="footer-note"><span className="footer-label">БЫСТРЫЙ СТАРТ</span><p>Не знаете номер кабинета? Найдите его по названию или отметьте на карте.</p><Link className="footer-about-link" to="/rooms">Найти кабинет <ArrowUpRight size={14} /></Link></div></div><div className="footer-bottom container"><span>ПУТЕВОДИТЕЛЬ КОЛЛЕДЖА</span><span>План и справочник помещений</span><a href="#top" onClick={(event) => { event.preventDefault(); window.scrollTo({ top: 0, behavior: 'smooth' }) }}>Наверх ↑</a></div></footer>
}
