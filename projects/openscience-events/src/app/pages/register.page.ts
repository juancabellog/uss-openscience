import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  Injector,
  input,
  signal,
  viewChild
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Location } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';
import {
  EventPublicDetail,
  EventRegistrationProfile,
  EventRegistrationReceipt,
  EventsApiService
} from '../core/events-api.service';
import { I18nService } from '../core/i18n/i18n.service';
import { PublicEventView, publicEventFailure } from '../core/public-event-state';
import { registerRequestCopy } from './register-request.copy';
import { I18N_IMPORTS } from '../shared/i18n/i18n.imports';

const RESEND_COOLDOWN_MS = 30_000;
const TOAST_MS = 4000;

@Component({
  selector: 'ose-register-page',
  imports: [ReactiveFormsModule, RouterLink, ...I18N_IMPORTS],
  templateUrl: './register.page.html',
  styleUrl: './page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegisterPage {
  private readonly api = inject(EventsApiService);
  private readonly i18n = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly location = inject(Location);
  readonly slug = input.required<string>();
  readonly token = input<string | undefined>();
  readonly confirmHeading = viewChild<HTMLHeadingElement>('confirmHeading');
  readonly emailInput = viewChild<HTMLInputElement>('emailInput');
  readonly view = toSignal(
    toObservable(this.slug).pipe(
      switchMap((slug) =>
        this.api.getBySlug(slug).pipe(
          map((event): PublicEventView => ({ kind: 'ok', event })),
          catchError((error: unknown) => of(publicEventFailure(error)))
        )
      )
    ),
    { initialValue: { kind: 'loading', event: null } as PublicEventView }
  );
  readonly event = computed(() => this.view().event);
  readonly profile = signal<EventRegistrationProfile | null>(null);
  readonly receipt = signal<EventRegistrationReceipt | null>(null);
  readonly step = signal<'email' | 'check-email' | 'confirm-link' | 'form' | 'done'>('email');
  readonly pendingToken = signal<string | null>(null);
  readonly busy = signal(false);
  readonly emailSent = signal(false);
  readonly errorKey = signal<string | null>(null);
  readonly toastKey = signal<string | null>(null);
  readonly now = signal(Date.now());
  readonly resendAvailableAt = signal(0);
  readonly resendWait = computed(() => Math.max(0, Math.ceil((this.resendAvailableAt() - this.now()) / 1000)));
  readonly confirmationBodyKey = computed(() => registerRequestCopy(this.emailSent()).bodyKey);
  readonly emailForm = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] })
  });
  readonly profileForm = new FormGroup({
    displayName: new FormControl('', { nonNullable: true }),
    facultyOrInstitution: new FormControl('', { nonNullable: true }),
    campusName: new FormControl('', { nonNullable: true }),
    positionTitle: new FormControl('', { nonNullable: true })
  });
  private toastTimer: ReturnType<typeof setTimeout> | null = null;
  private tickTimer: ReturnType<typeof setInterval> | null = null;
  private capturedLink = false;

  constructor() {
    this.destroyRef.onDestroy(() => {
      if (this.toastTimer) {
        clearTimeout(this.toastTimer);
      }
      if (this.tickTimer) {
        clearInterval(this.tickTimer);
      }
    });
    effect(() => {
      const slug = this.slug();
      const queryToken = this.token();
      if (!slug || this.capturedLink) {
        return;
      }
      const stored = readStoredChallenge(slug);
      const token = queryToken || stored;
      if (!token) {
        return;
      }
      this.capturedLink = true;
      this.pendingToken.set(token);
      storeChallenge(slug, token);
      if (queryToken) {
        this.stripTokenFromAddress();
      }
      if (this.step() === 'email') {
        this.step.set('confirm-link');
        this.moveFocus('confirm');
      }
    });
  }

  titleOf(event: EventPublicDetail): string {
    return this.i18n.localizedText(event.title, event.titleEn);
  }

  descriptionOf(event: EventPublicDetail): string {
    return this.i18n.localizedText(event.description, event.descriptionEn);
  }

  canRegister(event: EventPublicDetail): boolean {
    return event.publicationStatus === 'PUBLISHED' && event.registrationOpen;
  }

  closedKey(event: EventPublicDetail): string {
    if (event.publicationStatus === 'CANCELLED') {
      return 'register.cancelled';
    }
    return 'register.closedBanner';
  }

  request(): void {
    if (this.busy()) {
      return;
    }
    if (this.emailForm.invalid) {
      this.emailForm.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.errorKey.set(null);
    this.api.requestRegistration(this.slug(), this.emailForm.controls.email.value, this.i18n.locale()).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.emailSent.set(!!result.emailSent);
        this.step.set('check-email');
        this.startResendCooldown();
        this.showToast(registerRequestCopy(!!result.emailSent).toastKey);
        this.moveFocus('confirm');
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.errorKey.set(this.connectionOrApiKey(err, 'register.sendFailed'));
      }
    });
  }

  confirmEmail(): void {
    const token = this.pendingToken();
    const slug = this.slug();
    if (!token || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.errorKey.set(null);
    this.api.consumeVerification(slug, token).subscribe({
      next: (profile) => {
        clearStoredChallenge(slug);
        this.pendingToken.set(null);
        this.showProfile(profile);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.errorKey.set(this.connectionOrApiKey(err, 'register.invalidLink'));
      }
    });
  }

  changeEmail(): void {
    this.step.set('email');
    this.errorKey.set(null);
    this.toastKey.set(null);
    this.moveFocus('email');
  }

  confirm(): void {
    const profile = this.profile();
    if (!profile) {
      return;
    }
    if (!profile.directoryMatch) {
      const value = this.profileForm.getRawValue();
      if (!value.displayName || !value.facultyOrInstitution || !value.positionTitle) {
        this.profileForm.markAllAsTouched();
        this.errorKey.set('register.missingFields');
        return;
      }
    }
    this.busy.set(true);
    this.errorKey.set(null);
    this.api.confirmRegistration(this.slug(), {
      ...this.profileForm.getRawValue(),
      language: this.i18n.currentLanguage(),
      locale: this.i18n.locale()
    }).subscribe({
      next: (receipt) => {
        this.busy.set(false);
        this.receipt.set(receipt);
        this.step.set('done');
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.errorKey.set(this.i18n.errorKey(err, 'register.confirmFailed'));
      }
    });
  }

  private stripTokenFromAddress(): void {
    const path = this.location.path(false).split('?')[0];
    this.location.replaceState(path);
  }

  private showProfile(profile: EventRegistrationProfile): void {
    this.busy.set(false);
    this.profile.set(profile);
    this.step.set('form');
    this.profileForm.patchValue({
      displayName: profile.displayName ?? '',
      facultyOrInstitution: profile.facultyOrInstitution ?? '',
      campusName: profile.campusName ?? '',
      positionTitle: profile.positionTitle ?? ''
    });
  }

  private startResendCooldown(): void {
    this.resendAvailableAt.set(Date.now() + RESEND_COOLDOWN_MS);
    this.now.set(Date.now());
    if (this.tickTimer) {
      clearInterval(this.tickTimer);
    }
    this.tickTimer = setInterval(() => {
      this.now.set(Date.now());
      if (this.resendWait() === 0 && this.tickTimer) {
        clearInterval(this.tickTimer);
        this.tickTimer = null;
      }
    }, 500);
  }

  private moveFocus(target: 'confirm' | 'email'): void {
    const attempt = (): boolean => {
      const ref = target === 'confirm' ? this.confirmHeading() : this.emailInput();
      const el = asFocusable(ref);
      if (!el) {
        return false;
      }
      el.focus();
      return true;
    };
    afterNextRender(
      () => {
        if (!attempt()) {
          setTimeout(attempt, 0);
        }
      },
      { injector: this.injector }
    );
    setTimeout(attempt, 0);
    setTimeout(attempt, 50);
  }

  private showToast(key: string): void {
    this.toastKey.set(key);
    if (this.toastTimer) {
      clearTimeout(this.toastTimer);
    }
    this.toastTimer = setTimeout(() => this.toastKey.set(null), TOAST_MS);
  }

  private connectionOrApiKey(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse && err.status === 0) {
      return 'detail.connectionError';
    }
    return this.i18n.errorKey(err, fallback);
  }
}

const CHALLENGE_KEY = 'ose-email-challenge:';

function storeChallenge(slug: string, token: string): void {
  try {
    sessionStorage.setItem(CHALLENGE_KEY + slug, token);
  } catch {
    /* ignore quota / private mode */
  }
}

function readStoredChallenge(slug: string): string | null {
  try {
    return sessionStorage.getItem(CHALLENGE_KEY + slug);
  } catch {
    return null;
  }
}

function clearStoredChallenge(slug: string): void {
  try {
    sessionStorage.removeItem(CHALLENGE_KEY + slug);
  } catch {
    /* ignore */
  }
}

function asFocusable(ref: unknown): HTMLElement | null {
  if (ref instanceof HTMLElement) {
    return ref;
  }
  if (ref && typeof ref === 'object' && 'nativeElement' in ref) {
    const el = (ref as { nativeElement: unknown }).nativeElement;
    if (el instanceof HTMLElement) {
      return el;
    }
  }
  return null;
}
