import { ArrowRight, ArrowUpRight, BookOpen, Coffee, Monitor, Mic2, Plane, Wrench } from 'lucide-react'
import { Link } from 'react-router-dom'
import { rooms } from '../data'
import { FloorTag, PageHeading, Reveal, SectionLabel } from '../components/Primitives'

const spaces = [
  { id: 'canteen', icon: Coffee, label: 'ПЕРЕД ПАРОЙ И ПОСЛЕ', note: 'Столовая', copy: 'Место для обеда и короткой передышки. Первый этаж, левое крыло.', tone: 'service-warm' },
  { id: 'library-214', icon: BookOpen, label: 'ТИШИНА И ЧТЕНИЕ', note: 'Библиотека', copy: 'Читальный зал для самостоятельной работы. Кабинет 214, второй этаж.', tone: 'service-blue' },
  { id: 'auditorium-104', icon: Mic2, label: 'СОБЫТИЯ И ВСТРЕЧИ', note: 'Актовый зал', copy: 'Зал для мероприятий и общих встреч. Кабинет 104, первый этаж.', tone: 'service-lilac' },
  { id: 'training-centre-113', icon: Plane, label: 'ПРАКТИКА И ТРЕНАЖЁРЫ', note: 'Тренажёрный центр', copy: 'Малая полётная зона и тренажёрный центр. Кабинет 113, первый этаж.', tone: 'service-green' },
  { id: 'uav-lab-209', icon: Monitor, label: 'ТЕХНОЛОГИИ БЕСПИЛОТНИКОВ', note: 'Лаборатория БАС', copy: 'Лаборатория беспилотных авиационных систем. Кабинет 209, второй этаж.', tone: 'service-cyan' },
  { id: 'workshop-115', icon: Wrench, label: 'ПРОФЕССИОНАЛЬНЫЕ МАСТЕРСКИЕ', note: 'Слесарная', copy: 'Практическая мастерская. Кабинет 115, первый этаж.', tone: 'service-yellow' },
]

export default function ServicesPage() {
  return <>
    <PageHeading eyebrow="Пространства колледжа" title={<>Не только<br /><em>учебные кабинеты.</em></>} description="Где найти столовую, библиотеку, актовый зал или мастерскую — собрали самые полезные места на одной странице." />
    <section className="services-section container"><div className="section-top"><div><SectionLabel>На карте</SectionLabel><h2>Куда <em>зайти?</em></h2></div><Link className="text-link" to="/map">Открыть общую карту <span><ArrowRight size={15} /></span></Link></div><div className="services-grid">{spaces.map((space, index) => { const Icon = space.icon; const location = rooms.find((room) => room.id === space.id); return <Reveal key={space.id} delay={index * 45}><Link className={`service-card ${space.tone}`} to={`/map?floor=${location.floor}&room=${location.id}`}><span className="service-card-top"><small>{space.label}</small><ArrowUpRight size={16} /></span><span className="service-card-icon"><Icon size={23} strokeWidth={1.5} /></span><span className="service-card-content"><strong>{space.note}</strong><span>{space.copy}</span></span><span className="service-card-bottom"><FloorTag floor={location.floor} /><span>{location.number ? `КАБИНЕТ ${location.number}` : `${location.floor} ЭТАЖ`}</span></span></Link></Reveal> })}</div></section>
    <section className="services-help-band"><div className="container services-help-inner"><span className="services-help-mark">i</span><div><SectionLabel index="02">НЕ НАШЛИ НУЖНОЕ?</SectionLabel><h2>Откройте <em>справочник.</em></h2><p>В нём есть кабинеты, лаборатории, лестницы и общие пространства. Ищите по названию или номеру.</p></div><Link className="button button-primary" to="/rooms">Перейти к кабинетам <ArrowRight size={15} /></Link><div className="services-help-outline" aria-hidden="true"><i /><i /><i /></div></div></section>
  </>
}
