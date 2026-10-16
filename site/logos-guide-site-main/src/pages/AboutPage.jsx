import { useState } from 'react'
import { ArrowRight, DoorOpen, Map, MapPin } from 'lucide-react'
import { Link } from 'react-router-dom'
import { PageHeading, Reveal, SectionLabel } from '../components/Primitives.jsx'

const questions = [
  { title: 'Как пользоваться планом?', answer: 'Выберите этаж и нажмите на помещение. Синие короткие линии на схеме показывают входы. Двойное нажатие на лестницу переключает этаж; на телефоне можно также нажать кнопку в карточке лестницы.' },
  { title: 'Можно ли проложить путь между этажами?', answer: 'Да. Выберите начальную точку и кабинет, затем лифт или ближайшую лестницу. Маршрут пройдёт по нанесённым коридорам и дверям обоих этажей.' },
  { title: 'Можно ли отметить место без GPS?', answer: 'Да. На карте нажмите «Поставить метку», затем коснитесь любой точки внутри корпуса — помещения или коридора. Эта точка может стать началом маршрута.' },
  { title: 'GPS покажет кабинет автоматически?', answer: 'Нет. Телефон не определяет этаж и внутри здания может ошибаться на десятки метров. Чтобы показать приблизительную GPS-метку на плане, нужно привязать два известных места на выбранном этаже. Без привязки используйте ручную отметку.' },
  { title: 'Если проход закрыт или нужен доступный маршрут?', answer: 'Схема не знает о временных закрытиях и не подтверждает доступность всех проходов. Лифт показан на плане, но его работу и удобный путь лучше уточнить у сотрудников колледжа.' },
  { title: 'Что делать, если кабинет не найден?', answer: 'Попробуйте номер или часть названия в справочнике. Если надпись на двери отличается от схемы, ориентируйтесь на указатели и уточняйте у сотрудников.' },
]

// To replace placeholders, add JPGs with these exact filenames in public/team/.
const team = [
  { name: 'Давыдова Ксения', role: 'Руководитель проекта', photo: 'ksenia-davydova.jpg', initials: 'КД' },
  { name: 'Лисовой Данила', role: 'Дизайнер и разработчик сайта', photo: 'danila-lisovoy.jpg', initials: 'ДЛ' },
  { name: 'Бодян Тимофей', role: 'Разработчик приложения для Android', photo: 'timofey-bodyan.jpg', initials: 'ТБ' },
  { name: 'Брекин Артем', role: 'Вдохновитель проекта', photo: 'artem-brekin.jpg', initials: 'АБ' },
]

function TeamPhoto({ person }) {
  const [loaded, setLoaded] = useState(false)
  return <div className="about-photo-frame"><span className="about-photo-placeholder" aria-hidden="true">{person.initials}</span><img className={`about-person-photo ${loaded ? 'about-person-photo-loaded' : ''}`} src={`/team/${person.photo}`} alt={`Фото: ${person.name}`} loading="lazy" onLoad={() => setLoaded(true)} onError={() => setLoaded(false)} /></div>
}

export default function AboutPage() {
  const [openIndex, setOpenIndex] = useState(0)
  return <>
    <PageHeading eyebrow="О проекте" title={<>Карта, которая<br /><em>помогает не опоздать.</em></>} description="Мы собрали планы двух этажей, список помещений и простой поиск. Это учебный проект, поэтому будем уточнять схему по мере изменений в колледже." />
    <section className="about-values-band"><div className="container about-values"><Reveal className="about-values-quote"><SectionLabel>Для чего этот сайт</SectionLabel><blockquote>Меньше искать.<br /><em>Больше успевать.</em></blockquote><p>Нужный кабинет можно найти по номеру, на карте или через список помещений — как удобнее именно вам.</p></Reveal><div className="about-value-list">{[{ icon: Map, title: 'Карта двух этажей', detail: 'Помещения, входы, лестницы и лифт на одной схеме.' }, { icon: DoorOpen, title: 'Путь по коридорам', detail: 'Маршрут не проходит через соседние кабинеты.' }, { icon: MapPin, title: 'Своя точка старта', detail: 'Поставьте отметку в помещении или прямо в коридоре.' }].map((item, index) => { const Icon = item.icon; return <Reveal className="about-value-row" key={item.title} delay={index * 50}><span>0{index + 1}</span><i><Icon size={18} /></i><div><strong>{item.title}</strong><small>{item.detail}</small></div></Reveal> })}</div></div></section>
    <section className="about-team-band" aria-labelledby="about-team-title"><div className="container about-team"><header className="about-team-heading"><div><SectionLabel>Люди за проектом</SectionLabel><h2 id="about-team-title">Наша <em>команда.</em></h2></div><p>Пока здесь инициалы. Позже добавим настоящие фотографии авторов.</p></header><div className="about-team-grid">{team.map((person, index) => <Reveal key={person.name} className="about-person" delay={index * 55}><TeamPhoto person={person} /><div className="about-person-copy"><span className="about-person-index">0{index + 1}</span><h3>{person.name}</h3><p>{person.role}</p></div></Reveal>)}</div></div></section>
    <section className="about-access container"><div className="about-access-icon"><MapPin size={23} /></div><div><SectionLabel>Важно помнить</SectionLabel><h2>Схема — ориентир,<br /><em>не замена указателям.</em></h2><p>Проходы могут быть временно закрыты, а GPS в помещении неточен. Если вам нужен безбарьерный маршрут, заранее уточните детали у сотрудников колледжа.</p></div><Link to="/map">Посмотреть план <ArrowRight size={14} /></Link></section>
    <section className="faq-band"><div className="container faq-layout"><div className="faq-heading"><SectionLabel>Коротко о работе сайта</SectionLabel><h2>Вопросы<br />и <em>ответы.</em></h2><p>Что делать, если не получается найти кабинет или метку на плане.</p></div><div className="faq-list">{questions.map((item, index) => <div className={`faq-item ${openIndex === index ? 'faq-item-open' : ''}`} key={item.title}><button onClick={() => setOpenIndex(openIndex === index ? -1 : index)} aria-expanded={openIndex === index}><span>0{index + 1}</span><strong>{item.title}</strong><i><b /><b /></i></button>{openIndex === index && <p>{item.answer}</p>}</div>)}</div></div></section>
    <section className="about-bottom container"><div><SectionLabel>Попробовать</SectionLabel><h2>Откройте <em>карту.</em></h2></div><div className="about-bottom-actions"><Link className="button button-primary" to="/map">Карта этажей <ArrowRight size={15} /></Link><Link to="/rooms">Список помещений <ArrowRight size={14} /></Link></div></section>
  </>
}
