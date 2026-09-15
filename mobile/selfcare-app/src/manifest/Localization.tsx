/**
 * selfcare kernel — Localization Engine.
 *
 * Every label, error and success message in the app is resolved through this
 * engine from the compiled manifest `i18n.messages` (locale -> key ->
 * template), authored in the admin portal and stored in DB. Apps never ship
 * copy decision logic for a tenant — the bundled English literals are only a
 * last-resort fallback so a running app without published strings still works.
 *
 * Language switching is config-driven too: the tenant defines `locales` +
 * `defaultLocale`; the user may pick another supported locale (persisted in
 * MMKV — non-sensitive preference).
 */
import React, { useCallback, useSyncExternalStore } from 'react';
import { MMKV } from 'react-native-mmkv';
import { ManifestI18n } from './types';

const storage = new MMKV({ id: 'selfcare-i18n' });

type Listener = () => void;
interface TOptions {
  params?: Record<string, string | number>;
  /** Last-resort English fallback shipped with the app. */
  default?: string;
}

class Localizer {
  private i18n: ManifestI18n = { defaultLocale: 'en', locales: ['en'], messages: {} };
  private current: string;
  private listeners = new Set<Listener>();

  constructor() {
    this.current = storage.getString('locale') ?? 'en';
  }

  init(config?: ManifestI18n): void {
    if (!config) return;
    this.i18n = {
      defaultLocale: config.defaultLocale || 'en',
      locales: config.locales?.length ? config.locales : ['en'],
      messages: config.messages ?? {},
    };
    if (!this.locales.includes(this.current)) {
      this.current = this.i18n.defaultLocale;
    }
    this.emit();
  }

  get locale(): string {
    return this.current;
  }

  get locales(): string[] {
    return this.i18n.locales;
  }

  get defaultLocale(): string {
    return this.i18n.defaultLocale;
  }

  setLocale(locale: string): void {
    if (!this.locales.includes(locale)) return;
    this.current = locale;
    storage.set('locale', locale);
    this.emit();
  }

  isConfigured(): boolean {
    return Object.keys(this.i18n.messages).length > 0;
  }

  t(key: string, opts?: TOptions): string {
    const table =
      this.i18n.messages[this.current] ??
      this.i18n.messages[this.i18n.defaultLocale] ??
      {};
    let tmpl: string = table[key] ?? opts?.default ?? key;
    if (opts?.params) {
      Object.entries(opts.params).forEach(([k, v]) => {
        tmpl = tmpl.split(`{${k}}`).join(String(v));
      });
    }
    return tmpl;
  }

  subscribe = (fn: Listener): (() => void) => {
    this.listeners.add(fn);
    return () => {
      this.listeners.delete(fn);
    };
  };

  getSnapshot = (): string => this.current;

  private emit(): void {
    this.listeners.forEach((fn) => fn());
  }
}

/** Global localizer — initialized by ConfigSDK when the manifest loads. */
export const localizer = new Localizer();

/** React hook returning the active locale + interpolation translator. */
export function useLocalize() {
  const locale = useSyncExternalStore(localizer.subscribe, localizer.getSnapshot);
  const t = useCallback((key: string, opts?: TOptions) => localizer.t(key, opts), [locale]);
  const setLocale = useCallback((l: string) => localizer.setLocale(l), []);
  return {
    locale,
    locales: localizer.locales,
    defaultLocale: localizer.defaultLocale,
    configured: localizer.isConfigured(),
    setLocale,
    t,
  };
}