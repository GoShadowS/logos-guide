// Безопасная обёртка над localStorage.
// В приватном режиме Safari (и при заблокированных cookies) доступ к
// localStorage может бросать исключение — без try/catch это белый экран,
// особенно на iPhone. Все чтения/записи на сайте идут через эти функции.
export function storageGet(key) {
  try { return localStorage.getItem(key) } catch { return null }
}

export function storageSet(key, value) {
  try { localStorage.setItem(key, value) } catch { /* приватный режим: просто не сохраняем */ }
}

export function storageRemove(key) {
  try { localStorage.removeItem(key) } catch { /* ignore */ }
}

// Блокировка скролла фона, пока открыта модалка (iOS иначе «протягивает»
// страницу под диалогом). Возвращает функцию разблокировки.
export function lockBodyScroll() {
  const previous = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  return () => { document.body.style.overflow = previous }
}
