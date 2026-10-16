import { useState } from 'react'
import { ArrowRight, Award, Check, Compass, MapPin, RotateCcw, Target, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import { games, rooms } from '../data'
import FloorMap from '../components/FloorMap'
import { PageHeading, Reveal, SectionLabel } from '../components/Primitives'

const quizIds = ['library-214', 'simulator-114', 'physics-211', 'canteen', 'psychologist-224']
const findIds = ['auditorium-104', 'aerodynamics-120', 'library-214', 'training-centre-113', 'informatics-222']
const quizQuestions = quizIds.map((id) => rooms.find((room) => room.id === id)).filter(Boolean)
const findQuestions = findIds.map((id) => rooms.find((room) => room.id === id)).filter(Boolean)

function roomLabel(room) { return `${room.name}${room.number ? ` №${room.number}` : ''}` }

export default function GamesPage() {
  const [mode, setMode] = useState(games[0].id)
  const [quizIndex, setQuizIndex] = useState(0)
  const [quizAnswer, setQuizAnswer] = useState(null)
  const [quizScore, setQuizScore] = useState(0)
  const [gameIndex, setGameIndex] = useState(0)
  const [gameFloor, setGameFloor] = useState(findQuestions[0]?.floor || 1)
  const [mapFeedback, setMapFeedback] = useState('')
  const [found, setFound] = useState(false)
  const [findScore, setFindScore] = useState(0)
  const quizRoom = quizQuestions[quizIndex]
  const targetRoom = findQuestions[gameIndex]
  const quizFinished = quizIndex >= quizQuestions.length
  const findFinished = gameIndex >= findQuestions.length

  const answerFloor = (answer) => {
    if (quizAnswer !== null || quizFinished) return
    const correct = Number(answer) === quizRoom.floor
    setQuizAnswer({ value: Number(answer), correct })
    if (correct) setQuizScore((score) => score + 1)
  }
  const nextQuiz = () => { setQuizIndex((index) => index + 1); setQuizAnswer(null) }
  const restartQuiz = () => { setQuizIndex(0); setQuizAnswer(null); setQuizScore(0) }
  const onMapSelect = (room) => {
    if (found || findFinished) return
    if (room.id === targetRoom.id) { setFound(true); setFindScore((score) => score + 1); setMapFeedback(room.id) }
    else setMapFeedback(targetRoom.id)
  }
  const nextFind = () => { const nextIndex = gameIndex + 1; setGameIndex(nextIndex); setFound(false); setMapFeedback(''); if (findQuestions[nextIndex]) setGameFloor(findQuestions[nextIndex].floor) }
  const restartFind = () => { setGameIndex(0); setFound(false); setMapFeedback(''); setFindScore(0); setGameFloor(findQuestions[0]?.floor || 1) }

  return <>
    <PageHeading eyebrow="Игры" title={<>Потренируйтесь<br /><em>по карте.</em></>} description="Два небольших задания: угадайте этаж или найдите кабинет на плане. Ошибаться можно — это тренировка." />

    <section className="games-section container"><div className="game-mode-tabs" role="tablist" aria-label="Выберите игру">{games.map((item) => { const Icon = item.id === 'floor-quiz' ? Compass : Target; return <button key={item.id} role="tab" aria-selected={mode === item.id} className={mode === item.id ? 'game-mode-tab game-mode-active' : 'game-mode-tab'} onClick={() => setMode(item.id)}><span className="game-mode-icon"><Icon size={18} /></span><span><small>{item.tag}</small><strong>{item.title}</strong><em>{item.description}</em></span><ArrowRight size={16} /></button> })}</div>

      {mode === 'floor-quiz' ? <Reveal className="game-panel" key="quiz"><div className="game-panel-head"><div><SectionLabel index="01">УЗНАЙ ЭТАЖ</SectionLabel><h2>{quizFinished ? 'Раунд завершён!' : 'Где находится место?'}</h2></div><div className="game-score"><Award size={17} /><span><strong>{quizScore}</strong> / {quizQuestions.length}</span></div></div>
        {!quizFinished ? <><div className="game-progress-line"><span style={{ width: `${quizIndex / quizQuestions.length * 100}%` }} /></div><div className="quiz-question"><span className="quiz-question-index">ВОПРОС {String(quizIndex + 1).padStart(2, '0')} / 05</span><h3>{roomLabel(quizRoom)}</h3><p>На каком этаже искать?</p></div><div className="quiz-options" role="group" aria-label="Выберите этаж">{[1, 2].map((floor) => { const picked = quizAnswer?.value === floor; const correct = quizAnswer && floor === quizRoom.floor; return <button key={floor} className={`quiz-option ${picked ? 'quiz-option-picked' : ''} ${quizAnswer && correct ? 'quiz-option-correct' : ''} ${picked && !quizAnswer.correct ? 'quiz-option-wrong' : ''}`} onClick={() => answerFloor(floor)} disabled={quizAnswer !== null}><span className="quiz-option-number">0{floor}</span><span>{floor === 1 ? 'Первый этаж' : 'Второй этаж'}</span>{quizAnswer && correct && <Check size={16} />}{picked && !quizAnswer.correct && <X size={16} />}</button> })}</div>{quizAnswer && <div className={`quiz-feedback ${quizAnswer.correct ? 'quiz-feedback-correct' : 'quiz-feedback-wrong'}`} role="status"><span>{quizAnswer.correct ? 'Верно!' : `Почти — это ${quizRoom.floor} этаж.`}</span><button onClick={quizIndex === quizQuestions.length - 1 ? nextQuiz : nextQuiz}>{quizIndex === quizQuestions.length - 1 ? 'Итоги' : 'Следующий вопрос'} <ArrowRight size={14} /></button></div>}</> : <div className="game-finish"><span className="game-finish-medal"><Award size={27} /></span><span className="eyebrow">ВАШ РЕЗУЛЬТАТ</span><strong>{quizScore}<small> / {quizQuestions.length}</small></strong><p>{quizScore === quizQuestions.length ? 'Безупречно! Ты уже почти местный.' : quizScore >= 3 ? 'Хороший ориентир — с каждым разом будет легче.' : 'Теперь карта точно запомнится лучше.'}</p><button className="button button-primary" onClick={restartQuiz}><RotateCcw size={15} /> Сыграть ещё раз</button></div>}</Reveal> : <Reveal className="game-panel find-game-panel" key="find"><div className="game-panel-head"><div><SectionLabel index="02">НАЙДИ НА КАРТЕ</SectionLabel><h2>{findFinished ? 'Ты прошёл весь маршрут!' : found ? 'Отлично, нашёл!' : 'Отметь нужное помещение'}</h2></div><div className="game-score"><Target size={17} /><span><strong>{findScore}</strong> / {findQuestions.length}</span></div></div>
        {!findFinished ? <><div className="find-target-card"><span className="find-target-symbol"><Target size={18} /></span><span><small>НАЙДИ НА {targetRoom.floor} ЭТАЖЕ</small><strong>{roomLabel(targetRoom)}</strong></span><span className="find-target-count">{String(gameIndex + 1).padStart(2, '0')} / 05</span></div><div className="find-feedback-line" role="status">{found ? <><Check size={15} /> Верно! Кабинет найден на плане.</> : mapFeedback ? <><X size={15} /> Не то место — попробуй ещё раз, цель подсвечена.</> : <><MapPin size={15} /> Нажми на помещение на схеме.</>}</div><div className="find-map-toolbar"><span>ЭТАЖ {gameFloor}</span>{found && <button onClick={nextFind}>Следующее место <ArrowRight size={14} /></button>}</div><FloorMap floor={gameFloor} compact gameTargetId="" gameFeedback={mapFeedback} onSelect={onMapSelect} /></> : <div className="game-finish"><span className="game-finish-medal"><Target size={27} /></span><span className="eyebrow">ТОЧНОСТЬ</span><strong>{findScore}<small> / {findQuestions.length}</small></strong><p>Хороший результат! Теперь ты знаешь, как устроены оба этажа.</p><button className="button button-primary" onClick={restartFind}><RotateCcw size={15} /> Пройти ещё раз</button></div>}</Reveal>}
      </section>

    <section className="games-bottom container"><span className="games-bottom-icon"><MapPin size={17} /></span><p>После игры откройте карту и попробуйте найти кабинет в самом колледже.</p><Link to="/map">Карта этажей <ArrowRight size={14} /></Link></section>
  </>
}
