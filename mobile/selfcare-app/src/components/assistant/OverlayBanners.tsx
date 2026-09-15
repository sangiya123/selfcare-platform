/**
 * OverlayBanners — Config-driven onboarding/session popups, images and
 * prompts.
 *
 * Which banners exist, when they fire (first launch / session start / after N
 * days), their media (CDN URLs loaded at runtime), copy (i18n keys), dismissal
 * policy and tap targets are ALL authored in the admin portal and delivered in
 * the manifest `overlayBanners`. The app plays the queue — nothing is decided
 * or hardcoded in code (v6 rule #1).
 */

import React, { useCallback, useEffect, useState } from 'react';
import {
  View,
  Text,
  Image,
  Modal,
  Pressable,
  StyleSheet,
  Linking,
} from 'react-native';
import { MMKV } from 'react-native-mmkv';
import { useLocalize } from '../../manifest/Localization';
import { fontSizePx, fontWeight, readableOn, ResolvedTheme } from '../../manifest/ThemeEngine';
import { OverlayBannerConfig } from '../../manifest/types';
import { navigateToRoute } from '../../navigation/navigationRef';

const store = new MMKV({ id: 'selfcare-overlays' });

function dayKey(id: string, suffix: string): string {
  return `overlay.${id}.${suffix}`;
}

/**
 * Decide whether a banner is due. Dates persist in MMKV so 'afterDays' works
 * across app restarts; 'firstLaunch' is one-shot; 'sessionStart' is per-live
 * session (in-memory).
 */
export function useOverlayDecision(configs: OverlayBannerConfig[] | undefined) {
  const [dueIds, setDueIds] = useState<string[]>([]);
  const sessionFired = React.useRef<Set<string>>(new Set());

  useEffect(() => {
    if (!configs?.length) {
      setDueIds([]);
      return;
    }
    const now = Date.now();
    const due = configs.filter((c) => {
      if (c.trigger === 'firstLaunch') {
        const done = store.getBoolean(dayKey(c.id, 'fired'));
        if (done) return false;
        store.set(dayKey(c.id, 'fired'), true);
        return true;
      }
      if (c.trigger === 'sessionStart') {
        if (sessionFired.current.has(c.id)) return false;
        sessionFired.current.add(c.id);
        return true;
      }
      if (c.trigger === 'afterDays') {
        const days = c.afterDays ?? 1;
        const last = store.getNumber(dayKey(c.id, 'lastShownAt'));
        if (last && now - last < days * 24 * 60 * 60 * 1000) return false;
        store.set(dayKey(c.id, 'lastShownAt'), now);
        return true;
      }
      return false;
    });
    setDueIds(due.map((d) => d.id));
  }, [configs]);

  return dueIds;
}

export function OverlayBanners({
  configs,
  theme,
}: {
  configs: OverlayBannerConfig[] | undefined;
  theme: ResolvedTheme;
}): React.JSX.Element | null {
  const { t } = useLocalize();
  const L = theme.layout;
  const C = theme.colors;
  const dueIds = useOverlayDecision(configs);
  const [tracking, setTracking] = useState<string[]>(dueIds);

  useEffect(() => {
    setTracking((ids) => [...new Set([...ids, ...dueIds])]);
  }, [dueIds]);

  const dismiss = useCallback((id: string) => {
    store.set(dayKey(id, 'fired'), true);
    setTracking((ids) => ids.filter((i) => i !== id));
  }, []);

  if (!configs?.length) return null;
  const active = configs.find((c) => tracking.includes(c.id));
  if (!active) return null;

  const title = active.titleKey ? t(active.titleKey, { default: '' }) : '';
  const body = active.bodyKey ? t(active.bodyKey, { default: '' }) : '';
  const cta = active.ctaKey ? t(active.ctaKey, { default: 'Continue' }) : '';
  const dismissLabel = t('banner.dismiss', { default: 'Close' });
  const onPrimary = C.textOnPrimary ?? (readableOn(C.primary) === 'white' ? '#FFFFFF' : '#111111');

  const handleTarget = () => {
    if (active.targetType === 'external' && active.target) {
      Linking.openURL(active.target);
    } else if (active.targetType === 'route' && active.target) {
      navigateToRoute(active.target, undefined);
    }
    dismiss(active.id);
  };

  return (
    <Modal visible transparent animationType="fade">
      <View style={[styles.backdrop, { backgroundColor: C.overlay }]}>
        <View
          style={{
            width: '90%',
            backgroundColor: C.surface,
            borderRadius: L.radius,
            overflow: 'hidden',
          }}
        >
          {active.imageUrl ? (
            <Image
              source={{ uri: active.imageUrl }}
              style={[styles.image, { height: L.cardPadding * 10 }]}
              resizeMode="cover"
            />
          ) : null}
          <View style={{ padding: L.pagePadding }}>
            {title ? (
              <Text
                style={{
                  color: C.textPrimary,
                  fontSize: fontSizePx(theme, 'lg'),
                  fontWeight: fontWeight(theme, 'semibold'),
                }}
              >
                {title}
              </Text>
            ) : null}
            {body ? (
              <Text
                style={{
                  color: C.textSecondary,
                  fontSize: fontSizePx(theme, 'base'),
                  marginTop: L.sectionGap / 2,
                }}
              >
                {body}
              </Text>
            ) : null}
            <View
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginTop: L.pagePadding,
              }}
            >
              {active.dismissible ? (
                <Pressable
                  onPress={() => dismiss(active.id)}
                  style={{ paddingVertical: L.sectionGap / 2 }}
                >
                  <Text style={{ color: C.textSecondary, fontSize: fontSizePx(theme, 'base') }}>
                    {dismissLabel}
                  </Text>
                </Pressable>
              ) : null}
              {active.targetType !== 'none' && active.target ? (
                <Pressable
                  onPress={handleTarget}
                  style={{ paddingVertical: L.sectionGap / 2 }}
                >
                  <Text
                    style={{
                      color: onPrimary,
                      backgroundColor: C.primary,
                      fontSize: fontSizePx(theme, 'base'),
                      fontWeight: fontWeight(theme, 'semibold'),
                      borderRadius: L.radius,
                      paddingVertical: L.sectionGap / 2,
                      paddingHorizontal: L.pagePadding,
                    }}
                  >
                    {cta}
                  </Text>
                </Pressable>
              ) : null}
            </View>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  image: { width: '100%' },
});

export default OverlayBanners;