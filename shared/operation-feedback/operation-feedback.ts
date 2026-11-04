export const OPERATION_INDICATOR_MS = 500;
export const OPERATION_DETAIL_MS = 2_000;
export const OPERATION_LONG_MS = 8_000;

export type OperationPhase = 'idle' | 'loading' | 'success' | 'error';

export interface OperationCopy {
  loadingMessage: string;
  detailMessage?: string;
  longRunningMessage?: string;
  successMessage?: string;
  errorMessage?: string;
  blocking?: boolean;
}

export interface OperationView {
  phase: OperationPhase;
  running: boolean;
  showIndicator: boolean;
  showDetail: boolean;
  showLong: boolean;
  showPanel: boolean;
  loadingMessage: string;
  detailMessage: string;
  longRunningMessage: string;
  successMessage: string;
  errorMessage: string;
}

const EMPTY_VIEW: OperationView = {
  phase: 'idle',
  running: false,
  showIndicator: false,
  showDetail: false,
  showLong: false,
  showPanel: false,
  loadingMessage: '',
  detailMessage: '',
  longRunningMessage: '',
  successMessage: '',
  errorMessage: ''
};

export function operationButton(view: OperationView, idleLabel: string): { disabled: boolean; label: string; spinner: boolean } {
  if (!view.running) {
    return { disabled: false, label: idleLabel, spinner: false };
  }
  return { disabled: true, label: view.loadingMessage || idleLabel, spinner: true };
}

/**
 * Estado de una operación iniciada por la persona. El botón cambia al empezar.
 * El panel bloqueante espera medio segundo para no parpadear. La explicación
 * contextual espera dos segundos, y el aviso de demora, ocho. No hay porcentaje.
 */
export class OperationRun {
  private phase: OperationPhase = 'idle';
  private startedAt = 0;
  private observedAt = 0;
  private copy: OperationCopy = { loadingMessage: '' };
  private successMessage = '';
  private errorMessage = '';
  private readonly now: () => number;

  constructor(now: () => number = () => Date.now()) {
    this.now = now;
  }

  start(copy: OperationCopy): boolean {
    if (this.phase === 'loading') {
      return false;
    }
    this.copy = copy;
    this.phase = 'loading';
    this.startedAt = this.now();
    this.observedAt = this.startedAt;
    this.successMessage = '';
    this.errorMessage = '';
    return true;
  }

  observe(): void {
    if (this.phase === 'loading') {
      this.observedAt = this.now();
    }
  }

  succeed(message?: string): void {
    if (this.phase !== 'loading') {
      return;
    }
    this.phase = 'success';
    this.successMessage = message ?? this.copy.successMessage ?? '';
  }

  fail(message?: string): void {
    if (this.phase !== 'loading') {
      return;
    }
    this.phase = 'error';
    this.errorMessage = message ?? this.copy.errorMessage ?? '';
  }

  reset(): void {
    this.phase = 'idle';
    this.successMessage = '';
    this.errorMessage = '';
  }

  view(): OperationView {
    if (this.phase === 'idle') {
      return EMPTY_VIEW;
    }
    const elapsed = this.phase === 'loading' ? Math.max(0, this.observedAt - this.startedAt) : 0;
    const showIndicator = this.phase === 'loading' && elapsed >= OPERATION_INDICATOR_MS;
    const showDetail = this.phase === 'loading' && elapsed >= OPERATION_DETAIL_MS && !!this.copy.detailMessage;
    const showLong = this.phase === 'loading' && elapsed >= OPERATION_LONG_MS && !!this.copy.longRunningMessage;
    return {
      phase: this.phase,
      running: this.phase === 'loading',
      showIndicator,
      showDetail,
      showLong,
      showPanel: this.copy.blocking === true && showIndicator,
      loadingMessage: this.copy.loadingMessage,
      detailMessage: showDetail ? this.copy.detailMessage ?? '' : '',
      longRunningMessage: showLong ? this.copy.longRunningMessage ?? '' : '',
      successMessage: this.phase === 'success' ? this.successMessage : '',
      errorMessage: this.phase === 'error' ? this.errorMessage : ''
    };
  }
}

export function watchOperation(run: OperationRun, publish: () => void): () => void {
  const timer = setInterval(() => {
    run.observe();
    publish();
    if (!run.view().running) {
      clearInterval(timer);
    }
  }, 200);
  return () => clearInterval(timer);
}
