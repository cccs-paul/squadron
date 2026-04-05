import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AgentConfigService, AgentConfig } from '../../../core/services/agent-config.service';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'sq-agent-config',
  standalone: true,
  imports: [FormsModule, TranslateModule],
  templateUrl: './agent-config.component.html',
  styleUrl: './agent-config.component.scss',
})
export class AgentConfigComponent implements OnInit {
  private configService = inject(AgentConfigService);
  private authService = inject(AuthService);

  loading = signal(true);
  saving = signal(false);
  saveSuccess = signal(false);
  saveError = signal<string | null>(null);

  // Form fields
  provider = 'OpenAI';
  modelName = 'gpt-4';
  temperature = 0.7;
  maxTokens = 4096;
  systemPrompt = '';

  // Agent type overrides
  agentOverrides: Record<string, { provider: string; modelName: string; temperature: number; maxTokens: number }> = {};
  selectedOverrideType = '';

  get tenantId(): string {
    return this.authService.user()?.tenantId ?? '';
  }

  readonly providers = ['OpenAI', 'Ollama'];
  readonly agentTypes = ['PLANNING', 'CODING', 'REVIEW', 'QA'];

  ngOnInit(): void {
    this.loadConfig();
  }

  loadConfig(): void {
    this.loading.set(true);
    this.configService.getConfig(this.tenantId).subscribe({
      next: (config) => {
        this.applyConfig(config);
        this.loading.set(false);
      },
      error: () => {
        this.applyDefaults();
        this.loading.set(false);
      },
    });
  }

  saveConfig(): void {
    if (!this.validateMaxTokens()) {
      this.saveError.set('Max tokens must be between 1 and 128000.');
      setTimeout(() => this.saveError.set(null), 5000);
      return;
    }

    this.saving.set(true);
    this.saveSuccess.set(false);
    this.saveError.set(null);

    const config: Partial<AgentConfig> = {
      provider: this.provider,
      modelName: this.modelName,
      temperature: this.temperature,
      maxTokens: this.maxTokens,
      systemPrompt: this.systemPrompt || undefined,
      agentOverrides: Object.keys(this.agentOverrides).length
        ? this.agentOverrides
        : undefined,
    };

    this.configService.updateConfig(this.tenantId, config).subscribe({
      next: () => {
        this.saving.set(false);
        this.saveSuccess.set(true);
        setTimeout(() => this.saveSuccess.set(false), 3000);
      },
      error: () => {
        this.saving.set(false);
        this.saveError.set('Failed to save configuration. Please try again.');
        setTimeout(() => this.saveError.set(null), 5000);
      },
    });
  }

  validateMaxTokens(): boolean {
    return this.maxTokens >= 1 && this.maxTokens <= 128000;
  }

  addOverride(agentType: string): void {
    if (!agentType || this.agentOverrides[agentType]) return;
    this.agentOverrides = {
      ...this.agentOverrides,
      [agentType]: {
        provider: this.provider,
        modelName: this.modelName,
        temperature: this.temperature,
        maxTokens: this.maxTokens,
      },
    };
    this.selectedOverrideType = '';
  }

  removeOverride(agentType: string): void {
    const updated = { ...this.agentOverrides };
    delete updated[agentType];
    this.agentOverrides = updated;
  }

  getOverrideTypes(): string[] {
    return Object.keys(this.agentOverrides);
  }

  getAvailableAgentTypes(): string[] {
    return this.agentTypes.filter((t) => !this.agentOverrides[t]);
  }

  private applyConfig(config: AgentConfig): void {
    this.provider = config.provider || 'OpenAI';
    this.modelName = config.modelName || 'gpt-4';
    this.temperature = config.temperature ?? 0.7;
    this.maxTokens = config.maxTokens || 4096;
    this.systemPrompt = config.systemPrompt || '';
    if (config.agentOverrides) {
      const overrides: Record<string, any> = {};
      for (const [key, val] of Object.entries(config.agentOverrides)) {
        overrides[key] = {
          provider: val.provider || this.provider,
          modelName: val.modelName || this.modelName,
          temperature: val.temperature ?? this.temperature,
          maxTokens: val.maxTokens || this.maxTokens,
        };
      }
      this.agentOverrides = overrides;
    }
  }

  private applyDefaults(): void {
    this.provider = 'OpenAI';
    this.modelName = 'gpt-4';
    this.temperature = 0.7;
    this.maxTokens = 4096;
    this.systemPrompt = '';
    this.agentOverrides = {};
  }
}
