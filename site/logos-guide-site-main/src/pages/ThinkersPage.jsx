import { useMemo, useState } from 'react'
import { ArrowRight, ArrowUpRight, Search, ScrollText, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'
import { PageIntro, Reveal, SectionKicker } from '../components/Primitives'
import { thinkers } from '../data'

const periods = ['Все', 'До Сократа', 'Классика', 'Эллинизм']

export default function ThinkersPage() {
  const [period, setPeriod] = useState('Все')
  const [query, setQuery] = useState('')
  const [activeId, setActiveId] = useState('heraclitus')
  const visibleThinkers = useMemo(() => thinkers.filter((person) => {
    const matchesPeriod = period === 'Все' || person.era === period
    const text = `${person.name} ${person.greek} ${person.epithet} ${person.summary}`.toLocaleLowerCase('ru')
    return matchesPeriod && text.includes(query.trim().toLocaleLowerCase('ru'))
  }), [period, query])
  const active = visibleThinkers.find((person) => person.id === activeId) || visibleThinkers[0]

  return (
    <>
      <PageIntro eyebrow="КОЛЛЕКЦИЯ / ГОЛОСА" number="04—08" title={<>Не пантеон.<br /><em>Собеседники.</em></>} description="Мыслители здесь не стоят в ряд как памятники. Каждый оставил вопрос, который по-прежнему можно задать — и на который нет одного общего ответа." aside={<div className="thinkers-intro-quote"><span>ΦΙΛΟΣΟΦΙΑ</span><blockquote>«Любовь<br />к мудрости»</blockquote><small>БУКВАЛЬНО — И ЭТО УЖЕ ВОПРОС</small></div>} />

      <section className="thinkers-collection container">
        <Reveal className="thinkers-controls">
          <div className="thinker-search"><Search size={17} /><input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Найти мыслителя…" aria-label="Найти мыслителя" />{query && <button onClick={() => setQuery('')} aria-label="Очистить поиск">×</button>}</div>
          <div className="thinker-filters" role="group" aria-label="Период жизни"><span className="filter-prefix">ПЕРИОД</span>{periods.map((item) => <button key={item} className={period === item ? 'filter-chip filter-chip-active' : 'filter-chip'} onClick={() => setPeriod(item)} aria-pressed={period === item}>{item}</button>)}</div>
        </Reveal>

        <div className="thinkers-layout">
          <div className="thinkers-grid" aria-live="polite">
            {visibleThinkers.map((person, index) => (
              <Reveal key={person.id} delay={index * 45}>
                <button className={`thinker-card ${person.tint} ${active?.id === person.id ? 'thinker-card-active' : ''}`} onClick={() => setActiveId(person.id)} aria-pressed={active?.id === person.id}>
                  <span className="thinker-card-serial">{String(index + 1).padStart(2, '0')} / {person.era.toUpperCase()}</span>
                  <span className="thinker-avatar"><i>{person.initial}</i><b>{person.greek.slice(0, 1)}</b></span>
                  <span className="thinker-card-name"><strong>{person.name}</strong><small>{person.epithet}</small></span>
                  <span className="thinker-card-arrow"><ArrowUpRight size={16} /></span>
                  <span className="thinker-card-dates">{person.dates}</span>
                </button>
              </Reveal>
            ))}
            {visibleThinkers.length === 0 && <div className="thinkers-empty"><span>Никого не нашли.</span><p>Попробуйте другой запрос или сбросьте фильтр периода.</p><button onClick={() => { setQuery(''); setPeriod('Все') }}>Показать всех <ArrowRight size={14} /></button></div>}
          </div>

          {active && <Reveal className="thinker-profile" delay={100} key={active.id} aria-live="polite">
            <div className={`profile-art ${active.tint}`}><span className="profile-art-greek">{active.greek}</span><span className="profile-initial">{active.initial}</span><span className="profile-art-ring" /><span className="profile-art-label">ΠΡΟΣΩΠΟ / {active.era.toUpperCase()}</span></div>
            <div className="profile-copy">
              <div className="profile-meta"><span className="eyebrow"><span className="eyebrow-dot" />{active.epithet}</span><span>{active.dates}</span></div>
              <h2>{active.name}</h2>
              <p className="profile-summary">{active.summary}</p>
              <div className="profile-rule" />
              <p className="profile-detail">{active.detail}</p>
              <div className="profile-question"><Sparkles size={15} /><span>ВОПРОС В ЕГО ДУХЕ</span><strong>{active.question}</strong></div>
              <div className="profile-links"><Link to="/texts"><ScrollText size={15} /> Читать фрагменты <ArrowRight size={14} /></Link><Link to="/map"><span>Связи на карте</span><ArrowRight size={14} /></Link></div>
            </div>
          </Reveal>}
        </div>
      </section>

      <section className="thinkers-timeline-band">
        <div className="container thinkers-timeline-inner"><div><SectionKicker index="02">НЕ СТРОЙНАЯ ЛИНИЯ</SectionKicker><h2>Мысли не сменяют<br />друг друга <em>по очереди.</em></h2></div><div className="thinker-timeline"><div className="timeline-track"><span /><span /><span /><span /></div><div className="timeline-point"><span>VI–V вв.<small>до н. э.</small></span><strong>Ранние<br />мыслители</strong></div><div className="timeline-point"><span>V–IV вв.<small>до н. э.</small></span><strong>Афины<br />и диалог</strong></div><div className="timeline-point"><span>III в.<small>до н. э. →</small></span><strong>Эллинистические<br />школы</strong></div></div></div>
      </section>

      <section className="thinkers-end container"><span className="eyebrow"><span className="eyebrow-dot" />ЧИТАЙТЕ С ОГОВОРКОЙ</span><p>Даты приблизительны, источники фрагментарны, а многие формулировки известны в поздней передаче. Мы отмечаем это там, где важно.</p><Link to="/texts">Открыть архив текстов <ArrowRight size={14} /></Link></section>
    </>
  )
}
