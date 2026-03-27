import { Component, inject, output } from '@angular/core';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificationBellComponent } from '../notification-bell/notification-bell.component';
import { AvatarComponent } from '../avatar/avatar.component';

@Component({
  selector: 'sq-header',
  standalone: true,
  imports: [NotificationBellComponent, AvatarComponent],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss',
})
export class HeaderComponent {
  private authService = inject(AuthService);

  readonly user = this.authService.user;
  readonly menuToggle = output<void>();
  profileMenuOpen = false;

  toggleProfileMenu(): void {
    this.profileMenuOpen = !this.profileMenuOpen;
  }

  logout(): void {
    this.authService.logout();
  }
}
