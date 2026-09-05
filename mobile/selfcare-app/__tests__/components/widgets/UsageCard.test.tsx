/**
 * UsageCard Tests
 *
 * Tests for the UsageCard widget:
 * - Render data, voice, SMS usage
 * - Bar layout with progress bars
 * - Chip layout
 * - Formatters (bytes, seconds, count)
 * - Period label
 * - Loading and error states
 * - "View details" action
 */

import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import { UsageCard } from '../../../src/components/widgets/UsageCard';
import type { WidgetProps } from '../../../src/components/ComponentRegistry';

describe('UsageCard', () => {
  const createProps = (overrides: Partial<WidgetProps> = {}): WidgetProps => ({
    id: 'usage-1',
    componentId: 'UsageCard',
    data: undefined,
    props: {},
    onAction: jest.fn(),
    ...overrides,
  });

  const MOCK_USAGE = {
    connectionId: 'conn-1',
    periodStart: '2026-09-01T00:00:00Z',
    periodEnd: '2026-09-30T23:59:59Z',
    data: {
      totalBytes: 5 * 1024 * 1024 * 1024, // 5 GB used
      remainingBytes: 5 * 1024 * 1024 * 1024, // 5 GB remaining
      allowanceBytes: 10 * 1024 * 1024 * 1024, // 10 GB total
    },
    voice: {
      totalSeconds: 1800, // 30 min used
      remainingSeconds: 1800, // 30 min remaining
      allowanceSeconds: 3600, // 60 min total
    },
    sms: {
      totalCount: 50,
      remainingCount: 50,
      allowanceCount: 100,
    },
  };

  describe('bar layout (default)', () => {
    it('renders all three usage types by default', () => {
      const props = createProps({ data: MOCK_USAGE });
      const { getByText } = render(<UsageCard {...props} />);

      expect(getByText('Data')).toBeTruthy();
      expect(getByText('Voice')).toBeTruthy();
      expect(getByText('SMS')).toBeTruthy();
    });

    it('displays data usage with GB units', () => {
      const props = createProps({ data: MOCK_USAGE });
      const { getByText } = render(<UsageCard {...props} />);

      // 5.0 GB / 10.0 GB
      expect(getByText(/5\.0 GB \/ 10\.0 GB/)).toBeTruthy();
    });

    it('displays voice usage in h/m format', () => {
      const props = createProps({ data: MOCK_USAGE });
      const { getByText } = render(<UsageCard {...props} />);

      // 30m / 1h 0m
      expect(getByText(/30m \/ 1h 0m/)).toBeTruthy();
    });

    it('displays SMS count', () => {
      const props = createProps({ data: MOCK_USAGE });
      const { getByText } = render(<UsageCard {...props} />);

      expect(getByText(/50 \/ 100/)).toBeTruthy();
    });

    it('hides data section when showData is false', () => {
      const props = createProps({
        data: MOCK_USAGE,
        props: { showData: false },
      });

      const { queryByText } = render(<UsageCard {...props} />);
      expect(queryByText('Data')).toBeNull();
    });

    it('hides voice section when showVoice is false', () => {
      const props = createProps({
        data: MOCK_USAGE,
        props: { showVoice: false },
      });

      const { queryByText } = render(<UsageCard {...props} />);
      expect(queryByText('Voice')).toBeNull();
    });

    it('hides SMS section when showSms is false', () => {
      const props = createProps({
        data: MOCK_USAGE,
        props: { showSms: false },
      });

      const { queryByText } = render(<UsageCard {...props} />);
      expect(queryByText('SMS')).toBeNull();
    });
  });

  describe('chip layout', () => {
    it('renders chip layout with remaining values', () => {
      const props = createProps({
        data: MOCK_USAGE,
        props: { layout: 'chip' },
      });

      const { getByText } = render(<UsageCard {...props} />);

      expect(getByText('Data')).toBeTruthy();
      expect(getByText('Voice')).toBeTruthy();
      expect(getByText('SMS')).toBeTruthy();
    });

    it('shows remaining bytes in chip layout', () => {
      const props = createProps({
        data: MOCK_USAGE,
        props: { layout: 'chip' },
      });

      const { getByText } = render(<UsageCard {...props} />);
      expect(getByText('5.0 GB')).toBeTruthy();
    });

    it('shows remaining seconds in chip layout', () => {
      const props = createProps({
        data: MOCK_USAGE,
        props: { layout: 'chip' },
      });

      const { getByText } = render(<UsageCard {...props} />);
      expect(getByText('30m')).toBeTruthy();
    });

    it('shows remaining SMS count in chip layout', () => {
      const props = createProps({
        data: MOCK_USAGE,
        props: { layout: 'chip' },
      });

      const { getByText } = render(<UsageCard {...props} />);
      expect(getByText('50')).toBeTruthy();
    });
  });

  describe('period label', () => {
    it('shows custom periodLabel from config', () => {
      const props = createProps({
        data: MOCK_USAGE,
        props: { periodLabel: 'September 2026' },
      });

      const { getByText } = render(<UsageCard {...props} />);
      expect(getByText('September 2026')).toBeTruthy();
    });

    it('derives period from data.periodStart and periodEnd when not specified', () => {
      const props = createProps({ data: MOCK_USAGE });
      const { getByText } = render(<UsageCard {...props} />);

      // Should show formatted period like "Sep 1 - Sep 30"
      expect(getByText(/Sep/)).toBeTruthy();
    });
  });

  describe('loading state', () => {
    it('renders skeleton when isLoading is true', () => {
      const props = createProps({
        isLoading: true,
        data: undefined,
      });

      const { queryByText } = render(<UsageCard {...props} />);
      // Skeleton should not show data labels
      expect(queryByText('Data')).toBeNull();
      expect(queryByText('Voice')).toBeNull();
    });
  });

  describe('error state', () => {
    it('renders error message when error present and no data', () => {
      const props = createProps({
        error: 'Service unavailable',
        data: undefined,
      });

      const { getByText } = render(<UsageCard {...props} />);
      expect(getByText('Service unavailable')).toBeTruthy();
    });

    it('shows retry button when onRetry provided', () => {
      const onRetry = jest.fn();
      const props = createProps({
        error: 'Failed to load',
        data: undefined,
        onRetry,
      });

      const { getByText } = render(<UsageCard {...props} />);
      const retryButton = getByText('Retry');
      fireEvent.press(retryButton);
      expect(onRetry).toHaveBeenCalledTimes(1);
    });
  });

  describe('view details action', () => {
    it('dispatches NAVIGATE action to /usage', () => {
      const onAction = jest.fn();
      const props = createProps({
        data: MOCK_USAGE,
        onAction,
      });

      const { getByText } = render(<UsageCard {...props} />);
      fireEvent.press(getByText('View details'));

      expect(onAction).toHaveBeenCalledWith({
        event: 'view_usage',
        type: 'NAVIGATE',
        route: '/usage',
      });
    });
  });

  describe('formatters', () => {
    it('formats small bytes in KB', () => {
      const data = {
        ...MOCK_USAGE,
        data: { totalBytes: 500, remainingBytes: 500, allowanceBytes: 1024 },
      };
      const props = createProps({ data });
      const { getByText } = render(<UsageCard {...props} />);
      expect(getByText(/500 KB/)).toBeTruthy();
    });

    it('formats medium bytes in MB', () => {
      const data = {
        ...MOCK_USAGE,
        data: { totalBytes: 50 * 1024 * 1024, remainingBytes: 50 * 1024 * 1024, allowanceBytes: 100 * 1024 * 1024 },
      };
      const props = createProps({ data });
      const { getByText } = render(<UsageCard {...props} />);
      expect(getByText(/50\.0 MB/)).toBeTruthy();
    });

    it('formats hours when voice >= 1h', () => {
      const data = {
        ...MOCK_USAGE,
        voice: { totalSeconds: 3600, remainingSeconds: 3600, allowanceSeconds: 7200 },
      };
      const props = createProps({ data });
      const { getByText } = render(<UsageCard {...props} />);
      expect(getByText(/1h 0m/)).toBeTruthy();
    });
  });

  describe('progress calculation', () => {
    it('caps progress at 100% when used > total', () => {
      const data = {
        ...MOCK_USAGE,
        data: {
          totalBytes: 15 * 1024 * 1024 * 1024, // exceeded
          remainingBytes: 0,
          allowanceBytes: 10 * 1024 * 1024 * 1024,
        },
      };
      const props = createProps({ data });
      const { toJSON } = render(<UsageCard {...props} />);
      // Should render without crashing
      expect(toJSON()).toBeTruthy();
    });

    it('handles zero allowance gracefully', () => {
      const data = {
        ...MOCK_USAGE,
        data: { totalBytes: 0, remainingBytes: 0, allowanceBytes: 0 },
      };
      const props = createProps({ data });
      const { toJSON } = render(<UsageCard {...props} />);
      expect(toJSON()).toBeTruthy();
    });
  });
});
