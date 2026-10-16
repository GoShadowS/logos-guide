import { useEffect, useRef, useState } from 'react'
import { ArrowRight, ArrowUpRight, BookOpen, Quote, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import { PageIntro, Reveal, SectionKicker } from '../components/Primitives'
import { textFragments } from '../data'

const themes = ['Все фрагменты', ...new Set(textFragments.map((item) => item.theme))]

function FragmentDialog({ fragment, onClose }) {
  const closeRef = useRef(null)
  useEffect(() => {
    if (!fragment) return undefined
    closeRef.current?.focus()
    const onKeyDown = (event) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKeyDown)
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      window.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = previousOverflow
    }
  }, [fragment, onClose])

  if (!fragment) return null
  return (
    <div className="fragment-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <article className="fragment-dialog" role="dialog" aria-modal="true" aria-labelledby="fragment-dialog-title">
        <div className="fragment-dialog-top"><span className="eyebrow"><span className="eyebrow-dot" />АРХИВ / {fragment.reference.toUpperCase()}</span><button ref={closeRef} className="icon-button" onClick={onClose} aria-label="Закрыть фрагмент"><X size={19} /></button></div>
        <div className="fragment-dialog-symbol"><Quote size={22} strokeWidth={1.1} /></div>
        <span className="fragment-dialog-theme">{fragment.theme} · {fragment.period}</span>
        <h2 id="fragment-dialog-title">{fragment.author}</h2>
        <blockquote>{fragment.excerpt}</blockquote>
        <div className="fragment-dialog-rule" />
        <span className="fragment-context-label">КОНТЕКСТ</span>
        <p className="fragment-context">{fragment.context}</p>
        <p className="fragment-source-note">{fragment.note}</p>
        <div className="fragment-dialog-actions"><Link to={fragment.author === 'Гераклит' ? '/thinkers' : '/dictionary'} onClick={onClose}>Продолжить исследование <ArrowRight size={14} /></Link><span>ЧИТАТЬ МЕДЛЕННО</span></div>
      </article>
    </div>
  )
}

export default function TextsPage() {
  const [theme, setTheme] = useState('Все фрагменты')
  const [active, setActive] = useState(null)
  const fragments = theme === 'Все фрагменты' ? textFragments : textFragments.filter((item) => item.theme === theme)

  return (
    <>
      <PageIntro eyebrow="АРХИВ / КОРОТКОЕ ЧТЕНИЕ" number="06—08" title={<>Тексты, которые<br /><em>не замолкают.</em></>} description="Фрагмент — это не вся книга и не вся мысль. Здесь мы читаем внимательно: отмечаем, что сохранилось, кто передал слова и где начинаются разночтения." aside={<div className="texts-intro-quote"><span>ΑΝΑΓΝΩΣΙΣ</span><div className="texts-quote-lines"><i /><i /><i /><i /></div><small>ЧТЕНИЕ КАК ПУТЬ</small></div>} />

      <section className="texts-section container">
        <Reveal className="texts-heading-row"><div><SectionKicker index="01">ФРАГМЕНТЫ И ДИАЛОГИ</SectionKicker><p>Нажмите на карточку, чтобы открыть комментарий и контекст.</p></div><div className="text-filter" role="group" aria-label="Фильтр текстов">{themes.map((item) => <button key={item} className={theme === item ? 'filter-chip filter-chip-active' : 'filter-chip'} onClick={() => setTheme(item)} aria-pressed={theme === item}>{item}</button>)}</div></Reveal>

        <div className="texts-archive-grid" aria-live="polite">
          {fragments.map((item, index) => (
            <Reveal key={item.id} className="fragment-card-wrap" delay={index * 55}>
              <button className={`fragment-card fragment-card-${index % 4}`} onClick={() => setActive(item)} aria-label={`Открыть фрагмент: ${item.author}, ${item.reference}`}>
                <span className="fragment-card-head"><span>{item.reference}</span><ArrowUpRight size={16} /></span>
                <span className="fragment-card-glyph">“</span>
                <span className="fragment-card-quote">{item.excerpt}</span>
                <span className="fragment-card-bottom"><span><strong>{item.author}</strong><small>{item.period}</small></span><i><ArrowRight size={15} /></i></span>
                <span className="fragment-card-theme">{item.theme}</span>
              </button>
            </Reveal>
          ))}
          {fragments.length === 0 && <p className="text-no-results">В этом разделе пока нет текстов.</p>}
        </div>
      </section>

      <section className="texts-method-band">
        <div className="container texts-method-inner"><div className="method-icon"><BookOpen size={20} strokeWidth={1.4} /></div><div className="texts-method-copy"><SectionKicker index="02">ОТ ИСТОЧНИКА К НАШЕМУ ЧТЕНИЮ</SectionKicker><h2>Сохранившееся —<br /><em>не значит целое.</em></h2><p>Большая часть досократических текстов утрачена. Мы знаем их по цитатам, поздним пересказам и рукописям. Поэтому рядом с каждым фрагментом есть ссылка на контекст и честная оговорка о переводе.</p><Link to="/about">Как мы работаем с источниками <ArrowRight size={14} /></Link></div><div className="method-mark"><span>DK</span><small>FRAGMENTA<br />PHILOSOPHORUM</small></div></div>
      </section>

      <section className="texts-reading-note container"><div className="reading-note-line" /><p>Здесь цитаты служат началом разговора, а не его концом.</p><Link to="/dictionary">Искать слова в словаре <ArrowRight size={14} /></Link></section>
      <FragmentDialog fragment={active} onClose={() => setActive(null)} />
    </>
  )
}
