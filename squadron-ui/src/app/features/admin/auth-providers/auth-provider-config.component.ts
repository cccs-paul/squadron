import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PermissionService } from '../../../core/services/permission.service';
import { AuthProvider, AuthProviderType } from '../../../core/models/security.model';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';

@Component({
  selector: 'sq-auth-provider-config',
  standalone: true,
  imports: [FormsModule, TimeAgoPipe],
  templateUrl: './auth-provider-config.component.html',
  styleUrl: './auth-provider-config.component.scss',
})
export class AuthProviderConfigComponent implements OnInit {
  private permissionService = inject(PermissionService);

  providers = signal<AuthProvider[]>([]);
  loading = signal(true);
  showCreateModal = signal(false);
  editingProvider = signal<AuthProvider | null>(null);
  testingId = signal<string | null>(null);
  testResult = signal<{ success: boolean; message: string } | null>(null);

  formName = '';
  formType: AuthProviderType = AuthProviderType.OIDC;
  formEnabled = true;
  formClientId = '';
  formClientSecret = '';
  formIssuerUrl = '';

  readonly providerTypes = Object.values(AuthProviderType);

  ngOnInit(): void {
    this.loadProviders();
  }

  loadProviders(): void {
    this.loading.set(true);
    this.permissionService.getAuthProviders().subscribe({
      next: (providers) => {
        this.providers.set(providers);
        this.loading.set(false);
      },
      error: () => {
        console.error('Failed to load auth providers');
        this.providers.set([]);
        this.loading.set(false);
      },
    });
  }

  openCreateModal(): void {
    this.editingProvider.set(null);
    this.formName = '';
    this.formType = AuthProviderType.OIDC;
    this.formEnabled = true;
    this.formClientId = '';
    this.formClientSecret = '';
    this.formIssuerUrl = '';
    this.showCreateModal.set(true);
  }

  openEditModal(provider: AuthProvider): void {
    this.editingProvider.set(provider);
    this.formName = provider.name;
    this.formType = provider.type;
    this.formEnabled = provider.enabled;
    this.formClientId = provider.config['clientId'] || '';
    this.formClientSecret = '';
    this.formIssuerUrl = provider.config['issuerUrl'] || '';
    this.showCreateModal.set(true);
  }

  closeModal(): void {
    this.showCreateModal.set(false);
    this.editingProvider.set(null);
  }

  saveProvider(): void {
    const config: Record<string, string> = {};
    if (this.formClientId) config['clientId'] = this.formClientId;
    if (this.formClientSecret) config['clientSecret'] = this.formClientSecret;
    if (this.formIssuerUrl) config['issuerUrl'] = this.formIssuerUrl;

    const payload: Partial<AuthProvider> = {
      name: this.formName,
      type: this.formType,
      enabled: this.formEnabled,
      config,
    };

    const editing = this.editingProvider();
    if (editing) {
      this.permissionService.updateAuthProvider(editing.id, payload).subscribe({
        next: () => { this.closeModal(); this.loadProviders(); },
        error: () => {
          this.providers.set(this.providers().map((p) =>
            p.id === editing.id ? { ...p, ...payload } as AuthProvider : p,
          ));
          this.closeModal();
        },
      });
    } else {
      this.permissionService.createAuthProvider(payload).subscribe({
        next: () => { this.closeModal(); this.loadProviders(); },
        error: () => {
          console.error('Failed to create auth provider');
          this.closeModal();
        },
      });
    }
  }

  toggleProvider(provider: AuthProvider): void {
    const updated = { ...provider, enabled: !provider.enabled };
    this.permissionService.updateAuthProvider(provider.id, { enabled: !provider.enabled }).subscribe({
      next: () => this.loadProviders(),
      error: () => {
        this.providers.set(this.providers().map((p) => p.id === provider.id ? updated : p));
      },
    });
  }

  testProvider(provider: AuthProvider): void {
    this.testingId.set(provider.id);
    this.testResult.set(null);
    this.permissionService.testAuthProvider(provider.id).subscribe({
      next: (result) => {
        this.testResult.set(result);
        this.testingId.set(null);
      },
      error: () => {
        console.error('Failed to test auth provider');
        this.testResult.set({ success: false, message: 'Connection test failed' });
        this.testingId.set(null);
      },
    });
  }

  deleteProvider(provider: AuthProvider): void {
    if (!confirm(`Delete auth provider "${provider.name}"?`)) return;
    this.permissionService.deleteAuthProvider(provider.id).subscribe({
      next: () => this.loadProviders(),
      error: () => {
        this.providers.set(this.providers().filter((p) => p.id !== provider.id));
      },
    });
  }

  typeIcon(type: AuthProviderType): string {
    switch (type) {
      case AuthProviderType.OIDC: return 'OpenID Connect';
      case AuthProviderType.KEYCLOAK: return 'Keycloak';
      case AuthProviderType.LDAP: return 'LDAP';
      case AuthProviderType.SAML: return 'SAML';
      default: return type;
    }
  }

}
