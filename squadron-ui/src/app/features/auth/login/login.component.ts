import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

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

  username = '';
  password = '';
  selectedTenant = '';
  rememberMe = false;
  tenants = signal<{ id: string; name: string; slug: string }[]>([]);
  error = signal<string>('');
  loading = signal(false);
  showPassword = false;

  ngOnInit(): void {
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/dashboard']);
      return;
    }
    this.authService.getAvailableTenants().subscribe({
      next: (tenants) => this.tenants.set(tenants),
      error: () => {}, // Tenants endpoint may not be available yet
    });
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
}
