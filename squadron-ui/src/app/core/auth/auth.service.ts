import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, of, map, BehaviorSubject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthUser, LoginRequest, LoginResponse, TokenPair } from './auth.models';

const TOKEN_KEY = 'sq_token';
const REFRESH_KEY = 'sq_refresh';
const USER_KEY = 'sq_user';
const EXPIRES_KEY = 'sq_expires';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly apiUrl = environment.apiUrl;
  private currentUser = signal<AuthUser | null>(this.loadStoredUser());
  private isAuthenticatedSignal = signal<boolean>(this.hasValidToken());

  readonly user = this.currentUser.asReadonly();
  readonly isAuthenticated = this.isAuthenticatedSignal.asReadonly();
  readonly isAdmin = computed(() => this.currentUser()?.roles?.includes('ADMIN') ?? false);

  constructor(
    private http: HttpClient,
    private router: Router,
  ) {
    this.checkTokenExpiration();
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/auth/login`, request).pipe(
      tap((response) => this.handleAuthSuccess(response)),
      catchError((error) => {
        this.clearAuth();
        throw error;
      }),
    );
  }

  loginWithOidc(provider: string): void {
    window.location.href = `${this.apiUrl}/auth/oidc/${provider}/authorize`;
  }

  handleOidcCallback(code: string, state: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.apiUrl}/auth/oidc/callback`, { code, state })
      .pipe(tap((response) => this.handleAuthSuccess(response)));
  }

  refreshToken(): Observable<LoginResponse | null> {
    const refreshToken = localStorage.getItem(REFRESH_KEY);
    if (!refreshToken) {
      this.clearAuth();
      return of(null);
    }
    return this.http
      .post<LoginResponse>(`${this.apiUrl}/auth/refresh`, { refreshToken })
      .pipe(
        tap((response) => this.handleAuthSuccess(response)),
        catchError(() => {
          this.clearAuth();
          return of(null);
        }),
      );
  }

  logout(): void {
    this.http.post(`${this.apiUrl}/auth/logout`, {}).subscribe({
      complete: () => this.performLogout(),
      error: () => this.performLogout(),
    });
  }

  getAccessToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  getAvailableTenants(): Observable<{ id: string; name: string; slug: string }[]> {
    return this.http.get<{ id: string; name: string; slug: string }[]>(
      `${this.apiUrl}/auth/tenants`,
    );
  }

  private handleAuthSuccess(response: LoginResponse): void {
    const expiresAt = Date.now() + response.expiresIn * 1000;
    localStorage.setItem(TOKEN_KEY, response.accessToken);
    localStorage.setItem(REFRESH_KEY, response.refreshToken);
    localStorage.setItem(EXPIRES_KEY, expiresAt.toString());
    localStorage.setItem(USER_KEY, JSON.stringify(response.user));
    this.currentUser.set(response.user);
    this.isAuthenticatedSignal.set(true);
  }

  private performLogout(): void {
    this.clearAuth();
    this.router.navigate(['/login']);
  }

  private clearAuth(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(EXPIRES_KEY);
    localStorage.removeItem(USER_KEY);
    this.currentUser.set(null);
    this.isAuthenticatedSignal.set(false);
  }

  private hasValidToken(): boolean {
    const token = localStorage.getItem(TOKEN_KEY);
    const expiresAt = localStorage.getItem(EXPIRES_KEY);
    if (!token || !expiresAt) return false;
    return Date.now() < parseInt(expiresAt, 10);
  }

  private loadStoredUser(): AuthUser | null {
    try {
      const stored = localStorage.getItem(USER_KEY);
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  }

  private checkTokenExpiration(): void {
    if (!this.hasValidToken() && localStorage.getItem(TOKEN_KEY)) {
      this.clearAuth();
    }
  }
}
