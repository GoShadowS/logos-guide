import { useEffect, useRef, useState } from 'react'
import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router-dom'

export function Reveal({ children, className = '', delay = 0, as: Tag = 'div', style, ...props }) {
  const ref = useRef(null)
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const node = ref.current
    if (!node) return undefined
    if (!('IntersectionObserver' in window)) { setVisible(true); return undefined }
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) { setVisible(true); observer.unobserve(node) }
    }, { threshold: 0.08, rootMargin: '0px 0px -30px 0px' })
    observer.observe(node)
    return () => observer.disconnect()
  }, [])

  return <Tag ref={ref} {...props} className={`reveal ${visible ? 'reveal-visible' : ''} ${className}`} style={{ ...style, '--reveal-delay': `${delay}ms` }}>{children}</Tag>
}

export function PageHeading({ eyebrow, title, description, aside, className = '' }) {
  return <section className={`page-heading container ${className}`}><div className="page-heading-main"><span className="eyebrow"><i className="eyebrow-dot" />{eyebrow}</span><h1>{title}</h1>{description && <p>{description}</p>}</div>{aside && <div className="page-heading-aside">{aside}</div>}</section>
}

export function SectionLabel({ children, index }) {
  return <div className="section-label"><span className="section-label-dot" />{index && <span className="section-label-index">{index}</span>}<span>{children}</span></div>
}

export function TextLink({ to, children, className = '' }) {
  return <Link className={`text-link ${className}`} to={to}>{children}<span><ArrowRight size={15} /></span></Link>
}

export function FloorTag({ floor }) {
  return <span className={`floor-tag floor-tag-${floor}`}>{floor} этаж</span>
}
