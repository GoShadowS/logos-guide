import { ArrowRight, BookOpen, Coffee, GraduationCap, HeartPulse, Info, Library, MapPin, ShieldCheck, Utensils, Wifi } from 'lucide-react'
import { Link } from 'react-router-dom'

const places = [
  { icon: Utensils, title: 'Столовая', text: 'Горячие обеды и перекус', floor: '1 этаж · холл', id: 'hall', tone: 'yellow' },
  { icon: Library, title: 'Библиотека', text: 'Книги, тихая зона и подготовка', floor: '1 этаж · кабинет 120', id: 'library', tone: 'blue' },
  { icon: Coffee, title: 'Зона отдыха', text: 'Передохнуть между парами', floor: '1 этаж · главный холл', id: 'hall', tone: 'pink' },
  { icon: HeartPulse, title: 'Медпункт', text: 'Помощь и первая необходимость', floor: '1 этаж · кабинет 105', id: '105', tone: 'green' },
  { icon: ShieldCheck, title: 'Охрана', text: 'Бюро находок и пропускной пункт', floor: '1 этаж · главный вход', id: 'hall', tone: 'orange' },
  { icon: Wifi, title: 'Wi-Fi', text: 'Подключение к сети колледжа', floor: 'Для студентов', id: 'wifi', tone: 'lilac' },
]

export default function CollegeGuidePage() {
  return (
    <div className="college-page guide-page">
      <div className="college-container guide-container">
        <div className="guide-hero"><span className="college-overline">СПРАВОЧНИК СТУДЕНТА</span><h1>Всё нужное<br /><em>под рукой.</em></h1><p>Полезные места, люди и ответы на вопросы о колледже.</p><Link to="/map" className="guide-map-button"><MapPin size={17} /> Открыть карту <ArrowRight size={16} /></Link><div className="guide-scribble">первый<br />день? <span>→</span></div></div>
        <section className="guide-places"><div className="college-section-head"><div><span className="college-overline">НА ТЕРРИТОРИИ</span><h2>Полезные места</h2></div><span className="recent-count">06</span></div><div className="guide-place-list">{places.map(({ icon: Icon, title, text, floor, id, tone }) => <Link to="/map" state={{ search: id }} className="guide-place" key={title}><span className={`guide-place-icon guide-tone-${tone}`}><Icon size={19} /></span><span className="guide-place-copy"><strong>{title}</strong><small>{text}</small><i>{floor}</i></span><ArrowRight size={17} /></Link>)}</div></section>
        <section className="guide-help"><div className="help-icon"><Info size={19} /></div><div><span className="college-overline">ЕСТЬ ВОПРОС?</span><h2>Спроси, не ищи наугад.</h2><p>Учебная часть поможет с расписанием, документами и организационными вопросами.</p></div><Link to="/map" state={{ search: 'учебная часть' }} aria-label="Найти учебную часть"><ArrowRight size={18} /></Link></section>
        <section className="guide-first-day"><div className="first-day-mark"><GraduationCap size={24} /></div><div><span className="college-overline">НОВЕНЬКИМ</span><h2>Первый день в колледже</h2><p>Начни с главного входа, найди свою аудиторию и оставь время на знакомство с корпусом.</p><Link to="/map" state={{ search: '118' }}>Построить первый маршрут <ArrowRight size={15} /></Link></div><span className="first-day-number">01</span></section>
      </div>
      <div className="mobile-tabbar" aria-label="Быстрая навигация"><Link to="/"><MapPin size={19} /><span>Поиск</span></Link><Link to="/map"><MapPin size={19} /><span>Карта</span></Link><Link className="tab-active" to="/guide"><BookOpen size={19} /><span>Справочник</span></Link></div>
    </div>
  )
}
