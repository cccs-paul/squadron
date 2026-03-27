import { Component, inject } from '@angular/core';
import { NotificationService } from '../../../core/services/notification.service';
import { TimeAgoPipe } from '../../pipes/time-ago.pipe';
import { TruncatePipe } from '../../pipes/truncate.pipe';

@Component({
  selector: 'sq-notification-bell',
  standalone: true,
  imports: [TimeAgoPipe, TruncatePipe],
  templateUrl: './notification-bell.component.html',
  styleUrl: './notification-bell.component.scss',
})
export class NotificationBellComponent {
  private notificationService = inject(NotificationService);

  readonly unreadCount = this.notificationService.unreadCount;
  readonly notifications = this.notificationService.notifications;
  dropdownOpen = false;

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
