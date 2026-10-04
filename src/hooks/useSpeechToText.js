/**
 * hooks/useSpeechToText.js — голосовой ввод на базе expo-speech-recognition.
 *
 * Хук инкапсулирует всё, что связано с распознаванием речи:
 *   • проверку доступности службы распознавания на устройстве;
 *   • запрос разрешений (микрофон + распознавание речи);
 *   • подписку на события и корректное снятие подписок при размонтировании;
 *   • остановку сессии, если пользователь ушёл с экрана.
 *
 * Использование:
 *   const voice = useSpeechToText({ language, onResult });
 *   voice.toggle();          // запуск/остановка
 *   voice.recognizing;       // идёт ли распознавание
 *   voice.unavailableReason; // null | 'unavailable' | 'denied' | 'error'
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { ExpoSpeechRecognitionModule } from 'expo-speech-recognition';

/** Локаль распознавания по языку интерфейса */
function localeFor(language) {
  return language === 'ru' ? 'ru-RU' : 'en-US';
}

export function useSpeechToText({ language = 'ru', onResult, onFinal } = {}) {
  const [available, setAvailable] = useState(false);
  const [recognizing, setRecognizing] = useState(false);
  const [unavailableReason, setUnavailableReason] = useState(null);

  // Колбэки храним в ref, чтобы не переподписываться на каждое изменение
  const handleResultRef = useRef(onResult);
  const handleFinalRef = useRef(onFinal);
  handleResultRef.current = onResult;
  handleFinalRef.current = onFinal;

  // Доступна ли служба распознавания (Android: есть ли распознаватель в системе)
  useEffect(() => {
    let cancelled = false;
    try {
      const isAvailable = ExpoSpeechRecognitionModule.isRecognitionAvailable();
      if (!cancelled) {
        setAvailable(isAvailable);
        if (!isAvailable) setUnavailableReason('unavailable');
      }
    } catch (error) {
      // Модуль отсутствует (например, приложение запущено в Expo Go)
      if (!cancelled) {
        setAvailable(false);
        setUnavailableReason('unavailable');
      }
    }
    return () => {
      cancelled = true;
    };
  }, []);

  // Подписка на события распознавания
  useEffect(() => {
    const subscriptions = [];

    const subscribe = (event, handler) => {
      try {
        const subscription = ExpoSpeechRecognitionModule.addListener(event, handler);
        if (subscription) subscriptions.push(subscription);
      } catch (error) {
        // Событие не поддерживается на платформе — пропускаем
      }
    };

    subscribe('result', (event) => {
      const transcript = event?.results?.[0]?.transcript?.trim();
      if (!transcript) return;
      if (event.isFinal) {
        if (handleFinalRef.current) handleFinalRef.current(transcript);
      } else if (handleResultRef.current) {
        handleResultRef.current(transcript);
      }
    });
    subscribe('start', () => {
      setRecognizing(true);
      setUnavailableReason(null);
    });
    subscribe('end', () => setRecognizing(false));
    subscribe('error', (event) => {
      setRecognizing(false);
      const code = event?.error || event?.code;
      // Остановка по инициативе пользователя ошибкой не считается
      if (code === 'aborted' || code === 'no-match') return;
      setUnavailableReason(code === 'not-allowed' || code === 'audio-capture' ? 'denied' : 'error');
    });

    return () => {
      subscriptions.forEach((subscription) => {
        try {
          subscription.remove();
        } catch (error) {
          // подписка уже снята
        }
      });
      // Не оставляем висящую сессию распознавания после ухода с экрана
      try {
        ExpoSpeechRecognitionModule.abort();
      } catch (error) {
        // сессии не было
      }
      setRecognizing(false);
    };
  }, []);

  const stop = useCallback(() => {
    try {
      ExpoSpeechRecognitionModule.stop();
    } catch (error) {
      setRecognizing(false);
    }
  }, []);

  const start = useCallback(async () => {
    if (!available) {
      setUnavailableReason('unavailable');
      return false;
    }

    let granted = false;
    try {
      const permission = await ExpoSpeechRecognitionModule.requestPermissionsAsync();
      granted = !!permission?.granted;
    } catch (error) {
      granted = false;
    }

    if (!granted) {
      setUnavailableReason('denied');
      return false;
    }

    try {
      setUnavailableReason(null);
      ExpoSpeechRecognitionModule.start({
        lang: localeFor(language),
        interimResults: true,
        continuous: false,
        androidIntentOptions: { EXTRA_LANGUAGE_MODEL: 'free_form' },
      });
      return true;
    } catch (error) {
      setRecognizing(false);
      setUnavailableReason('error');
      return false;
    }
  }, [available, language]);

  const toggle = useCallback(async () => {
    if (recognizing) {
      stop();
      return false;
    }
    return start();
  }, [recognizing, start, stop]);

  return { available, recognizing, unavailableReason, start, stop, toggle };
}

export default useSpeechToText;
