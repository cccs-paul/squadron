import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { NotificationService } from '../../../core/services/notification.service';
import { AuthService } from '../../../core/auth/auth.service';
import { TimeAgoPipe } from '../../pipes/time-ago.pipe';
import { TruncatePipe } from '../../pipes/truncate.pipe';

@Component({
  selector: 'sq-notification-bell',
  standalone: true,
  imports: [TimeAgoPipe, TruncatePipe, TranslateModule],
  templateUrl: './notification-bell.component.html',
  styleUrl: './notification-bell.component.scss',
})
export class NotificationBellComponent implements OnInit, OnDestroy {
  private notificationService = inject(NotificationService);
  private authService = inject(AuthService);

  readonly unreadCount = this.notificationService.unreadCount;
  readonly notifications = this.notificationService.notifications;
  dropdownOpen = false;

  ngOnInit(): void {
    // Connect to notification WebSocket for real-time updates
    const user = this.authService.user();
    if (user?.id) {
      this.notificationService.connectWebSocket(user.id);
    }
  }

  ngOnDestroy(): void {
    this.notificationService.disconnectWebSocket();
  }

  toggleDropdown(): void {
    this.dropdownOpen = !this.dropdownOpen;
    if (this.dropdownOpen) {
      this.notificationService.getNotifications(0, 10).subscribe();
    }
  }

  markAsRead(id: string): void {
    this.notificationService.markAsRead(id).subscribe();
  }

  markAllAsRead(): void {
    this.notificationService.markAllAsRead().subscribe();
  }

  closeDropdown(): void {
    this.dropdownOpen = false;
  }
}
