export interface FixedPopoverOptions {
  widthRem?: number;
  heightRem?: number;
}

export function positionFixedPopover(
  trigger: DOMRect,
  options: FixedPopoverOptions = {}
): Record<string, string> {
  const width = Math.min((options.widthRem ?? 22) * 16, window.innerWidth - 16);
  const height = Math.min((options.heightRem ?? 18) * 16, window.innerHeight - 16);
  let left = trigger.left;
  if (left + width > window.innerWidth - 8) {
    left = Math.max(8, window.innerWidth - width - 8);
  }
  let top = trigger.bottom + 6;
  if (top + 160 > window.innerHeight) {
    top = Math.max(8, trigger.top - Math.min(height, 240) - 6);
  }
  return {
    top: `${top}px`,
    left: `${left}px`,
    width: `${width}px`,
    maxHeight: `${height}px`
  };
}
