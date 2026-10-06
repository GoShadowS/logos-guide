/**
 * Foreground-only GPS tracking for the indoor floor-plan navigator.
 * Permission is requested only after the user taps the location button.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import * as Location from 'expo-location';

export function useGpsLocation() {
  const [status, setStatus] = useState('idle');
  const [location, setLocation] = useState(null);
  const [error, setError] = useState(null);
  const subscriptionRef = useRef(null);
  const locationRef = useRef(null);
  const requestingRef = useRef(false);

  const updateLocation = useCallback((nextLocation) => {
    if (!nextLocation?.coords) return;
    locationRef.current = nextLocation;
    setLocation(nextLocation);
    setError(null);
    setStatus('tracking');
  }, []);

  const start = useCallback(async () => {
    if (subscriptionRef.current) return locationRef.current;
    if (requestingRef.current) return null;

    requestingRef.current = true;
    setStatus('requesting');
    setError(null);
    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (permission.status !== 'granted') {
        setStatus('denied');
        return null;
      }

      setStatus('locating');
      const currentLocation = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.High,
      });
      updateLocation(currentLocation);

      const subscription = await Location.watchPositionAsync(
        {
          accuracy: Location.Accuracy.High,
          timeInterval: 2500,
          distanceInterval: 1,
        },
        updateLocation
      );
      subscriptionRef.current = subscription;
      return currentLocation;
    } catch (locationError) {
      setError(locationError);
      setStatus('error');
      return null;
    } finally {
      requestingRef.current = false;
    }
  }, [updateLocation]);

  const stop = useCallback(() => {
    subscriptionRef.current?.remove?.();
    subscriptionRef.current = null;
    setStatus('idle');
  }, []);

  useEffect(
    () => () => {
      subscriptionRef.current?.remove?.();
      subscriptionRef.current = null;
    },
    []
  );

  return { status, location, error, start, stop };
}

export default useGpsLocation;
