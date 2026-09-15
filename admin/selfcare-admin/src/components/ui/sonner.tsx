/**
 * Sonner — themed Toaster component for Selfcare Studio.
 *
 * `sonner` is already configured globally in `src/main.tsx` via
 * `<Toaster richColors position="top-right" />`. This file exists so that
 * any page can render its own Toaster if it needs a context-specific
 * toast viewport (e.g. inside a modal).
 *
 * For most cases, prefer the global Toaster in main.tsx and just call
 * `toast('...')` from anywhere in the app.
 */
import { Toaster as Sonner } from 'sonner';

type ToasterProps = React.ComponentProps<typeof Sonner>;

const Toaster = ({ ...props }: ToasterProps) => {
  return (
    <Sonner
      className="toaster group"
      toastOptions={{
        classNames: {
          toast:
            'group toast group-[.toaster]:bg-background group-[.toaster]:text-foreground group-[.toaster]:border-border group-[.toaster]:shadow-lg',
          description: 'group-[.toast]:text-muted-foreground',
          actionButton: 'group-[.toast]:bg-primary group-[.toast]:text-primary-foreground',
          cancelButton: 'group-[.toast]:bg-muted group-[.toast]:text-muted-foreground',
        },
      }}
      {...props}
    />
  );
};

export { Toaster };
