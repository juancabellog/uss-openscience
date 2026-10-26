import { Injectable } from '@angular/core';
import { CommitteeSession, EthicsCommittee, SessionType } from '../models/ethics.models';
import { AuditService } from './audit.service';
import { EthicsStore } from './ethics-store.service';
import { uid } from './ids';

@Injectable({ providedIn: 'root' })
export class SessionService {
  constructor(
    private readonly store: EthicsStore,
    private readonly audit: AuditService
  ) {}

  list(committee?: EthicsCommittee): CommitteeSession[] {
    return this.store
      .snapshot()
      .sessions.filter((item) => !committee || item.committee === committee)
      .sort((a, b) => b.date.localeCompare(a.date));
  }

  get(id: string): CommitteeSession | undefined {
    return this.store.snapshot().sessions.find((item) => item.id === id);
  }

  create(committee: EthicsCommittee, date: string, time: string, type: SessionType): CommitteeSession {
    const session: CommitteeSession = {
      id: uid('ses'),
      committee,
      date,
      time,
      type,
      status: 'SCHEDULED',
      caseIds: []
    };
    this.store.update((state) => ({ ...state, sessions: [session, ...state.sessions] }));
    this.audit.record({
      action: 'CREATE_SESSION',
      objectType: 'CommitteeSession',
      objectId: session.id,
      description: `Creó sesión ${type === 'EXTRAORDINARY' ? 'extraordinaria' : 'ordinaria'}`
    });
    return session;
  }

  attach(sessionId: string, caseId: string): void {
    this.store.update((state) => ({
      ...state,
      sessions: state.sessions.map((item) =>
        item.id === sessionId ? { ...item, caseIds: Array.from(new Set([...item.caseIds, caseId])) } : item
      ),
      cases: state.cases.map((item) => (item.id === caseId ? { ...item, sessionId } : item))
    }));
  }
}
