/**
 * Universal Primitives — The ONLY components that need to be registered.
 * All feature UIs are composed via manifest config using these primitives.
 *
 * Primitives:
 * - UniversalBox: Container/surface/card/sheet/divider/spacer (flex layout, theme-driven)
 * - UniversalText: All text (label/value/title/subtitle/body/caption/error/success/warning/link)
 * - UniversalImage: All images (avatar/thumbnail/banner/icon/full/circle/rounded)
 * - UniversalButton: All buttons (primary/secondary/outline/ghost/destructive/tonal, sm/md/lg/xl)
 * - UniversalInput: All inputs (text/password/email/phone/number/otp/search/multiline)
 * - UniversalList: Virtualized list with child primitive config
 * - UniversalGrid: Grid layout with child primitive config
 * - UniversalChart: Charts (line/bar/pie/donut) via config
 */

export { UniversalBox } from './UniversalBox';
export { UniversalText } from './UniversalText';
export { UniversalImage } from './UniversalImage';
export { UniversalButton } from './UniversalButton';
export { UniversalInput } from './UniversalInput';
export { UniversalList } from './UniversalList';
export { UniversalGrid } from './UniversalGrid';
export { UniversalChart } from './UniversalChart';