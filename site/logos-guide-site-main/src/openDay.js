export const OPEN_DAY_STORAGE_KEY = 'open-day-enabled'
export const OPEN_DAY_EVENT = 'open-day-change'

export const openDayVenues = [
  {
    roomId: 'class-138',
    title: 'Кибербез разума',
    badge: 'Площадка дня открытых дверей',
    description: 'Площадка о критическом мышлении и цифровой гигиене: как отличать факты от манипуляций, проверять источники, защищать личные данные и безопасно пользоваться сервисами.',
    leader: 'Руководитель — Рыбыдак Матвей',
    audience: 'Для абитуриентов и гостей колледжа',
    highlights: ['Критическое мышление', 'Цифровая гигиена', 'Безопасность в сети'],
  },
  {
    roomId: 'language-148',
    title: 'Игра «Информационная безопасность»',
    badge: 'Площадка дня открытых дверей',
    description: 'Командная игра: участники ищут фишинговые письма, поддельные ссылки и небезопасные сценарии, учатся замечать уловки и правильно реагировать на них.',
    leader: 'Ведущие — студенты групп 0-2',
    audience: 'Командная игра, 10–20 минут',
    highlights: ['Фишинг', 'Командная работа', 'Практика на примерах'],
  },
  {
    roomId: 'simulator-114',
    title: 'Мастер-класс «Сетевое администрирование»',
    badge: 'Площадка дня открытых дверей',
    description: 'На практике покажем, как работает коммутатор и собирается мини-схема сети: подключение кабелей, настройка портов и проверка связи между устройствами.',
    leader: 'Руководитель — Федеров Илья',
    audience: 'Студенты групп 0-2',
    highlights: ['Коммутатор', 'Мини-схема сети', 'Практическая настройка'],
  },
  {
    roomId: 'training-centre-113',
    title: 'Полёт на симуляторах БПЛА',
    badge: 'Площадка дня открытых дверей',
    description: 'Знакомство с типами дронов и основами управления: тренажёрный центр с малой полётной зоной, инструктаж и пробный полёт на симуляторе.',
    leader: 'Инструктор БПЛА и студенты группы Э-2',
    audience: 'Пробный полёт под присмотром',
    highlights: ['Типы дронов', 'Управление', 'Тренажёрный центр'],
  },
  {
    roomId: 'radio-lab-228',
    title: 'Робототехника',
    badge: 'Площадка дня открытых дверей',
    description: 'Демонстрация роботов и сборка: участники увидят, как устроены механизмы и программы, и попробуют собрать простую конструкцию.',
    leader: 'Студенты группы Р-2',
    audience: 'Демонстрация и сборка',
    highlights: ['Демонстрация роботов', 'Сборка', 'Программирование'],
  },
]

export function venueByRoomId(roomId) {
  return openDayVenues.find((venue) => venue.roomId === roomId) || null
}

export function isVenueRoom(roomId) {
  return openDayVenues.some((venue) => venue.roomId === roomId)
}

export function readOpenDayEnabled(storage = null, env = null) {
  const store = storage || (typeof localStorage !== 'undefined' ? localStorage : null)
  try {
    const stored = store ? store.getItem(OPEN_DAY_STORAGE_KEY) : null
    if (stored !== null) return stored === 'true'
  } catch {
    // ignore storage errors
  }
  const flag = env || (typeof import.meta !== 'undefined' && import.meta.env ? import.meta.env.VITE_OPEN_DAY : undefined)
  if (flag !== undefined && flag !== null) return flag !== 'false'
  return true
}

export function persistOpenDayEnabled(value) {
  try {
    localStorage.setItem(OPEN_DAY_STORAGE_KEY, String(value))
  } catch {
    // ignore storage errors
  }
}
