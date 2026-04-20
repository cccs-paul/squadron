import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'squadron_theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private http = inject(HttpClient);

  readonly currentTheme = signal<Theme>(this.getInitialTheme());

  readonly isDark = computed(() => this.currentTheme() === 'dark');

  init(): void {
    this.applyTheme(this.currentTheme());
  }

  toggleTheme(): void {
    const next: Theme = this.currentTheme() === 'light' ? 'dark' : 'light';
    this.setTheme(next);
  }

  setTheme(theme: Theme): void {
    this.currentTheme.set(theme);
    this.applyTheme(theme);
    localStorage.setItem(STORAGE_KEY, theme);
  }

  /**
   * Persist the user's theme preference to the backend.
   */
  persistThemeToBackend(userId: string): void {
    const theme = this.currentTheme();
    this.http
      .patch<{ data?: Record<string, unknown> }>(`/api/users/${userId}/preferences`, { theme })
      .subscribe({ error: () => {} });
  }

  /**
   * Load the user's theme preference from the backend and apply it.
   */
  loadThemeFromBackend(userId: string): void {
    this.http
      .get<{ data?: { theme?: string } }>(`/api/users/${userId}/preferences`)
      .subscribe({
        next: (response) => {
          const theme = response?.data?.theme;
          if (theme === 'light' || theme === 'dark') {
            this.setTheme(theme);
          }
        },
        error: () => {},
      });
  }

  private applyTheme(theme: Theme): void {
    document.documentElement.setAttribute('data-theme', theme);
  }

  private getInitialTheme(): Theme {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'light' || stored === 'dark') {
      return stored;
    }
    if (window.matchMedia?.('(prefers-color-scheme: dark)').matches) {
      return 'dark';
    }
    return 'light';
  }
}
