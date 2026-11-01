export function storageGet(key) {
  try { return localStorage.getItem(key) } catch { return null }
}

export function storageSet(key, value) {
  try { localStorage.setItem(key, value) } catch { return }
}

export function storageRemove(key) {
  try { localStorage.removeItem(key) } catch { return }
}

export function lockBodyScroll() {
  const previous = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  return () => { document.body.style.overflow = previous }
}
