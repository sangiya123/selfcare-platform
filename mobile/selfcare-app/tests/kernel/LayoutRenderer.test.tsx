import React from 'react';
import { Text } from 'react-native';
import { render } from '@testing-library/react-native';
import {
  LayoutRenderer,
  SectionRenderer,
  isSectionVisible,
  isSectionAvailable,
  satisfiesMinVersion,
  RendererContext,
} from '../../src/renderer/LayoutRenderer';
import { ComponentRegistry, WidgetProps } from '../../src/components/ComponentRegistry';
import { ManifestSection } from '../../src/manifest/types';
import { APP_VERSION } from '../../src/utils/appVersion';

function LabelWidget({ props }: WidgetProps): React.JSX.Element {
  return <Text>{String(props?.label ?? '')}</Text>;
}

function buildRegistry(): ComponentRegistry {
  const r = new ComponentRegistry();
  r.register('Label', LabelWidget, { platforms: ['android', 'ios'] });
  return r;
}

const CTX: RendererContext = { platform: 'android', appVersion: APP_VERSION };

describe('LayoutRenderer gates', () => {
  it('sorts sections by order (ascending)', () => {
    const sections: ManifestSection[] = [
      { id: 'b', component: 'Label', props: { label: 'B' }, order: 2 },
      { id: 'a', component: 'Label', props: { label: 'A' }, order: 1 },
    ];
    const { getAllByText } = render(
      <LayoutRenderer sections={sections} registry={buildRegistry()} onAction={jest.fn()} context={CTX} />
    );
    const labels = getAllByText(/^[AB]$/).map((n) => n.props.children);
    expect(labels).toEqual(['A', 'B']);
  });

  it('hides sections disabled by feature flag (with flags present)', () => {
    const section: ManifestSection = {
      id: 'premium',
      component: 'Label',
      props: { label: 'Premium' },
      visibleWhen: { feature: 'premium.cards' },
    };
    expect(isSectionVisible(section, { ...CTX, featureFlags: { 'premium.cards': false } })).toBe(false);
    expect(isSectionVisible(section, { ...CTX, featureFlags: { 'premium.cards': true } })).toBe(true);
    // Unknown/absent flags default to visible.
    expect(isSectionVisible(section, CTX)).toBe(true);
  });

  it('filters sections by LOB and segment', () => {
    expect(
      isSectionVisible({ id: 'x', component: 'Label', visibleWhen: { lob: 'telco' } }, { ...CTX, lob: 'insurance' })
    ).toBe(false);
    expect(
      isSectionVisible({ id: 'x', component: 'Label', visibleWhen: { lob: 'telco' } }, { ...CTX, lob: 'telco' })
    ).toBe(true);
    expect(
      isSectionVisible({ id: 'x', component: 'Label', visibleWhen: { segment: 'vip' } }, { ...CTX, segment: 'std' })
    ).toBe(false);
  });

  it('filters sections by availability gates (platform + minAppVersion)', () => {
    expect(
      isSectionAvailable(
        { id: 'x', component: 'Label', availability: { platform: ['ios'] } },
        buildRegistry(),
        CTX
      )
    ).toBe(false);
    expect(
      isSectionAvailable(
        { id: 'x', component: 'Label', availability: { minAppVersion: '2.0.0' } },
        buildRegistry(),
        CTX
      )
    ).toBe(false);
    expect(
      isSectionAvailable({ id: 'x', component: 'Label' }, buildRegistry(), CTX)
    ).toBe(true);
  });

  it('respects component-level availability via the registry', () => {
    const r = buildRegistry();
    r.register('IosOnly', LabelWidget, { platforms: ['ios'] });
    expect(isSectionAvailable({ id: 'x', component: 'IosOnly' }, r, CTX)).toBe(false);
    expect(isSectionAvailable({ id: 'x', component: 'IosOnly' }, r, { ...CTX, platform: 'ios' })).toBe(true);
  });

  it('renders an unknown component fallback instead of crashing', () => {
    const { getByText } = render(
      <SectionRenderer
        section={{ id: 'ghost', component: 'Nope' }}
        registry={buildRegistry()}
        context={CTX}
        onAction={jest.fn()}
        onRefresh={jest.fn()}
      />
    );
    expect(getByText(/Unknown component/)).toBeTruthy();
  });

  it('compares semantic versions', () => {
    expect(satisfiesMinVersion('5.0.0', '4.2.0')).toBe(true);
    expect(satisfiesMinVersion('4.1.9', '4.2.0')).toBe(false);
    expect(satisfiesMinVersion('4.2.0', '4.2.0')).toBe(true);
  });
});