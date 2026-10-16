import { useMemo, useState } from 'react'
import { ArrowRight, BookOpen, Check, Compass, Map, Quote, Sparkles, Users, TimerReset } from 'lucide-react'
import { Link } from 'react-router-dom'
import { PageIntro, Reveal, SectionKicker } from '../components/Primitives'
import { guideRoutes } from '../data'

const durations = [
  { id: '10', label: '10 минут', note: 'Короткое знакомство' },
  { id: '25', label: '25 минут', note: 'Основной маршрут' },
  { id: '45', label: '45 минут', note: 'Неспешное исследование' },
]
const icons = { spark: Sparkles, map: Map, book: BookOpen, people: Users, quote: Quote }

export default function GuidePage() {
  const [duration, setDuration] = useState('25')
  const [completed, setCompleted] = useState(() => new Set())
  const route = useMemo(() => guideRoutes[duration], [duration])
  const progress = route.length ? Math.round((completed.size / route.length) * 100) : 0
  const selectedDuration = durations.find((item) => item.id === duration)

  const toggleStep = (number) => {
    setCompleted((current) => {
      const next = new Set(current)
      next.has(number) ? next.delete(number) : next.add(number)
      return next
    })
  }

  return (
    <>
      <PageIntro eyebrow="ПУТЕВОДИТЕЛЬ / ВХОД В ТЕМУ" number="01—08" title={<>Начать можно<br /><em>с любого вопроса.</em></>} description="Это не курс и не лестница с правильными ступенями. Выберите время, которое есть сейчас, — и соберите свой первый маршрут." aside={<div className="intro-aside-card"><span className="aside-card-glyph">Λ</span><span>МАЛЕНЬКИЙ СОВЕТ</span><p>Читайте не быстрее, чем успеваете удивляться.</p></div>} />

      <section className="guide-selector container">
        <Reveal className="guide-selector-head">
          <SectionKicker index="01">ВЫБЕРИТЕ ТЕМП</SectionKicker>
          <p>Маршрут можно менять на ходу.<br />Отмечайте то, что уже прошли.</p>
        </Reveal>
        <Reveal className="duration-tabs" delay={70} role="tablist" aria-label="Продолжительность маршрута">
          {durations.map((item) => (
            <button key={item.id} role="tab" aria-selected={duration === item.id} className={`duration-tab ${duration === item.id ? 'duration-tab-active' : ''}`} onClick={() => setDuration(item.id)}>
              <span>{item.label}</span><small>{item.note}</small><i>{duration === item.id && <Check size={13} />}</i>
            </button>
          ))}
        </Reveal>
        <div className="guide-route-layout">
          <div className="guide-route-list" role="tabpanel">
            {route.map((step, index) => {
              const Icon = icons[step.icon] || BookOpen
              const isDone = completed.has(`${duration}-${step.number}`)
              return (
                <Reveal key={`${duration}-${step.number}`} className={`guide-step ${isDone ? 'guide-step-done' : ''}`} delay={index * 55}>
                  <Link to={step.to} className="guide-step-link">
                    <span className="guide-step-number">{step.number}</span>
                    <span className="guide-step-icon"><Icon size={19} strokeWidth={1.6} /></span>
                    <span className="guide-step-copy"><strong>{step.title}</strong><small>{step.detail}</small></span>
                    <span className="guide-step-time">{step.time}</span>
                    <ArrowRight className="guide-step-arrow" size={17} />
                  </Link>
                  <button className="guide-step-check" aria-label={`${isDone ? 'Снять отметку' : 'Отметить как пройденное'}: ${step.title}`} aria-pressed={isDone} onClick={() => toggleStep(step.number)}>
                    {isDone && <Check size={14} />}
                  </button>
                </Reveal>
              )
            })}
            <div className="guide-route-end"><span /><span>МАРШРУТ ПРОДОЛЖАЕТСЯ</span><span /></div>
          </div>
          <Reveal className="guide-progress-card" delay={100}>
            <div className="progress-card-top"><span className="eyebrow">ВАШ МАРШРУТ</span><TimerReset size={16} /></div>
            <div className="progress-card-time">{duration}<small>мин</small></div>
            <p>{selectedDuration?.note}. Без спешки и обязательных остановок.</p>
            <div className="progress-track" aria-label={`Пройдено ${progress}%`}><span style={{ width: `${progress}%` }} /></div>
            <div className="progress-foot"><span>{completed.size} из {route.length} шагов</span><strong>{progress}%</strong></div>
            <div className="progress-card-rule" />
            <div className="progress-tip"><span className="tip-dot" /><p>Не уверены, что дальше? Кликайте на то, что вызывает вопрос.</p></div>
            <Link to="/map" className="card-arrow-link">Открыть карту идей <ArrowRight size={15} /></Link>
          </Reveal>
        </div>
      </section>

      <section className="guide-principles container section-pad-small">
        <Reveal><SectionKicker index="02">КАК ЗДЕСЬ ЧИТАТЬ</SectionKicker><h2>Три простых<br /><em>разрешения.</em></h2></Reveal>
        <div className="principle-grid">
          <Reveal className="principle" delay={50}><span>01</span><h3>Можно не знать.</h3><p>Греческие слова даны с подсказками. Мы не проверяем знание транслитерации.</p></Reveal>
          <Reveal className="principle" delay={120}><span>02</span><h3>Можно спорить.</h3><p>У многих фрагментов несколько переводов и толкований. Разногласие — часть истории.</p></Reveal>
          <Reveal className="principle" delay={190}><span>03</span><h3>Можно свернуть.</h3><p>Откройте карту, словарь или текст в любой момент. Линейный путь — только предложение.</p></Reveal>
        </div>
      </section>

      <section className="guide-final-band">
        <div className="container guide-final-inner"><div><span className="eyebrow"><span className="eyebrow-dot" />СЛЕДУЮЩАЯ ОСТАНОВКА</span><h2>Карта, на которой<br /><em>можно заблудиться.</em></h2></div><Link className="button button-light" to="/map">Открыть карту <ArrowRight size={16} /></Link><div className="guide-final-mark"><Compass size={26} strokeWidth={1} /></div></div>
      </section>
    </>
  )
}
