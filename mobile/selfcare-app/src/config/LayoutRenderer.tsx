import React from 'react';
import { ComponentRegistry } from './ComponentRegistry';
import { ActionEngine } from './ActionEngine';
import type { Layout, Section, Widget, Spacing } from './types';
import { View, ActivityIndicator, Text, StyleSheet } from 'react-native';

interface Props {
  layout: Layout;
  onAction?: (action: any) => void;
  loadingComponent?: React.ReactNode;
}

export class LayoutRenderer {
  private registry: ComponentRegistry;
  private actionEngine: ActionEngine;

  constructor(opts: {
    registry: ComponentRegistry;
    actionEngine: ActionEngine;
  }) {
    this.registry = opts.registry;
    this.actionEngine = opts.actionEngine;
  }

  render(props: Props): React.ReactElement {
    return <LayoutView {...props} renderer={this} />;
  }
}

function LayoutView({ layout, onAction, loadingComponent }: Props & { renderer: LayoutRenderer }) {
  if (!layout) {
    return (
      <View style={styles.center}>
        {loadingComponent ?? <ActivityIndicator size="large" color="#7c3aed" />}
      </View>
    );
  }

  return (
    <View style={styles.root}>
      {layout.sections.map((section) => (
        <SectionRenderer
          key={section.id}
          section={section}
          onAction={onAction}
          renderer={renderer}
        />
      ))}
    </View>
  );
}

function SectionRenderer({ section, onAction, renderer }: {
  section: Section;
  onAction?: (action: any) => void;
  renderer: LayoutRenderer;
}) {
  const spacing = section.layout?.spacing ?? 8;
  const padding = section.layout?.padding ?? { top: 0, right: 16, bottom: 0, left: 16 };

  if (section.type === 'stack') {
    return (
      <View style={{ paddingHorizontal: padding.right }}>
        {section.widgets.map((widget) => (
          <WidgetRenderer
            key={widget.id}
            widget={widget}
            onAction={onAction}
            renderer={renderer}
          />
        ))}
      </View>
    );
  }

  if (section.type === 'grid') {
    const cols = section.layout?.columns ?? 2;
    return (
      <View style={[styles.grid, { paddingHorizontal: padding.right }]}>
        {section.widgets.map((widget, i) => (
          <View
            key={widget.id}
            style={{
              flex: 1,
              marginLeft: i % cols === 0 ? 0 : spacing,
              marginBottom: spacing,
            }}
          >
            <WidgetRenderer
              widget={widget}
              onAction={onAction}
              renderer={renderer}
            />
          </View>
        ))}
      </View>
    );
  }

  if (section.type === 'carousel') {
    return (
      <View style={styles.carousel}>
        {section.widgets.map((widget) => (
          <WidgetRenderer
            key={widget.id}
            widget={widget}
            onAction={onAction}
            renderer={renderer}
          />
        ))}
      </View>
    );
  }

  // Default: list
  return (
    <View>
      {section.widgets.map((widget) => (
        <WidgetRenderer
          key={widget.id}
          widget={widget}
          onAction={onAction}
          renderer={renderer}
        />
      ))}
    </View>
  );
}

function WidgetRenderer({ widget, onAction, renderer }: {
  widget: Widget;
  onAction?: (action: any) => void;
  renderer: LayoutRenderer;
}) {
  const Component = renderer.registry.get(widget.componentId);

  if (!Component) {
    return (
      <View style={styles.widgetError}>
        <Text>Unknown widget: {widget.componentId}</Text>
      </View>
    );
  }

  const handlePress = () => {
    if (widget.onTap && onAction) {
      onAction(widget.onTap);
    }
  };

  return (
    <View onTouchEnd={handlePress}>
      <Component data={widget.data} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  grid: { flexDirection: 'row', flexWrap: 'wrap' },
  carousel: { flexDirection: 'row', overflow: 'scroll' },
  widgetError: {
    backgroundColor: '#fee',
    padding: 8,
    borderRadius: 8,
    marginBottom: 8,
  },
});