import { Injectable, signal } from '@angular/core';
import { StoreState } from '../models/ethics.models';
import { createSeed } from '../../mocks/seed';

const KEY = 'ethics-uss-store-v6';

@Injectable({ providedIn: 'root' })
export class EthicsStore {
  readonly state = signal<StoreState>(this.restore());

  snapshot(): StoreState {
    return this.state();
  }

  update(mutator: (current: StoreState) => StoreState): StoreState {
    const next = mutator(structuredClone(this.state()));
    this.state.set(next);
    localStorage.setItem(KEY, JSON.stringify(next));
    return next;
  }

  reset(): StoreState {
    const seed = createSeed();
    this.state.set(seed);
    localStorage.setItem(KEY, JSON.stringify(seed));
    return seed;
  }

  private restore(): StoreState {
    try {
      const raw = localStorage.getItem(KEY);
      if (raw) {
        return JSON.parse(raw) as StoreState;
      }
    } catch {
      localStorage.removeItem(KEY);
    }
    return createSeed();
  }
}
