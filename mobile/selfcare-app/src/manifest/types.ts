/**
 * selfcare kernel — canonical server-driven UI contract.
 *
 * These types mirror the immutable, signed Experience Manifest produced by
 * config-tenant-service (Step 2 publish pipeline). They are additive — the
 * legacy types in `src/config/types.ts` remain untouched.
 *
 * Contract source:
 *   config-tenant-service Compiler: CompiledManifest { manifestId, signingAlg,
 *   signature, tenantId, environment, experience, profileKey, configVersion,
 *   compiledAt, theme, navigation, sections, features, actions }
 */

export type ManifestActionType =
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

/**
 * An action attached to a section (or emitted by a widget). The closed action
 * set is defined by ADR-009 — config cannot execute arbitrary code.
 */
export interface ManifestAction {
  /** Channel event that triggers the action, e.g. "tap" | "longPress". */
  event: string;
  type: ManifestActionType;
  /** In-app route for NAVIGATE actions, e.g. "/usage/history". */
  route?: string;
  /** Journey id for START_JOURNEY actions. */
  journeyId?: string;
  /** Arbitrary parameters passed with the action. */
  params?: Record<string, unknown>;
  /** Optional confirmation prompt shown before executing the action. */
  confirmation?: string;
  /** Analytics event name to log on execution. */
  analyticsEvent?: string;
  /** Restricts which screen state action handlers run on (mobile shell). */
  platform?: 'ios' | 'android' | 'web';
}

/** Component availability gate (v6 §12 resolution, NO per-user layouts). */
export interface ComponentAvailability {
  platform?: string[];
  lob?: string[];
  customerType?: string[];
  /** Minimum app version, semver string, e.g. "4.2.0". */
  minAppVersion?: string;
}

/** Visibility rule for a section (feanture flag / segment gates). */
export interface VisibilityCondition {
  feature?: string;
  lob?: string;
  segment?: string;
}

/** A single server-driven section rendered by the LayoutRenderer. */
export interface ManifestSection {
  id: string;
  /** Component registry key, e.g. "UsageCard". */
  component: string;
  /** Registered variant, e.g. "hero" | "compact" | "chip". */
  variant?: string;
  /** Data source key resolved by the DataSourceResolver, e.g. "usage.summary". */
  dataSource?: string;
  /** Render order — stable keys, lowest first. */
  order?: number;
  /** Immutable static props passed to the widget. */
  props?: Record<string, unknown>;
  visibleWhen?: VisibilityCondition;
  availability?: ComponentAvailability;
  actions?: ManifestAction[];
}

/** Where a navigation item may be surfaced — decided per operator in admin. */
export type NavItemPlacement =
  | 'tabBar'
  | 'header'
  | 'hamburger'
  | 'dashboard'
  | 'quickActions'
  | 'subPage';

/**
 * Route target flavour (mirrors Dialog's current layout contracts:
 * navigationType). This is the CLOSED behavioural set the app implements
 * (ADR-009 — config cannot execute arbitrary code). The admin portal chooses
 * from this set per nav item; the app never invents target behaviours. An
 * absent/unknown value == legacy empty navigationType == feature hidden.
 */
export type NavItemTargetType =
  | 'IN_APP_SCREEN'
  | 'WEB_SCREEN'
  | 'IN_APP_WEB'
  | 'UTILITY'
  | 'EXTERNAL';

/** Per-platform version gate, like Dialog's availability[] blocks. */
export interface NavItemPlatformAvailability {
  platform: string;
  active: boolean;
  minVersion?: string;
  maxVersion?: string;
}

/**
 * Navigation item authored in the admin portal: which features an operator
 * exposes and WHERE each lives (tab bar, header, hamburger menu, dashboard
 * tiles, quick actions) is per-tenant config — the app renders containers from
 * this structure, never a hardcoded menu (v6 rule #1). Mirrors the real
 * Dialog homeView / hamburger / miniBanner layout contracts.
 */
export interface NavItem {
  id: string;
  /** Static fallback text until i18n provides copy. */
  label?: string;
  /** i18n message key (e.g. "hamburgerMenu.manageConn") — admin copy wins. */
  labelKey?: string;
  /** Icon name resolved through the app icon set (config-selected). */
  icon?: string;
  /** Operator-hosted asset URL (CDN) via theme/catalog config. */
  iconUrl?: string;
  route: string;
  /** Target flavour; controls how `route` is opened. */
  targetType?: NavItemTargetType;
  /** Params for WEB_SCREEN / IN_APP_WEB / EXTERNAL targets. */
  navParams?: Record<string, unknown>;
  /** Where this feature is surfaced. Default "hamburger" when absent. */
  placement?: NavItemPlacement;
  /** Requires an authenticated session to be shown/entered. */
  requiresAuth?: boolean;
  /** Show/hide without a release (legacy empty-navigationType behaviour). */
  enabled?: boolean;
  /** Whether navigation triggers data charges (Dialog dataCharges flag). */
  dataCharges?: boolean;
  /** Per-platform version gates (Dialog availability[]). */
  availability?: NavItemPlatformAvailability[];
  /** Nested entries for hamburger sub-menus / sub-pages. */
  children?: NavItem[];
}

/** Shadow/elevation token authored in the admin portal theme. */
export interface ElevationTokenShape {
  shadowColor?: string;
  shadowOffset?: { width?: number; height?: number };
  shadowOpacity?: number;
  shadowRadius?: number;
  elevation?: number;
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
  /** Border-radius scale keyed by name ("sm"|"md"|"lg"|"xl"|"full"), admin-authored. */
  borderRadius?: Record<string, number>;
  /** Elevation/shadow scale keyed by name ("none"|"sm"|"md"|"lg"), admin-authored. */
  elevation?: Record<string, ElevationTokenShape>;
  logoUrl?: string;
  /**
   * Button style tokens authored by the admin portal (from
   * ThemeDocument.baseTokens.buttons): keyed by variant ("primary",
   * "secondary", "outline", ...) with bg/text/border/gradient fields.
   */
  buttons?: Record<string, Record<string, unknown>>;
  /**
   * Colour-mode policy authored by the admin portal:
   *   "light" | "dark" -> app always uses that mode;
   *   "system"         -> app follows the OS colour scheme.
   * Absent = "system" behaviour is a decision (not a brand decision).
   */
  mode?: 'light' | 'dark' | 'system';
  /** Chrome layout metrics authored by the admin portal. */
  layout?: ManifestLayoutTokens;
  /** Optional dark-mode override layer merged over the light tokens. */
  dark?: {
    colors?: Record<string, string>;
    typography?: ManifestTheme['typography'];
    radius?: string;
    spacing?: number[];
    borderRadius?: Record<string, number>;
    elevation?: Record<string, ElevationTokenShape>;
    layout?: ManifestLayoutTokens;
  };
}

/** Chrome layout metrics — all configurable from the admin portal. */
export interface ManifestLayoutTokens {
  headerHeight?: number;
  footerHeight?: number;
  tabBarHeight?: number;
  sectionGap?: number;
  pagePadding?: number;
  screenMargin?: number;
  cardPadding?: number;
  minTouchTarget?: number;
  radius?: number;
  /** Hairline divider width in px (cards, headers). */
  hairlinePx?: number;
  /** Bubble/chip max width as a percentage of the screen (0-100). */
  bubbleMaxWidthPct?: number;
}

/**
 * Service endpoint definitions authored in the admin portal, stored in Mongo,
 * and compiled into the manifest. Apps resolve SDK endpoint paths from here —
 * endpoint changes are DB-level changes, never app releases (v6 rule #1).
 */
export interface ServiceEndpoints {
  auth?: {
    otp?: string;
    otpVerify?: string;
    refresh?: string;
    signout?: string;
    /** Endpoint for validating an external-flow auth code (may be external). */
    exchange?: string;
  };
  customer?: {
    consent?: string;
    dataErasure?: string;
  };
  billing?: {
    bills?: string;
    bill?: string;
    current?: string;
  };
  usage?: {
    current?: string;
    history?: string;
  };
  payments?: {
    create?: string;
    history?: string;
    savedCards?: string;
  };
  /** Product/offer catalog domain (product-service). */
  catalog?: {
    list?: string;
    getActive?: string;
    recommendations?: string;
  };
  /** CMS domain (content-service). */
  content?: {
    articles?: string;
    banners?: string;
    faqs?: string;
  };
  /** Notification domain (notification-service). */
  notifications?: {
    list?: string;
  };
  /** Profile domain (account-entitlement-service). */
  profile?: {
    summary?: string;
    connections?: string;
  };
  ai?: {
    chat?: string;
    chatStream?: string;
    chatStreamEvents?: string;
    classify?: string;
    recommendations?: string;
    churn?: string;
    sessions?: string;
    sessionHistory?: string;
    sentiment?: string;
    summarize?: string;
    search?: string;
    chatFallback?: string;
    health?: string;
  };
  /** Support-domain (support-service) service-request endpoints. */
  support?: {
    tickets?: string;
    ticket?: string;
    create?: string;
    status?: string;
  };
  /** Token/session policy overrides (ADR-011 is still under review). */
  session?: {
    accessTtlDays?: number;
    refreshOffsetRatio?: number;
  };
}

/**
 * Single status-code presentation entry: label + colors resolved from the
 * manifest (admin-authored). Colors reference either hex values or theme token
 * keys (e.g. "colors:error") which the app resolves at runtime.
 */
export interface StatusPresentationItem {
  label: string;
  /** Literal color or theme color-token key (e.g. "error", "success"). */
  fg: string;
  /** Literal color, theme color-token key, or "surfaceSubtle"-style key. */
  bg: string;
}

/**
 * Component catalog entry — authored in the admin portal (DB), served to the
 * app, and merged over the app-side registry bindings at runtime. The app
 * binds implementation code (ADR-009); every behavioural attribute
 * (security level, platform availability, min app version, analytics events,
 * labels/icons) comes from this catalog.
 */
export interface ComponentCatalogItem {
  componentId: string;
  label?: string;
  description?: string;
  icon?: string;
  category?: string;
  /** Privilege classification: display / read / write. */
  security: 'display' | 'read' | 'write';
  platforms?: string[];
  minAppVersion?: string;
  analyticsEvents?: string[];
  variants?: string[];
  enabled?: boolean;
  /**
   * Universal primitive the component renders (e.g. "UniversalBox"). New
   * features compose existing primitives — no app code per feature.
   */
  primitive?: string;
  /** Immutable primitive config recipe authored in the admin portal (DB). */
  config?: Record<string, unknown>;
}

export type RendererMode = 'NATIVE' | 'SERVER_DRIVEN_NATIVE' | 'WEB_EMBEDDED' | 'SYSTEM_BROWSER_SSO';

/**
 * Auth strategy authored in the admin portal, stored in DB and compiled into
 * the manifest. The app ONLY offers the methods and flows the tenant enables —
 * which factors (OTP / PIN / biometric / face / passkey), their order, and
 * their policies (lengths, TTLs, fallbacks) are decided per operator. App
 * releases never change authentication behaviour (v6 rule #1).
 *
 * All policy values are REQUIREMENTS (no app-side defaults): the compiler
 * validates these are present. The app never invents a fallback, attempt limit
 * or lockout window — config missing means the auth screen declares the tenant
 * auth strategy unconfigured instead of guessing.
 */
export interface ManifestAuthConfig {
  defaultMethod: string;
  /**
   * Verification methods enabled for this tenant, in the order the app should
   * present them (strongest-factor-first recommended for verification).
   */
  methods: AuthMethodConfig[];
  /** Behaviour when a fast factor (PIN/biometric) is unavailable or fails. */
  fallback: 'otp' | 'pin' | 'none';
  /** Local session/lockout policies (shown to the user via i18n). */
  sessionPolicy: {
    /** Failed attempts allowed before lockout. */
    maxAttempts: number;
    /** Lockout window in seconds. */
    lockoutSeconds: number;
  };
  /** Onboarding / login journey (in-app steps or external operator URL). */
  login?: ManifestLoginFlow;
}

export interface AuthMethodConfig {
  method: 'otp' | 'pin' | 'biometric' | 'face' | 'password' | 'magicLink';
  enabled: boolean;
  /** Lower = presented/attempted first. */
  order: number;
  /** i18n message key for the method label, e.g. "auth.method.otp". */
  labelKey?: string;
  options?: {
    /** OTP delivery channels supported by the tenant ("sms" | "email" | "call" | "push"). */
    channels?: string[];
    /** OTP length. */
    length?: number;
    /** OTP validity in seconds. */
    ttlSeconds?: number;
    /** PIN/passcode length. */
    pinLength?: number;
    /** Biometric kinds allowed ("face" | "fingerprint"). */
    biometricKinds?: Array<'face' | 'fingerprint'>;
  };
}

/**
 * Login journey step — the onboarding/registration flow is configurable too.
 * Operators like Dialog run: language selection -> external web URL (mobile
 * number + OTP on the operator's own site) -> deep-link return to the app ->
 * the app validates with the auth service (which may also be an external URL).
 * Exactly which steps run, and whether they are in-app or external, is authored
 * in the admin portal (v6 rule #1).
 */
export interface ManifestLoginFlow {
  /** "external" delegates journey steps to the operator's own web flow. */
  mode: 'inApp' | 'external';
  /** Ordered journey steps for the current mode (duplicates are ignored). */
  steps: Array<'languageSelection' | 'externalAuth' | 'otp' | 'success'>;
  external?: {
    /** Operator registration / OTP web URL opened via the system browser. */
    url: string;
    /** App scheme returned to, e.g. "selfcare". */
    returnScheme: string;
    /** App route path on return, e.g. "/login/callback". */
    returnPath?: string;
    /** Query/params key carrying the validated auth code, e.g. "code". */
    codeParam?: string;
    /** Query/params key carrying the error description, e.g. "error". */
    errorParam?: string;
  };
}

/**
 * Localization content authored in the admin portal, stored in DB
 * (content-service), compiled into the manifest. Apps render every label,
 * error and success message through this — operators manage copy and
 * languages without app releases (v6 rule #1).
 */
export interface ManifestI18n {
  defaultLocale: string;
  locales: string[];
  /** locale -> message-key -> template ("Hello {name}"). */
  messages: Record<string, Record<string, string>>;
}

export interface DataSourceConfig {
  service: string;
  endpoint: string;
  method?: 'GET' | 'POST';
  params?: Record<string, string | number | boolean>;
  transform?: string;
  cacheTtl?: number;
}

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
  sections?: ManifestSection[];
  featureFlags?: Record<string, boolean>;
  /** Configurable auth strategy — which factors the tenant enables (OTP/PIN/biometric/face). */
  auth?: ManifestAuthConfig;
  /** Configurable service endpoints (auth/ai/billing/...) from admin (DB). */
  services?: ServiceEndpoints;
  /** Admin-authored dataSource -> endpoint mappings. */
  dataSources?: Record<string, DataSourceConfig>;
  /** Configurable component catalog (security/platforms/analytics/...). */
  components?: ComponentCatalogItem[];
  /**
   * Floating AI assistant (Dialog-style rounded launcher on any page). When
   * absent, the app renders none — enablement is an admin decision.
   */
  aiAssistant?: AiAssistantConfig;
  /** Onboarding / session popup banners, images, videos and consent prompts. */
  overlayBanners?: OverlayBannerConfig[];
  /** GDPR / required-consent prompts with i18n copy. */
  consents?: ConsentConfig[];

  /**
   * Presentation mapping for domain status codes. Domain codes come from the
   * backend integration contract; how each is LABELLED and COLORED is authored
   * in the admin portal and delivered here — so operators can add/rename
   * statuses and re-skin them without an app release.
   */
  statusPresentation?: {
    billStatus?: Record<string, StatusPresentationItem>;
  };
  /** Renderer mode chosen per journey/page (v6 §17). */
  rendererModes?: Record<string, RendererMode>;
}

/** Data-source loader contract consumed by the LayoutRenderer. */
export interface DataSourceResolver {
  resolve(
    dataSource: string,
    context: { tenantId?: string | null; connectionId?: string | null }
  ): Promise<unknown>;
}

/**
 * Floating AI assistant (Dialog "Dia"-style launcher). Every attribute is
 * authored in the admin portal and delivered in the manifest: whether the
 * assistant exists at all, WHICH engine answers (OBMOBIO AI or an external
 * chat-bot service/URL), where the launcher sits, its shape/size, and how it
 * opens. The app ships one launcher component — behaviour is config (v6 #1).
 */
export interface AiAssistantConfig {
  enabled: boolean;
  /** Engine that answers chats: platform AI gateway or an external bot URL. */
  provider: 'selfcare' | 'external';
  /** External chat-bot endpoint/URL (only when provider === 'external'). */
  externalUrl?: string;
  /** Where the floating launcher is mounted on screen. */
  placement: 'bottomRight' | 'bottomLeft' | 'topRight' | 'topLeft';
  /** Launcher icon from CDN; label copy via labelKey (i18n). */
  launcherIconUrl?: string;
  launcherLabelKey?: string;
  /** Roundedness of the launcher + chat sheet (px, admin-authored). */
  radius?: number;
  /** Launcher diameter in px. */
  sizePx?: number;
  /** How tapping the launcher opens the chat. */
  openMode: 'panel' | 'fullscreen' | 'external';
  /** Routes the launcher is rendered on; empty = every screen. */
  availableOn?: string[];
  /** Suggestion chips shown when the chat opens (i18n keys). */
  initialSuggestionKeys?: string[];
}

/**
 * Onboarding / session popup banners, images, videos and consent prompts. All
 * copy, timing, media (CDN URLs loaded at runtime) and targets are authored in
 * the admin portal and compiled into the manifest — the app only renders.
 */
export interface OverlayBannerConfig {
  id: string;
  trigger: 'firstLaunch' | 'sessionStart' | 'afterDays';
  /** Only when trigger === 'afterDays'. */
  afterDays?: number;
  imageUrl?: string;
  videoUrl?: string;
  targetType?: 'route' | 'external' | 'none';
  /** Route path for targetType 'route'. */
  target?: string;
  dismissible: boolean;
  titleKey?: string;
  bodyKey?: string;
  ctaKey?: string;
  platforms?: string[];
}

/**
 * Consent prompt configuration (GDPR Article 7). Each consent's required/
 * version/title/text come from config + i18n keys — the ConsentManager
 * records the audit trail; the app never decides which consents exist.
 */
export interface ConsentConfig {
  id: string;
  /** ConsentManager purpose identifier. */
  purpose: string;
  required: boolean;
  version: string;
  titleKey: string;
  textKey: string;
  acceptKey?: string;
  declineKey?: string;
}