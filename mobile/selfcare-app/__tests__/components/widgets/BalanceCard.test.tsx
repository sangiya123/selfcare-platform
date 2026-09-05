/**
 * BalanceCard Tests
 *
 * Tests for the BalanceCard widget:
 * - Render with PREPAID and POSTPAID_DUE data
 * - Layout variants (hero, compact, minimal)
 * - Loading, error, and empty states
 * - Currency display
 * - Expiry date display
 * - Recharge button (PREPAID only)
 * - Action dispatch
 */

import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import { BalanceCard } from '../../../src/components/widgets/BalanceCard';
import type { WidgetProps } from '../../../src/components/ComponentRegistry';

describe('BalanceCard', () => {
  const createProps = (overrides: Partial<WidgetProps> = {}): WidgetProps => ({
    id: 'balance-1',
    componentId: 'BalanceCard',
    data: undefined,
    props: {},
    onAction: jest.fn(),
    ...overrides,
  });

  describe('rendering with PREPAID data', () => {
    it('displays the balance amount', () => {
      const props = createProps({
        data: {
          amount: 150.50,
          currency: 'LKR',
          balanceType: 'PREPAID',
        },
      });

      const { getByText } = render(<BalanceCard {...props} />);
      expect(getByText('150.50')).toBeTruthy();
    });

    it('displays the currency code', () => {
      const props = createProps({
        data: {
          amount: 100,
          currency: 'LKR',
          balanceType: 'PREPAID',
        },
      });

      const { getByText } = render(<BalanceCard {...props} />);
      expect(getByText('LKR')).toBeTruthy();
    });

    it('shows "Available Balance" label for PREPAID', () => {
      const props = createProps({
        data: {
          amount: 100,
          currency: 'LKR',
          balanceType: 'PREPAID',
        },
      });

      const { getByText } = render(<BalanceCard {...props} />);
      expect(getByText('Available Balance')).toBeTruthy();
    });

    it('hides currency when showCurrency is false', () => {
      const props = createProps({
        data: {
          amount: 100,
          currency: 'LKR',
          balanceType: 'PREPAID',
        },
        props: { showCurrency: false },
      });

      const { queryByText } = render(<BalanceCard {...props} />);
      expect(queryByText('LKR')).toBeNull();
      // Amount should still be visible
      expect(queryByText('100.00')).toBeTruthy();
    });
  });

  describe('rendering with POSTPAID_DUE data', () => {
    it('shows "Amount Due" label', () => {
      const props = createProps({
        data: {
          amount: 2500.75,
          currency: 'LKR',
          balanceType: 'POSTPAID_DUE',
        },
      });

      const { getByText } = render(<BalanceCard {...props} />);
      expect(getByText('Amount Due')).toBeTruthy();
    });

    it('displays the due amount', () => {
      const props = createProps({
        data: {
          amount: 2500.75,
          currency: 'LKR',
          balanceType: 'POSTPAID_DUE',
        },
      });

      const { getByText } = render(<BalanceCard {...props} />);
      expect(getByText('2500.75')).toBeTruthy();
    });

    it('does not show expiry for POSTPAID_DUE', () => {
      const props = createProps({
        data: {
          amount: 1000,
          currency: 'LKR',
          balanceType: 'POSTPAID_DUE',
          expiryDate: '2026-12-31',
        },
        props: { showExpiry: true },
      });

      const { queryByText } = render(<BalanceCard {...props} />);
      expect(queryByText(/Valid until/)).toBeNull();
    });
  });

  describe('layout variants', () => {
    it('renders minimal layout with simplified design', () => {
      const props = createProps({
        data: {
          amount: 50,
          currency: 'LKR',
          balanceType: 'PREPAID',
        },
        props: { layout: 'minimal' },
      });

      const { getByText } = render(<BalanceCard {...props} />);
      expect(getByText('Balance')).toBeTruthy();
      expect(getByText('50.00')).toBeTruthy();
    });

    it('renders compact layout for POSTPAID', () => {
      const props = createProps({
        data: {
          amount: 100,
          currency: 'LKR',
          balanceType: 'POSTPAID_DUE',
        },
        props: { layout: 'compact' },
      });

      const { getByText } = render(<BalanceCard {...props} />);
      expect(getByText('Amount Due')).toBeTruthy();
    });

    it('defaults to hero layout when not specified', () => {
      const props = createProps({
        data: {
          amount: 100,
          currency: 'LKR',
          balanceType: 'PREPAID',
        },
      });

      const { getByText } = render(<BalanceCard {...props} />);
      expect(getByText('Available Balance')).toBeTruthy();
    });
  });

  describe('expiry date', () => {
    it('shows expiry when showExpiry is true and data has expiryDate (PREPAID)', () => {
      const props = createProps({
        data: {
          amount: 100,
          currency: 'LKR',
          balanceType: 'PREPAID',
          expiryDate: '2026-12-31T00:00:00Z',
        },
        props: { showExpiry: true },
      });

      const { getByText } = render(<BalanceCard {...props} />);
      expect(getByText(/Valid until/)).toBeTruthy();
    });

    it('hides expiry by default', () => {
      const props = createProps({
        data: {
          amount: 100,
          currency: 'LKR',
          balanceType: 'PREPAID',
          expiryDate: '2026-12-31T00:00:00Z',
        },
      });

      const { queryByText } = render(<BalanceCard {...props} />);
      expect(queryByText(/Valid until/)).toBeNull();
    });
  });

  describe('recharge button', () => {
    it('shows recharge button for PREPAID with showQuickRecharge', () => {
      const onAction = jest.fn();
      const props = createProps({
        data: {
          amount: 50,
          currency: 'LKR',
          balanceType: 'PREPAID',
        },
        props: { showQuickRecharge: true },
        onAction,
      });

      const { getByText } = render(<BalanceCard {...props} />);
      expect(getByText('Recharge')).toBeTruthy();
    });

    it('does not show recharge button for POSTPAID_DUE', () => {
      const props = createProps({
        data: {
          amount: 100,
          currency: 'LKR',
          balanceType: 'POSTPAID_DUE',
        },
        props: { showQuickRecharge: true },
      });

      const { queryByText } = render(<BalanceCard {...props} />);
      expect(queryByText('Recharge')).toBeNull();
    });

    it('dispatches NAVIGATE action to /recharge', () => {
      const onAction = jest.fn();
      const props = createProps({
        data: {
          amount: 50,
          currency: 'LKR',
          balanceType: 'PREPAID',
        },
        props: { showQuickRecharge: true },
        onAction,
      });

      const { getByText } = render(<BalanceCard {...props} />);
      fireEvent.press(getByText('Recharge'));

      expect(onAction).toHaveBeenCalledWith({
        event: 'recharge',
        type: 'NAVIGATE',
        route: '/recharge',
      });
    });
  });

  describe('loading state', () => {
    it('renders skeleton when isLoading is true', () => {
      const props = createProps({
        isLoading: true,
        data: undefined,
      });

      const { queryByText } = render(<BalanceCard {...props} />);
      // Skeleton should not show actual data labels
      expect(queryByText('Available Balance')).toBeNull();
      expect(queryByText('Amount Due')).toBeNull();
    });
  });

  describe('error state', () => {
    it('renders error message when error is present', () => {
      const props = createProps({
        error: 'Network error',
        data: undefined,
      });

      const { getByText } = render(<BalanceCard {...props} />);
      expect(getByText("Couldn't load balance")).toBeTruthy();
    });

    it('shows retry button when retryable', () => {
      const onRetry = jest.fn();
      const props = createProps({
        error: 'Network error',
        data: undefined,
        retryable: true,
        onRetry,
      });

      const { getByText } = render(<BalanceCard {...props} />);
      expect(getByText('Try again')).toBeTruthy();
    });

    it('calls onRetry when retry button is pressed', () => {
      const onRetry = jest.fn();
      const props = createProps({
        error: 'Network error',
        data: undefined,
        retryable: true,
        onRetry,
      });

      const { getByText } = render(<BalanceCard {...props} />);
      fireEvent.press(getByText('Try again'));
      expect(onRetry).toHaveBeenCalledTimes(1);
    });

    it('hides retry button when not retryable', () => {
      const props = createProps({
        error: 'Network error',
        data: undefined,
        retryable: false,
      });

      const { queryByText } = render(<BalanceCard {...props} />);
      expect(queryByText('Try again')).toBeNull();
    });
  });

  describe('empty state', () => {
    it('renders nothing when no data, no error, not loading', () => {
      const props = createProps({
        data: undefined,
      });

      const { toJSON } = render(<BalanceCard {...props} />);
      const tree = toJSON();
      // Should render empty view
      expect(tree).toBeTruthy();
    });
  });

  describe('action dispatch', () => {
    it('passes onAction to recharge button', () => {
      const onAction = jest.fn();
      const props = createProps({
        data: {
          amount: 10,
          currency: 'LKR',
          balanceType: 'PREPAID',
        },
        props: { showQuickRecharge: true },
        onAction,
      });

      const { getByText } = render(<BalanceCard {...props} />);
      fireEvent.press(getByText('Recharge'));

      expect(onAction).toHaveBeenCalledTimes(1);
    });
  });
});
