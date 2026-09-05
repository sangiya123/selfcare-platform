import type { ComponentType } from 'react';

interface WidgetComponentProps {
  data?: Record<string, any>;
}

export class ComponentRegistry {
  private map = new Map<string, ComponentType<WidgetComponentProps>>();

  register(id: string, component: ComponentType<WidgetComponentProps>): void {
    this.map.set(id, component);
  }

  get(id: string): ComponentType<WidgetComponentProps> | null {
    return this.map.get(id) ?? null;
  }

  registerDefaultWidgets(): void {
    // Built-in widgets registered by the app
    // These are mapped in the mobile app's widget folder
  }
}