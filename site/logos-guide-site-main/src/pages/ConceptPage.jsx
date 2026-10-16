import { useState } from 'react'
import { ArrowDown, ArrowUpRight, BookOpen, CircleHelp } from 'lucide-react'
import { Breadcrumb, Reveal, SectionKicker, TextLink } from '../components/Primitives'

const senses = [
  { id: 'speech', number: '01', greek: 'λέγειν', label: 'Слово и речь', short: 'То, что сказано', question: 'Что говорится?', detail: 'В обычном греческом λόγος может обозначать слово, речь, рассказ или высказывание. Это самый осязаемый вход в понятие: кто-то говорит, кто-то слушает, и между ними возникает смысл.', mark: 'φωνή' },
  { id: 'account', number: '02', greek: 'λογίζεσθαι', label: 'Довод и объяснение', short: 'То, что обосновано', question: 'Почему это так?', detail: 'Логосом может быть рассуждение, расчёт, основание или объяснение. Важен не просто звук, а связность: можно ли развернуть мысль и показать, на чём она держится?', mark: 'αἰτία' },
  { id: 'order', number: '03', greek: 'κοινός', label: 'Связь и порядок', short: 'То, что объединяет', question: 'Что делает целое связным?', detail: 'В некоторых философских контекстах Логос читают как общий принцип или порядок. Это уже не нейтральный перевод слова: такая интерпретация зависит от автора и требует внимательного чтения.', mark: 'κόσμος' },
]

const moments = [
  { date: 'VI–V вв. до н. э.', name: 'Гераклит', tag: 'ФРАГМЕНТЫ', text: 'В сохранившихся высказываниях Логос связан с тем, что общее и доступно для слушания, хотя люди часто не замечают его. Детали толкования остаются предметом спора.' },
  { date: 'IV в. до н. э.', name: 'Аристотель', tag: 'РАЗНЫЕ КОНТЕКСТЫ', text: 'Логос встречается в значениях речи, рассуждения, определения и довода. У Аристотеля нельзя свести это слово к одной доктрине.' },
  { date: 'III в. до н. э. →', name: 'Стоики', tag: 'ФИЛОСОФСКАЯ ШКОЛА', text: 'В стоической картине мира Логос получает смысл разумного начала, пронизывающего космос. Это часть целой системы взглядов, а не универсальный перевод.' },
  { date: 'I в. н. э.', name: 'Евангелие от Иоанна', tag: 'ИНОЙ ГОЛОС', text: 'Начало Евангелия использует греческое слово Logos в богословском контексте. Связь с философскими употреблениями обсуждают, но их не следует считать тождественными.' },
]

export default function ConceptPage() {
  const [selected, setSelected] = useState('speech')
  const sense = senses.find((item) => item.id === selected) || senses[0]

  return (
    <>
      <section className="concept-hero">
        <div className="container concept-hero-inner">
          <Breadcrumb items={[{ label: 'Понятие', to: '/ideas' }, { label: 'Логос' }]} />
          <div className="concept-hero-grid">
            <div className="concept-hero-copy">
              <span className="eyebrow"><span className="eyebrow-dot" />ОСНОВНОЕ ПОНЯТИЕ / 01</span>
              <div className="concept-greek-large">λόγος</div>
              <h1>Одна нить.<br /><em>Несколько смыслов.</em></h1>
              <p>Иногда это слово, иногда довод, а иногда — имя для общего порядка. У Логоса нет одного короткого определения, которое работало бы во всех текстах.</p>
              <a className="concept-scroll" href="#meanings" onClick={(event) => { event.preventDefault(); document.getElementById('meanings')?.scrollIntoView({ behavior: 'smooth' }) }}>ПРОСЛЕДИТЬ ЗНАЧЕНИЯ <ArrowDown size={14} /></a>
            </div>
            <div className="concept-hero-graphic" aria-hidden="true">
              <div className="concept-ring concept-ring-1" /><div className="concept-ring concept-ring-2" /><div className="concept-ring concept-ring-3" />
              <span className="concept-graphic-word">ΛΟΓΟΣ</span><span className="concept-graphic-index">03 / 01</span><span className="concept-graphic-side">КОНТЕКСТ<br />ИЗМЕНЯЕТ<br />ЗНАЧЕНИЕ</span><i className="concept-graphic-dot" />
            </div>
          </div>
          <div className="concept-hero-footer"><span>ΛΟΓΟΣ / LOGOS</span><span>НЕ ФОРМУЛА, А ПОЛЕ ЗНАЧЕНИЙ</span><span>ЧИТАТЬ ДАЛЬШЕ ↓</span></div>
        </div>
      </section>

      <section className="concept-meanings container section-pad" id="meanings">
        <Reveal className="concept-meanings-heading"><SectionKicker index="01">ТРИ СПОСОБА СЛЫШАТЬ</SectionKicker><h2>Сначала —<br /><em>не перевод, а вопрос.</em></h2><p>Выберите оттенок. Он не заменяет остальные — только показывает, с какого края можно подступиться.</p></Reveal>
        <Reveal className="sense-selector" delay={80} role="tablist" aria-label="Значения слова логос">
          {senses.map((item) => <button key={item.id} className={`sense-tab ${selected === item.id ? 'sense-tab-active' : ''}`} role="tab" aria-selected={selected === item.id} onClick={() => setSelected(item.id)}><span>{item.number}</span><strong>{item.label}</strong><small>{item.short}</small><i>{selected === item.id && <ArrowUpRight size={16} />}</i></button>)}
        </Reveal>
        <Reveal className="sense-detail" delay={130} key={sense.id} aria-live="polite">
          <div className="sense-detail-left"><span className="sense-detail-greek">{sense.mark}</span><span className="sense-detail-number">{sense.number}</span></div>
          <div className="sense-detail-copy"><span className="eyebrow">{sense.greek}</span><h3>{sense.label}</h3><p>{sense.detail}</p><div className="sense-question"><CircleHelp size={15} /><span>ПОПРОБУЙТЕ СПРОСИТЬ</span><strong>{sense.question}</strong></div></div>
          <div className="sense-detail-aside"><span>λόγος</span><i>≠</i><span>ОДИН ПЕРЕВОД</span></div>
        </Reveal>
      </section>

      <section className="concept-timeline-band">
        <div className="container">
          <Reveal className="concept-timeline-heading"><SectionKicker index="02">СЛОВО ВРЕМЕНИ</SectionKicker><h2>Значение не путешествует<br />в неизменном <em>виде.</em></h2><p>Вот несколько контекстов, не одна непрерывная история. Между ними есть сходство — и существенные различия.</p></Reveal>
          <div className="concept-timeline">
            {moments.map((moment, index) => <Reveal key={moment.name} className="concept-moment" delay={index * 70}><div className="moment-axis"><span>{moment.date}</span><i /><b>{String(index + 1).padStart(2, '0')}</b></div><div className="moment-copy"><span className="moment-tag">{moment.tag}</span><h3>{moment.name}</h3><p>{moment.text}</p></div></Reveal>)}
          </div>
          <div className="timeline-caveat"><i />ПАРАЛЛЕЛИ НЕ ОЗНАЧАЮТ ОДИНАКОВОГО СМЫСЛА</div>
        </div>
      </section>

      <section className="concept-reading container section-pad-small">
        <Reveal className="concept-reading-symbol"><BookOpen size={29} strokeWidth={1.1} /></Reveal>
        <Reveal className="concept-reading-copy" delay={80}><SectionKicker index="03">ПРОВЕРИТЬ НА ТЕКСТЕ</SectionKicker><h2>Лучше один<br /><em>фрагмент, чем тезис.</em></h2><p>Войдите в слово через фрагменты Гераклита и посмотрите, что именно может значить «общий Логос» в контексте.</p><div className="concept-reading-links"><TextLink to="/texts">Открыть фрагменты</TextLink><TextLink to="/dictionary">Свериться со словарём</TextLink></div></Reveal>
        <div className="concept-reading-foot"><span>DK 22</span><span>ΣΧΟΛΙΟΝ</span><span>01 / 03</span></div>
      </section>
    </>
  )
}
