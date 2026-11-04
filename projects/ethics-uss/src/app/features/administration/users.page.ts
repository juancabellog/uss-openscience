import { ChangeDetectionStrategy, Component, DestroyRef, HostListener, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { capabilityForUrl } from '../../core/guards/demo-auth.guard';
import { SessionIdentityService } from '../../core/auth/session-identity.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { isAllowedInstitutionalEmail } from '../../core/identity/institutional-email';
import {
  EthicsDirectoryApi,
  EthicsDirectoryError,
  EthicsDirectoryRole,
  EthicsDirectoryUser,
  EthicsOnboarding,
  EthicsRemoval
} from '../../core/services/ethics-directory.api';
import { PermissionService } from '../../core/services/permission.service';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { IconComponent } from '../../shared/components/icon.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { OperationFeedbackPanelComponent } from '../../../../../../shared/operation-feedback/operation-feedback-panel.component';
import { OperationRun, watchOperation } from '../../../../../../shared/operation-feedback/operation-feedback';

const PAGE_SIZE = 25;
const GRANTABLE = ['RESEARCHER', 'ADMINISTRATOR', 'COMMITTEE_PRESIDENT', 'EXECUTIVE_SECRETARY'] as const;

@Component({
  selector: 'eth-users-page',
  imports: [...I18N_IMPORTS, FormsModule, StatusChipComponent, IconComponent, OperationFeedbackPanelComponent],
  templateUrl: './users.page.html',
  styleUrl: './users.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UsersPage {
  private readonly api = inject(EthicsDirectoryApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(DemoAuthService);
  private readonly session = inject(SessionIdentityService);
  private readonly permissions = inject(PermissionService);
  private readonly i18n = inject(I18nService);
  private searchTimer = 0;
  private request = 0;

  readonly searchInput = signal('');
  readonly query = signal('');
  readonly kind = signal<'ALL' | 'INSTITUTIONAL' | 'EXTERNAL'>('ALL');
  readonly status = signal<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL');
  readonly access = signal<'ALL' | 'YES' | 'NO'>('ALL');
  readonly page = signal(1);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly rows = signal<EthicsDirectoryUser[]>([]);

  readonly mode = signal<'list' | 'create' | 'detail'>('list');
  readonly accountType = signal<'INSTITUTIONAL' | 'EXTERNAL'>('INSTITUTIONAL');
  readonly selected = signal<EthicsDirectoryUser | null>(null);
  readonly message = signal<string | null>(null);
  readonly confirmDisable = signal(false);
  readonly confirmRequired = signal(false);
  readonly disableNeedsConfirm = computed(() =>
    this.confirmRequired()
    || (this.selected()?.roles ?? []).some((role) => role.id === 'REVIEWER')
  );
  readonly lookedUp = signal<EthicsDirectoryUser | null>(null);
  readonly resolution = signal<EthicsOnboarding | null>(null);
  readonly resolving = signal(false);

  readonly email = signal('');
  readonly givenName = signal('');
  readonly familyName = signal('');
  readonly institution = signal('');
  readonly structuralRole = signal('');
  readonly pendingRemove = signal<EthicsDirectoryRole | null>(null);
  readonly removal = signal<EthicsRemoval | null>(null);
  readonly deleteOpen = signal(false);
  readonly disableOpen = signal(false);
  readonly removing = signal(false);
  readonly menuUserId = signal<string | null>(null);
  readonly menuRemoval = signal<EthicsRemoval | null>(null);
  readonly menuTop = signal<number | null>(null);
  readonly menuBottom = signal<number | null>(null);
  readonly menuRight = signal(8);
  private menuTrigger: HTMLButtonElement | null = null;
  readonly revoking = signal(false);
  readonly canManage = computed(() => this.permissions.can(this.auth.currentUser(), 'MANAGE_USERS'));
  readonly filtersActive = computed(() =>
    this.searchInput().trim() !== ''
    || this.kind() !== 'ALL'
    || this.status() !== 'ALL'
    || this.access() !== 'ALL'
  );
  readonly grantChoices = computed(() => {
    const held = new Set(
      (this.selected()?.roles ?? [])
        .filter((role) => !role.contextual && role.scopeType === 'GLOBAL' && role.scopeId === '*')
        .map((role) => role.id)
    );
    return GRANTABLE
      .filter((id) => !held.has(id))
      .map((id) => ({ id, label: `roles.${id.toLowerCase()}` }));
  });

  private readonly inviteRun = new OperationRun();
  private stopInviteWatch: (() => void) | null = null;
  readonly inviteView = signal(this.inviteRun.view());
  readonly inviteErrorKey = signal<string | null>(null);

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      window.clearTimeout(this.searchTimer);
      this.stopInviteWatch?.();
    });
    if (this.route.snapshot.queryParamMap.get('crear') === 'externo') {
      this.mode.set('create');
      this.accountType.set('EXTERNAL');
    }
    void this.load();
  }

  onSearch(value: string): void {
    this.searchInput.set(value);
    window.clearTimeout(this.searchTimer);
    this.searchTimer = window.setTimeout(() => {
      this.query.set(value);
      this.page.set(1);
      void this.load();
    }, 300);
  }

  clearSearch(): void {
    window.clearTimeout(this.searchTimer);
    this.searchInput.set('');
    this.query.set('');
    this.page.set(1);
    void this.load();
  }

  clearFilters(): void {
    window.clearTimeout(this.searchTimer);
    this.searchInput.set('');
    this.query.set('');
    this.kind.set('ALL');
    this.status.set('ALL');
    this.access.set('ALL');
    this.page.set(1);
    void this.load();
  }

  resultCountLabel(): string {
    const count = this.totalElements();
    const key = count === 1 ? 'users.resultCountOne' : 'users.resultCountMany';
    return this.i18n.translate(key, { count });
  }

  setKind(value: 'ALL' | 'INSTITUTIONAL' | 'EXTERNAL'): void {
    this.kind.set(value);
    this.page.set(1);
    void this.load();
  }

  setStatus(value: 'ALL' | 'ACTIVE' | 'INACTIVE'): void {
    this.status.set(value);
    this.page.set(1);
    void this.load();
  }

  setAccess(value: 'ALL' | 'YES' | 'NO'): void {
    this.access.set(value);
    this.page.set(1);
    void this.load();
  }

  previous(): void {
    if (this.page() <= 1) {
      return;
    }
    this.page.update((current) => current - 1);
    void this.load();
  }

  next(): void {
    if (this.page() >= this.totalPages()) {
      return;
    }
    this.page.update((current) => current + 1);
    void this.load();
  }

  startCreate(): void {
    this.message.set(null);
    this.lookedUp.set(null);
    this.resolution.set(null);
    this.mode.set('create');
  }

  back(): void {
    this.closeMenu(false);
    this.mode.set('list');
    this.message.set(null);
    this.confirmDisable.set(false);
    this.confirmRequired.set(false);
    void this.load();
  }

  pendingInvitation(user: EthicsDirectoryUser): boolean {
    return user.accountStatus === 'PENDING_ACTIVATION';
  }

  actionsLabel(user: EthicsDirectoryUser): string {
    const name = user.displayName?.trim() || user.email;
    return this.i18n.translate('users.actionsFor', { name });
  }

  showResend(user: EthicsDirectoryUser): boolean {
    return this.canManage() && this.pendingInvitation(user) && user.personKind === 'INSTITUTIONAL';
  }

  showEditRoles(user: EthicsDirectoryUser): boolean {
    return this.canManage() && user.personKind === 'INSTITUTIONAL' && !this.pendingInvitation(user);
  }

  showEnable(user: EthicsDirectoryUser): boolean {
    return this.canManage() && !user.ethicsAccess && !this.pendingInvitation(user);
  }

  showDisable(user: EthicsDirectoryUser): boolean {
    return this.canManage() && user.ethicsAccess;
  }

  showDelete(user: EthicsDirectoryUser): boolean {
    return this.canManage() && this.menuUserId() === user.id && this.menuRemoval()?.deletable === true;
  }

  hasMenu(user: EthicsDirectoryUser): boolean {
    return this.showResend(user)
      || this.showEditRoles(user)
      || this.showEnable(user)
      || this.showDisable(user)
      || (this.canManage() && this.pendingInvitation(user));
  }

  menuUser(): EthicsDirectoryUser | null {
    const id = this.menuUserId();
    return id ? this.rows().find((user) => user.id === id) ?? null : null;
  }

  toggleMenu(user: EthicsDirectoryUser, event: Event): void {
    if (this.menuUserId() === user.id) {
      this.closeMenu(true);
      return;
    }
    const trigger = event.currentTarget as HTMLButtonElement;
    const rect = trigger.getBoundingClientRect();
    const below = window.innerHeight - rect.bottom;
    this.menuTrigger = trigger;
    this.menuUserId.set(user.id);
    this.menuRemoval.set(null);
    this.menuRight.set(Math.max(8, window.innerWidth - rect.right));
    if (below < 180 && rect.top > 180) {
      this.menuTop.set(null);
      this.menuBottom.set(window.innerHeight - rect.top + 4);
    } else {
      this.menuBottom.set(null);
      this.menuTop.set(rect.bottom + 4);
    }
    void this.api.removal(user.id).then((facts) => {
      if (this.menuUserId() === user.id) {
        this.menuRemoval.set(facts);
        setTimeout(() => {
          const active = document.activeElement;
          if (!(active instanceof HTMLElement) || active.getAttribute('role') !== 'menuitem') {
            this.focusMenuItem(0);
          }
        });
      }
    }).catch(() => {
      if (this.menuUserId() === user.id) {
        this.menuRemoval.set(null);
      }
    });
    setTimeout(() => this.focusMenuItem(0));
  }

  private focusMenuItem(index: number): void {
    const id = this.menuUserId();
    if (!id) {
      return;
    }
    const items = document.getElementById(`user-menu-${id}`)?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]');
    items?.[index]?.focus();
  }

  closeMenu(restoreFocus: boolean): void {
    const trigger = this.menuTrigger;
    this.menuUserId.set(null);
    this.menuRemoval.set(null);
    this.menuTrigger = null;
    if (restoreFocus) {
      trigger?.focus();
    }
  }

  onMenuKey(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeMenu(true);
      return;
    }
    const menu = event.currentTarget as HTMLElement;
    const items = Array.from(menu.querySelectorAll<HTMLButtonElement>('[role="menuitem"]'));
    if (!items.length) {
      return;
    }
    const current = items.indexOf(document.activeElement as HTMLButtonElement);
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      items[(current + 1 + items.length) % items.length].focus();
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      items[(current - 1 + items.length) % items.length].focus();
    } else if (event.key === 'Home') {
      event.preventDefault();
      items[0].focus();
    } else if (event.key === 'End') {
      event.preventDefault();
      items[items.length - 1].focus();
    }
  }

  resendFromMenu(user: EthicsDirectoryUser): void {
    this.closeMenu(false);
    void this.resendInvitation(user);
  }

  editRolesFromMenu(user: EthicsDirectoryUser): void {
    this.closeMenu(false);
    void this.openDetail(user.id);
  }

  enableFromMenu(user: EthicsDirectoryUser): void {
    this.closeMenu(false);
    void this.enableFromList(user);
  }

  disableFromMenu(user: EthicsDirectoryUser): void {
    this.closeMenu(false);
    this.selected.set(user);
    this.confirmRequired.set(false);
    this.askDisable();
  }

  deleteFromMenu(user: EthicsDirectoryUser): void {
    const facts = this.menuRemoval();
    if (!facts?.deletable) {
      return;
    }
    this.selected.set(user);
    this.removal.set(facts);
    this.closeMenu(false);
    this.deleteOpen.set(true);
  }

  async resendInvitation(user: EthicsDirectoryUser): Promise<void> {
    await this.deliverInvitation(user.email, 'users.sendingInvitation');
  }

  @HostListener('document:pointerdown', ['$event'])
  closeMenuFromPointer(event: PointerEvent): void {
    if (!this.menuUserId()) {
      return;
    }
    const target = event.target as Node | null;
    const menu = document.getElementById(`user-menu-${this.menuUserId()}`);
    if (target && (menu?.contains(target) || this.menuTrigger?.contains(target))) {
      return;
    }
    this.closeMenu(false);
  }

  @HostListener('document:keydown', ['$event'])
  closeMenuFromEscape(event: KeyboardEvent): void {
    if (event.key === 'Escape' && this.menuUserId() && !this.deleteOpen() && !this.disableOpen()) {
      event.preventDefault();
      this.closeMenu(true);
    }
  }

  @HostListener('window:resize')
  closeMenuOnResize(): void {
    if (this.menuUserId()) {
      this.closeMenu(false);
    }
  }

  async openDetail(id: string): Promise<void> {
    this.closeMenu(false);
    this.message.set(null);
    this.confirmDisable.set(false);
    this.confirmRequired.set(false);
    this.mode.set('detail');
    this.loading.set(true);
    this.loadError.set(null);
    try {
      const [user, removal] = await Promise.all([
        this.api.get(id),
        this.api.removal(id).catch(() => null)
      ]);
      this.removal.set(removal);
      this.applyDetail(user);
    } catch (error) {
      this.selected.set(null);
      this.loadError.set(this.failureKey(error));
    } finally {
      this.loading.set(false);
    }
  }

  accountStatusKey(status: string | null | undefined): string {
    if (status === 'ACTIVE') {
      return 'users.active';
    }
    if (status === 'PENDING_ACTIVATION') {
      return 'users.invitationPending';
    }
    if (status === 'DISABLED') {
      return 'users.inactive';
    }
    return 'users.inactive';
  }

  async searchInstitutional(): Promise<void> {
    this.message.set(null);
    this.lookedUp.set(null);
    this.resolution.set(null);
    this.resolving.set(true);
    try {
      const resolved = await this.api.resolveInstitutional(this.email());
      this.resolution.set(resolved);
      this.lookedUp.set(resolved.user ?? null);
    } catch (error) {
      this.message.set(this.failureKey(error));
    } finally {
      this.resolving.set(false);
    }
  }

  async sendInvitation(): Promise<void> {
    await this.deliverInvitation(this.email(), 'users.creatingUser');
  }

  messageRole(): 'alert' | 'status' {
    const key = this.message();
    return key != null && key === this.inviteErrorKey() ? 'alert' : 'status';
  }

  private async deliverInvitation(email: string, loadingKey: string): Promise<void> {
    if (!this.beginInvite(loadingKey)) {
      return;
    }
    this.message.set(null);
    this.inviteErrorKey.set(null);
    try {
      const resolved = await this.api.inviteInstitutional(email);
      this.resolution.set(resolved);
      const key = resolved.emailSent ? 'users.invitationSent' : 'users.invitationMailFailed';
      this.message.set(key);
      this.inviteRun.succeed(this.i18n.translate(key));
    } catch (error) {
      const key = this.failureKey(error);
      this.message.set(key);
      this.inviteErrorKey.set(key);
      this.inviteRun.fail(this.i18n.translate(key));
    }
    this.publishInvite();
  }

  private beginInvite(loadingKey: string): boolean {
    const started = this.inviteRun.start({
      loadingMessage: this.i18n.translate(loadingKey),
      detailMessage: this.i18n.translate('users.inviteWorkingDetail'),
      longRunningMessage: this.i18n.translate('feedback.stillWorking'),
      blocking: true
    });
    if (!started) {
      return false;
    }
    this.publishInvite();
    this.stopInviteWatch?.();
    this.stopInviteWatch = watchOperation(this.inviteRun, () => this.publishInvite());
    return true;
  }

  private publishInvite(): void {
    this.inviteView.set(this.inviteRun.view());
  }

  async createInstitutional(): Promise<void> {
    const found = this.lookedUp();
    if (!found) {
      this.message.set('users.accountMissing');
      return;
    }
    await this.enable(found);
  }

  async createExternal(): Promise<void> {
    this.message.set(null);
    if (!this.email().includes('@')) {
      this.message.set('users.invalidEmail');
      return;
    }
    if (isAllowedInstitutionalEmail(this.email())) {
      this.message.set('users.useInstitutionalFlow');
      return;
    }
    const found = await this.findExact(this.email());
    if (!found) {
      this.message.set('users.accountMissing');
      return;
    }
    if (found.personKind === 'INSTITUTIONAL') {
      this.message.set('users.useInstitutionalFlow');
      return;
    }
    await this.enable(found);
  }

  async saveExternal(): Promise<void> {
    const current = this.selected();
    if (!current) {
      return;
    }
    try {
      const user = await this.api.updateLocal(current.id, {
        givenName: this.givenName(),
        familyName: this.familyName()
      });
      this.applyDetail(user);
      this.message.set('users.updated');
    } catch (error) {
      this.message.set(this.failureKey(error));
    }
  }

  async enableFromList(user: EthicsDirectoryUser): Promise<void> {
    await this.enable(user);
  }

  async enableSelected(): Promise<void> {
    const current = this.selected();
    if (!current) {
      return;
    }
    await this.enable(current);
  }

  askDisable(): void {
    if (this.disableNeedsConfirm()) {
      this.disableOpen.set(true);
      return;
    }
    void this.disable(false);
  }

  async disable(confirmed = false): Promise<void> {
    const current = this.selected();
    if (!current) {
      return;
    }
    try {
      const user = await this.api.setAccess(current.id, false, confirmed);
      this.confirmDisable.set(false);
      this.confirmRequired.set(false);
      this.disableOpen.set(false);
      this.applyDetail(user);
      this.removal.set(await this.api.removal(current.id).catch(() => null));
      await this.refreshOwnContext(current.id);
      this.message.set('users.disabled');
    } catch (error) {
      if (error instanceof EthicsDirectoryError && error.code === 'ACTIVE_REVIEW') {
        this.confirmRequired.set(true);
        this.disableOpen.set(true);
      }
      this.message.set(this.failureKey(error));
    }
  }

  askDelete(): void {
    if (!this.removal()?.deletable) {
      return;
    }
    this.deleteOpen.set(true);
  }

  async confirmDelete(): Promise<void> {
    const current = this.selected();
    if (!current || this.removing()) {
      return;
    }
    this.removing.set(true);
    try {
      await this.api.remove(current.id);
      this.deleteOpen.set(false);
      this.removal.set(null);
      this.message.set('users.removed');
      this.mode.set('list');
      await this.load();
    } catch (error) {
      this.deleteOpen.set(false);
      if (error instanceof EthicsDirectoryError && error.code === 'ETHICS_USER_HAS_HISTORY') {
        this.removal.set(await this.api.removal(current.id).catch(() => this.removal()));
      }
      this.message.set(this.failureKey(error));
    } finally {
      this.removing.set(false);
    }
  }

  historyLines(): string[] {
    const removal = this.removal();
    if (!removal) {
      return [];
    }
    const lines: string[] = [];
    if (removal.snapshots > 0) {
      lines.push(this.i18n.translate('users.historySnapshots', { count: removal.snapshots }));
    }
    if (removal.reviewAssignments > 0) {
      lines.push(this.i18n.translate('users.historyReviews', { count: removal.reviewAssignments }));
    }
    if (removal.recordedActs > 0) {
      lines.push(this.i18n.translate('users.historyActs', { count: removal.recordedActs }));
    }
    if (removal.committeeAssignments > 0) {
      lines.push(this.i18n.translate('users.historyCommittees', { count: removal.committeeAssignments }));
    }
    return lines;
  }

  historyOnlyKey(): string | null {
    const removal = this.removal();
    if (!removal || removal.deletable) {
      return null;
    }
    const kinds = [
      removal.snapshots > 0,
      removal.reviewAssignments > 0,
      removal.recordedActs > 0,
      removal.committeeAssignments > 0
    ].filter(Boolean).length;
    if (kinds !== 1) {
      return null;
    }
    if (removal.reviewAssignments > 0) {
      return 'users.historyReviewsOnly';
    }
    if (removal.recordedActs > 0 || removal.snapshots > 0) {
      return 'users.historyProjectsOnly';
    }
    return null;
  }

  async grantRole(): Promise<void> {
    const current = this.selected();
    const role = this.structuralRole();
    if (!current || !role) {
      return;
    }
    try {
      const user = await this.api.grantRole(current.id, role);
      this.applyDetail(user);
      this.removal.set(await this.api.removal(current.id).catch(() => null));
      const granted = (user.roles ?? []).some((item) =>
        item.id === role && !item.contextual && item.scopeType === 'GLOBAL' && item.scopeId === '*'
      );
      if (!granted) {
        this.message.set('users.roleNotAssigned');
        return;
      }
      this.structuralRole.set('');
      await this.refreshOwnContext(current.id);
      this.message.set('users.roleGranted');
    } catch (error) {
      this.message.set(this.failureKey(error));
    }
  }

  roleFeedback(): string | null {
    const key = this.message();
    if (key === 'users.roleGranted' || key === 'users.roleNotAssigned' || key === 'users.roleAlreadyAssigned') {
      return key;
    }
    return null;
  }

  structuralRoles(): EthicsDirectoryRole[] {
    return (this.selected()?.roles ?? []).filter((role) => !role.contextual);
  }

  contextualRoles(): EthicsDirectoryRole[] {
    return (this.selected()?.roles ?? []).filter((role) => role.contextual);
  }

  chipName(role: EthicsDirectoryRole): string {
    const label = this.i18n.translate(role.idLabel);
    return role.scopeLabel ? `${label} · ${role.scopeLabel}` : label;
  }

  askRemove(role: EthicsDirectoryRole): void {
    if (!this.canManage() || !role.removable || !role.assignmentId) {
      return;
    }
    this.message.set(null);
    this.pendingRemove.set(role);
  }

  cancelRemove(): void {
    if (this.revoking()) {
      return;
    }
    this.pendingRemove.set(null);
  }

  selfRevoke(role: EthicsDirectoryRole): boolean {
    return role.id === 'ADMINISTRATOR' && this.auth.currentUser()?.id === this.selected()?.id;
  }

  async confirmRemove(): Promise<void> {
    const current = this.selected();
    const role = this.pendingRemove();
    if (!current || !role?.assignmentId || this.revoking()) {
      return;
    }
    this.revoking.set(true);
    this.message.set(null);
    try {
      const user = await this.api.revokeRole(current.id, role.assignmentId);
      this.applyDetail(user);
      this.removal.set(await this.api.removal(current.id).catch(() => null));
      this.pendingRemove.set(null);
      await this.refreshOwnContext(current.id);
      this.message.set('users.roleRevoked');
    } catch (error) {
      this.message.set(this.failureKey(error));
      this.pendingRemove.set(null);
    } finally {
      this.revoking.set(false);
    }
  }

  private async refreshOwnContext(targetId: string): Promise<void> {
    const actorId = this.auth.currentUser()?.id;
    if (!actorId || actorId !== targetId) {
      return;
    }
    try {
      const sessionUser = await this.session.refresh();
      if (!sessionUser) {
        await this.router.navigateByUrl('/ingresar');
        return;
      }
      const required = capabilityForUrl(this.router.url);
      if (required && !this.permissions.can(sessionUser, required)) {
        await this.router.navigateByUrl('/');
      }
    } catch {
      await this.router.navigateByUrl('/');
    }
  }

  private async enable(user: EthicsDirectoryUser): Promise<void> {
    try {
      const updated = await this.api.setAccess(user.id, true, false);
      this.applyDetail(updated);
      this.mode.set('detail');
      await this.refreshOwnContext(user.id);
      this.message.set(updated.accountStatus === 'PENDING_ACTIVATION'
        ? 'users.pendingInviteHint'
        : user.ethicsAccess ? 'users.alreadyEthics' : 'users.enabled');
    } catch (error) {
      this.message.set(this.failureKey(error));
    }
  }

  private async findExact(email: string): Promise<EthicsDirectoryUser | null> {
    try {
      const page = await this.api.list({
        search: email.trim(),
        type: 'ALL',
        status: 'ALL',
        ethicsAccess: 'ALL',
        page: 1,
        size: 25
      });
      const normalized = email.trim().toLowerCase();
      return page.items.find((item) => item.email.trim().toLowerCase() === normalized) ?? null;
    } catch (error) {
      this.message.set(this.failureKey(error));
      return null;
    }
  }

  private async load(): Promise<void> {
    if (this.mode() !== 'list') {
      return;
    }
    const ticket = ++this.request;
    this.closeMenu(false);
    this.loading.set(true);
    this.loadError.set(null);
    this.rows.set([]);
    try {
      const page = await this.api.list({
        search: this.query(),
        type: this.kind(),
        status: this.status(),
        ethicsAccess: this.access(),
        page: this.page(),
        size: PAGE_SIZE
      });
      if (ticket !== this.request) {
        return;
      }
      this.rows.set(page.items ?? []);
      this.totalPages.set(page.totalPages ?? 0);
      this.totalElements.set(page.totalElements ?? 0);
    } catch (error) {
      if (ticket !== this.request) {
        return;
      }
      this.rows.set([]);
      this.totalPages.set(0);
      this.totalElements.set(0);
      this.loadError.set(this.failureKey(error));
    } finally {
      if (ticket === this.request) {
        this.loading.set(false);
      }
    }
  }

  private applyDetail(user: EthicsDirectoryUser): void {
    this.selected.set(user);
    this.givenName.set(user.givenName);
    this.familyName.set(user.familyName);
    this.email.set(user.email);
    this.loadError.set(null);
  }

  private failureKey(error: unknown): string {
    if (error instanceof EthicsDirectoryError) {
      if (error.code === 'LAST_ADMINISTRATOR') {
        return 'users.lastAdministrator';
      }
      if (error.code === 'ROLE_ALREADY_REVOKED') {
        return 'users.roleAlreadyRevoked';
      }
      if (error.code === 'ROLE_NOT_REMOVABLE') {
        return 'users.roleNotRemovable';
      }
      if (error.code === 'ROLE_ALREADY_ASSIGNED') {
        return 'users.roleAlreadyAssigned';
      }
      if (error.code === 'ROLE_NOT_ASSIGNED') {
        return 'users.roleNotAssigned';
      }
      if (error.code === 'ASSIGNMENT_NOT_FOUND') {
        return 'users.roleAlreadyRevoked';
      }
      if (error.code === 'MANAGE_USERS_REQUIRED' || error.status === 403) {
        return 'users.denied';
      }
      if (error.code === 'ACTIVE_REVIEW') {
        return 'users.activeReviewWarning';
      }
      if (error.code === 'ETHICS_USER_HAS_HISTORY') {
        return 'users.notDeletableHint';
      }
      if (error.code === 'EXTERNAL_ROLE_DENIED') {
        return 'users.externalRoleDenied';
      }
      if (error.code === 'PURE_LOCKED') {
        return 'users.pureLocked';
      }
      if (error.code === 'USER_NOT_FOUND') {
        return 'users.accountMissing';
      }
      if (error.code === 'NOT_INSTITUTIONAL_DOMAIN' || error.code === 'INSTITUTIONAL_EMAIL') {
        return 'users.notInstitutionalDomain';
      }
      if (error.code === 'ACCOUNT_EXISTS') {
        return 'users.openScienceExists';
      }
    }
    return 'users.loadFailed';
  }
}
