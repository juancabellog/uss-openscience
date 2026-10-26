import { Injectable } from '@angular/core';

export const PROTOTYPE_PDF_TEMPLATE = 'PROTOTYPE_PDF_TEMPLATE';

export interface PdfPreview {
  title: string;
  disclaimer: string;
  rows: Array<{ label: string; value: string }>;
  body: string;
  signature?: string;
  stamp?: string;
}

@Injectable({ providedIn: 'root' })
export class PdfGenerationService {
  readonly templateId = PROTOTYPE_PDF_TEMPLATE;

  open(preview: PdfPreview): void {
    const blob = new Blob([this.html(preview)], { type: 'text/html' });
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank', 'noopener');
  }

  html(preview: PdfPreview): string {
    const rows = preview.rows.map((row) => `<p><strong>${esc(row.label)}</strong> ${esc(row.value)}</p>`).join('');
    const signature = preview.signature ? `<aside class="mark">${esc(preview.signature)}</aside>` : '';
    const stamp = preview.stamp ? `<aside class="stamp">${esc(preview.stamp)}</aside>` : '';
    return `<!doctype html><html><head><meta charset="utf-8"><title>${esc(preview.title)}</title>
      <style>
        body{font-family:Georgia,serif;margin:0;color:#1a2433;background:#f4f1ea}
        main{max-width:760px;margin:32px auto;background:#fff;padding:48px 56px;border:1px solid #d9d1c3}
        h1{font-size:22px;margin:0 0 8px}
        .disclaimer{font-family:sans-serif;font-size:12px;color:#6b6256}
        p{line-height:1.5}
        .body{white-space:pre-wrap}
        .mark,.stamp{margin-top:28px;border:1px dashed #8a8174;padding:12px 16px;font-family:sans-serif;font-size:13px}
      </style></head>
      <body><main data-template="${PROTOTYPE_PDF_TEMPLATE}">
        <p class="disclaimer">${esc(preview.disclaimer)}</p>
        <h1>${esc(preview.title)}</h1>
        ${rows}
        <div class="body">${esc(preview.body)}</div>
        ${signature}
        ${stamp}
      </main></body></html>`;
  }
}

function esc(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
