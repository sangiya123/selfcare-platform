/**
 * OMOBIO SDK — TypeScript type definitions
 */

export interface Manifest {
  version: string;
  layouts: Layout[];
  theme: ThemeTokens;
  navigation: NavItem[];
  journeys: Journey[];
  featureFlags: Record<string, boolean>;
  /**
   * Per-experience layouts. Each key is an experience name (e.g. "home",
   * "support", "postpaid-dashboard") and the value is the experience-specific
   * manifest entry. The renderer can resolve `experiences[route]?.sections`
   * to render the screen.
   */
  experiences?: Record<
    string,
    {
      name?: string;
      sections?: Section[];
      layout?: Layout;
    }
  >;
  /** Top-level tenant identifier. */
  tenantId?: string;
}

export interface Layout {
  id: string;
  name: string;
  sections: Section[];
}

export interface Section {
  id: string;
  type: 'stack' | 'grid' | 'carousel' | 'list' | 'single';
  widgets: Widget[];
  layout?: {
    columns?: number;
    spacing?: number;
    padding?: Spacing;
  };
}

export interface Widget {
  id: string;
  componentId: string;
  data?: Record<string, any>;
  onTap?: Action;
  onLongPress?: Action;
  visibility?: VisibilityRule[];
}

export interface Action {
  type: ActionType;
  payload: Record<string, any>;
}

export type ActionType =
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

export interface NavItem {
  id: string;
  label: string;
  icon?: string;
  route: string;
  children?: NavItem[];
}

export interface Journey {
  id: string;
  name: string;
  steps: JourneyStep[];
}

export interface JourneyStep {
  id: string;
  type: 'SCREEN' | 'ACTION' | 'DECISION' | 'WAIT' | 'MESSAGE';
  config: Record<string, any>;
}

export interface ThemeTokens {
  name: string;
  colors: Record<string, string>;
  typography: {
    fontFamily: string;
    sizes: Record<string, string>;
    weights: Record<string, number>;
  };
  radius: string;
  spacing: number[];
  logoUrl?: string;
}

export interface VisibilityRule {
  featureFlag?: string;
  segment?: string;
  tenant?: string;
}

export interface Spacing {
  top: number;
  right: number;
  bottom: number;
  left: number;
}

// Domain types

export interface Balance {
  connectionId: string;
  amount: number;
  currency: string;
  balanceType: 'PREPAID' | 'POSTPAID_DUE' | 'POSTPAID_AVAILABLE';
  expiryDate?: string;
}

export interface UsageSummary {
  connectionId: string;
  periodStart: string;
  periodEnd: string;
  data: DataUsage;
  voice: VoiceUsage;
  sms: SmsUsage;
}

export interface DataUsage {
  totalBytes: number;
  remainingBytes: number;
  allowanceBytes: number;
}

export interface VoiceUsage {
  totalSeconds: number;
  remainingSeconds: number;
  allowanceSeconds: number;
}

export interface SmsUsage {
  totalCount: number;
  remainingCount: number;
  allowanceCount: number;
}

export interface Product {
  productId: string;
  name: string;
  description: string;
  category: string;
  price: { amount: number; currency: string };
  allowances: Allowance[];
  validityDays: number;
  tags: string[];
}

export interface Allowance {
  type: 'DATA' | 'VOICE' | 'SMS';
  quantityBytes?: number;
  quantitySeconds?: number;
  quantityCount?: number;
  unit: string;
}

export interface PaymentTransaction {
  transactionId: string;
  status: 'PENDING' | 'SUCCESS' | 'FAILED' | 'UNKNOWN';
  amount: number;
  currency: string;
  receiptUrl?: string;
}

export interface Bill {
  billId: string;
  billNumber: string;
  totalAmount: number;
  outstandingAmount: number;
  currency: string;
  status: string;
  dueDate: string;
}

export interface Article {
  id: string;
  slug: string;
  title: string;
  summary: string;
  body: string;
  category: string;
  tags: string[];
}

export interface FAQ {
  id: string;
  question: string;
  answer: string;
  category: string;
}

export interface Banner {
  id: string;
  title: string;
  imageUrl: string;
  targetUrl?: string;
  ctaText?: string;
}

export interface AIResponse {
  content?: string;
  toolUse?: {
    tool: string;
    input: string;
  };
}

export interface PaginatedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, any>;
}