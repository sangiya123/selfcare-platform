/**
 * AppHeader — config-driven app chrome (header bar + hamburger menu).
 *
 * Rendered by the tab navigator on every core screen. What appears is authored
 * in the admin portal via manifest navigation placement:
 *   - placement "header"  -> quick actions in the top bar (e.g. notifications
 *     bell), ordered, per-platform gated, enabled-gated;
 *   - placement "hamburger" -> slide-in menu (with optional `children`
 *     submenus), route/external targets from the CLOSED set (ADR-009).
 * Brand comes from `theme.logoUrl` + i18n `brand.name`. Everything is token
 * and i18n driven — nothing hardcoded (v6 rule #1).
 */
import React, { useState } from 'react';
import {
  View,
  Text,
  Image,
  TouchableOpacity,
  Modal,
  ScrollView,
  StyleSheet,
  Platform,
  Linking,
} from 'react-native';
import { useTheme, fontSizePx, ResolvedTheme } from '../../manifest/ThemeEngine';
import { useLocalize } from '../../manifest/Localization';
import type { NavItem } from '../../manifest/types';
import { appRouter, navigateToRoute } from '../../navigation/navigationRef';

function getManifestNavigation(): NavItem[] {
  const sdk = (globalThis as unknown as { __SELFCARE_SDK__?: { getManifest?: () => unknown } })
    .__SELFCARE_SDK__;
  const manifest = sdk?.getManifest?.() as unknown as { navigation?: NavItem[] } | null;
  return manifest?.navigation ?? [];
}

function platformActive(item: NavItem): boolean {
  const availability = item.availability;
  if (!availability?.length) return true;
  const key = Platform.OS === 'ios' ? 'ios' : 'android';
  return availability.some((a) => a.platform === key && a.active !== false);
}

function visibleItems(placement: NavItem['placement']): NavItem[] {
  return getManifestNavigation().filter(
    (i) => i.placement === placement && i.enabled !== false && platformActive(i)
  );
}

export function AppHeader(): React.JSX.Element {
  const theme = useTheme();
  const { t } = useLocalize();
  const s = headerStyles(theme);
  const headerActions = visibleItems('header');
  const menuItems = visibleItems('hamburger');
  const [menuOpen, setMenuOpen] = useState(false);

  const brandName = t('brand.name', { default: 'selfcare' });
  const hasMenu = menuItems.length > 0;

  const openTarget = (item: NavItem): void => {
    if (item.targetType === 'EXTERNAL') {
      const url = (item.navParams?.url as string | undefined) ?? item.route;
      if (url) {
        Linking.openURL(url).catch(() => {
          // External target failures are left to the operator's URL config.
        });
      }
      return;
    }
    const routed = appRouter.navigate(item.route, item.navParams);
    if (!routed) navigateToRoute(item.route, item.navParams);
  };

  return (
    <>
      <View style={s.bar}>
        {hasMenu ? (
          <TouchableOpacity
            style={s.menuButton}
            onPress={() => setMenuOpen(true)}
            testID="hamburger-toggle"
            accessibilityLabel={t('chrome.menu.open', { default: 'Open menu' })}
          >
            <View style={[s.menuLine, { width: 18 }]} />
            <View style={[s.menuLine, { width: 14 }]} />
            <View style={[s.menuLine, { width: 18 }]} />
          </TouchableOpacity>
        ) : (
          <View style={s.menuButtonPlaceholder} />
        )}

        {theme.logoUrl ? (
          <Image source={{ uri: theme.logoUrl }} style={s.logo} resizeMode="contain" />
        ) : (
          <Text style={s.brand}>{brandName}</Text>
        )}

        {headerActions.length ? (
          <View style={s.actions}>
            {headerActions.map((action) => (
              <TouchableOpacity
                key={action.id}
                style={s.actionButton}
                onPress={() => openTarget(action)}
                testID={`header-action-${action.id}`}
                accessibilityLabel={action.labelKey ? t(action.labelKey, { default: action.label }) : action.label}
              >
                {action.iconUrl ? (
                  <Image source={{ uri: action.iconUrl }} style={s.actionIcon} resizeMode="contain" />
                ) : (
                  <Text style={s.actionGlyph}>
                    {action.labelKey ? t(action.labelKey, { default: action.label ?? action.route }).slice(0, 1) : '•'}
                  </Text>
                )}
              </TouchableOpacity>
            ))}
          </View>
        ) : (
          <View style={s.actionsPlaceholder} />
        )}
      </View>

      <Modal
        visible={menuOpen}
        transparent
        animationType="fade"
        onRequestClose={() => setMenuOpen(false)}
      >
        <View style={s.menuOverlay}>
          <View style={s.menuDrawer}>
            <View style={s.menuHeader}>
              <Text style={s.menuTitle}>{t('chrome.menu.title', { default: 'Menu' })}</Text>
              <TouchableOpacity
                onPress={() => setMenuOpen(false)}
                style={s.menuClose}
                testID="hamburger-close"
                accessibilityLabel={t('chrome.menu.close', { default: 'Close menu' })}
              >
                <Text style={s.menuCloseText}>{t('chrome.menu.closeShort', { default: '×' })}</Text>
              </TouchableOpacity>
            </View>
            <ScrollView>
              {menuItems.map((item) => (
                <MenuItemRow key={item.id} item={item} onSelect={openTarget} />
              ))}
            </ScrollView>
          </View>
        </View>
      </Modal>
    </>
  );
}

function MenuItemRow({ item, onSelect }: { item: NavItem; onSelect: (item: NavItem) => void }): React.JSX.Element {
  const theme = useTheme();
  const { t } = useLocalize();
  const s = headerStyles(theme);
  const children = (item.children ?? []).filter((c) => c.enabled !== false && platformActive(c));
  const label = item.labelKey ? t(item.labelKey, { default: item.label ?? item.route }) : (item.label ?? item.route);

  return (
    <>
      <TouchableOpacity
        style={s.menuRow}
        onPress={() => onSelect(item)}
        testID={`menu-item-${item.id}`}
      >
        {item.iconUrl ? (
          <Image source={{ uri: item.iconUrl }} style={s.menuIcon} resizeMode="contain" />
        ) : null}
        <Text style={s.menuLabel}>{label}</Text>
        {children.length ? <Text style={s.menuCaret}>{t('chrome.menu.caret', { default: '›' })}</Text> : null}
      </TouchableOpacity>
      {children.map((child) => (
        <TouchableOpacity
          key={child.id}
          style={[s.menuRow, s.menuRowChild]}
          onPress={() => onSelect(child)}
          testID={`menu-item-${child.id}`}
        >
          <Text style={s.menuLabel}>
            {child.labelKey ? t(child.labelKey, { default: child.label ?? child.route }) : (child.label ?? child.route)}
          </Text>
        </TouchableOpacity>
      ))}
    </>
  );
}

function headerStyles(theme: ResolvedTheme) {
  const colors = theme.colors;
  const l = theme.layout;
  const primary = colors.primary500 ?? colors.primary;
  const barHeight = Math.max(l.headerHeight, l.minTouchTarget);
  return StyleSheet.create({
    bar: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'space-between',
      height: barHeight,
      paddingHorizontal: l.pagePadding,
      backgroundColor: colors.surface,
      borderBottomWidth: l.hairlinePx,
      borderBottomColor: colors.border,
    },
    menuButton: {
      width: l.minTouchTarget,
      height: l.minTouchTarget,
      alignItems: 'flex-start',
      justifyContent: 'center',
      gap: 4,
    },
    menuButtonPlaceholder: { width: l.minTouchTarget },
    menuLine: {
      height: Math.max(2, Math.round(l.hairlinePx * 2)),
      backgroundColor: colors.textPrimary,
      borderRadius: 1,
    },
    logo: { width: 120, height: barHeight * 0.6 },
    brand: {
      fontSize: fontSizePx(theme, 'lg'),
      fontWeight: '700',
      color: colors.primary700 ?? primary,
      letterSpacing: 1,
    },
    actions: { flexDirection: 'row', alignItems: 'center' },
    actionsPlaceholder: { width: 1 },
    actionButton: {
      width: l.minTouchTarget,
      height: l.minTouchTarget,
      alignItems: 'center',
      justifyContent: 'center',
    },
    actionIcon: {
      width: fontSizePx(theme, 'lg'),
      height: fontSizePx(theme, 'lg'),
      tintColor: colors.textPrimary,
    },
    actionGlyph: {
      fontSize: fontSizePx(theme, 'lg'),
      fontWeight: '600',
      color: colors.textPrimary,
    },
    menuOverlay: {
      flex: 1,
      backgroundColor: colors.scrim ?? 'rgba(0,0,0,0.35)',
      flexDirection: 'row',
    },
    menuDrawer: {
      width: '78%',
      maxWidth: 320,
      backgroundColor: colors.surface,
      borderTopRightRadius: theme.layout.radius,
      borderBottomRightRadius: theme.layout.radius,
      paddingVertical: l.sectionGap,
    },
    menuHeader: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
      paddingHorizontal: l.pagePadding,
      paddingBottom: l.sectionGap,
    },
    menuTitle: { fontSize: fontSizePx(theme, 'base'), fontWeight: '700', color: colors.textPrimary },
    menuClose: {
      width: l.minTouchTarget,
      height: l.minTouchTarget,
      alignItems: 'center',
      justifyContent: 'center',
    },
    menuCloseText: { fontSize: fontSizePx(theme, '2xl'), color: colors.textSecondary },
    menuRow: {
      flexDirection: 'row',
      alignItems: 'center',
      paddingVertical: Math.round(l.sectionGap / 2),
      paddingHorizontal: l.pagePadding,
      gap: l.sectionGap,
    },
    menuRowChild: { paddingLeft: l.pagePadding + l.minTouchTarget * 0.6 },
    menuIcon: {
      width: fontSizePx(theme, 'lg'),
      height: fontSizePx(theme, 'lg'),
      tintColor: colors.textPrimary,
    },
    menuLabel: { fontSize: fontSizePx(theme, 'base'), color: colors.textPrimary, flex: 1 },
    menuCaret: { fontSize: fontSizePx(theme, '2xl'), color: colors.textSecondary },
  });
}

export default AppHeader;