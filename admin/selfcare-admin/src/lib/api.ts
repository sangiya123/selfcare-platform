/**
 * Centralized API client for selfcare Studio.
 *
 * All HTTP calls go through this client. NO hardcoded URLs — base URL
 * comes from VITE_API_GATEWAY_URL (loaded from .env.<mode>).
 */
import axios, { AxiosInstance, AxiosRequestConfig } from 'axios';
import { config } from './config';
import { requestContextHeaders } from '../services/requestContext';

const TOKEN_KEY = 'selfcare-admin-token';
const TENANT_KEY = 'selfcare-active-tenant';

class ApiClient {
  private client: AxiosInstance;

  constructor() {
    this.client = axios.create({
      baseURL: config.apiGatewayUrl,
      timeout: 30000,
      headers: { 'Content-Type': 'application/json' },
    });

    // Request interceptor — attach auth token + tenant header
    this.client.interceptors.request.use((req) => {
      const context = requestContextHeaders();
      const existing: Record<string, string> =
        req.headers && typeof req.headers.toJSON === 'function'
          ? (req.headers.toJSON() as Record<string, string>)
          : ((req.headers as Record<string, string>) ?? {});
      // Context headers first; explicit per-request headers win on collision.
      req.headers = { ...context, ...existing } as typeof req.headers;
      const token = this.getToken();
      if (token) {
        req.headers.Authorization = `Bearer ${token}`;
      }
      const tenant = this.getActiveTenant();
      if (tenant) {
        req.headers['X-Tenant-Id'] = tenant;
      }
      return req;
    });

    // Response interceptor — surface error envelopes
    this.client.interceptors.response.use(
      (resp) => resp,
      (err) => {
        const data = err?.response?.data;
        const errMessage = data?.error?.message ?? data?.message ?? err.message;
        return Promise.reject(new Error(errMessage));
      }
    );
  }

  // --- token / tenant state ---
  setToken(token: string) {
    localStorage.setItem(TOKEN_KEY, token);
  }
  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }
  clearToken() {
    localStorage.removeItem(TOKEN_KEY);
  }
  setActiveTenant(tenantId: string) {
    localStorage.setItem(TENANT_KEY, tenantId);
  }
  getActiveTenant(): string | null {
    return localStorage.getItem(TENANT_KEY);
  }

  // --- HTTP verbs ---
  get<T>(path: string, config?: AxiosRequestConfig): Promise<T> {
    return this.client.get<T>(path, config).then((r) => r.data);
  }
  post<T>(path: string, body?: any, config?: AxiosRequestConfig): Promise<T> {
    return this.client.post<T>(path, body, config).then((r) => r.data);
  }
  put<T>(path: string, body?: any, config?: AxiosRequestConfig): Promise<T> {
    return this.client.put<T>(path, body, config).then((r) => r.data);
  }
  patch<T>(path: string, body?: any, config?: AxiosRequestConfig): Promise<T> {
    return this.client.patch<T>(path, body, config).then((r) => r.data);
  }
  delete<T>(path: string, config?: AxiosRequestConfig): Promise<T> {
    return this.client.delete<T>(path, config).then((r) => r.data);
  }

  // --- API endpoint helpers (tenant-scoped where applicable) ---
  tenants = {
    list: () => this.get<any[]>('/api/v1/admin/tenants'),
    get: (id: string) => this.get<any>(`/api/v1/admin/tenants/${id}`),
    create: (data: any) => this.post<any>('/api/v1/admin/tenants', data),
    update: (id: string, data: any) => this.put<any>(`/api/v1/admin/tenants/${id}`, data),
  };

  pages = {
    list: (tenantId: string) => this.get<any[]>(`/api/v1/admin/pages?tenantId=${tenantId}`),
    get: (id: string) => this.get<any>(`/api/v1/admin/pages/${id}`),
    save: (data: any) => this.post<any>('/api/v1/admin/pages', data),
    publish: (id: string) => this.post<any>(`/api/v1/admin/pages/${id}/publish`),
  };

  themes = {
    list: (tenantId: string) => this.get<any[]>(`/api/v1/admin/themes?tenantId=${tenantId}`),
    save: (data: any) => this.post<any>('/api/v1/admin/themes', data),
  };

  integrations = {
    list: () => {
      const tenant = this.getActiveTenant();
      const q = tenant ? `?tenantId=${encodeURIComponent(tenant)}` : '';
      return this.get<any[]>(`/api/v1/admin/integrations${q}`);
    },
    get: (id: string) => this.get<any>(`/api/v1/admin/integrations/${id}`),
    save: (data: any) => this.post<any>('/api/v1/admin/integrations', data),
    update: (id: string, data: any) => this.put<any>(`/api/v1/admin/integrations/${id}`, data),
    delete: (id: string) => this.delete<any>(`/api/v1/admin/integrations/${id}`),
    test: (id: string) => this.post<any>(`/api/v1/admin/integrations/${id}/test`),
  };

  features = {
    list: (tenantId: string) => this.get<any[]>(`/api/v1/admin/feature-flags?tenantId=${tenantId}`),
    save: (data: any) => this.post<any>('/api/v1/admin/feature-flags', data),
    toggle: (key: string, enabled: boolean) =>
      this.patch<any>(`/api/v1/admin/feature-flags/${key}`, { enabled }),
  };

  // ============================================================
  // Component Catalog (selfcare Studio palette)
  // Backs the PageBuilder palette — labels, icons, categories and
  // enablement are authored here and compiled into the manifest.
  // ============================================================
  components = {
    list: (tenantId: string) => this.get<any[]>(`/api/v1/admin/components?tenantId=${tenantId}`),
    get: (id: string) => this.get<any>(`/api/v1/admin/components/${id}`),
    save: (data: any) => this.post<any>('/api/v1/admin/components', data),
    publish: (id: string) => this.post<any>(`/api/v1/admin/components/${id}/publish`),
    archive: (id: string) => this.post<any>(`/api/v1/admin/components/${id}/archive`),
    delete: (id: string) => this.delete<any>(`/api/v1/admin/components/${id}`),
  };

  ai = {
    config: () => this.get<any>('/api/v1/admin/ai/config'),
    updateConfig: (data: any) => this.put<any>('/api/v1/admin/ai/config', data),
    tools: () => this.get<any[]>('/api/v1/admin/ai/tools'),
    updateTool: (name: string, data: any) => this.put<any>(`/api/v1/admin/ai/tools/${name}`, data),
    prompts: () => this.get<any[]>('/api/v1/admin/ai/prompts'),
    getPrompt: (id: string) => this.get<any>(`/api/v1/admin/ai/prompts/${id}`),
    updatePrompt: (id: string, data: any) => this.put<any>(`/api/v1/admin/ai/prompts/${id}`, data),
    usage: () => this.get<any>('/api/v1/admin/ai/usage'),
    knowledge: (params?: { query?: string; topK?: number }) => {
      const qs = new URLSearchParams();
      if (params?.query) qs.set('query', params.query);
      if (params?.topK) qs.set('topK', String(params.topK));
      return this.get<any[]>(`/api/v1/admin/ai/knowledge${qs.toString() ? '?' + qs : ''}`);
    },
    indexKnowledge: (data: any) => this.post<any>('/api/v1/admin/ai/knowledge', data),
    deleteKnowledge: (id: string) => this.delete<any>(`/api/v1/admin/ai/knowledge/${id}`),
  };

  // ============================================================
  // Users & Roles
  // ============================================================
  users = {
    list: () => this.get<any[]>('/api/v1/admin/users'),
    get: (id: string) => this.get<any>(`/api/v1/admin/users/${id}`),
    create: (data: any) => this.post<any>('/api/v1/admin/users', data),
    update: (id: string, data: any) => this.put<any>(`/api/v1/admin/users/${id}`, data),
    delete: (id: string) => this.delete<any>(`/api/v1/admin/users/${id}`),
  };

  // ============================================================
  // MFA
  // ============================================================
  mfa = {
    status: (userId: string) => this.get<any>(`/api/v1/admin/mfa/status?userId=${userId}`),
    setup: (userId: string) => this.post<any>('/api/v1/admin/mfa/setup', { userId }),
    verifySetup: (userId: string, code: string) =>
      this.post<any>('/api/v1/admin/mfa/verify-setup', { userId, code }),
    verify: (userId: string, code: string) =>
      this.post<any>('/api/v1/admin/mfa/verify', { userId, code }),
    disable: (userId: string) => this.post<any>('/api/v1/admin/mfa/disable', { userId }),
  };

  // ============================================================
  // Asset Manager
  // ============================================================
  assets = {
    list: (params?: { type?: string; q?: string; page?: number }) => {
      const qs = new URLSearchParams();
      if (params?.type) qs.set('type', params.type);
      if (params?.q) qs.set('q', params.q);
      if (params?.page) qs.set('page', String(params.page));
      return this.get<any[]>(`/api/v1/admin/assets${qs.toString() ? '?' + qs : ''}`);
    },
    upload: (formData: FormData) =>
      this.post<any>('/api/v1/admin/assets', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      }),
    delete: (id: string) => this.delete<any>(`/api/v1/admin/assets/${id}`),
  };

  // ============================================================
  // Navigation / Deep Links
  // ============================================================
  navigation = {
    get: (tenantId: string) => this.get<any>(`/api/v1/admin/navigation?tenantId=${tenantId}`),
    save: (data: any) => this.post<any>('/api/v1/admin/navigation', data),
    publish: (id: string) => this.post<any>(`/api/v1/admin/navigation/${id}/publish`),
    delete: (id: string) => this.delete<any>(`/api/v1/admin/navigation/${id}`),
  };

  // ============================================================
  // Content / CMS
  // ============================================================
  content = {
    list: (params?: { type?: string; locale?: string; status?: string }) => {
      const qs = new URLSearchParams();
      if (params?.type) qs.set('type', params.type);
      if (params?.locale) qs.set('locale', params.locale);
      if (params?.status) qs.set('status', params.status);
      return this.get<any[]>(`/api/v1/admin/content${qs.toString() ? '?' + qs : ''}`);
    },
    get: (id: string) => this.get<any>(`/api/v1/admin/content/${id}`),
    create: (data: any) => this.post<any>('/api/v1/admin/content', data),
    update: (id: string, data: any) => this.put<any>(`/api/v1/admin/content/${id}`, data),
    delete: (id: string) => this.delete<any>(`/api/v1/admin/content/${id}`),
  };

  // ============================================================
  // Notifications / Campaigns
  // Backend: NotificationController @ /api/v1/notifications (send + status)
  // Admin template management needs new AdminNotificationController
  // ============================================================
  notifications = {
    send: (data: any) => this.post<any>('/api/v1/notifications', data),
    get: (notificationId: string) => this.get<any>(`/api/v1/notifications/${notificationId}`),
    listByUser: (userId: string, page = 0, size = 20) =>
      this.get<any>(`/api/v1/notifications?userId=${userId}&page=${page}&size=${size}`),
    // Template management (requires AdminNotificationController to be added)
    templates: {
      list: (tenantId: string) => this.get<any[]>(`/api/v1/admin/notifications/templates?tenantId=${tenantId}`),
      create: (data: any) => this.post<any>('/api/v1/admin/notifications/templates', data),
      update: (id: string, data: any) => this.put<any>(`/api/v1/admin/notifications/templates/${id}`, data),
      delete: (id: string) => this.delete<any>(`/api/v1/admin/notifications/templates/${id}`),
    },
  };

  // ============================================================
  // Product Mapping
  // ============================================================
  productMapping = {
    list: (tenantId: string, params?: { sourceProvider?: string; status?: string; q?: string; page?: number }) => {
      const qs = new URLSearchParams({ tenantId });
      if (params?.sourceProvider) qs.set('sourceProvider', params.sourceProvider);
      if (params?.status) qs.set('status', params.status);
      if (params?.q) qs.set('q', params.q);
      if (params?.page != null) qs.set('page', String(params.page));
      return this.get<any[]>('/api/v1/admin/product-mapping?' + qs.toString());
    },
    save: (data: any) => this.post<any>('/api/v1/admin/product-mapping', data),
    bulkSave: (data: any[]) => this.post<any>('/api/v1/admin/product-mapping/bulk', data),
    review: (id: string, approved: boolean, reviewer: string) =>
      this.post<any>(`/api/v1/admin/product-mapping/${id}/review?approved=${approved}&reviewer=${encodeURIComponent(reviewer)}`),
    delete: (id: string) => this.delete<any>(`/api/v1/admin/product-mapping/${id}`),
  };

  // ============================================================
  // Approvals (four-eyes workflow)
  // ============================================================
  approvals = {
    list: (params?: { status?: string; action?: string }) => {
      const qs = new URLSearchParams();
      if (params?.status) qs.set('status', params.status);
      if (params?.action) qs.set('action', params.action);
      return this.get<any[]>(`/api/v1/admin/approvals${qs.toString() ? '?' + qs : ''}`);
    },
    pending: () => this.get<any[]>('/api/v1/admin/approvals/pending'),
    history: () => this.get<any[]>('/api/v1/admin/approvals/history'),
    submit: (data: any) => this.post<any>('/api/v1/admin/approvals', data),
    get: (id: string) => this.get<any>(`/api/v1/admin/approvals/${id}`),
    approve: (id: string, comments?: string) =>
      this.post<any>(`/api/v1/admin/approvals/${id}/approve`, { comments }),
    reject: (id: string, comments?: string) =>
      this.post<any>(`/api/v1/admin/approvals/${id}/reject`, { comments }),
    cancel: (id: string, reason?: string) =>
      this.post<any>(`/api/v1/admin/approvals/${id}/cancel`, { reason }),
  };

  // ============================================================
  // Reports (AdminReportController @ /api/v1/admin/reports)
  // ============================================================
  reports = {
    list: () => this.get<any[]>('/api/v1/admin/reports'),
    get: (id: string) => this.get<any>(`/api/v1/admin/reports/${id}`),
    create: (data: any) => this.post<any>('/api/v1/admin/reports', data),
    update: (id: string, data: any) => this.put<any>(`/api/v1/admin/reports/${id}`, data),
    activate: (id: string) => this.post<any>(`/api/v1/admin/reports/${id}/activate`),
    disable: (id: string) => this.post<any>(`/api/v1/admin/reports/${id}/disable`),
    execute: (reportId: string, params: Record<string, any>) =>
      this.post<any>(`/api/v1/reports/${reportId}/execute`, params),
    executions: (reportId: string, page = 0, size = 20) =>
      this.get<any>(`/api/v1/reports/${reportId}/executions?page=${page}&size=${size}`),
    getExecution: (executionId: string) =>
      this.get<any>(`/api/v1/reports/executions/${executionId}`),
    downloadExecution: (executionId: string) =>
      this.get<Blob>(`/api/v1/reports/executions/${executionId}/download`, { responseType: 'blob' }),
    catalog: (params?: { category?: string; search?: string }) => {
      const qs = new URLSearchParams();
      if (params?.category) qs.set('category', params.category);
      if (params?.search) qs.set('search', params.search);
      return this.get<any[]>(`/api/v1/reports/catalog${qs.toString() ? '?' + qs : ''}`);
    },
    catalogCounts: () => this.get<Record<string, number>>('/api/v1/reports/catalog/counts'),
    catalogIds: () => this.get<string[]>('/api/v1/reports/catalog/ids'),
  };

  // ============================================================
  // Journeys (AdminJourneyController @ /api/v1/admin/journeys)
  // ============================================================
  journeys = {
    list: () => this.get<any[]>('/api/v1/admin/journeys'),
    get: (id: string) => this.get<any>(`/api/v1/admin/journeys/${id}`),
    create: (data: any) => this.post<any>('/api/v1/admin/journeys', data),
    update: (id: string, data: any) => this.put<any>(`/api/v1/admin/journeys/${id}`, data),
    publish: (id: string) => this.post<any>(`/api/v1/admin/journeys/${id}/publish`),
    archive: (id: string) => this.post<any>(`/api/v1/admin/journeys/${id}/archive`),
  };

  // ============================================================
  // Experiences (Config Service @ /api/v1/config/experiences)
  // ============================================================
  experiences = {
    list: (tenantId: string) => this.get<any[]>(`/api/v1/config/experiences?tenantId=${tenantId}`),
    get: (tenantId: string, experienceId: string) =>
      this.get<any>(`/api/v1/config/experiences/${experienceId}?tenantId=${tenantId}`),
    create: (tenantId: string, data: any) =>
      this.post<any>(`/api/v1/config/experiences?tenantId=${tenantId}`, data),
    update: (tenantId: string, experienceId: string, data: any) =>
      this.put<any>(`/api/v1/config/experiences/${experienceId}?tenantId=${tenantId}`, data),
    delete: (tenantId: string, experienceId: string) =>
      this.delete<any>(`/api/v1/config/experiences/${experienceId}?tenantId=${tenantId}`),
  };

  // ============================================================
  // Audit log
  // Backend: AdminAuditController @ /api/v1/admin/audit
  // ============================================================
  audit = {
    list: (params?: { actorId?: string; action?: string; page?: number }) => {
      const qs = new URLSearchParams();
      if (params?.actorId) qs.set('actorId', params.actorId);
      if (params?.action) qs.set('action', params.action);
      if (params?.page) qs.set('page', String(params.page));
      return this.get<any[]>(`/api/v1/admin/audit${qs.toString() ? '?' + qs : ''}`);
    },
  };

  // ============================================================
  // Auth & security settings
  // ============================================================
  authSettings = {
    get: () => this.get<any>('/api/v1/admin/settings/auth'),
    update: (data: any) => this.put<any>('/api/v1/admin/settings/auth', data),
    otpPolicy: () => this.get<any>('/api/v1/admin/settings/auth/otp-policy'),
    updateOtpPolicy: (data: any) => this.put<any>('/api/v1/admin/settings/auth/otp-policy', data),
    oidc: () => this.get<any>('/api/v1/admin/settings/auth/oidc'),
    updateOidc: (data: any) => this.put<any>('/api/v1/admin/settings/auth/oidc', data),
    session: () => this.get<any>('/api/v1/admin/settings/auth/session-policy'),
    updateSession: (data: any) => this.put<any>('/api/v1/admin/settings/auth/session-policy', data),
  };

  // ============================================================
  // Insurance APIs
  // ============================================================
  insurance = {
    policies: {
      list: (customerId: string) => this.get<any[]>(`/api/v1/insurance/policies?customerId=${customerId}`),
      get: (customerId: string, policyId: string) =>
        this.get<any>(`/api/v1/insurance/policies/${policyId}?customerId=${customerId}`),
      initiateRenewal: (policyId: string) =>
        this.post<any>(`/api/v1/insurance/policies/${policyId}/renewal/initiate`),
      confirmRenewal: (policyId: string, renewalOptionId: string) =>
        this.post<any>(`/api/v1/insurance/policies/${policyId}/renewal/confirm`, { renewalOptionId }),
    },
    claims: {
      list: (customerId: string) => this.get<any[]>(`/api/v1/insurance/claims?customerId=${customerId}`),
      get: (customerId: string, claimId: string) =>
        this.get<any>(`/api/v1/insurance/claims/${claimId}?customerId=${customerId}`),
      submit: (customerId: string, policyId: string, submission: any) =>
        this.post<any>(`/api/v1/insurance/policies/${policyId}/claims?customerId=${customerId}`, submission),
      activities: (claimId: string) =>
        this.get<any[]>(`/api/v1/insurance/claims/${claimId}/activities`),
      uploadDocument: (claimId: string, file: File) => {
        const formData = new FormData();
        formData.append('file', file);
        return this.post<any>(`/api/v1/insurance/claims/${claimId}/documents`, formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
      },
    },
    beneficiaries: {
      list: (policyId: string) => this.get<any[]>(`/api/v1/insurance/policies/${policyId}/beneficiaries`),
      add: (policyId: string, beneficiary: any) =>
        this.post<any>(`/api/v1/insurance/policies/${policyId}/beneficiaries`, beneficiary),
      update: (beneficiaryId: string, updates: any) =>
        this.put<any>(`/api/v1/insurance/beneficiaries/${beneficiaryId}`, updates),
      remove: (beneficiaryId: string) =>
        this.delete<any>(`/api/v1/insurance/beneficiaries/${beneficiaryId}`),
    },
    premiums: {
      schedule: (policyId: string) => this.get<any[]>(`/api/v1/insurance/policies/${policyId}/premiums`),
      next: (policyId: string) => this.get<any>(`/api/v1/insurance/policies/${policyId}/premiums/next`),
      pay: (premiumId: string, payment: any) =>
        this.post<any>(`/api/v1/insurance/premiums/${premiumId}/pay`, payment),
      setupAutoDebit: (policyId: string, setup: any) =>
        this.post<any>(`/api/v1/insurance/policies/${policyId}/auto-debit`, setup),
      cancelAutoDebit: (policyId: string) =>
        this.delete<any>(`/api/v1/insurance/policies/${policyId}/auto-debit`),
    },
  };
}

export const api = new ApiClient();