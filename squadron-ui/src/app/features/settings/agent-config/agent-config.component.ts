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

  get tenantId(): string {
    return this.authService.user()?.tenantId ?? '';
  }

  readonly providers = ['OpenAI', 'Ollama'];

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

  private applyConfig(config: AgentConfig): void {
    this.provider = config.provider || 'OpenAI';
    this.modelName = config.modelName || 'gpt-4';
    this.temperature = config.temperature ?? 0.7;
    this.maxTokens = config.maxTokens || 4096;
    this.systemPrompt = config.systemPrompt || '';
  }

  private applyDefaults(): void {
    this.provider = 'OpenAI';
    this.modelName = 'gpt-4';
    this.temperature = 0.7;
    this.maxTokens = 4096;
    this.systemPrompt = '';
  }
}
