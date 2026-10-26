import { Injectable, computed, signal } from '@angular/core';
import { EthicsCommittee, Role, User } from '../models/ethics.models';
import { EthicsStore } from '../services/ethics-store.service';

const KEY = 'ethics-uss-user';
const COMMITTEE_KEY = 'ethics-uss-committee';

@Injectable({ providedIn: 'root' })
export class DemoAuthService {
  private readonly userId = signal<string | null>(this.readId());
  private readonly committee = signal<string | null>(localStorage.getItem(COMMITTEE_KEY));
  readonly currentUser = computed(() => {
    const id = this.userId();
    return this.store.snapshot().users.find((user) => user.id === id) ?? null;
  });

  constructor(private readonly store: EthicsStore) {}

  readonly activeCommittee = computed((): EthicsCommittee | null => {
    const user = this.currentUser();
    if (!user?.committees.length) {
      return null;
    }
    const stored = this.committee();
    if (stored && user.committees.includes(stored as EthicsCommittee)) {
      return stored as EthicsCommittee;
    }
    return user.committees[0];
  });

  users(): User[] {
    return this.store.snapshot().users;
  }

  setCommittee(code: string): void {
    this.committee.set(code);
    localStorage.setItem(COMMITTEE_KEY, code);
  }

  assume(userId: string): void {
    this.userId.set(userId);
    localStorage.setItem(KEY, userId);
  }

  logout(): void {
    this.userId.set(null);
    localStorage.removeItem(KEY);
  }

  hasRole(...roles: Role[]): boolean {
    const user = this.currentUser();
    return !!user && roles.includes(user.role);
  }

  private readId(): string | null {
    return localStorage.getItem(KEY);
  }
}
