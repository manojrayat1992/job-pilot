import { useState, useCallback, useRef } from 'react';

interface UseLoadingReturn {
  isLoading: boolean;
  loadingMessage: string;
  startLoading: (message?: string) => void;
  stopLoading: () => void;
  withLoading: <T>(fn: () => Promise<T>, message?: string) => Promise<T>;
}

export const useLoading = (defaultMessage: string = 'Loading...'): UseLoadingReturn => {
  const [isLoading, setIsLoading] = useState(false);
  const [loadingMessage, setLoadingMessage] = useState(defaultMessage);
  const loadingTimeoutRef = useRef<ReturnType<typeof setTimeout>>();

  const startLoading = useCallback((message?: string) => {
    setLoadingMessage(message || defaultMessage);
    setIsLoading(true);
  }, [defaultMessage]);

  const stopLoading = useCallback(() => {
    setIsLoading(false);
    if (loadingTimeoutRef.current) {
      clearTimeout(loadingTimeoutRef.current);
    }
  }, []);

  const withLoading = useCallback(async <T>(
    fn: () => Promise<T>,
    message?: string
  ): Promise<T> => {
    startLoading(message);
    
    try {
      return await fn();
    } finally {
      stopLoading();
    }
  }, [startLoading, stopLoading]);

  return {
    isLoading,
    loadingMessage,
    startLoading,
    stopLoading,
    withLoading,
  };
};

export default useLoading;
