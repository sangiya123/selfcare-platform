/**
 * BannersCarousel — Auto-scrolling promotional banner carousel.
 *
 * Component ID: BannersCarousel
 */

import React, { useEffect, useRef, useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Image, ScrollView, Dimensions } from 'react-native';
import type { WidgetProps } from '../ComponentRegistry';
import { tokens } from '../../styles/design-tokens';
import type { Banner } from '../../config/types';

interface Config {
  autoPlayInterval?: number;
  showDots?: boolean;
  height?: number;
  roundedCorners?: boolean;
}

const SCREEN_WIDTH = Dimensions.get('window').width;
const DEFAULT_HEIGHT = 160;
const DEFAULT_INTERVAL = 5000;

export function BannersCarousel(props: WidgetProps): React.JSX.Element {
  const config = (props.props ?? {}) as Config;
  const banners = (props.data as Banner[] | undefined) ?? [];
  const [activeIndex, setActiveIndex] = useState(0);
  const scrollRef = useRef<ScrollView>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const height = config.height ?? DEFAULT_HEIGHT;
  const interval = config.autoPlayInterval ?? DEFAULT_INTERVAL;

  useEffect(() => {
    if (banners.length <= 1) return;
    intervalRef.current = setInterval(() => {
      const next = (activeIndex + 1) % banners.length;
      setActiveIndex(next);
      scrollRef.current?.scrollTo({ x: next * SCREEN_WIDTH, animated: true });
    }, interval);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [activeIndex, banners.length, interval]);

  if (props.isLoading) return <BannersSkeleton height={height} />;

  if (!banners.length) {
    return <View style={[styles.container, { height }]} />;
  }

  return (
    <View style={[styles.container, { height }, config.roundedCorners && styles.rounded]}>
      <ScrollView
        ref={scrollRef}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onMomentumScrollEnd={(e) => {
          const idx = Math.round(e.nativeEvent.contentOffset.x / SCREEN_WIDTH);
          setActiveIndex(idx);
        }}
      >
        {banners.map((b) => (
          <BannerSlide key={b.id} banner={b} onAction={props.onAction} height={height} />
        ))}
      </ScrollView>
      {config.showDots !== false && banners.length > 1 && (
        <View style={styles.dotsContainer}>
          {banners.map((_, i) => (
            <View key={i} style={[styles.dot, i === activeIndex && styles.dotActive]} />
          ))}
        </View>
      )}
    </View>
  );
}

function BannerSlide({ banner, onAction, height }: { banner: Banner; onAction?: WidgetProps['onAction']; height: number }): React.JSX.Element {
  return (
    <TouchableOpacity
      style={[styles.slide, { width: SCREEN_WIDTH, height }]}
      activeOpacity={0.9}
      onPress={() => banner.targetUrl && onAction?.({ event: 'banner_tap', type: 'OPEN_URL', route: banner.targetUrl })}
    >
      {banner.imageUrl
        ? <Image source={{ uri: banner.imageUrl }} style={styles.image} resizeMode="cover" />
        : <View style={[styles.image, styles.fallback]}><Text style={styles.fallbackText}>{banner.title}</Text></View>
      }
      {banner.ctaText && (
        <View style={styles.overlay}>
          <View style={styles.ctaPill}>
            <Text style={styles.ctaText}>{banner.ctaText}</Text>
          </View>
        </View>
      )}
    </TouchableOpacity>
  );
}

function BannersSkeleton({ height }: { height: number }): React.JSX.Element {
  return <View style={[styles.container, { height, backgroundColor: tokens.colors.surfaceSubtle }]} />;
}

const styles = StyleSheet.create({
  container: { position: 'relative' },
  rounded: { borderRadius: tokens.borderRadius.xl, overflow: 'hidden' },
  slide: { position: 'relative' },
  image: { width: '100%', height: '100%' },
  fallback: { backgroundColor: tokens.colors.primary700, justifyContent: 'center', alignItems: 'center' },
  fallbackText: { color: tokens.colors.surface, fontSize: tokens.fontSize.lg, fontWeight: tokens.fontWeight.semibold },
  overlay: { position: 'absolute', bottom: tokens.spacing.md, right: tokens.spacing.md },
  ctaPill: { backgroundColor: 'rgba(0,0,0,0.6)', paddingVertical: 6, paddingHorizontal: tokens.spacing.md, borderRadius: tokens.borderRadius.full },
  ctaText: { color: tokens.colors.surface, fontSize: tokens.fontSize.sm, fontWeight: tokens.fontWeight.semibold },
  dotsContainer: { position: 'absolute', bottom: tokens.spacing.sm, left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', gap: 6 },
  dot: { width: 6, height: 6, borderRadius: 3, backgroundColor: 'rgba(255,255,255,0.5)' },
  dotActive: { backgroundColor: tokens.colors.surface, width: 20 },
});
