import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { HealthService, HealthStatus } from '../../../core/services/health.service';

@Component({
  selector: 'sq-login',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);
  private healthService = inject(HealthService);

  username = '';
  password = '';
  selectedTenant = '';
  rememberMe = false;
  tenants = signal<{ id: string; name: string; slug: string }[]>([]);
  error = signal<string>('');
  loading = signal(false);
  showPassword = false;

  healthStatus = signal<HealthStatus | null>(null);
  healthLoading = signal(false);
  showHealthPanel = false;

  ngOnInit(): void {
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/dashboard']);
      return;
    }
    this.authService.getAvailableTenants().subscribe({
      next: (tenants) => this.tenants.set(tenants),
      error: () => {}, // Tenants endpoint may not be available yet
    });
    this.refreshHealth();
  }

  login(): void {
    if (!this.username || !this.password) {
      this.error.set('Please enter your username and password');
      return;
    }
    this.loading.set(true);
    this.error.set('');

    this.authService
      .login({
        username: this.username,
        password: this.password,
        tenantSlug: this.selectedTenant || undefined,
        rememberMe: this.rememberMe,
      })
      .subscribe({
        next: () => {
          this.router.navigate(['/dashboard']);
        },
        error: (err) => {
          this.loading.set(false);
          if (err.status === 401) {
            this.error.set('Invalid username or password');
          } else if (err.status === 403) {
            this.error.set('Your account has been suspended');
          } else {
            this.error.set('Unable to sign in. Please try again.');
          }
        },
      });
  }

  loginWithSso(): void {
    this.authService.loginWithOidc('keycloak');
  }

  togglePasswordVisibility(): void {
    this.showPassword = !this.showPassword;
  }

  refreshHealth(): void {
    this.healthLoading.set(true);
    this.healthService.getHealthStatus().subscribe({
      next: (status) => {
        this.healthStatus.set(status);
        this.healthLoading.set(false);
      },
      error: () => {
        this.healthLoading.set(false);
      },
    });
  }

  toggleHealthPanel(): void {
    this.showHealthPanel = !this.showHealthPanel;
  }

  getStatusColor(status: string): string {
    switch (status?.toUpperCase()) {
      case 'UP':
        return 'health-dot--up';
      case 'DOWN':
        return 'health-dot--down';
      case 'DEGRADED':
        return 'health-dot--degraded';
      default:
        return 'health-dot--unknown';
    }
  }

  objectKeys(obj: Record<string, unknown>): string[] {
    return Object.keys(obj);
  }
}
