import { Injectable, inject, signal, computed } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';

export type SupportedLanguage = 'en' | 'fr';

export interface LanguageOption {
  code: SupportedLanguage;
  label: string;
  flag: string;
}

const STORAGE_KEY = 'squadron_lang';

@Injectable({ providedIn: 'root' })
export class I18nService {
  private translate = inject(TranslateService);

  readonly supportedLanguages: LanguageOption[] = [
    { code: 'en', label: 'English', flag: 'EN' },
    { code: 'fr', label: 'Francais', flag: 'FR' },
  ];

  readonly currentLang = signal<SupportedLanguage>(this.getInitialLang());

  readonly currentLangLabel = computed(() => {
    const lang = this.currentLang();
    return this.supportedLanguages.find((l) => l.code === lang)?.label ?? lang;
  });

  readonly currentLangFlag = computed(() => {
    const lang = this.currentLang();
    return this.supportedLanguages.find((l) => l.code === lang)?.flag ?? lang.toUpperCase();
  });

  init(): void {
    this.translate.addLangs(['en', 'fr']);
    this.translate.setDefaultLang('en');
    const lang = this.getInitialLang();
    this.translate.use(lang);
    this.currentLang.set(lang);
  }

  switchLanguage(lang: SupportedLanguage): void {
    this.translate.use(lang);
    this.currentLang.set(lang);
    localStorage.setItem(STORAGE_KEY, lang);
  }

  /**
   * Persist the user's language preference to the backend.
   * Called after login when user context is available.
   */
  persistLanguageToBackend(http: HttpClient, userId: string): void {
    const lang = this.currentLang();
    http
      .patch<{ data?: Record<string, unknown> }>(`/api/users/${userId}/preferences`, { language: lang })
      .subscribe({ error: () => {} });
  }

  /**
   * Load the user's language preference from the backend and apply it.
   * Called after login when user context is available.
   */
  loadLanguageFromBackend(http: HttpClient, userId: string): void {
    http
      .get<{ data?: { language?: string } }>(`/api/users/${userId}/preferences`)
      .subscribe({
        next: (response) => {
          const lang = response?.data?.language;
          if (lang === 'en' || lang === 'fr') {
            this.switchLanguage(lang);
          }
        },
        error: () => {},
      });
  }

  private getInitialLang(): SupportedLanguage {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'en' || stored === 'fr') {
      return stored;
    }
    const browserLang = navigator.language?.substring(0, 2);
    if (browserLang === 'fr') {
      return 'fr';
    }
    return 'en';
  }
}
