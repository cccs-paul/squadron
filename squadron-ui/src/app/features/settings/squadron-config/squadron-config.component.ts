import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { UserSquadronService } from '../../../core/services/user-squadron.service';
import { AgentTestService } from '../../../core/services/agent-test.service';
import {
  UserAgentConfig,
  HostingType,
  PROVIDER_CATALOG,
  ProviderCatalogEntry,
  ModelCatalogEntry,
  generateAgentDescription,
} from '../../../core/models/squadron-config.model';
import {
  TestMode,
  TEST_MODE_LABELS,
  AgentTestResult,
  TestLogEntry,
} from '../../../core/models/agent-test.model';

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
                    <button class="squadron-config__btn squadron-config__btn--test" (click)="openTestMenu(agent)"
                      [disabled]="testingAgentId() === agent.id">
                      {{ 'settings.squadronConfig.test' | translate }}
                    </button>
                    <button class="squadron-config__btn squadron-config__btn--edit" (click)="startEdit(agent)">{{ 'settings.squadronConfig.edit' | translate }}</button>
                    <button class="squadron-config__btn squadron-config__btn--remove" (click)="removeAgent(agent)"
                      [disabled]="agents().length <= 1">{{ 'settings.squadronConfig.remove' | translate }}</button>
                  }
                </div>
              </div>

              <!-- Test mode selector (shows when test menu is open for this agent) -->
              @if (testMenuAgentId() === agent.id && !testingAgentId()) {
                <div class="squadron-config__test-menu">
                  <span class="squadron-config__test-menu-label">{{ 'settings.squadronConfig.selectTestMode' | translate }}</span>
                  <div class="squadron-config__test-menu-buttons">
                    <button class="squadron-config__btn squadron-config__btn--test-mode"
                      (click)="runTest(agent, 'PLANNING')">
                      {{ 'settings.squadronConfig.testModes.planning' | translate }}
                    </button>
                    <button class="squadron-config__btn squadron-config__btn--test-mode"
                      (click)="runTest(agent, 'CODE_GENERATION')">
                      {{ 'settings.squadronConfig.testModes.codeGeneration' | translate }}
                    </button>
                    <button class="squadron-config__btn squadron-config__btn--test-mode"
                      (click)="runTest(agent, 'CODE_REVIEW')">
                      {{ 'settings.squadronConfig.testModes.codeReview' | translate }}
                    </button>
                    <button class="squadron-config__btn squadron-config__btn--cancel"
                      (click)="closeTestMenu()">
                      {{ 'common.cancel' | translate }}
                    </button>
                  </div>
                </div>
              }

              <!-- Expandable test result panel -->
              @if (testResultForAgent(agent.id!); as result) {
                <div class="squadron-config__test-panel">
                  <div class="squadron-config__test-panel-header" (click)="toggleTestExpanded(agent.id!)">
                    <div class="squadron-config__test-status"
                         [class.squadron-config__test-status--running]="result.status === 'RUNNING'"
                         [class.squadron-config__test-status--success]="result.status === 'SUCCESS'"
                         [class.squadron-config__test-status--failure]="result.status === 'FAILURE' || result.status === 'ERROR'">
                      @if (result.status === 'RUNNING') {
                        <span class="squadron-config__spinner"></span>
                      }
                      {{ result.status }}
                    </div>
                    <span class="squadron-config__test-summary">{{ result.summary }}</span>
                    @if (result.durationMs) {
                      <span class="squadron-config__test-duration">{{ result.durationMs }}ms</span>
                    }
                    <span class="squadron-config__test-expand-icon">
                      {{ isTestExpanded(agent.id!) ? '&#9660;' : '&#9654;' }}
                    </span>
                  </div>

                  @if (isTestExpanded(agent.id!)) {
                    <div class="squadron-config__test-panel-body">
                      <!-- Verbose log entries -->
                      <div class="squadron-config__test-log">
                        @for (entry of result.logEntries; track $index) {
                          <div class="squadron-config__test-log-entry"
                               [class.squadron-config__test-log-entry--success]="entry.level === 'SUCCESS'"
                               [class.squadron-config__test-log-entry--warning]="entry.level === 'WARNING'"
                               [class.squadron-config__test-log-entry--error]="entry.level === 'ERROR'">
                            <span class="squadron-config__test-log-phase">{{ entry.phase }}</span>
                            <span class="squadron-config__test-log-message">{{ entry.message }}</span>
                          </div>
                        }
                      </div>

                      <!-- Agent output (collapsed by default, expandable) -->
                      @if (result.agentOutput) {
                        <div class="squadron-config__test-output">
                          <div class="squadron-config__test-output-header" (click)="toggleOutputExpanded(agent.id!)">
                            <span>{{ 'settings.squadronConfig.agentOutput' | translate }}</span>
                            <span>{{ isOutputExpanded(agent.id!) ? '&#9660;' : '&#9654;' }}</span>
                          </div>
                          @if (isOutputExpanded(agent.id!)) {
                            <pre class="squadron-config__test-output-content">{{ result.agentOutput }}</pre>
                          }
                        </div>
                      }

                      <button class="squadron-config__btn squadron-config__btn--dismiss"
                        (click)="dismissTestResult(agent.id!)">
                        {{ 'settings.squadronConfig.dismissResult' | translate }}
                      </button>
                    </div>
                  }
                </div>
              }

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

                  <!-- Base URL (for self-hosted / custom / providers with default base URL) -->
                  @if (editHostingType === 'SELF_HOSTED' || editHostingType === 'CUSTOM' || selectedProviderEntry()?.defaultBaseUrl) {
                    <div class="squadron-config__field">
                      <label class="squadron-config__label">{{ 'settings.squadronConfig.baseUrl' | translate }}</label>
                      <input class="squadron-config__input" [(ngModel)]="editBaseUrl"
                             [placeholder]="selectedProviderEntry()?.defaultBaseUrl
                               ?? (editHostingType === 'SELF_HOSTED'
                                 ? 'http://localhost:11434'
                                 : 'https://api.example.com/v1')" />
                      @if (selectedProviderEntry()?.defaultBaseUrl) {
                        <span class="squadron-config__hint">{{ 'settings.squadronConfig.baseUrlHint' | translate:{ url: selectedProviderEntry()!.defaultBaseUrl } }}</span>
                      }
                    </div>
                  }

                  <!-- API Key (for custom endpoints or providers that require it) -->
                  @if (editHostingType === 'CUSTOM' || selectedProviderEntry()?.requiresApiKey) {
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
    .squadron-config__btn--test { background: #f0fdf4; color: #15803d; border-color: #bbf7d0; font-weight: 500; }
    .squadron-config__btn--test:hover { background: #dcfce7; }
    .squadron-config__btn--test-mode { background: #f0fdf4; color: #15803d; border-color: #bbf7d0; }
    .squadron-config__btn--test-mode:hover { background: #dcfce7; }
    .squadron-config__btn--dismiss { background: #f3f4f6; font-size: 0.75rem; margin-top: 8px; }
    .squadron-config__footer { margin-top: 20px; display: flex; gap: 12px; }

    /* Test menu */
    .squadron-config__test-menu {
      margin-top: 12px; padding: 12px; background: #f0fdf4; border-radius: 6px;
      border: 1px solid #bbf7d0;
    }
    .squadron-config__test-menu-label { font-size: 0.75rem; font-weight: 500; color: #374151; display: block; margin-bottom: 8px; }
    .squadron-config__test-menu-buttons { display: flex; gap: 8px; flex-wrap: wrap; }

    /* Test result panel */
    .squadron-config__test-panel {
      margin-top: 12px; border: 1px solid #e5e7eb; border-radius: 6px; overflow: hidden;
    }
    .squadron-config__test-panel-header {
      display: flex; align-items: center; gap: 12px; padding: 10px 14px;
      background: #f9fafb; cursor: pointer; user-select: none;
    }
    .squadron-config__test-panel-header:hover { background: #f3f4f6; }
    .squadron-config__test-status {
      font-size: 0.6875rem; font-weight: 700; padding: 2px 8px; border-radius: 4px;
      text-transform: uppercase; letter-spacing: 0.05em; display: flex; align-items: center; gap: 6px;
    }
    .squadron-config__test-status--running { background: #dbeafe; color: #1d4ed8; }
    .squadron-config__test-status--success { background: #d1fae5; color: #065f46; }
    .squadron-config__test-status--failure { background: #fee2e2; color: #991b1b; }
    .squadron-config__test-summary { flex: 1; font-size: 0.8125rem; color: #374151; }
    .squadron-config__test-duration { font-size: 0.75rem; color: #6b7280; }
    .squadron-config__test-expand-icon { font-size: 0.75rem; color: #9ca3af; }
    .squadron-config__test-panel-body { padding: 14px; border-top: 1px solid #e5e7eb; }

    /* Spinner */
    .squadron-config__spinner {
      display: inline-block; width: 12px; height: 12px; border: 2px solid #93c5fd;
      border-top-color: #2563eb; border-radius: 50%; animation: sq-spin 0.8s linear infinite;
    }
    @keyframes sq-spin { to { transform: rotate(360deg); } }

    /* Log entries */
    .squadron-config__test-log { display: flex; flex-direction: column; gap: 4px; margin-bottom: 12px; }
    .squadron-config__test-log-entry {
      display: flex; gap: 8px; font-size: 0.75rem; font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
      padding: 4px 8px; border-radius: 3px; background: #f9fafb;
    }
    .squadron-config__test-log-entry--success { background: #f0fdf4; color: #166534; }
    .squadron-config__test-log-entry--warning { background: #fffbeb; color: #92400e; }
    .squadron-config__test-log-entry--error { background: #fef2f2; color: #991b1b; }
    .squadron-config__test-log-phase {
      font-weight: 600; min-width: 100px; flex-shrink: 0; color: #6b7280;
    }
    .squadron-config__test-log-message { word-break: break-word; }

    /* Agent output */
    .squadron-config__test-output { border: 1px solid #e5e7eb; border-radius: 4px; overflow: hidden; }
    .squadron-config__test-output-header {
      display: flex; justify-content: space-between; padding: 8px 12px;
      background: #f9fafb; cursor: pointer; font-size: 0.8125rem; font-weight: 500;
      user-select: none;
    }
    .squadron-config__test-output-header:hover { background: #f3f4f6; }
    .squadron-config__test-output-content {
      padding: 12px; font-size: 0.75rem; font-family: 'SF Mono', 'Fira Code', monospace;
      background: #1e293b; color: #e2e8f0; overflow-x: auto; max-height: 400px; overflow-y: auto;
      margin: 0; white-space: pre-wrap; word-break: break-word;
    }
  `],
})
export class SquadronConfigComponent implements OnInit {
  private squadronService = inject(UserSquadronService);
  private testService = inject(AgentTestService);

  loading = signal(true);
  saving = signal(false);
  saveSuccess = signal(false);
  saveError = signal<string | null>(null);
  agents = signal<UserAgentConfig[]>([]);
  maxAgents = signal(8);
  editingId = signal<string | null>(null);

  // Test state
  testMenuAgentId = signal<string | null>(null);
  testingAgentId = signal<string | null>(null);
  testResults = signal<Map<string, AgentTestResult>>(new Map());
  expandedTests = signal<Set<string>>(new Set());
  expandedOutputs = signal<Set<string>>(new Set());

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

  /** The currently selected provider catalog entry (if any). */
  selectedProviderEntry = signal<ProviderCatalogEntry | null>(null);

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

  // ====================== Test Methods ======================

  openTestMenu(agent: UserAgentConfig): void {
    if (this.testMenuAgentId() === agent.id) {
      this.testMenuAgentId.set(null);
    } else {
      this.testMenuAgentId.set(agent.id ?? null);
    }
  }

  closeTestMenu(): void {
    this.testMenuAgentId.set(null);
  }

  runTest(agent: UserAgentConfig, mode: TestMode): void {
    if (!agent.id) return;
    this.testMenuAgentId.set(null);
    this.testingAgentId.set(agent.id);

    // Set initial running state
    const runningResult: AgentTestResult = {
      testId: '',
      agentConfigId: agent.id,
      testMode: mode,
      status: 'RUNNING',
      summary: 'Running ' + TEST_MODE_LABELS[mode] + '...',
      logEntries: [
        { timestamp: new Date().toISOString(), phase: 'INIT', message: 'Starting test...', level: 'INFO' },
      ],
    };
    this.updateTestResult(agent.id, runningResult);
    this.expandTest(agent.id);

    this.testService.executeTest({ agentConfigId: agent.id, testMode: mode }).subscribe({
      next: (result) => {
        this.updateTestResult(agent.id!, result);
        this.testingAgentId.set(null);
      },
      error: (err) => {
        const errorResult: AgentTestResult = {
          testId: '',
          agentConfigId: agent.id!,
          testMode: mode,
          status: 'ERROR',
          summary: 'Test failed: ' + (err.error?.message || err.message || 'Unknown error'),
          logEntries: [
            { timestamp: new Date().toISOString(), phase: 'ERROR', message: 'Request failed: ' + (err.status || 'network error'), level: 'ERROR' },
          ],
        };
        this.updateTestResult(agent.id!, errorResult);
        this.testingAgentId.set(null);
      },
    });
  }

  testResultForAgent(agentId: string): AgentTestResult | null {
    return this.testResults().get(agentId) ?? null;
  }

  isTestExpanded(agentId: string): boolean {
    return this.expandedTests().has(agentId);
  }

  toggleTestExpanded(agentId: string): void {
    const expanded = new Set(this.expandedTests());
    if (expanded.has(agentId)) {
      expanded.delete(agentId);
    } else {
      expanded.add(agentId);
    }
    this.expandedTests.set(expanded);
  }

  isOutputExpanded(agentId: string): boolean {
    return this.expandedOutputs().has(agentId);
  }

  toggleOutputExpanded(agentId: string): void {
    const expanded = new Set(this.expandedOutputs());
    if (expanded.has(agentId)) {
      expanded.delete(agentId);
    } else {
      expanded.add(agentId);
    }
    this.expandedOutputs.set(expanded);
  }

  dismissTestResult(agentId: string): void {
    const results = new Map(this.testResults());
    results.delete(agentId);
    this.testResults.set(results);
    const expanded = new Set(this.expandedTests());
    expanded.delete(agentId);
    this.expandedTests.set(expanded);
  }

  private updateTestResult(agentId: string, result: AgentTestResult): void {
    const results = new Map(this.testResults());
    results.set(agentId, result);
    this.testResults.set(results);
  }

  private expandTest(agentId: string): void {
    const expanded = new Set(this.expandedTests());
    expanded.add(agentId);
    this.expandedTests.set(expanded);
  }

  // ====================== CRUD Methods ======================

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
      this.filteredProviders.set(PROVIDER_CATALOG);
    } else {
      this.filteredProviders.set(
        PROVIDER_CATALOG.filter(p => p.hostingType === this.editHostingType),
      );
    }
  }

  private updateAvailableModels(): void {
    const provider = PROVIDER_CATALOG.find(p => p.id === this.editProvider) ?? null;
    this.selectedProviderEntry.set(provider);
    this.availableModels.set(provider?.models ?? []);
  }
}
