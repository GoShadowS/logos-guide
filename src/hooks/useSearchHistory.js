/**
 * hooks/useSearchHistory.js — история поиска (хранится в AsyncStorage).
 */
import { useCallback, useEffect, useState } from 'react';
import {
  getSearchHistory,
  addSearchHistory,
  clearSearchHistory,
  removeSearchHistory,
} from '../services/storage';

export function useSearchHistory() {
  const [history, setHistory] = useState([]);

  const refresh = useCallback(async () => {
    const list = await getSearchHistory();
    setHistory(list);
    return list;
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const add = useCallback(
    async (query) => {
      const next = await addSearchHistory(query);
      setHistory(next);
      return next;
    },
    []
  );

  const remove = useCallback(
    async (query) => {
      const next = await removeSearchHistory(query);
      setHistory(next);
      return next;
    },
    []
  );

  const clear = useCallback(async () => {
    const next = await clearSearchHistory();
    setHistory(next);
    return next;
  }, []);

  return { history, add, remove, clear, refresh };
}

export default useSearchHistory;
