/**
 * InsurancePolicyCard Tests
 *
 * Tests for the InsurancePolicyCard widget (INSURANCE industry pack).
 *
 * Verifies:
 * - Policy details rendering
 * - Status badge (ACTIVE, LAPSED, MATURED, PENDING)
 * - Product type icon
 * - Sum assured and premium display
 * - Compact vs full layout
 * - Next due date for ACTIVE policies
 * - Action dispatch
 * - Industry-specific terminology
 */

import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import { InsurancePolicyCard } from '../../../src/components/widgets/InsurancePolicyCard';
import type { WidgetProps } from '../../../src/components/ComponentRegistry';

describe('InsurancePolicyCard', () => {
  const createProps = (overrides: Partial<WidgetProps> = {}): WidgetProps => ({
    id: 'policy-1',
    componentId: 'InsurancePolicyCard',
    data: undefined,
    props: {},
    onAction: jest.fn(),
    ...overrides,
  });

  const createPolicy = (overrides: Partial<any> = {}) => ({
    policyId: 'pol-001',
    policyNumber: 'POL-2026-0001',
    productName: 'AIA Life Secure Plus',
    productType: 'LIFE' as const,
    policyHolderName: 'John Doe',
    sumAssured: 5000000,
    premiumAmount: 15000,
    premiumFrequency: 'MONTHLY' as const,
    currency: 'LKR',
    startDate: '2025-01-01T00:00:00Z',
    endDate: '2050-01-01T00:00:00Z',
    status: 'ACTIVE' as const,
    nextDueDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
    ...overrides,
  });

  describe('basic rendering', () => {
    it('displays product name', () => {
      const props = createProps({ data: createPolicy() });
      const { getByText } = render(<InsurancePolicyCard {...props} />);
      expect(getByText('AIA Life Secure Plus')).toBeTruthy();
    });

    it('displays policy number by default', () => {
      const props = createProps({ data: createPolicy() });
      const { getByText } = render(<InsurancePolicyCard {...props} />);
      expect(getByText('#POL-2026-0001')).toBeTruthy();
    });

    it('hides policy number when showPolicyNumber is false', () => {
      const props = createProps({
        data: createPolicy(),
        props: { showPolicyNumber: false },
      });

      const { queryByText } = render(<InsurancePolicyCard {...props} />);
      expect(queryByText('#POL-2026-0001')).toBeNull();
    });
  });

  describe('product type icons', () => {
    const types = [
      { type: 'LIFE' as const, label: 'L' },
      { type: 'HEALTH' as const, label: 'H' },
      { type: 'MOTOR' as const, label: 'M' },
      { type: 'HOME' as const, label: 'Hm' },
      { type: 'CRITICAL_ILLNESS' as const, label: 'CI' },
      { type: 'TRAVEL' as const, label: 'T' },
    ];

    types.forEach(({ type, label }) => {
      it(`shows correct icon for ${type}`, () => {
        const props = createProps({ data: createPolicy({ productType: type }) });
        const { getByText } = render(<InsurancePolicyCard {...props} />);
        expect(getByText(label)).toBeTruthy();
      });
    });
  });

  describe('status badge', () => {
    it('shows ACTIVE status', () => {
      const props = createProps({ data: createPolicy({ status: 'ACTIVE' }) });
      const { getByText } = render(<InsurancePolicyCard {...props} />);
      expect(getByText('ACTIVE')).toBeTruthy();
    });

    it('shows LAPSED status', () => {
      const props = createProps({ data: createPolicy({ status: 'LAPSED' }) });
      const { getByText } = render(<InsurancePolicyCard {...props} />);
      expect(getByText('LAPSED')).toBeTruthy();
    });

    it('shows MATURED status', () => {
      const props = createProps({ data: createPolicy({ status: 'MATURED' }) });
      const { getByText } = render(<InsurancePolicyCard {...props} />);
      expect(getByText('MATURED')).toBeTruthy();
    });

    it('shows PENDING status', () => {
      const props = createProps({ data: createPolicy({ status: 'PENDING' }) });
      const { getByText } = render(<InsurancePolicyCard {...props} />);
      expect(getByText('PENDING')).toBeTruthy();
    });
  });

  describe('financial details', () => {
    it('displays sum assured with currency', () => {
      const props = createProps({ data: createPolicy() });
      const { getByText } = render(<InsurancePolicyCard {...props} />);

      expect(getByText('Sum Assured')).toBeTruthy();
      expect(getByText(/LKR 5,000,000/)).toBeTruthy();
    });

    it('displays premium with frequency', () => {
      const props = createProps({ data: createPolicy() });
      const { getByText } = render(<InsurancePolicyCard {...props} />);

      expect(getByText(/Premium \(MONTHLY\)/)).toBeTruthy();
      expect(getByText(/LKR 15,000/)).toBeTruthy();
    });

    it('formats large numbers with commas', () => {
      const props = createProps({
        data: createPolicy({ sumAssured: 10000000 }),
      });

      const { getByText } = render(<InsurancePolicyCard {...props} />);
      expect(getByText(/LKR 10,000,000/)).toBeTruthy();
    });

    it('hides sum assured when showSumAssured is false', () => {
      const props = createProps({
        data: createPolicy(),
        props: { showSumAssured: false },
      });

      const { queryByText } = render(<InsurancePolicyCard {...props} />);
      expect(queryByText('Sum Assured')).toBeNull();
    });

    it('hides premium when showPremium is false', () => {
      const props = createProps({
        data: createPolicy(),
        props: { showPremium: false },
      });

      const { queryByText } = render(<InsurancePolicyCard {...props} />);
      expect(queryByText(/Premium/)).toBeNull();
    });
  });

  describe('premium frequencies', () => {
    it('shows MONTHLY frequency', () => {
      const props = createProps({
        data: createPolicy({ premiumFrequency: 'MONTHLY' }),
      });

      const { getByText } = render(<InsurancePolicyCard {...props} />);
      expect(getByText(/MONTHLY/)).toBeTruthy();
    });

    it('shows QUARTERLY frequency', () => {
      const props = createProps({
        data: createPolicy({ premiumFrequency: 'QUARTERLY' }),
      });

      const { getByText } = render(<InsurancePolicyCard {...props} />);
      expect(getByText(/QUARTERLY/)).toBeTruthy();
    });

    it('shows ANNUAL frequency', () => {
      const props = createProps({
        data: createPolicy({ premiumFrequency: 'ANNUAL' }),
      });

      const { getByText } = render(<InsurancePolicyCard {...props} />);
      expect(getByText(/ANNUAL/)).toBeTruthy();
    });
  });

  describe('next premium due', () => {
    it('shows next due date for ACTIVE policies', () => {
      const dueDate = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString();
      const props = createProps({
        data: createPolicy({ status: 'ACTIVE', nextDueDate: dueDate }),
      });

      const { getByText } = render(<InsurancePolicyCard {...props} />);
      expect(getByText(/Next premium/)).toBeTruthy();
    });

    it('does not show next due for LAPSED policies', () => {
      const props = createProps({
        data: createPolicy({
          status: 'LAPSED',
          nextDueDate: new Date().toISOString(),
        }),
      });

      const { queryByText } = render(<InsurancePolicyCard {...props} />);
      expect(queryByText(/Next premium/)).toBeNull();
    });

    it('does not show next due when nextDueDate is missing', () => {
      const props = createProps({
        data: createPolicy({ nextDueDate: undefined }),
      });

      const { queryByText } = render(<InsurancePolicyCard {...props} />);
      expect(queryByText(/Next premium/)).toBeNull();
    });
  });

  describe('layout variants', () => {
    it('renders full layout (default) with all details', () => {
      const props = createProps({ data: createPolicy() });
      const { getByText } = render(<InsurancePolicyCard {...props} />);

      expect(getByText('Sum Assured')).toBeTruthy();
      expect(getByText(/Premium/)).toBeTruthy();
    });

    it('renders compact layout without detail row', () => {
      const props = createProps({
        data: createPolicy(),
        props: { layout: 'compact' },
      });

      const { queryByText } = render(<InsurancePolicyCard {...props} />);
      expect(queryByText('Sum Assured')).toBeNull();
    });
  });

  describe('action dispatch', () => {
    it('dispatches NAVIGATE to policy detail on tap', () => {
      const onAction = jest.fn();
      const policy = createPolicy();
      const props = createProps({ data: policy, onAction });

      const { getByText } = render(<InsurancePolicyCard {...props} />);
      fireEvent.press(getByText('AIA Life Secure Plus'));

      expect(onAction).toHaveBeenCalledWith({
        event: 'view_policy',
        type: 'NAVIGATE',
        route: `/insurance/policies/${policy.policyId}`,
      });
    });
  });

  describe('loading state', () => {
    it('renders skeleton when isLoading is true', () => {
      const props = createProps({ isLoading: true, data: undefined });
      const { queryByText } = render(<InsurancePolicyCard {...props} />);

      expect(queryByText('AIA Life Secure Plus')).toBeNull();
    });
  });

  describe('error state', () => {
    it('renders error message when error present and no data', () => {
      const props = createProps({
        error: 'Failed to load policy',
        data: undefined,
      });

      const { getByText } = render(<InsurancePolicyCard {...props} />);
      expect(getByText('Failed to load policy')).toBeTruthy();
    });

    it('shows retry button when onRetry provided', () => {
      const onRetry = jest.fn();
      const props = createProps({
        error: 'Network error',
        data: undefined,
        onRetry,
      });

      const { getByText } = render(<InsurancePolicyCard {...props} />);
      fireEvent.press(getByText('Retry'));
      expect(onRetry).toHaveBeenCalledTimes(1);
    });
  });

  describe('industry isolation', () => {
    it('uses insurance-specific terms (NOT telco)', () => {
      const props = createProps({ data: createPolicy() });
      const { getByText, queryByText } = render(<InsurancePolicyCard {...props} />);

      // Should use insurance terms
      expect(getByText('Sum Assured')).toBeTruthy();
      expect(getByText(/Premium/)).toBeTruthy();

      // Should NOT use telco terms
      expect(queryByText('Balance')).toBeNull();
      expect(queryByText('MSISDN')).toBeNull();
      expect(queryByText('Usage')).toBeNull();
    });
  });
});
