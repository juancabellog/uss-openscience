export function localizedPureTerm(
  language: string,
  es: string | null | undefined,
  en: string | null | undefined,
  fallback: string | null | undefined
): string {
  const preferred = language === 'en' ? en || es : es || en;
  return (preferred || fallback || '').trim();
}

export function workflowStepCode(
  label: string,
  step: string | null | undefined
): string {
  const code = (step ?? '').trim();
  if (!code || !label || label === code) {
    return '';
  }
  return code;
}
