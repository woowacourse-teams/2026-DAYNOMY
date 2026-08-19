import axios from 'axios';

type ErrorResponse = {
  code?: unknown;
  message?: unknown;
};

export class ApiError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
  }
}

export function toApiError(error: unknown, fallbackMessage: string) {
  if (!axios.isAxiosError<ErrorResponse>(error)) return null;

  const code =
    typeof error.response?.data?.code === 'string' ? error.response.data.code : 'UNKNOWN_ERROR';
  const message =
    typeof error.response?.data?.message === 'string'
      ? error.response.data.message
      : fallbackMessage;

  return new ApiError(code, message);
}
