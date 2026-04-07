import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { UserSquadronService } from '../../../core/services/user-squadron.service';
import {
  UserAgentConfig,
  HostingType,
  PROVIDER_CATALOG,
  ProviderCatalogEntry,
  ModelCatalogEntry,
  generateAgentDescription,
} from '../../../core/models/squadron-config.model';

@Component({
  selector: 'sq-squadron-config',
  standalone: true,
  imports: [FormsModule, TranslateModule],
  template: `
    <div class="squadron-config">
      <div class="squadron-config__header">
        <h2 class="squadron-config__title">{{ 'settings.squadronConfig.title' | translate }}</h2>
        <p class="squadron-config__subtitle">
          {{ 'settings.squadronConfig.subtitle' | translate:{ maxAgents: maxAgents() } }}
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
                <div class="squadron-config__agent-info">
                  @if (editingId() === agent.id) {
                    <input
                      class="squadron-config__input squadron-config__input--name"
                      [(ngModel)]="editName"
                      [placeholder]="'settings.squadronConfig.agentName' | translate"
                    />
                  } @else {
                    <span class="squadron-config__agent-name">{{ agent.agentName }}</span>
                    @if (agent.description) {
                      <span class="squadron-config__agent-description">{{ agent.description }}</span>
                    }
                    @if (agent.hostingType) {
                      <span class="squadron-config__hosting-badge"
                            [class.squadron-config__hosting-badge--platform]="agent.hostingType === 'PLATFORM'"
                            [class.squadron-config__hosting-badge--self-hosted]="agent.hostingType === 'SELF_HOSTED'"
                            [class.squadron-config__hosting-badge--custom]="agent.hostingType === 'CUSTOM'">
                        {{ getHostingLabel(agent.hostingType) }}
                      </span>
                    }
                  }
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
                  <!-- Hosting Type -->
                  <div class="squadron-config__field">
                    <label class="squadron-config__label">{{ 'settings.squadronConfig.hostingType' | translate }}</label>
                    <select class="squadron-config__select" [(ngModel)]="editHostingType" (ngModelChange)="onHostingTypeChange()">
                      <option value="PLATFORM">{{ 'settings.squadronConfig.hostingTypes.platform' | translate }}</option>
                      <option value="SELF_HOSTED">{{ 'settings.squadronConfig.hostingTypes.selfHosted' | translate }}</option>
                      <option value="CUSTOM">{{ 'settings.squadronConfig.hostingTypes.custom' | translate }}</option>
                    </select>
                  </div>

                  <!-- Provider -->
                  <div class="squadron-config__field">
                    <label class="squadron-config__label">{{ 'settings.squadronConfig.provider' | translate }}</label>
                    <select class="squadron-config__select" [(ngModel)]="editProvider" (ngModelChange)="onProviderChange()">
                      <option value="">{{ 'settings.squadronConfig.selectProvider' | translate }}</option>
                      @for (p of filteredProviders(); track p.id) {
                        <option [value]="p.id">{{ p.label }}</option>
                      }
                      @if (editHostingType === 'CUSTOM') {
                        <option value="__custom__">{{ 'settings.squadronConfig.customProvider' | translate }}</option>
                      }
                    </select>
                  </div>

                  <!-- Model -->
                  <div class="squadron-config__field">
                    <label class="squadron-config__label">{{ 'settings.squadronConfig.model' | translate }}</label>
                    @if (availableModels().length > 0) {
                      <select class="squadron-config__select" [(ngModel)]="editModel">
                        <option value="">{{ 'settings.squadronConfig.selectModel' | translate }}</option>
                        @for (m of availableModels(); track m.id) {
                          <option [value]="m.id">{{ m.label }}</option>
                        }
                      </select>
                    } @else {
                      <input class="squadron-config__input" [(ngModel)]="editModel"
                             [placeholder]="'settings.squadronConfig.modelPlaceholder' | translate" />
                    }
                  </div>

                  <!-- Base URL (for self-hosted / custom) -->
                  @if (editHostingType === 'SELF_HOSTED' || editHostingType === 'CUSTOM') {
                    <div class="squadron-config__field">
                      <label class="squadron-config__label">{{ 'settings.squadronConfig.baseUrl' | translate }}</label>
                      <input class="squadron-config__input" [(ngModel)]="editBaseUrl"
                             [placeholder]="editHostingType === 'SELF_HOSTED'
                               ? 'http://localhost:11434'
                               : 'https://api.example.com/v1'" />
                    </div>
                  }

                  <!-- API Key (for custom endpoints) -->
                  @if (editHostingType === 'CUSTOM') {
                    <div class="squadron-config__field">
                      <label class="squadron-config__label">{{ 'settings.squadronConfig.apiKey' | translate }}</label>
                      <input class="squadron-config__input" type="password" [(ngModel)]="editApiKeyRef"
                             [placeholder]="'settings.squadronConfig.apiKeyPlaceholder' | translate" />
                    </div>
                  }

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

                  <!-- Description (auto-generated or manual) -->
                  <div class="squadron-config__field">
                    <label class="squadron-config__label">{{ 'settings.squadronConfig.description' | translate }}</label>
                    <input class="squadron-config__input" [(ngModel)]="editDescription"
                           [placeholder]="autoDescription()" />
                    <span class="squadron-config__hint">{{ 'settings.squadronConfig.descriptionHint' | translate }}</span>
                  </div>

                  <div class="squadron-config__field">
                    <label class="squadron-config__label">{{ 'settings.squadronConfig.systemPrompt' | translate }}</label>
                    <textarea class="squadron-config__textarea" [(ngModel)]="editSystemPrompt"
                              rows="3" [placeholder]="'settings.squadronConfig.systemPromptPlaceholder' | translate"></textarea>
                  </div>

                  <div class="squadron-config__field">
                    <label class="squadron-config__label">
                      <input type="checkbox" [(ngModel)]="editEnabled" />
                      {{ 'settings.squadronConfig.enabled' | translate }}
                    </label>
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
    .squadron-config__agent-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
    .squadron-config__agent-info { display: flex; flex-direction: column; gap: 4px; min-width: 0; flex: 1; }
    .squadron-config__agent-name { font-weight: 600; font-size: 1rem; }
    .squadron-config__agent-description { font-size: 0.8125rem; color: #6b7280; }
    .squadron-config__hosting-badge {
      display: inline-block; width: fit-content;
      font-size: 0.6875rem; font-weight: 600; padding: 2px 8px; border-radius: 9999px;
      text-transform: uppercase; letter-spacing: 0.025em;
    }
    .squadron-config__hosting-badge--platform { background: #ede9fe; color: #6d28d9; }
    .squadron-config__hosting-badge--self-hosted { background: #dbeafe; color: #1d4ed8; }
    .squadron-config__hosting-badge--custom { background: #fef3c7; color: #b45309; }
    .squadron-config__agent-actions { display: flex; gap: 8px; flex-shrink: 0; }
    .squadron-config__agent-details { margin-top: 16px; display: flex; flex-direction: column; gap: 12px;
      border-top: 1px solid #f3f4f6; padding-top: 16px; }
    .squadron-config__field { display: flex; flex-direction: column; gap: 4px; }
    .squadron-config__field-row { display: flex; gap: 16px; }
    .squadron-config__field-row .squadron-config__field { flex: 1; }
    .squadron-config__label { font-size: 0.75rem; font-weight: 500; color: #374151; }
    .squadron-config__hint { font-size: 0.6875rem; color: #9ca3af; }
    .squadron-config__input, .squadron-config__select {
      padding: 6px 10px; border: 1px solid #d1d5db; border-radius: 4px; font-size: 0.875rem;
    }
    .squadron-config__input--name { font-weight: 600; }
    .squadron-config__textarea {
      padding: 6px 10px; border: 1px solid #d1d5db; border-radius: 4px; font-size: 0.875rem;
      font-family: inherit; resize: vertical; min-height: 60px;
    }
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

  // Edit form fields
  editName = '';
  editProvider = '';
  editModel = '';
  editHostingType: HostingType = 'PLATFORM';
  editBaseUrl = '';
  editApiKeyRef = '';
  editDescription = '';
  editSystemPrompt = '';
  editMaxTokens: number | null = null;
  editTemperature: number | null = null;
  editEnabled = true;

  /** Providers filtered by the selected hosting type. */
  filteredProviders = signal<ProviderCatalogEntry[]>([]);

  /** Models available for the currently selected provider. */
  availableModels = signal<ModelCatalogEntry[]>([]);

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
    this.editProvider = agent.provider ?? '';
    this.editModel = agent.model ?? '';
    this.editHostingType = agent.hostingType ?? 'PLATFORM';
    this.editBaseUrl = agent.baseUrl ?? '';
    this.editApiKeyRef = agent.apiKeyRef ?? '';
    this.editDescription = agent.description ?? '';
    this.editSystemPrompt = agent.systemPromptOverride ?? '';
    this.editMaxTokens = agent.maxTokens ?? null;
    this.editTemperature = agent.temperature ?? null;
    this.editEnabled = agent.enabled;
    this.updateFilteredProviders();
    this.updateAvailableModels();
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  onHostingTypeChange(): void {
    this.editProvider = '';
    this.editModel = '';
    this.editBaseUrl = '';
    this.editApiKeyRef = '';
    this.updateFilteredProviders();
    this.updateAvailableModels();
  }

  onProviderChange(): void {
    this.editModel = '';
    this.updateAvailableModels();
  }

  /** Returns a auto-generated description based on current edit form state. */
  autoDescription(): string {
    return generateAgentDescription(this.editProvider, this.editModel, this.editHostingType);
  }

  /** Translates a hosting type to a display label. */
  getHostingLabel(hostingType: string): string {
    switch (hostingType) {
      case 'PLATFORM': return 'Cloud';
      case 'SELF_HOSTED': return 'Local';
      case 'CUSTOM': return 'Custom';
      default: return hostingType;
    }
  }

  saveAgent(agent: UserAgentConfig): void {
    if (!agent.id) return;
    this.saving.set(true);
    this.saveSuccess.set(false);
    this.saveError.set(null);

    const description = this.editDescription || this.autoDescription();

    const update: Partial<UserAgentConfig> = {
      agentName: this.editName,
      agentType: agent.agentType,
      displayOrder: agent.displayOrder,
      provider: this.editProvider || undefined,
      model: this.editModel || undefined,
      hostingType: this.editHostingType,
      baseUrl: this.editBaseUrl || undefined,
      apiKeyRef: this.editApiKeyRef || undefined,
      description,
      systemPromptOverride: this.editSystemPrompt || undefined,
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
      agentType: 'GENERAL',
      displayOrder: order,
      hostingType: 'PLATFORM',
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

  private updateFilteredProviders(): void {
    if (this.editHostingType === 'CUSTOM') {
      // Custom shows all providers plus a custom option
      this.filteredProviders.set(PROVIDER_CATALOG);
    } else {
      this.filteredProviders.set(
        PROVIDER_CATALOG.filter(p => p.hostingType === this.editHostingType),
      );
    }
  }

  private updateAvailableModels(): void {
    const provider = PROVIDER_CATALOG.find(p => p.id === this.editProvider);
    this.availableModels.set(provider?.models ?? []);
  }
}
