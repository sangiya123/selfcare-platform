/**
 * BillCard Tests
 *
 * Tests for the BillCard widget:
 * - Bill summary display
 * - Status badge (PAID, DUE, OVERDUE, PENDING)
 * - Days until due calculation
 * - Pay button (with PAYMENT action)
 * - Layout variants (full, compact)
 * - Tap to view bill
 * - Loading and error states
 */

import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import { BillCard } from '../../../src/components/widgets/BillCard';
import type { WidgetProps } from '../../../src/components/ComponentRegistry';

describe('BillCard', () => {
  const createProps = (overrides: Partial<WidgetProps> = {}): WidgetProps => ({
    id: 'bill-1',
    componentId: 'BillCard',
    data: undefined,
    props: {},
    onAction: jest.fn(),
    ...overrides,
  });

  const createBill = (overrides: Partial<any> = {}) => ({
    billId: 'bill-123',
    billNumber: 'INV-2026-09-001',
    totalAmount: 5500.00,
    outstandingAmount: 5500.00,
    currency: 'LKR',
    status: 'DUE',
    dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
    ...overrides,
  });

  describe('basic rendering', () => {
    it('displays bill number and amount', () => {
      const props = createProps({ data: createBill() });
      const { getByText } = render(<BillCard {...props} />);

      expect(getByText('Bill INV-2026-09-001')).toBeTruthy();
      expect(getByText('LKR 5500.00')).toBeTruthy();
    });

    it('hides bill number when showBillNumber is false', () => {
      const props = createProps({
        data: createBill(),
        props: { showBillNumber: false },
      });

      const { queryByText } = render(<BillCard {...props} />);
      expect(queryByText(/INV-2026-09-001/)).toBeNull();
    });
  });

  describe('status badge', () => {
    it('shows DUE status badge by default', () => {
      const props = createProps({ data: createBill() });
      const { getByText } = render(<BillCard {...props} />);
      expect(getByText('DUE')).toBeTruthy();
    });

    it('shows PAID status', () => {
      const props = createProps({ data: createBill({ status: 'PAID' }) });
      const { getByText } = render(<BillCard {...props} />);
      expect(getByText('PAID')).toBeTruthy();
    });

    it('shows OVERDUE status', () => {
      const props = createProps({ data: createBill({ status: 'OVERDUE' }) });
      const { getByText } = render(<BillCard {...props} />);
      expect(getByText('OVERDUE')).toBeTruthy();
    });

    it('shows PENDING status', () => {
      const props = createProps({ data: createBill({ status: 'PENDING' }) });
      const { getByText } = render(<BillCard {...props} />);
      expect(getByText('PENDING')).toBeTruthy();
    });

    it('hides status badge when showStatusBadge is false', () => {
      const props = createProps({
        data: createBill(),
        props: { showStatusBadge: false },
      });

      const { queryByText } = render(<BillCard {...props} />);
      expect(queryByText('DUE')).toBeNull();
    });
  });

  describe('days until due', () => {
    it('shows "Due in X days" for future due date', () => {
      const props = createProps({
        data: createBill({ dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString() }),
      });

      const { getByText } = render(<BillCard {...props} />);
      expect(getByText(/Due in/)).toBeTruthy();
    });

    it('shows "Due in 1 day" singular for single day', () => {
      const props = createProps({
        data: createBill({ dueDate: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString() }),
      });

      const { getByText } = render(<BillCard {...props} />);
      expect(getByText(/Due in 1 day\b/)).toBeTruthy();
    });

    it('shows "Overdue by X days" for past due date', () => {
      const props = createProps({
        data: createBill({
          status: 'OVERDUE',
          dueDate: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString(),
        }),
      });

      const { getByText } = render(<BillCard {...props} />);
      expect(getByText(/Overdue by/)).toBeTruthy();
    });
  });

  describe('pay button', () => {
    it('shows Pay button for DUE status', () => {
      const props = createProps({
        data: createBill(),
        props: { showPayButton: true },
      });

      const { getByText } = render(<BillCard {...props} />);
      expect(getByText('Pay')).toBeTruthy();
    });

    it('shows "Pay Now" for OVERDUE status', () => {
      const props = createProps({
        data: createBill({ status: 'OVERDUE' }),
        props: { showPayButton: true },
      });

      const { getByText } = render(<BillCard {...props} />);
      expect(getByText('Pay Now')).toBeTruthy();
    });

    it('does not show Pay button for PAID status', () => {
      const props = createProps({
        data: createBill({ status: 'PAID' }),
        props: { showPayButton: true },
      });

      const { queryByText } = render(<BillCard {...props} />);
      expect(queryByText('Pay')).toBeNull();
      expect(queryByText('Pay Now')).toBeNull();
    });

    it('hides Pay button when showPayButton is false', () => {
      const props = createProps({
        data: createBill(),
        props: { showPayButton: false },
      });

      const { queryByText } = render(<BillCard {...props} />);
      expect(queryByText('Pay')).toBeNull();
    });

    it('dispatches PAYMENT action with billId and amount', () => {
      const onAction = jest.fn();
      const bill = createBill();
      const props = createProps({
        data: bill,
        props: { showPayButton: true },
        onAction,
      });

      const { getByText } = render(<BillCard {...props} />);
      fireEvent.press(getByText('Pay'));

      expect(onAction).toHaveBeenCalledWith({
        event: 'pay_bill',
        type: 'PAYMENT',
        params: {
          billId: bill.billId,
          amount: bill.outstandingAmount,
        },
      });
    });
  });

  describe('tap to view bill', () => {
    it('dispatches NAVIGATE action when card is tapped', () => {
      const onAction = jest.fn();
      const bill = createBill();
      const props = createProps({ data: bill, onAction });

      const { getByText } = render(<BillCard {...props} />);
      fireEvent.press(getByText('Bill INV-2026-09-001'));

      expect(onAction).toHaveBeenCalledWith({
        event: 'view_bill',
        type: 'NAVIGATE',
        route: `/bills/${bill.billId}`,
      });
    });
  });

  describe('layout variants', () => {
    it('renders compact layout', () => {
      const props = createProps({
        data: createBill(),
        props: { layout: 'compact' },
      });

      const { getByText } = render(<BillCard {...props} />);
      expect(getByText('Bill INV-2026-09-001')).toBeTruthy();
    });

    it('defaults to full layout', () => {
      const props = createProps({ data: createBill() });
      const { getByText } = render(<BillCard {...props} />);
      expect(getByText('Bill INV-2026-09-001')).toBeTruthy();
    });
  });

  describe('loading state', () => {
    it('renders skeleton when isLoading is true', () => {
      const props = createProps({
        isLoading: true,
        data: undefined,
      });

      const { queryByText } = render(<BillCard {...props} />);
      expect(queryByText(/Bill INV-/)).toBeNull();
    });
  });

  describe('error state', () => {
    it('renders error message when error present and no data', () => {
      const props = createProps({
        error: 'Failed to load bill',
        data: undefined,
      });

      const { getByText } = render(<BillCard {...props} />);
      expect(getByText('Failed to load bill')).toBeTruthy();
    });

    it('shows retry button when retryable', () => {
      const onRetry = jest.fn();
      const props = createProps({
        error: 'Network error',
        data: undefined,
        retryable: true,
        onRetry,
      });

      const { getByText } = render(<BillCard {...props} />);
      const retry = getByText('Retry');
      fireEvent.press(retry);
      expect(onRetry).toHaveBeenCalledTimes(1);
    });
  });

  describe('outstanding amount vs total', () => {
    it('displays total amount in header', () => {
      const props = createProps({
        data: createBill({
          totalAmount: 10000,
          outstandingAmount: 3000, // partial payment
        }),
      });

      const { getByText } = render(<BillCard {...props} />);
      expect(getByText(/LKR 10000\.00/)).toBeTruthy();
    });

    it('uses outstandingAmount for PAYMENT action', () => {
      const onAction = jest.fn();
      const props = createProps({
        data: createBill({ totalAmount: 10000, outstandingAmount: 3000 }),
        props: { showPayButton: true },
        onAction,
      });

      const { getByText } = render(<BillCard {...props} />);
      fireEvent.press(getByText('Pay'));

      expect(onAction.mock.calls[0][0].params.amount).toBe(3000);
    });
  });
});
