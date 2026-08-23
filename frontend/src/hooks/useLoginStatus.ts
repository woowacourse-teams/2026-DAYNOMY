import { useEffect, useRef, useState } from 'react';
import { getMyProfile } from '../features/pages/api';

type UseLoginStatusOptions = {
  onLoggedIn?: () => void;
};

export function useLoginStatus(options: UseLoginStatusOptions = {}) {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const { onLoggedIn } = options;
  const onLoggedInRef = useRef(onLoggedIn);

  useEffect(() => {
    onLoggedInRef.current = onLoggedIn;
  }, [onLoggedIn]);

  useEffect(() => {
    const controller = new AbortController();

    getMyProfile(controller.signal)
      .then(() => {
        if (!controller.signal.aborted) {
          setIsLoggedIn(true);
          onLoggedInRef.current?.();
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setIsLoggedIn(false);
        }
      });

    return () => controller.abort();
  }, []);

  return isLoggedIn;
}
