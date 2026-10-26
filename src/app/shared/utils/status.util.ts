import { DataQualityIssueStatus, DataQualityStatus, IntegrationHealth } from '../../core/models/openscience.models';
import { OsBadgeTone } from '../components/status-badge/status-badge.component';

export function qualityKey(status?: DataQualityStatus): string {
  return status ? `status.${status}` : 'status.unavailable';
}

export function qualityTone(status?: DataQualityStatus): OsBadgeTone {
  if (status === 'ok') return 'ok';
  if (status === 'review') return 'review';
  if (status === 'pending') return 'pending';
  return 'neutral';
}

export function issueKey(status: DataQualityIssueStatus): string {
  return `status.${status}`;
}

export function issueTone(status: DataQualityIssueStatus): OsBadgeTone {
  if (status === 'corrected' || status === 'validated') return 'success';
  if (status === 'in_review') return 'review';
  if (status === 'dismissed') return 'neutral';
  return 'open';
}

export function integrationKey(health: IntegrationHealth): string {
  if (health === 'pending') {
    return 'status.syncing';
  }
  return `status.${health}`;
}

export function integrationTone(health: IntegrationHealth): OsBadgeTone {
  if (health === 'operational') return 'success';
  if (health === 'warning') return 'warning';
  if (health === 'pending') return 'pending';
  if (health === 'error') return 'danger';
  if (health === 'unconfigured' || health === 'disabled') return 'muted';
  return 'neutral';
}

export function integrationIcon(health: IntegrationHealth): string {
  if (health === 'operational') return 'check';
  if (health === 'warning') return 'warning';
  if (health === 'pending') return 'sync';
  if (health === 'error') return 'status-error';
  return 'minus-circle';
}

export function auditTone(result: 'success' | 'warning' | 'error'): OsBadgeTone {
  if (result === 'success') return 'success';
  if (result === 'warning') return 'warning';
  return 'danger';
}

export function indicatorValueKey(state: 'measured' | 'pending' | 'unavailable'): string {
  if (state === 'measured') return 'status.measured';
  if (state === 'unavailable') return 'status.unavailable';
  return 'status.pending_measurement';
}
