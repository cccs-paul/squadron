import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { TenantService } from '../../core/services/tenant.service';
import { Tenant, TenantSettings } from '../../core/models/tenant.model';

@Component({
  selector: 'sq-settings',
  standalone: true,
  imports: [FormsModule],
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

  // Settings form
  settingsDefaultBranch = 'main';
  settingsAutoReview = true;
  settingsAiEnabled = true;
  settingsMaxUsers = 50;
  settingsMaxProjects = 20;

  // Profile form
  profileDisplayName = '';
  profileEmail = '';

  ngOnInit(): void {
    this.loadSettings();
    const currentUser = this.user();
    if (currentUser) {
      this.profileDisplayName = currentUser.displayName;
      this.profileEmail = currentUser.email;
    }
  }

  loadSettings(): void {
    this.loading.set(true);
    this.tenantService.getTenant().subscribe({
      next: (tenant) => {
        this.tenant.set(tenant);
        this.applySettings(tenant.settings);
        this.loading.set(false);
      },
      error: () => {
        this.applyMockSettings();
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
      error: () => {
        // Optimistic update for demo
        this.saving.set(false);
        this.saveSuccess.set(true);
        setTimeout(() => this.saveSuccess.set(false), 3000);
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

  private applyMockSettings(): void {
    this.tenant.set({
      id: '1',
      name: 'Acme Corp',
      slug: 'acme',
      plan: 'TEAM' as any,
      settings: {
        maxUsers: 50,
        maxProjects: 20,
        aiEnabled: true,
        defaultBranch: 'main',
        autoReview: true,
      },
      createdAt: new Date(Date.now() - 86400000 * 90).toISOString(),
    });
    this.applySettings(this.tenant()!.settings);
  }
}
