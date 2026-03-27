import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'sq-oidc-callback',
  standalone: true,
  template: `
    <div class="callback">
      <div class="callback__spinner"></div>
      <p>Completing sign in...</p>
    </div>
  `,
  styles: [`
    .callback {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      gap: 16px;
      color: #fff;
      font-size: 16px;
    }
    .callback__spinner {
      width: 40px;
      height: 40px;
      border: 3px solid rgba(255,255,255,0.2);
      border-top-color: #fff;
      border-radius: 50%;
      animation: spin 600ms linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
  `],
})
export class OidcCallbackComponent implements OnInit {
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  ngOnInit(): void {
    const code = this.route.snapshot.queryParamMap.get('code');
    const state = this.route.snapshot.queryParamMap.get('state');

    if (code && state) {
      this.authService.handleOidcCallback(code, state).subscribe({
        next: () => this.router.navigate(['/dashboard']),
        error: () => this.router.navigate(['/login']),
      });
    } else {
      this.router.navigate(['/login']);
    }
  }
}
