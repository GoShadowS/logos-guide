import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, Navigation, Sparkles, Users, X } from 'lucide-react'
import { rooms } from '../data.js'
import { venueByRoomId } from '../openDay.js'
import { lockBodyScroll } from '../storage.js'

export default function VenueModal({ roomId, onClose, fromParam = '', manualPoint = null, mode = '' }) {
  const venue = venueByRoomId(roomId)
  const room = rooms.find((item) => item.id === roomId)
  const closeRef = useRef(null)
  const dialogRef = useRef(null)

  useEffect(() => {
    if (!venue) return undefined
    const previouslyFocused = document.activeElement
    const unlock = lockBodyScroll()
    closeRef.current?.focus()
    const onKeyDown = (event) => {
      if (event.key === 'Escape') { onClose(); return }
      if (event.key !== 'Tab') return
      const dialog = dialogRef.current
      if (!dialog) return
      const focusable = dialog.querySelectorAll('a[href], button:not([disabled])')
      if (!focusable.length) return
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => {
      unlock()
      window.removeEventListener('keydown', onKeyDown)
      if (previouslyFocused instanceof HTMLElement) previouslyFocused.focus()
    }
  }, [onClose, venue])

  if (!venue || !room) return null

  const routeQuery = `to=${encodeURIComponent(room.id)}${fromParam ? `&from=${encodeURIComponent(fromParam)}` : manualPoint ? '&from=manual' : ''}${mode === 'stairs' ? '&mode=stairs' : ''}`

  return (
    <div className="venue-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className="venue-dialog" role="dialog" aria-modal="true" aria-labelledby="venue-modal-title" ref={dialogRef}>
        <div className="venue-dialog-top">
          <span className="venue-dialog-badge"><Sparkles size={13} /> {venue.badge}</span>
          <button ref={closeRef} className="icon-button" onClick={onClose} aria-label="Закрыть описание площадки"><X size={18} /></button>
        </div>
        <h2 id="venue-modal-title">{venue.title}</h2>
        <div className="venue-dialog-meta">
          <span className="venue-room-chip">Аудитория {room.number} · {room.floor} этаж</span>
          <span className="venue-audience"><Users size={13} /> {venue.audience}</span>
        </div>
        <p className="venue-dialog-text">{venue.description}</p>
        <div className="venue-highlights">
          {venue.highlights.map((item) => <span key={item} className="venue-highlight-chip">{item}</span>)}
        </div>
        <div className="venue-leader"><strong>{venue.leader}</strong></div>
        <div className="venue-dialog-actions">
          <Link className="button button-primary venue-route-button" to={`/route?${routeQuery}`} onClick={onClose}>
            <Navigation size={15} /> Построить маршрут <ArrowRight size={15} />
          </Link>
          <button className="venue-later-button" type="button" onClick={onClose}>Закрыть</button>
        </div>
        <div className="venue-dialog-foot">Маршрут проложим по коридорам этажа — от входа или от вашей отметки.</div>
      </section>
    </div>
  )
}
