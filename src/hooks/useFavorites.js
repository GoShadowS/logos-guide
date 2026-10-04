/**
 * hooks/useFavorites.js — избранные места (хранятся в AsyncStorage).
 */
import { useCallback, useEffect, useState } from 'react';
import { getFavorites, toggleFavorite as toggleFavoriteStorage } from '../services/storage';

export function useFavorites() {
  const [ids, setIds] = useState([]);
  const [ready, setReady] = useState(false);

  const refresh = useCallback(async () => {
    const list = await getFavorites();
    setIds(list);
    setReady(true);
    return list;
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const toggle = useCallback(
    async (id) => {
      const next = await toggleFavoriteStorage(id);
      setIds(next);
      return next;
    },
    []
  );

  const isFavorite = useCallback((id) => ids.includes(id), [ids]);

  return { ids, ready, toggle, isFavorite, refresh };
}

export default useFavorites;
