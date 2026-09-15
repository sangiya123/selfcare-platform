/**
 * selfcare kernel (web) — canonical server-driven manifest types mirrored from
 * the mobile kernel (`mobile/selfcare-app/src/manifest/types.ts`).
 *
 * The admin web kernel renders the same compiled, signed Experience Manifest
 * contract that the mobile app consumes, so previews in selfcare Studio exactly
 * match device output.
 */

export type WebActionType =
  | 'NAVIGATE'
  | 'CALL_API'
  | 'START_JOURNEY'
  | 'OPEN_URL'
  | 'COPY_TO_CLIPBOARD'
  | 'SHOW_MODAL'
  | 'SHOW_TOAST'
  | 'DISMISS'
  | 'REFRESH'
  | 'OPEN_DRAWER'
  | 'CLOSE_DRAWER'
  | 'SCROLL_TO'
  | 'SET_VARIABLE'
  | 'LOG_EVENT'
  | 'PAYMENT'
  | 'SHARE'
  | 'CALL_NUMBER'
  | 'SEND_SMS';

/** A closed-set action (ADR-009) attached to a section or emitted by a widget. */
export interface WebAction {
  event: string;
  type: WebActionType;
  route?: string;
  journeyId?: string;
  params?: Record<string, unknown>;
  confirmation?: string;
  analyticsEvent?: string;
  platform?: 'ios' | 'android' | 'web';
}

/** Visibility gate for a section (feature flag / segment rules). */
export interface VisibilityCondition {
  feature?: string;
  lob?: string;
  segment?: string;
}

/** Component availability gate (platform / LOB / customer type / app version). */
export interface ComponentAvailability {
  platform?: string[];
  lob?: string[];
  customerType?: string[];
  minAppVersion?: string;
}

/** A single server-driven section rendered by the LayoutRenderer. */
export interface Section {
  id: string;
  /** Component registry key, e.g. "BalanceCard". */
  component: string;
  /** Registered variant, e.g. "hero" | "compact". */
  variant?: string;
  /** Data source key resolved by the DataSourceResolver, e.g. "balance.summary". */
  dataSource?: string;
  /** Render order — stable keys, lowest first. */
  order?: number;
  /** Immutable static props passed to the widget. */
  props?: Record<string, unknown>;
  visibleWhen?: VisibilityCondition;
  availability?: ComponentAvailability;
  actions?: WebAction[];
}

/** Navigation item rendered from config (menu / tabs). */
export interface NavItem {
  id: string;
  label?: string;
  icon?: string;
  route: string;
  children?: NavItem[];
}

/** Design tokens resolved from the compiled manifest theme. */
export interface ManifestTheme {
  name: string;
  colors: Record<string, string>;
  typography?: {
    fontFamily?: string;
    sizes?: Record<string, string>;
    weights?: Record<string, number>;
  };
  radius?: string;
  spacing?: number[];
  logoUrl?: string;
  dark?: {
    colors?: Record<string, string>;
    typography?: ManifestTheme['typography'];
    radius?: string;
    spacing?: number[];
  };
}

/** Compiled Experience Manifest (web kernel view). */
export interface ExperienceManifest {
  manifestId: string;
  signingAlg?: 'HMAC-SHA256';
  signature?: string;
  tenantId: string;
  environment: string;
  experience: string;
  profileKey?: string;
  configVersion: number;
  compiledAt?: string;
  theme?: ManifestTheme;
  navigation?: NavItem[];
  sections?: Section[];
  featureFlags?: Record<string, boolean>;
}

/** Data-source loader contract consumed by the LayoutRenderer. */
export interface DataSourceContext {
  tenantId?: string | null;
  connectionId?: string | null;
}

export interface DataSourceResolver {
  resolve(dataSource: string, context: DataSourceContext): Promise<unknown>;
}

/** Props handed to every registered preview/widget component. */
export interface WebWidgetProps {
  id: string;
  componentId: string;
  variant?: string;
  data?: unknown;
  dataSource?: string;
  props?: Record<string, unknown>;
  onAction?: (action: WebAction) => void;
  isLoading?: boolean;
  isStale?: boolean;
  error?: string;
  retryable?: boolean;
  onRetry?: () => void;
}

/** Render context passed to the LayoutRenderer (tenant + feature state). */
export interface RenderContext {
  tenantId?: string | null;
  connectionId?: string | null;
  features?: Record<string, boolean>;
  lob?: string | null;
  segment?: string | null;
}