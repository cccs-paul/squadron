import { Component, inject, output } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../../core/auth/auth.service';
import { I18nService, SupportedLanguage } from '../../../core/services/i18n.service';
import { NotificationBellComponent } from '../notification-bell/notification-bell.component';
import { AvatarComponent } from '../avatar/avatar.component';

@Component({
  selector: 'sq-header',
  standalone: true,
  imports: [NotificationBellComponent, AvatarComponent, TranslateModule],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss',
})
export class HeaderComponent {
  private authService = inject(AuthService);
  readonly i18n = inject(I18nService);

  readonly user = this.authService.user;
  readonly menuToggle = output<void>();
  profileMenuOpen = false;
  langMenuOpen = false;

  toggleProfileMenu(): void {
    this.profileMenuOpen = !this.profileMenuOpen;
    this.langMenuOpen = false;
  }

  toggleLangMenu(): void {
    this.langMenuOpen = !this.langMenuOpen;
    this.profileMenuOpen = false;
  }

  switchLang(lang: SupportedLanguage): void {
    this.i18n.switchLanguage(lang);
    this.langMenuOpen = false;
  }

  logout(): void {
    this.authService.logout();
  }
}
