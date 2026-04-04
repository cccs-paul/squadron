import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { TenantService } from '../../core/services/tenant.service';
import { Tenant, TenantSettings } from '../../core/models/tenant.model';
import { ProjectConfigComponent } from './project-config/project-config.component';
import { SquadronConfigComponent } from './squadron-config/squadron-config.component';
import { NotificationPreferencesComponent } from './notification-preferences/notification-preferences.component';
import { AgentConfigComponent } from './agent-config/agent-config.component';
import { UserTokensComponent } from './user-tokens/user-tokens.component';

export type SettingsTab = 'general' | 'providers-projects' | 'squadron' | 'notifications' | 'agent-config' | 'platform-tokens';

@Component({
  selector: 'sq-settings',
  standalone: true,
  imports: [
    FormsModule,
    TranslateModule,
    ProjectConfigComponent,
    SquadronConfigComponent,
    NotificationPreferencesComponent,
    AgentConfigComponent,
    UserTokensComponent,
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent implements OnInit {
  private authService = inject(AuthService);
  private tenantService = inject(TenantService);

  user = this.authService.user;
  tenant = signal<Tenant | null>(null);
  loading = signal(true);
  saving = signal(false);
  saveSuccess = signal(false);
  activeTab = signal<SettingsTab>('general');

  // General tab - Settings form
  settingsDefaultBranch = 'main';
  settingsAutoReview = true;
  settingsAiEnabled = true;
  settingsMaxUsers = 50;
  settingsMaxProjects = 20;

  // General tab - Profile form
  profileDisplayName = '';
  profileEmail = '';

  readonly tabs: { id: SettingsTab; labelKey: string }[] = [
    { id: 'general', labelKey: 'settings.tabs.general' },
    { id: 'providers-projects', labelKey: 'settings.tabs.providersProjects' },
    { id: 'squadron', labelKey: 'settings.tabs.agentSquadron' },
    { id: 'notifications', labelKey: 'settings.tabs.notifications' },
    { id: 'agent-config', labelKey: 'settings.tabs.agentConfig' },
    { id: 'platform-tokens', labelKey: 'settings.tabs.platformTokens' },
  ];

  ngOnInit(): void {
    this.loadSettings();
    const currentUser = this.user();
    if (currentUser) {
      this.profileDisplayName = currentUser.displayName;
      this.profileEmail = currentUser.email;
    }
  }

  setTab(tab: SettingsTab): void {
    this.activeTab.set(tab);
  }

  loadSettings(): void {
    this.loading.set(true);
    this.tenantService.getTenant().subscribe({
      next: (tenant) => {
        this.tenant.set(tenant);
        if (tenant.settings) {
          this.applySettings(tenant.settings);
        }
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load settings', err);
        this.tenant.set(null);
        this.loading.set(false);
      },
    });
  }

  saveSettings(): void {
    this.saving.set(true);
    this.saveSuccess.set(false);
    const settings: Partial<TenantSettings> = {
      defaultBranch: this.settingsDefaultBranch,
      autoReview: this.settingsAutoReview,
      aiEnabled: this.settingsAiEnabled,
      maxUsers: this.settingsMaxUsers,
      maxProjects: this.settingsMaxProjects,
    };

    this.tenantService.updateTenantSettings(settings).subscribe({
      next: (tenant) => {
        this.tenant.set(tenant);
        this.saving.set(false);
        this.saveSuccess.set(true);
        setTimeout(() => this.saveSuccess.set(false), 3000);
      },
      error: (err) => {
        console.error('Failed to save settings', err);
        this.saving.set(false);
      },
    });
  }

  private applySettings(settings: TenantSettings): void {
    this.settingsDefaultBranch = settings.defaultBranch;
    this.settingsAutoReview = settings.autoReview;
    this.settingsAiEnabled = settings.aiEnabled;
    this.settingsMaxUsers = settings.maxUsers;
    this.settingsMaxProjects = settings.maxProjects;
  }

}
