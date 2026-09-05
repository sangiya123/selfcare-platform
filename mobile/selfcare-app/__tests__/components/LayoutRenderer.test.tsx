/**
 * LayoutRenderer Tests
 *
 * Tests for recursive layout rendering from config manifests.
 *
 * Covers:
 * - Section rendering (stack, grid, carousel, list)
 * - Widget dispatch via ComponentRegistry
 * - Missing component handling (unknown widget fallback)
 * - Action propagation
 * - Recursive nested sections
 * - Layout loading state
 */

import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import { Text, View } from 'react-native';
import { LayoutRenderer } from '../../src/config/LayoutRenderer';
import { ComponentRegistry } from '../../src/config/ComponentRegistry';
import { ActionEngine } from '../../src/config/ActionEngine';
import type { WidgetProps } from '../../src/components/ComponentRegistry';
import type { Layout, Section, Widget } from '../../src/config/types';

describe('LayoutRenderer', () => {
  let registry: ComponentRegistry;
  let actionEngine: ActionEngine;
  let mockSdk: any;

  // Test components
  const TestWidget = (props: WidgetProps) => {
    const data = props.data as any;
    return <Text testID="test-widget">{`TestWidget: ${data?.label ?? 'no-data'}`}</Text>;
  };
  const AnotherWidget = (props: WidgetProps) => (
    <Text testID="another-widget">{`AnotherWidget: ${(props.data as any)?.label ?? ''}`}</Text>
  );
  const CrashWidget = (props: WidgetProps) => {
    throw new Error('Widget crash');
  };

  beforeEach(() => {
    registry = new ComponentRegistry();
    registry.register('TestWidget', TestWidget);
    registry.register('AnotherWidget', AnotherWidget);

    mockSdk = {
      api: { post: jest.fn().mockResolvedValue({}) },
      config: { getManifest: jest.fn() },
      auth: { getToken: jest.fn() },
    };
    actionEngine = new ActionEngine(mockSdk);
  });

  const renderLayout = (layout: Layout | null, onAction?: (action: any) => void) => {
    const renderer = new LayoutRenderer({
      registry,
      actionEngine,
    });

    if (layout === null) {
      // Special case for null layout
      return render(renderer.render({ layout: {} as Layout, onAction }));
    }

    return render(renderer.render({ layout, onAction }));
  };

  describe('basic rendering', () => {
    it('renders stack section with widget', () => {
      const layout: Layout = {
        id: 'home',
        name: 'Home',
        sections: [
          {
            id: 's1',
            type: 'stack',
            widgets: [
              { id: 'w1', componentId: 'TestWidget', data: { label: 'Hello' } },
            ],
          },
        ],
      };

      const { getByTestId } = renderLayout(layout);
      expect(getByTestId('test-widget')).toBeTruthy();
    });

    it('renders multiple sections', () => {
      const layout: Layout = {
        id: 'home',
        name: 'Home',
        sections: [
          {
            id: 's1',
            type: 'stack',
            widgets: [{ id: 'w1', componentId: 'TestWidget', data: { label: 'First' } }],
          },
          {
            id: 's2',
            type: 'stack',
            widgets: [{ id: 'w2', componentId: 'AnotherWidget', data: { label: 'Second' } }],
          },
        ],
      };

      const { getByTestId } = renderLayout(layout);
      expect(getByTestId('test-widget')).toBeTruthy();
      expect(getByTestId('another-widget')).toBeTruthy();
    });
  });

  describe('section types', () => {
    it('renders grid section with columns', () => {
      const layout: Layout = {
        id: 'home',
        name: 'Home',
        sections: [
          {
            id: 's1',
            type: 'grid',
            layout: { columns: 2 },
            widgets: [
              { id: 'w1', componentId: 'TestWidget', data: { label: '1' } },
              { id: 'w2', componentId: 'AnotherWidget', data: { label: '2' } },
            ],
          },
        ],
      };

      const { getByTestId } = renderLayout(layout);
      expect(getByTestId('test-widget')).toBeTruthy();
      expect(getByTestId('another-widget')).toBeTruthy();
    });

    it('renders carousel section', () => {
      const layout: Layout = {
        id: 'home',
        name: 'Home',
        sections: [
          {
            id: 's1',
            type: 'carousel',
            widgets: [
              { id: 'w1', componentId: 'TestWidget', data: { label: '1' } },
              { id: 'w2', componentId: 'AnotherWidget', data: { label: '2' } },
            ],
          },
        ],
      };

      const { getByTestId } = renderLayout(layout);
      expect(getByTestId('test-widget')).toBeTruthy();
      expect(getByTestId('another-widget')).toBeTruthy();
    });

    it('renders list section (default)', () => {
      const layout: Layout = {
        id: 'home',
        name: 'Home',
        sections: [
          {
            id: 's1',
            type: 'list',
            widgets: [
              { id: 'w1', componentId: 'TestWidget', data: { label: 'A' } },
            ],
          },
        ],
      };

      const { getByTestId } = renderLayout(layout);
      expect(getByTestId('test-widget')).toBeTruthy();
    });
  });

  describe('missing component handling', () => {
    it('shows fallback for unknown widget componentId', () => {
      const layout: Layout = {
        id: 'home',
        name: 'Home',
        sections: [
          {
            id: 's1',
            type: 'stack',
            widgets: [
              { id: 'w1', componentId: 'UnknownComponent', data: {} },
            ],
          },
        ],
      };

      const { getByText } = renderLayout(layout);
      expect(getByText(/Unknown widget: UnknownComponent/)).toBeTruthy();
    });

    it('renders known widgets alongside unknown ones', () => {
      const layout: Layout = {
        id: 'home',
        name: 'Home',
        sections: [
          {
            id: 's1',
            type: 'stack',
            widgets: [
              { id: 'w1', componentId: 'TestWidget', data: { label: 'Known' } },
              { id: 'w2', componentId: 'Unknown', data: {} },
            ],
          },
        ],
      };

      const { getByTestId, getByText } = renderLayout(layout);
      expect(getByTestId('test-widget')).toBeTruthy();
      expect(getByText(/Unknown widget: Unknown/)).toBeTruthy();
    });
  });

  describe('action propagation', () => {
    it('propagates widget tap action to onAction handler', () => {
      const onAction = jest.fn();
      const layout: Layout = {
        id: 'home',
        name: 'Home',
        sections: [
          {
            id: 's1',
            type: 'stack',
            widgets: [
              {
                id: 'w1',
                componentId: 'TestWidget',
                data: { label: 'Click me' },
                onTap: {
                  type: 'NAVIGATE',
                  payload: { route: '/details' },
                } as any,
              },
            ],
          },
        ],
      };

      const { getByTestId } = renderLayout(layout, onAction);
      fireEvent(getByTestId('test-widget'), 'touchEnd');
      expect(onAction).toHaveBeenCalled();
    });
  });

  describe('null layout', () => {
    it('renders loading state when layout is null', () => {
      const renderer = new LayoutRenderer({ registry, actionEngine });
      const { toJSON } = render(renderer.render({ layout: null as any }));
      expect(toJSON()).toBeTruthy();
    });
  });

  describe('widget data passing', () => {
    it('passes data prop to widget component', () => {
      const layout: Layout = {
        id: 'home',
        name: 'Home',
        sections: [
          {
            id: 's1',
            type: 'stack',
            widgets: [
              { id: 'w1', componentId: 'TestWidget', data: { label: 'CustomLabel' } },
            ],
          },
        ],
      };

      const { getByText } = renderLayout(layout);
      expect(getByText('TestWidget: CustomLabel')).toBeTruthy();
    });
  });

  describe('section padding and spacing', () => {
    it('respects custom section padding', () => {
      const layout: Layout = {
        id: 'home',
        name: 'Home',
        sections: [
          {
            id: 's1',
            type: 'stack',
            layout: {
              padding: { top: 10, right: 20, bottom: 10, left: 20 },
              spacing: 16,
            },
            widgets: [
              { id: 'w1', componentId: 'TestWidget', data: { label: 'Padded' } },
            ],
          },
        ],
      };

      const { getByTestId } = renderLayout(layout);
      expect(getByTestId('test-widget')).toBeTruthy();
    });
  });

  describe('empty sections', () => {
    it('renders section with no widgets', () => {
      const layout: Layout = {
        id: 'home',
        name: 'Home',
        sections: [
          { id: 's1', type: 'stack', widgets: [] },
        ],
      };

      const { toJSON } = renderLayout(layout);
      expect(toJSON()).toBeTruthy();
    });
  });

  describe('default section type', () => {
    it('falls back to list for unknown section type', () => {
      const layout: Layout = {
        id: 'home',
        name: 'Home',
        sections: [
          {
            id: 's1',
            type: 'unknown' as any,
            widgets: [
              { id: 'w1', componentId: 'TestWidget', data: { label: 'A' } },
            ],
          },
        ],
      };

      const { getByTestId } = renderLayout(layout);
      expect(getByTestId('test-widget')).toBeTruthy();
    });
  });
});
