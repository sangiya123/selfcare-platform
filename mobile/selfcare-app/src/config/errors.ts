import { localizer } from '../manifest/Localization';

export class selfcareError extends Error {
  constructor(
    public code: string,
    message: string,
    public details?: Record<string, any>
  ) {
    super(message);
    this.name = 'selfcareError';
  }

  /** i18n key used to resolve per-language copy from the manifest. */
  get messageKey(): string {
    return `errors.${this.code}`;
  }
}

/**
 * Resolve an error's HUMAN copy for the active locale.
 *
 * The error CODE is the closed platform contract (ADR-009); the message for
 * each code is authored per language in the admin portal (i18n messages, key
 * `errors.<CODE>`), stored in DB and delivered in the manifest. The bundled
 * `default` copy is only the last-resort fallback until strings are published.
 * `details` are interpolation params (e.g. { retryAfter }).
 */
export function errorMessage(
  error: unknown,
  opts?: { default?: string }
): string {
  if (error instanceof selfcareError) {
    const params = error.details as Record<string, string | number> | undefined;
    return localizer.t(error.messageKey, {
      params,
      default: opts?.default ?? error.message,
    });
  }
  if (error instanceof Error) return error.message;
  return String(error ?? '');
}

export const ErrorCodes = {
  UNAUTHORIZED: 'UNAUTHORIZED',
  FORBIDDEN: 'FORBIDDEN',
  NOT_FOUND: 'NOT_FOUND',
  CONFLICT: 'CONFLICT',
  RATE_LIMITED: 'RATE_LIMITED',
  STEP_UP_REQUIRED: 'STEP_UP_REQUIRED',
  PAYMENT_FAILED: 'PAYMENT_FAILED',
  SERVICE_UNAVAILABLE: 'SERVICE_UNAVAILABLE',
  NETWORK_ERROR: 'NETWORK_ERROR',
} as const;