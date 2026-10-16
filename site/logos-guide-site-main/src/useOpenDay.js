import { useCallback, useEffect, useState } from 'react'
import { OPEN_DAY_EVENT, persistOpenDayEnabled, readOpenDayEnabled } from './openDay'

export function useOpenDay() {
  const [enabled, setEnabled] = useState(() => readOpenDayEnabled())

  useEffect(() => {
    const handler = (event) => setEnabled(Boolean(event.detail))
    window.addEventListener(OPEN_DAY_EVENT, handler)
    return () => window.removeEventListener(OPEN_DAY_EVENT, handler)
  }, [])

  const toggle = useCallback((value) => {
    setEnabled((current) => {
      const next = typeof value === 'boolean' ? value : !current
      persistOpenDayEnabled(next)
      window.dispatchEvent(new CustomEvent(OPEN_DAY_EVENT, { detail: next }))
      return next
    })
  }, [])

  return [enabled, toggle]
}
