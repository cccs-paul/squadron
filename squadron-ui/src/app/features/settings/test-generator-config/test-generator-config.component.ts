import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AgentTestService } from '../../../core/services/agent-test.service';
import { AgentTestConfig } from '../../../core/models/agent-test.model';
import {
  PROVIDER_CATALOG,
  ProviderCatalogEntry,
  ModelCatalogEntry,
  HostingType,
} from '../../../core/models/squadron-config.model';

@Component({
  selector: 'sq-test-generator-config',
  standalone: true,
  imports: [FormsModule, TranslateModule],
  template: `
    <div class="test-gen-config">
      <div class="test-gen-config__header">
        <h2 class="test-gen-config__title">{{ 'settings.testGenerator.title' | translate }}</h2>
        <p class="test-gen-config__subtitle">{{ 'settings.testGenerator.subtitle' | translate }}</p>
      </div>

      @if (loading()) {
        <div class="test-gen-config__loading">{{ 'settings.testGenerator.loading' | translate }}</div>
      } @else {
        @if (saveSuccess()) {
          <div class="test-gen-config__message test-gen-config__message--success">
            {{ 'settings.testGenerator.saveSuccess' | translate }}
          </div>
        }
        @if (saveError()) {
          <div class="test-gen-config__message test-gen-config__message--error">
            {{ saveError() }}
          </div>
        }

        <div class="test-gen-config__form">
          <div class="test-gen-config__field">
            <label class="test-gen-config__label">{{ 'settings.testGenerator.hostingType' | translate }}</label>
            <select class="test-gen-config__select" [(ngModel)]="hostingType" (ngModelChange)="onHostingTypeChange()">
              <option value="PLATFORM">{{ 'settings.squadronConfig.hostingTypes.platform' | translate }}</option>
              <option value="SELF_HOSTED">{{ 'settings.squadronConfig.hostingTypes.selfHosted' | translate }}</option>
              <option value="CUSTOM">{{ 'settings.squadronConfig.hostingTypes.custom' | translate }}</option>
            </select>
          </div>

          <div class="test-gen-config__field">
            <label class="test-gen-config__label">{{ 'settings.testGenerator.provider' | translate }}</label>
            <select class="test-gen-config__select" [(ngModel)]="provider" (ngModelChange)="onProviderChange()">
              <option value="">{{ 'settings.squadronConfig.selectProvider' | translate }}</option>
              @for (p of filteredProviders(); track p.id) {
                <option [value]="p.id">{{ p.label }}</option>
              }
            </select>
          </div>

          <div class="test-gen-config__field">
            <label class="test-gen-config__label">{{ 'settings.testGenerator.model' | translate }}</label>
            @if (availableModels().length > 0) {
              <select class="test-gen-config__select" [(ngModel)]="model">
                <option value="">{{ 'settings.squadronConfig.selectModel' | translate }}</option>
                @for (m of availableModels(); track m.id) {
                  <option [value]="m.id">{{ m.label }}</option>
                }
              </select>
            } @else {
              <input class="test-gen-config__input" [(ngModel)]="model"
                     [placeholder]="'settings.squadronConfig.modelPlaceholder' | translate" />
            }
          </div>

          @if (hostingType === 'SELF_HOSTED' || hostingType === 'CUSTOM') {
            <div class="test-gen-config__field">
              <label class="test-gen-config__label">{{ 'settings.testGenerator.baseUrl' | translate }}</label>
              <input class="test-gen-config__input" [(ngModel)]="baseUrl"
                     [placeholder]="hostingType === 'SELF_HOSTED' ? 'http://localhost:11434' : 'https://api.example.com/v1'" />
            </div>
          }

          @if (hostingType === 'CUSTOM' || selectedProviderEntry()?.requiresApiKey) {
            <div class="test-gen-config__field">
              <label class="test-gen-config__label">{{ 'settings.testGenerator.apiKey' | translate }}</label>
              <input class="test-gen-config__input" type="password" [(ngModel)]="apiKey"
                     [placeholder]="'settings.squadronConfig.apiKeyPlaceholder' | translate" />
            </div>
          }

          <div class="test-gen-config__info">
            <p>{{ 'settings.testGenerator.info' | translate }}</p>
          </div>

          <button class="test-gen-config__btn test-gen-config__btn--save"
            (click)="save()" [disabled]="saving()">
            @if (saving()) {
              {{ 'common.saving' | translate }}
            } @else {
              {{ 'settings.testGenerator.save' | translate }}
            }
          </button>
        </div>
      }
    </div>
  `,
  styles: [`
    .test-gen-config { max-width: 600px; margin: 0 auto; padding: 24px; }
    .test-gen-config__header { margin-bottom: 24px; }
    .test-gen-config__title { font-size: 1.5rem; font-weight: 600; margin: 0 0 8px; }
    .test-gen-config__subtitle { color: #6b7280; margin: 0; font-size: 0.875rem; }
    .test-gen-config__loading { text-align: center; padding: 40px; color: #6b7280; }
    .test-gen-config__message { padding: 12px 16px; border-radius: 6px; margin-bottom: 16px; font-size: 0.875rem; }
    .test-gen-config__message--success { background: #d1fae5; color: #065f46; }
    .test-gen-config__message--error { background: #fee2e2; color: #991b1b; }
    .test-gen-config__form { display: flex; flex-direction: column; gap: 16px; }
    .test-gen-config__field { display: flex; flex-direction: column; gap: 4px; }
    .test-gen-config__label { font-size: 0.75rem; font-weight: 500; color: #374151; }
    .test-gen-config__input, .test-gen-config__select {
      padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 0.875rem;
    }
    .test-gen-config__info {
      padding: 12px 16px; background: #f0f9ff; border: 1px solid #bae6fd; border-radius: 6px;
      font-size: 0.8125rem; color: #0369a1;
    }
    .test-gen-config__info p { margin: 0; }
    .test-gen-config__btn {
      padding: 8px 20px; border: 1px solid #d1d5db; border-radius: 6px; background: #fff;
      cursor: pointer; font-size: 0.875rem; align-self: flex-start;
    }
    .test-gen-config__btn:disabled { opacity: 0.5; cursor: not-allowed; }
    .test-gen-config__btn--save { background: #2563eb; color: #fff; border-color: #2563eb; }
  `],
})
export class TestGeneratorConfigComponent implements OnInit {
  private testService = inject(AgentTestService);

  loading = signal(true);
  saving = signal(false);
  saveSuccess = signal(false);
  saveError = signal<string | null>(null);

  hostingType: HostingType = 'SELF_HOSTED';
  provider = 'ollama';
  model = 'gemma4:e2b';
  baseUrl = '';
  apiKey = '';

  filteredProviders = signal<ProviderCatalogEntry[]>([]);
  availableModels = signal<ModelCatalogEntry[]>([]);
  selectedProviderEntry = signal<ProviderCatalogEntry | null>(null);

  ngOnInit(): void {
    this.loadConfig();
  }

  loadConfig(): void {
    this.loading.set(true);
    this.testService.getTestConfig().subscribe({
      next: (config) => {
        this.hostingType = (config.generatorHostingType as HostingType) || 'SELF_HOSTED';
        this.provider = config.generatorProvider || 'ollama';
        this.model = config.generatorModel || 'gemma4:e2b';
        this.baseUrl = config.generatorBaseUrl || '';
        this.apiKey = '';
        this.updateFilteredProviders();
        this.updateAvailableModels();
        this.loading.set(false);
      },
      error: () => {
        // Use defaults
        this.updateFilteredProviders();
        this.updateAvailableModels();
        this.loading.set(false);
      },
    });
  }

  onHostingTypeChange(): void {
    this.provider = '';
    this.model = '';
    this.baseUrl = '';
    this.apiKey = '';
    this.updateFilteredProviders();
    this.updateAvailableModels();
  }

  onProviderChange(): void {
    this.model = '';
    this.updateAvailableModels();
  }

  save(): void {
    this.saving.set(true);
    this.saveSuccess.set(false);
    this.saveError.set(null);

    const config: AgentTestConfig = {
      generatorProvider: this.provider,
      generatorModel: this.model,
      generatorHostingType: this.hostingType,
      generatorBaseUrl: this.baseUrl || undefined,
      generatorApiKey: this.apiKey || undefined,
    };

    this.testService.updateTestConfig(config).subscribe({
      next: () => {
        this.saving.set(false);
        this.saveSuccess.set(true);
        setTimeout(() => this.saveSuccess.set(false), 3000);
      },
      error: () => {
        this.saving.set(false);
        this.saveError.set('Failed to save configuration.');
        setTimeout(() => this.saveError.set(null), 5000);
      },
    });
  }

  private updateFilteredProviders(): void {
    if (this.hostingType === 'CUSTOM') {
      this.filteredProviders.set(PROVIDER_CATALOG);
    } else {
      this.filteredProviders.set(
        PROVIDER_CATALOG.filter(p => p.hostingType === this.hostingType),
      );
    }
  }

  private updateAvailableModels(): void {
    const provider = PROVIDER_CATALOG.find(p => p.id === this.provider) ?? null;
    this.selectedProviderEntry.set(provider);
    this.availableModels.set(provider?.models ?? []);
  }
}
