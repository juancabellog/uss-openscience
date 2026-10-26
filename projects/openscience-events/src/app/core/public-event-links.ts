import { Injectable } from '@angular/core';
import { buildPublicEventUrl, buildPublicRegistrationUrl } from './public-event-url';

@Injectable({ providedIn: 'root' })
export class PublicEventLinks {
  eventUrl(slug?: string | null): string | null {
    const value = slug?.trim();
    if (!value) {
      return null;
    }
    return buildPublicEventUrl(document.baseURI, value);
  }

  registrationUrl(slug?: string | null): string | null {
    const value = slug?.trim();
    if (!value) {
      return null;
    }
    return buildPublicRegistrationUrl(document.baseURI, value);
  }

  slugOf(event?: { publicSlug?: string | null; slug?: string | null } | null): string | null {
    return event?.publicSlug?.trim() || event?.slug?.trim() || null;
  }
}
