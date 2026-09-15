/**
 * ConsentPrompt — Config-driven consent capture (GDPR Article 7).
 *
 * WHICH consents exist, whether each is required, its policy version and its
 * copy (i18n keys) are authored in the admin portal and delivered in the
 * manifest `consents`. The app queues not-yet-decided consents and records the
 * audit trail via ConsentManager — it never decides purposes or text (v6 #1).
 */

import React, { useCallback, useEffect, useState } from 'react';
import {
  View,
  Text,
  Modal,
  Pressable,
  StyleSheet,
  ScrollView,
} from 'react-native';
import { useLocalize } from '../../manifest/Localization';
import { fontSizePx, fontWeight, readableOn, ResolvedTheme } from '../../manifest/ThemeEngine';
import { ConsentConfig } from '../../manifest/types';
import { ConsentManager, ConsentPurpose } from '../../services/ConsentManager';

const KNOWN_PURPOSES = new Set<string>([
  'TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'MARKETING_EMAIL', 'MARKETING_SMS',
  'MARKETING_PUSH', 'ANALYTICS', 'PERSONALIZATION', 'THIRD_PARTY_SHARING',
  'AI_PROCESSING', 'BIOMETRIC_AUTH', 'LOCATION_TRACKING',
]);

export function ConsentPrompt({
  configs,
  theme,
}: {
  configs: ConsentConfig[] | undefined;
  theme: ResolvedTheme;
}): React.JSX.Element | null {
  const { t } = useLocalize();
  const [queue, setQueue] = useState<ConsentConfig[]>([]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const valid = (configs ?? []).filter(
        (c) => KNOWN_PURPOSES.has(c.purpose) && c.purpose !== 'TERMS_OF_SERVICE'
      );
      if (!valid.length) return;
      const denied = await ConsentManager.getDeniedPurposes();
      const pending: ConsentConfig[] = [];
      for (const c of valid) {
        const granted = await ConsentManager.isGranted(c.purpose as ConsentPurpose);
        if (!granted && !denied.has(c.purpose as ConsentPurpose)) {
          pending.push(c);
        }
      }
      if (!cancelled) setQueue(pending);
    })();
    return () => {
      cancelled = true;
    };
  }, [configs]);

  const resolve = useCallback(async (config: ConsentConfig, granted: boolean) => {
    const purpose = config.purpose as ConsentPurpose;
    if (granted) {
      await ConsentManager.grant(purpose, 'consent-prompt', config.version);
    } else {
      await ConsentManager.revoke(purpose, 'consent-prompt-declined');
    }
    setQueue((q) => q.filter((c) => c.id !== config.id));
  }, []);

  const active = queue[0];
  if (!active) return null;

  const L = theme.layout;
  const C = theme.colors;
  const onPrimary = C.textOnPrimary ?? (readableOn(C.primary) === 'white' ? '#FFFFFF' : '#111111');
  const title = t(active.titleKey, { default: active.titleKey });
  const body = t(active.textKey, { default: active.textKey });
  const acceptLabel = active.acceptKey
    ? t(active.acceptKey, { default: 'Accept' })
    : 'Accept';
  const declineLabel = active.declineKey
    ? t(active.declineKey, { default: 'Decline' })
    : 'Decline';

  return (
    <Modal visible transparent animationType="fade">
      <View style={[styles.backdrop, { backgroundColor: C.overlay }]}>
        <View
          style={{
            width: '90%',
            backgroundColor: C.surface,
            borderRadius: L.radius,
            padding: L.pagePadding,
          }}
        >
          <Text
            style={{
              color: C.textPrimary,
              fontSize: fontSizePx(theme, 'lg'),
              fontWeight: fontWeight(theme, 'semibold'),
            }}
          >
            {title}
          </Text>
          <ScrollView
            style={[
              styles.bodyScroll,
              { marginTop: L.sectionGap / 2, maxHeight: L.minTouchTarget * 6 },
            ]}
          >
            <Text style={{ color: C.textSecondary, fontSize: fontSizePx(theme, 'base') }}>
              {body}
            </Text>
          </ScrollView>
          <View
            style={{
              flexDirection: 'row',
              justifyContent: active.required ? 'flex-end' : 'space-between',
              marginTop: L.pagePadding,
            }}
          >
            {!active.required ? (
              <Pressable
                style={{ paddingVertical: L.sectionGap / 2, paddingRight: L.pagePadding }}
                onPress={() => resolve(active, false)}
              >
                <Text style={{ color: C.textSecondary, fontSize: fontSizePx(theme, 'base') }}>
                  {declineLabel}
                </Text>
              </Pressable>
            ) : null}
            <Pressable
              style={{
                backgroundColor: C.primary,
                borderRadius: L.radius,
                paddingVertical: L.sectionGap,
                paddingHorizontal: L.pagePadding,
              }}
              onPress={() => resolve(active, true)}
            >
              <Text
                style={{
                  color: onPrimary,
                  fontSize: fontSizePx(theme, 'base'),
                  fontWeight: fontWeight(theme, 'semibold'),
                }}
              >
                {acceptLabel}
              </Text>
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  bodyScroll: { flexGrow: 0 },
});

export default ConsentPrompt;