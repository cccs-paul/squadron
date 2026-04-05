import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { UserSquadronService } from '../../../core/services/user-squadron.service';
import { UserAgentConfig, AGENT_TYPES } from '../../../core/models/squadron-config.model';

@Component({
  selector: 'sq-squadron-config',
  standalone: true,
  imports: [FormsModule, TranslateModule],
  template: `
    <div class="squadron-config">
      <div class="squadron-config__header">
        <h2 class="squadron-config__title">{{ 'settings.squadronConfig.title' | translate }}</h2>
        <p class="squadron-config__subtitle">
          {{ 'settings.squadronConfig.subtitle' | translate:{ max: maxAgents() } }}
        </p>
      </div>

      @if (loading()) {
        <div class="squadron-config__loading">{{ 'settings.squadronConfig.loading' | translate }}</div>
      } @else {
        @if (saveSuccess()) {
          <div class="squadron-config__message squadron-config__message--success">
            {{ 'settings.squadronConfig.saveSuccess' | translate }}
          </div>
        }
        @if (saveError()) {
          <div class="squadron-config__message squadron-config__message--error">
            {{ saveError() }}
          </div>
        }

        <div class="squadron-config__agents">
          @for (agent of agents(); track agent.id) {
            <div class="squadron-config__agent-card">
              <div class="squadron-config__agent-header">
                <div class="squadron-config__agent-name-row">
                  @if (editingId() === agent.id) {
                    <input
                      class="squadron-config__input squadron-config__input--name"
                      [(ngModel)]="editName"
                      [placeholder]="'settings.squadronConfig.agentName' | translate"
                    />
                  } @else {
                    <span class="squadron-config__agent-name">{{ agent.agentName }}</span>
                  }
                  <span class="squadron-config__agent-type-badge">{{ agent.agentType }}</span>
                </div>
                <div class="squadron-config__agent-actions">
                  @if (editingId() === agent.id) {
                    <button class="squadron-config__btn squadron-config__btn--save" (click)="saveAgent(agent)"
                      [disabled]="saving()">{{ 'settings.squadronConfig.save' | translate }}</button>
                    <button class="squadron-config__btn squadron-config__btn--cancel" (click)="cancelEdit()">{{ 'common.cancel' | translate }}</button>
                  } @else {
                    <button class="squadron-config__btn squadron-config__btn--edit" (click)="startEdit(agent)">{{ 'settings.squadronConfig.edit' | translate }}</button>
                    <button class="squadron-config__btn squadron-config__btn--remove" (click)="removeAgent(agent)"
                      [disabled]="agents().length <= 1">{{ 'settings.squadronConfig.remove' | translate }}</button>
                  }
                </div>
              </div>

              @if (editingId() === agent.id) {
                <div class="squadron-config__agent-details">
                  <div class="squadron-config__field">
                    <label class="squadron-config__label">{{ 'settings.squadronConfig.type' | translate }}</label>
                    <select class="squadron-config__select" [(ngModel)]="editType">
                      @for (t of agentTypes; track t) {
                        <option [value]="t">{{ t }}</option>
                      }
                    </select>
                  </div>
                  <div class="squadron-config__field">
                    <label class="squadron-config__label">{{ 'settings.squadronConfig.provider' | translate }}</label>
                    <input class="squadron-config__input" [(ngModel)]="editProvider" [placeholder]="'settings.squadronConfig.providerPlaceholder' | translate" />
                  </div>
                  <div class="squadron-config__field">
                    <label class="squadron-config__label">{{ 'settings.squadronConfig.model' | translate }}</label>
                    <input class="squadron-config__input" [(ngModel)]="editModel" [placeholder]="'settings.squadronConfig.modelPlaceholder' | translate" />
                  </div>
                  <div class="squadron-config__field-row">
                    <div class="squadron-config__field">
                      <label class="squadron-config__label">{{ 'settings.squadronConfig.maxTokens' | translate }}</label>
                      <input class="squadron-config__input" type="number" [(ngModel)]="editMaxTokens" />
                    </div>
                    <div class="squadron-config__field">
                      <label class="squadron-config__label">{{ 'settings.squadronConfig.temperature' | translate }}</label>
                      <input class="squadron-config__input" type="number" step="0.1" min="0" max="2"
                        [(ngModel)]="editTemperature" />
                    </div>
                  </div>
                  <div class="squadron-config__field">
                    <label class="squadron-config__label">{{ 'settings.squadronConfig.enabled' | translate }}</label>
                    <input type="checkbox" [(ngModel)]="editEnabled" />
                  </div>
                </div>
              }
            </div>
          }
        </div>

        <div class="squadron-config__footer">
          <button class="squadron-config__btn squadron-config__btn--add"
            (click)="addAgent()" [disabled]="agents().length >= maxAgents()">
            {{ 'settings.squadronConfig.addAgent' | translate }}
          </button>
          <button class="squadron-config__btn squadron-config__btn--reset" (click)="resetToDefaults()">
            {{ 'settings.squadronConfig.resetDefaults' | translate }}
          </button>
        </div>
      }
    </div>
  `,
  styles: [`
    .squadron-config { max-width: 800px; margin: 0 auto; padding: 24px; }
    .squadron-config__header { margin-bottom: 24px; }
    .squadron-config__title { font-size: 1.5rem; font-weight: 600; margin: 0 0 8px; }
    .squadron-config__subtitle { color: #6b7280; margin: 0; font-size: 0.875rem; }
    .squadron-config__loading { text-align: center; padding: 40px; color: #6b7280; }
    .squadron-config__message { padding: 12px 16px; border-radius: 6px; margin-bottom: 16px; font-size: 0.875rem; }
    .squadron-config__message--success { background: #d1fae5; color: #065f46; }
    .squadron-config__message--error { background: #fee2e2; color: #991b1b; }
    .squadron-config__agents { display: flex; flex-direction: column; gap: 12px; }
    .squadron-config__agent-card { border: 1px solid #e5e7eb; border-radius: 8px; padding: 16px; background: #fff; }
    .squadron-config__agent-header { display: flex; justify-content: space-between; align-items: center; }
    .squadron-config__agent-name-row { display: flex; align-items: center; gap: 8px; }
    .squadron-config__agent-name { font-weight: 600; font-size: 1rem; }
    .squadron-config__agent-type-badge {
      background: #e0e7ff; color: #3730a3; padding: 2px 8px; border-radius: 4px;
      font-size: 0.75rem; font-weight: 500; text-transform: uppercase;
    }
    .squadron-config__agent-actions { display: flex; gap: 8px; }
    .squadron-config__agent-details { margin-top: 12px; display: flex; flex-direction: column; gap: 12px; }
    .squadron-config__field { display: flex; flex-direction: column; gap: 4px; }
    .squadron-config__field-row { display: flex; gap: 16px; }
    .squadron-config__field-row .squadron-config__field { flex: 1; }
    .squadron-config__label { font-size: 0.75rem; font-weight: 500; color: #374151; }
    .squadron-config__input, .squadron-config__select {
      padding: 6px 10px; border: 1px solid #d1d5db; border-radius: 4px; font-size: 0.875rem;
    }
    .squadron-config__input--name { font-weight: 600; }
    .squadron-config__btn {
      padding: 6px 14px; border: 1px solid #d1d5db; border-radius: 4px; background: #fff;
      cursor: pointer; font-size: 0.8125rem;
    }
    .squadron-config__btn:disabled { opacity: 0.5; cursor: not-allowed; }
    .squadron-config__btn--save { background: #2563eb; color: #fff; border-color: #2563eb; }
    .squadron-config__btn--cancel { background: #f3f4f6; }
    .squadron-config__btn--edit { background: #f9fafb; }
    .squadron-config__btn--remove { color: #dc2626; border-color: #fecaca; }
    .squadron-config__btn--add { background: #2563eb; color: #fff; border-color: #2563eb; }
    .squadron-config__btn--reset { background: #f3f4f6; }
    .squadron-config__footer { margin-top: 20px; display: flex; gap: 12px; }
  `],
})
export class SquadronConfigComponent implements OnInit {
  private squadronService = inject(UserSquadronService);

  loading = signal(true);
  saving = signal(false);
  saveSuccess = signal(false);
  saveError = signal<string | null>(null);
  agents = signal<UserAgentConfig[]>([]);
  maxAgents = signal(8);
  editingId = signal<string | null>(null);

  readonly agentTypes = [...AGENT_TYPES];

  // Edit form fields
  editName = '';
  editType = 'CODING';
  editProvider = '';
  editModel = '';
  editMaxTokens: number | null = null;
  editTemperature: number | null = null;
  editEnabled = true;

  ngOnInit(): void {
    this.loadSquadron();
    this.loadLimits();
  }

  loadSquadron(): void {
    this.loading.set(true);
    this.squadronService.getMySquadron().subscribe({
      next: (agents) => {
        this.agents.set(agents);
        this.loading.set(false);
      },
      error: () => {
        this.agents.set([]);
        this.loading.set(false);
      },
    });
  }

  loadLimits(): void {
    this.squadronService.getLimits().subscribe({
      next: (limits) => this.maxAgents.set(limits.maxAgentsPerUser),
      error: () => { /* keep default */ },
    });
  }

  startEdit(agent: UserAgentConfig): void {
    this.editingId.set(agent.id ?? null);
    this.editName = agent.agentName;
    this.editType = agent.agentType;
    this.editProvider = agent.provider ?? '';
    this.editModel = agent.model ?? '';
    this.editMaxTokens = agent.maxTokens ?? null;
    this.editTemperature = agent.temperature ?? null;
    this.editEnabled = agent.enabled;
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveAgent(agent: UserAgentConfig): void {
    if (!agent.id) return;
    this.saving.set(true);
    this.saveSuccess.set(false);
    this.saveError.set(null);

    const update: Partial<UserAgentConfig> = {
      agentName: this.editName,
      agentType: this.editType,
      displayOrder: agent.displayOrder,
      provider: this.editProvider || undefined,
      model: this.editModel || undefined,
      maxTokens: this.editMaxTokens ?? undefined,
      temperature: this.editTemperature ?? undefined,
      enabled: this.editEnabled,
    };

    this.squadronService.updateAgent(agent.id, update).subscribe({
      next: (updated) => {
        this.agents.update((list) =>
          list.map((a) => (a.id === updated.id ? updated : a)),
        );
        this.editingId.set(null);
        this.saving.set(false);
        this.saveSuccess.set(true);
        setTimeout(() => this.saveSuccess.set(false), 3000);
      },
      error: () => {
        this.saving.set(false);
        this.saveError.set('Failed to save agent. Please try again.');
        setTimeout(() => this.saveError.set(null), 5000);
      },
    });
  }

  addAgent(): void {
    const order = this.agents().length;
    const newAgent: Partial<UserAgentConfig> = {
      agentName: `Agent ${order + 1}`,
      agentType: 'CODING',
      displayOrder: order,
      enabled: true,
    };

    this.squadronService.addAgent(newAgent).subscribe({
      next: (agent) => {
        this.agents.update((list) => [...list, agent]);
        this.startEdit(agent);
      },
      error: () => {
        this.saveError.set('Failed to add agent.');
        setTimeout(() => this.saveError.set(null), 5000);
      },
    });
  }

  removeAgent(agent: UserAgentConfig): void {
    if (!agent.id || this.agents().length <= 1) return;

    this.squadronService.removeAgent(agent.id).subscribe({
      next: () => {
        this.agents.update((list) => list.filter((a) => a.id !== agent.id));
        if (this.editingId() === agent.id) {
          this.editingId.set(null);
        }
      },
      error: () => {
        this.saveError.set('Failed to remove agent.');
        setTimeout(() => this.saveError.set(null), 5000);
      },
    });
  }

  resetToDefaults(): void {
    this.squadronService.resetToDefaults().subscribe({
      next: (agents) => {
        this.agents.set(agents);
        this.editingId.set(null);
        this.saveSuccess.set(true);
        setTimeout(() => this.saveSuccess.set(false), 3000);
      },
      error: () => {
        this.saveError.set('Failed to reset squadron.');
        setTimeout(() => this.saveError.set(null), 5000);
      },
    });
  }
}
