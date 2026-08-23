import { useEffect, useState } from 'react';
import { getMyProfile } from '../../pages/api';

export function useLoginStatus() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  useEffect(() => {
    const controller = new AbortController();

    getMyProfile(controller.signal)
      .then(() => {
        if (!controller.signal.aborted) {
          setIsLoggedIn(true);
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
