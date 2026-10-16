import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Bug, Construction, Smartphone, X } from 'lucide-react'
import { lockBodyScroll } from '../storage.js'

export const WELCOME_HIDDEN_KEY = 'logos-welcome-hidden'

function readHidden() {
  try { return localStorage.getItem(WELCOME_HIDDEN_KEY) === '1' } catch { return false }
}

export default function WelcomeDialog() {
  const [open, setOpen] = useState(() => !readHidden())
  const [dontShow, setDontShow] = useState(false)
  const closeRef = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    const timer = window.setTimeout(() => closeRef.current?.focus(), 40)
    const unlock = lockBodyScroll()
    const onKeyDown = (event) => { if (event.key === 'Escape') setOpen(false) }
    window.addEventListener('keydown', onKeyDown)
    return () => {
      window.clearTimeout(timer)
      unlock()
      window.removeEventListener('keydown', onKeyDown)
    }
  }, [open ])

  if (!open) return null

  const close = () => {
    if (dontShow) {
      try { localStorage.setItem(WELCOME_HIDDEN_KEY, '1') } catch { /* private mode: просто закрыть */ }
    }
    setOpen(false)
  }

  return (
    <div className="welcome-backdrop" onMouseDown={(event) => event.target === event.currentTarget && close()}>
      <section className="welcome-dialog" role="dialog" aria-modal="true" aria-labelledby="welcome-title" aria-describedby="welcome-text">
        <button className="icon-button welcome-close" onClick={close} aria-label="Закрыть приветствие"><X size={18} /></button>
        <span className="welcome-badge"><Construction size={15} /> Сайт в разработке</span>
        <h2 id="welcome-title">Добро пожаловать в «ЛОГОС»!</h2>
        <p id="welcome-text">
          Путеводитель ещё дорабатывается, поэтому иногда могут встречаться ошибки.
          Если заметите баг или неточность на карте — пожалуйста, сообщите{' '}
          <Link to="/about" onClick={close}>команде проекта</Link>: так мы быстрее всё исправим.
        </p>

        <div className="welcome-app">
          <img className="welcome-qr" src="/android-app-qr.png" alt="QR-код для скачивания Android-приложения «ЛОГОС»" width="148" height="148" />
          <div className="welcome-app-copy">
            <span className="welcome-app-title"><Smartphone size={17} /> Приложение для Android</span>
            <p>Наведите камеру телефона на QR-код, чтобы скачать приложение.</p>
            <span className="welcome-app-hint"><Bug size={14} /> О багах в приложении тоже сообщайте команде.</span>
          </div>
        </div>

        <label className="welcome-toggle">
          <input type="checkbox" checked={dontShow} onChange={(event) => setDontShow(event.target.checked)} />
          <i className="welcome-switch" aria-hidden="true"><b /></i>
          <span>Больше не показывать</span>
        </label>

        <button ref={closeRef} className="button button-primary welcome-submit" onClick={close}>Понятно, к карте!</button>
      </section>
    </div>
  )
}
