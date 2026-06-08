import { useState, useCallback } from 'react';
import { useToast } from '@/hooks/use-toast';

export type ApiErrorType = 'network' | 'auth' | 'validation' | 'server' | 'unknown';

export interface ApiError {
  type: ApiErrorType;
  message: string;
  status?: number;
  originalError?: any;
}

interface UseApiCallReturn {
  isLoading: boolean;
  error: ApiError | null;
  execute: <T>(apiCall: () => Promise<T>, options?: ApiCallOptions) => Promise<T | null>;
  clearError: () => void;
}

interface ApiCallOptions {
  successMessage?: string;
  errorMessage?: string;
  showSuccessToast?: boolean;
  showErrorToast?: boolean;
  onSuccess?: (data: any) => void;
  onError?: (error: ApiError) => void;
}

export const useApiCall = (): UseApiCallReturn => {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const { toast } = useToast();

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  const classifyError = useCallback((err: any): ApiError => {
    // Network errors (no response)
    if (!err.response) {
      return {
        type: 'network',
        message: 'Network error. Please check your connection and try again.',
        originalError: err,
      };
    }

    const status = err.response?.status;
    const data = err.response?.data;

    // Auth errors
    if (status === 401 || status === 403) {
      return {
        type: 'auth',
        message: data?.message || 'You need to log in to perform this action.',
        status,
        originalError: err,
      };
    }

    // Validation errors
    if (status === 400) {
      return {
        type: 'validation',
        message: data?.message || 'Please check your input and try again.',
        status,
        originalError: err,
      };
    }

    // Not found
    if (status === 404) {
      return {
        type: 'server',
        message: 'The requested resource was not found.',
        status,
        originalError: err,
      };
    }

    // Server errors
    if (status >= 500) {
      return {
        type: 'server',
        message: 'Server error. Please try again later.',
        status,
        originalError: err,
      };
    }

    // Unknown errors
    return {
      type: 'unknown',
      message: data?.message || 'An unexpected error occurred.',
      status,
      originalError: err,
    };
  }, []);

  const execute = useCallback(async <T>(
    apiCall: () => Promise<T>,
    options: ApiCallOptions = {}
  ): Promise<T | null> => {
    const {
      successMessage,
      errorMessage,
      showSuccessToast = false,
      showErrorToast = true,
      onSuccess,
      onError,
    } = options;

    setIsLoading(true);
    setError(null);

    try {
      const data = await apiCall();
      
      if (showSuccessToast && successMessage) {
        toast({
          title: 'Success',
          description: successMessage,
        });
      }

      onSuccess?.(data);
      return data;
    } catch (err: any) {
      const apiError = classifyError(err);
      setError(apiError);

      if (showErrorToast) {
        toast({
          title: 'Error',
          description: errorMessage || apiError.message,
          variant: 'destructive',
        });
      }

      onError?.(apiError);
      return null;
    } finally {
      setIsLoading(false);
    }
  }, [classifyError, toast]);

  return {
    isLoading,
    error,
    execute,
    clearError,
  };
};

export default useApiCall;
