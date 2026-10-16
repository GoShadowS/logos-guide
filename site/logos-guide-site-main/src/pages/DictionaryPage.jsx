import { useMemo, useState } from 'react'
import { ArrowRight, ArrowUpRight, Search, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import { PageIntro, Reveal, SectionKicker } from '../components/Primitives'
import { terms } from '../data'

const groups = ['Все слова', 'Язык и мысль', 'Мир и начало', 'Человек и действие']

export default function DictionaryPage() {
  const [query, setQuery] = useState('')
  const [group, setGroup] = useState('Все слова')
  const [openTerm, setOpenTerm] = useState('λόγος')
  const visibleTerms = useMemo(() => terms.filter((term) => {
    const matchesGroup = group === 'Все слова' || term.group === group
    const content = `${term.word} ${term.transliteration} ${term.translation} ${term.explanation}`.toLocaleLowerCase('ru')
    return matchesGroup && content.includes(query.trim().toLocaleLowerCase('ru'))
  }), [group, query])

  return (
    <>
      <PageIntro eyebrow="СПРАВОЧНИК / СЛОВА В КОНТЕКСТЕ" number="05—08" title={<>Не переводить.<br /><em>Прислушиваться.</em></>} description="Древние слова не помещаются в одну строку словаря. Здесь у каждого есть несколько оттенков, короткое объяснение и путь к соседним идеям." aside={<div className="dictionary-intro-art"><span>α</span><span>β</span><span>γ</span><i /><small>ALPHA / BETA / GAMMA</small></div>} />

      <section className="dictionary-section container">
        <Reveal className="dictionary-toolbar">
          <div className="dictionary-search"><Search size={18} /><input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Искать греческое слово или значение…" aria-label="Искать в словаре" />{query && <button onClick={() => setQuery('')} aria-label="Очистить поиск"><X size={15} /></button>}<kbd>⌕</kbd></div>
          <div className="dictionary-filters" role="group" aria-label="Раздел словаря">{groups.map((item) => <button key={item} className={group === item ? 'filter-chip filter-chip-active' : 'filter-chip'} onClick={() => setGroup(item)} aria-pressed={group === item}>{item}</button>)}</div>
        </Reveal>

        <div className="dictionary-layout">
          <div className="dictionary-list">
            <div className="dictionary-list-head"><span>ГРЕЧЕСКОЕ</span><span>ТРАНСЛИТЕРАЦИЯ</span><span>ВОЗМОЖНЫЕ ОТТЕНКИ</span><span /></div>
            {visibleTerms.map((term, index) => {
              const isOpen = openTerm === term.word
              return (
                <Reveal key={term.word} className={`term-item ${isOpen ? 'term-item-open' : ''}`} delay={index * 35}>
                  <button className="term-row" onClick={() => setOpenTerm(isOpen ? '' : term.word)} aria-expanded={isOpen}>
                    <span className="term-greek">{term.word}</span><span className="term-transliteration">{term.transliteration}</span><span className="term-translation">{term.translation}</span><span className="term-open-icon"><i /><i /></span>
                  </button>
                  {isOpen && <div className="term-expansion"><p>{term.explanation}</p><div><span>{term.group}</span><Link to={term.route}>Идти по этой нити <ArrowUpRight size={13} /></Link></div></div>}
                </Reveal>
              )
            })}
            {visibleTerms.length === 0 && <div className="dictionary-empty"><strong>Такого слова пока нет.</strong><p>Проверьте написание или снимите фильтр раздела.</p><button onClick={() => { setQuery(''); setGroup('Все слова') }}>Сбросить фильтры</button></div>}
          </div>

          <Reveal className="dictionary-side-note" delay={120}>
            <div className="dictionary-side-glyph">λόγος</div>
            <span className="eyebrow"><span className="eyebrow-dot" />ЗАМЕТКА ПЕРЕВОДЧИКА</span>
            <h2>Слова живут<br /><em>в предложениях.</em></h2>
            <p>Значение нельзя отделить от того, кто говорит, кому и зачем. Поэтому здесь нет «единственно верного» русского эквивалента — зато есть контекст.</p>
            <div className="side-note-rule" />
            <span className="side-note-foot">ГРЕЧЕСКИЙ ОРИГИНАЛ<br />+ КОРОТКИЙ ПУТЕВОДИТЕЛЬ</span>
            <Link className="side-note-link" to="/texts">Увидеть слова в тексте <ArrowRight size={14} /></Link>
          </Reveal>
        </div>
      </section>

      <section className="dictionary-bottom container"><div className="dictionary-bottom-number">08</div><div><SectionKicker index="02">МАЛЕНЬКАЯ ОГОВОРКА</SectionKicker><p>Транслитерация — подсказка для чтения, а не точное воспроизведение древнего произношения. Ударения и звучание менялись от эпохи к эпохе.</p></div><Link to="/concept/logos">Почему важен контекст? <ArrowRight size={14} /></Link></section>
    </>
  )
}
