/**
 * Toast utilities — re-exports the Sonner toast API for consistency.
 *
 * The admin portal uses `sonner` for toast notifications (already configured
 * in main.tsx via <Toaster richColors position="top-right" />).
 *
 * This module re-exports the `toast` function so any component that wants
 * to fire notifications can import from `@/components/ui/toast` rather than
 * depending on the sonner package directly — keeping the API surface stable
 * if we later swap toast providers.
 *
 * Usage:
 *   import { toast } from '@/components/ui/toast';
 *   toast.success('Saved');
 *   toast.error('Failed');
 *   toast.info('Heads up');
 *   toast.warning('Careful');
 */
export { toast } from 'sonner';
