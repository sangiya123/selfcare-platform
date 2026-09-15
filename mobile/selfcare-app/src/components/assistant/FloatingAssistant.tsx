/**
 * FloatingAssistant — Config-driven AI assistant launcher (Dialog "Dia"-style).
 *
 * Renders a floating, rounded launcher on configured screens and opens the
 * chat as a panel sheet (selfcare engine) or the operator's external chat URL.
 * NEVER decides existence, position, shape or engine itself — everything is
 * read from the manifest `aiAssistant` config (v6 rule #1): the app ships one
 * launcher; enablement/behaviour belongs to the admin portal.
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Pressable,
  Image,
  Modal,
  View,
  Text,
  StyleSheet,
  Linking,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { navigationRef, navigateToRoute } from '../../navigation/navigationRef';
import { useLocalize } from '../../manifest/Localization';
import {
  fontSizePx,
  readableOn,
  ResolvedTheme,
} from '../../manifest/ThemeEngine';
import { AiAssistantConfig } from '../../manifest/types';
import { ChatSheet } from './ChatSheet';

type Anchor = 'top' | 'bottom';

function positionFor(
  placement: AiAssistantConfig['placement'],
  margin: number
): { top?: number; bottom?: number; left?: number; right?: number } {
  const vertical: Anchor = placement.startsWith('top') ? 'top' : 'bottom';
  const horizontal = placement.endsWith('Right') ? 'right' : 'left';
  return vertical === 'top'
    ? { top: margin + 12, [horizontal]: margin }
    : { bottom: margin + 12, [horizontal]: margin };
}

export function FloatingAssistant({
  config,
  theme,
}: {
  config: AiAssistantConfig | null | undefined;
  theme: ResolvedTheme;
}): React.JSX.Element | null {
  const { t } = useLocalize();
  const C = theme.colors;
  const [sheetOpen, setSheetOpen] = useState(false);
  const [activeRoute, setActiveRoute] = useState<string | null>(null);

  useEffect(() => {
    const update = () => {
      const current = navigationRef.isReady()
        ? navigationRef.getCurrentRoute()?.name ?? null
        : null;
      setActiveRoute(current);
    };
    update();
    const sub = navigationRef.addListener('state', update);
    return () => sub();
  }, []);

  const handleOpen = useCallback(() => {
    if (!config) return;
    if (config.provider === 'external') {
      if (config.externalUrl) Linking.openURL(config.externalUrl);
      return;
    }
    if (config.openMode === 'panel') {
      setSheetOpen(true);
    } else {
      navigateToRoute('AIChat', undefined);
    }
  }, [config]);

  if (!config?.enabled) return null;
  if (config.availableOn?.length && !config.availableOn.includes(activeRoute ?? '')) {
    return null;
  }

  const size = config.sizePx ?? theme.layout.minTouchTarget;
  const radius = config.radius ?? theme.layout.radius;
  const margin = theme.layout.screenMargin;
  const background = C.primary;
  const foreground = C.textOnPrimary ?? (readableOn(background) === 'white' ? '#FFFFFF' : '#111111');
  const label = config.launcherLabelKey
    ? t(config.launcherLabelKey, { default: 'Assistant' })
    : undefined;

  return (
    <>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={label ?? t('assistant.launcher', { default: 'AI assistant' })}
        onPress={handleOpen}
        style={[
          styles.launcher,
          positionFor(config.placement, margin),
          {
            width: size,
            height: size,
            borderRadius: radius,
            backgroundColor: background,
            shadowColor: C.shadow,
          },
        ]}
      >
        {config.launcherIconUrl ? (
          <Image
            source={{ uri: config.launcherIconUrl }}
            style={[styles.launcherIcon, { width: size * 0.52, height: size * 0.52 }]}
            resizeMode="contain"
          />
        ) : (
          <View style={styles.bubbleFallback}>
            {[0, 1, 2].map((i) => (
              <View
                key={i}
                style={{
                  width: size * 0.1,
                  height: size * 0.1,
                  borderRadius: size * 0.05,
                  backgroundColor: foreground,
                  marginHorizontal: size * 0.04,
                }}
              />
            ))}
          </View>
        )}
      </Pressable>

      {sheetOpen && (
        <Modal
          visible
          animationType="slide"
          presentationStyle="pageSheet"
          onRequestClose={() => setSheetOpen(false)}
        >
          <SafeAreaView style={styles.sheetRoot}>
            <ChatSheet
              theme={theme}
              suggestionKeys={config.initialSuggestionKeys}
              onClose={() => setSheetOpen(false)}
            />
          </SafeAreaView>
        </Modal>
      )}
    </>
  );
}

const styles = StyleSheet.create({
  launcher: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
    shadowOpacity: 0.18,
    shadowOffset: { width: 0, height: 4 },
    shadowRadius: 8,
    elevation: 6,
  },
  launcherIcon: { marginTop: 0 },
  bubbleFallback: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center' },
  sheetRoot: { flex: 1 },
});

export default FloatingAssistant;