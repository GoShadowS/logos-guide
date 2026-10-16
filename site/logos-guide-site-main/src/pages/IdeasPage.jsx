import { useMemo, useState } from 'react'
import { ArrowRight, ArrowUpRight, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'
import { PageIntro, Reveal, SectionKicker } from '../components/Primitives'
import { ideas } from '../data'

const filters = ['Все', 'Мир', 'Человек', 'Язык']

export default function IdeasPage() {
  const [filter, setFilter] = useState('Все')
  const [activeId, setActiveId] = useState('logos')
  const visibleIdeas = useMemo(() => filter === 'Все' ? ideas : ideas.filter((idea) => idea.category === filter), [filter])
  const active = visibleIdeas.find((idea) => idea.id === activeId) || visibleIdeas[0] || ideas[0]

  return (
    <>
      <PageIntro eyebrow="КОЛЛЕКЦИЯ / ОСНОВНЫЕ ИДЕИ" number="03—08" title={<>Шесть линз,<br /><em>чтобы увидеть мир.</em></>} description="Античная философия не начинается с одного ответа. Она начинается с вопросов — о природе, о том, как мы мыслим, и о словах, которыми это можно сказать." aside={<div className="ideas-intro-notation"><span>ἰδέα</span><i /><small>ИДЕЯ · ОБРАЗ · ФОРМА</small></div>} />

      <section className="ideas-explorer container">
        <Reveal className="ideas-explorer-top">
          <div><SectionKicker index="01">ВЫБЕРИТЕ ОПТИКУ</SectionKicker><p>Нажмите на карточку — откроется вопрос, который она ставит.</p></div>
          <div className="idea-filters" role="group" aria-label="Фильтр идей">
            {filters.map((item) => <button key={item} className={filter === item ? 'filter-chip filter-chip-active' : 'filter-chip'} onClick={() => setFilter(item)} aria-pressed={filter === item}>{item}</button>)}
          </div>
        </Reveal>

        <div className="ideas-workspace">
          <div className="ideas-grid" aria-live="polite">
            {visibleIdeas.map((idea, index) => (
              <Reveal key={idea.id} delay={index * 55} className={`idea-card-wrap ${active.id === idea.id ? 'idea-card-wrap-active' : ''}`}>
                <button className={`idea-card idea-card-${idea.color} ${active.id === idea.id ? 'idea-card-active' : ''}`} onClick={() => setActiveId(idea.id)} aria-pressed={active.id === idea.id}>
                  <div className="idea-card-head"><span>{idea.number} / {idea.category.toUpperCase()}</span><ArrowUpRight size={15} /></div>
                  <div className="idea-sigil" aria-hidden="true"><span>{idea.greek.slice(0, 1)}</span><i /><b /></div>
                  <div className="idea-card-title"><h3>{idea.title}</h3><span>{idea.greek}</span></div>
                  <div className="idea-card-foot"><span>{idea.question}</span><i><ArrowRight size={14} /></i></div>
                </button>
              </Reveal>
            ))}
          </div>

          <Reveal className="idea-detail-panel" delay={100} aria-live="polite">
            <div className={`idea-detail-orbit idea-detail-${active.color}`}><div className="idea-detail-outer" /><span>{active.greek}</span><i /></div>
            <span className="eyebrow"><span className="eyebrow-dot" />{active.number} / {active.category}</span>
            <h2>{active.title}<span>{active.greek}</span></h2>
            <p>{active.description}</p>
            <div className="idea-question"><Sparkles size={15} /><span>ВОПРОС ДЛЯ ВАС</span><strong>{active.question}</strong></div>
            <Link className="button button-dark" to={active.to}>Открыть понятие <ArrowRight size={15} /></Link>
            <span className="idea-detail-index">Λ · Ε · Ω</span>
          </Reveal>
        </div>
      </section>

      <section className="ideas-note-band">
        <div className="container ideas-note-inner"><div className="ideas-note-greek">πάντα<br /><em>ῥεῖ</em></div><div className="ideas-note-copy"><SectionKicker index="02">НЕ СЛОВАРЬ ОТВЕТОВ</SectionKicker><h2>Понятие — это<br /><em>способ заметить.</em></h2><p>Названия помогают держать нить разговора, но не закрывают её. Одна идея может менять смысл, когда меняются вопрос, автор и время.</p><Link className="ideas-note-link" to="/map">Посмотреть связи на карте <ArrowRight size={15} /></Link></div><div className="ideas-note-stamp"><span>ANCIENT<br />QUESTIONS</span><i>?</i></div></div>
      </section>

      <section className="ideas-end container section-pad-small">
        <Reveal><SectionKicker index="03">ХОТИТЕ ПРОДОЛЖИТЬ?</SectionKicker><h2>Идеи обретают голос<br />в <em>текстах.</em></h2></Reveal>
        <Reveal className="ideas-end-links" delay={80}><p>Короткие фрагменты — с контекстом, чтобы читать не только слова, но и их время.</p><Link className="button button-outline-dark" to="/texts">Перейти к текстам <ArrowRight size={15} /></Link></Reveal>
      </section>
    </>
  )
}
