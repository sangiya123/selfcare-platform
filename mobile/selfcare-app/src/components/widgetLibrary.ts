/**
 * selfcare kernel — widget library singleton.
 *
 * Registers ONLY universal primitives. Feature-specific components (BalanceCard,
 * UsageCard, etc.) are resolved at runtime from the manifest's component catalog
 * which maps componentId -> { primitive, config } via ComponentRegistry.
 *
 * This means:
 * - Admin portal defines "BalanceCard" = UniversalBox with specific children config
 * - App registry resolves "BalanceCard" to the universal primitive + config
 * - Zero feature-specific code in the app
 */

import { ComponentRegistry } from './ComponentRegistry';
import { UniversalBox } from './universal/UniversalBox';
import { UniversalText } from './universal/UniversalText';
import { UniversalImage } from './universal/UniversalImage';
import { UniversalButton } from './universal/UniversalButton';
import { UniversalInput } from './universal/UniversalInput';
import { UniversalList } from './universal/UniversalList';
import { UniversalGrid } from './universal/UniversalGrid';
import { UniversalChart } from './universal/UniversalChart';

let _registry: ComponentRegistry | null = null;

/**
 * Get the widget registry with universal primitives registered.
 * Component catalog entries from the manifest are resolved via
 * ComponentRegistry.getOrResolve() which lazily creates the component
 * from the catalog definition.
 */
export function getWidgetRegistry(): ComponentRegistry {
  if (!_registry) {
    _registry = new ComponentRegistry();
    registerUniversalPrimitives(_registry);
  }
  return _registry;
}

/**
 * Register universal primitives with the registry.
 * These are the ONLY components that need explicit registration.
 * Everything else comes from the manifest's component_catalog.
 */
function registerUniversalPrimitives(registry: ComponentRegistry): void {
  // Container primitive
  registry.register('UniversalBox', UniversalBox as any, {
    description: 'Universal container - card, surface, overlay, sheet, divider, spacer',
    security: 'display',
    analyticsEvents: [],
    isPrimitive: true,
  });
  registry.registerPrimitive('UniversalBox', UniversalBox);

  // Text primitive
  registry.register('UniversalText', UniversalText as any, {
    description: 'Universal text - label, value, title, subtitle, body, caption, error, link',
    security: 'display',
    analyticsEvents: [],
    isPrimitive: true,
  });
  registry.registerPrimitive('UniversalText', UniversalText);

  // Image primitive
  registry.register('UniversalImage', UniversalImage as any, {
    description: 'Universal image - avatar, thumbnail, banner, icon, full, circle, rounded',
    security: 'display',
    analyticsEvents: [],
    isPrimitive: true,
  });
  registry.registerPrimitive('UniversalImage', UniversalImage);

  // Button primitive
  registry.register('UniversalButton', UniversalButton as any, {
    description: 'Universal button - primary, secondary, outline, ghost, destructive, tonal',
    security: 'write',
    analyticsEvents: ['button_pressed'],
    isPrimitive: true,
  });
  registry.registerPrimitive('UniversalButton', UniversalButton);

  // Input primitive
  registry.register('UniversalInput', UniversalInput as any, {
    description: 'Universal input - text, password, email, phone, number, OTP, search',
    security: 'write',
    analyticsEvents: ['input_changed', 'input_submitted'],
    isPrimitive: true,
  });
  registry.registerPrimitive('UniversalInput', UniversalInput);

  // List primitive
  registry.register('UniversalList', UniversalList as any, {
    description: 'Universal virtualized list with child primitive config',
    security: 'display',
    analyticsEvents: ['list_viewed', 'item_tapped'],
    isPrimitive: true,
  });
  registry.registerPrimitive('UniversalList', UniversalList);

  // Grid primitive
  registry.register('UniversalGrid', UniversalGrid as any, {
    description: 'Universal grid layout with child primitive config',
    security: 'display',
    analyticsEvents: ['grid_viewed', 'item_tapped'],
    isPrimitive: true,
  });
  registry.registerPrimitive('UniversalGrid', UniversalGrid);

  // Chart primitive
  registry.register('UniversalChart', UniversalChart as any, {
    description: 'Universal chart - line, bar, area, pie, donut via config',
    security: 'display',
    analyticsEvents: ['chart_viewed'],
    isPrimitive: true,
  });
  registry.registerPrimitive('UniversalChart', UniversalChart);
}