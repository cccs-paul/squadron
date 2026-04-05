import { Component, inject } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { NotificationService, ToastNotification } from '../../../core/services/notification.service';

@Component({
  selector: 'sq-notification-toast',
  standalone: true,
  imports: [TranslateModule],
  template: `
    @if (toasts().length > 0) {
      <div class="toast-container">
        @for (toast of toasts(); track toast.id) {
          <div class="toast" [class]="'toast--' + toast.type">
            <div class="toast__content">
              <span class="toast__title">{{ toast.title }}</span>
              <span class="toast__message">{{ toast.message }}</span>
            </div>
            <button class="toast__close" (click)="dismiss(toast.id)" [attr.aria-label]="'notifications.toast.dismissAriaLabel' | translate">&times;</button>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .toast-container {
      position: fixed; top: 16px; right: 16px; z-index: 9999;
      display: flex; flex-direction: column; gap: 8px; max-width: 380px;
    }
    .toast {
      display: flex; align-items: flex-start; gap: 10px; padding: 12px 16px;
      border-radius: var(--sq-radius-md, 8px); background: var(--sq-surface, #fff);
      border: 1px solid var(--sq-border, #e2e8f0);
      box-shadow: 0 4px 12px rgba(0,0,0,0.12); animation: slideIn 0.25s ease-out;
    }
    .toast--info { border-left: 4px solid var(--sq-primary, #4f46e5); }
    .toast--success { border-left: 4px solid var(--sq-success, #22c55e); }
    .toast--warning { border-left: 4px solid var(--sq-warning, #eab308); }
    .toast--error { border-left: 4px solid var(--sq-error, #ef4444); }
    .toast__content { flex: 1; display: flex; flex-direction: column; gap: 2px; }
    .toast__title { font-size: 13px; font-weight: 600; color: var(--sq-text, #1e293b); }
    .toast__message { font-size: 12px; color: var(--sq-text-tertiary, #94a3b8); line-height: 1.4; }
    .toast__close {
      background: none; border: none; cursor: pointer; font-size: 18px; line-height: 1;
      color: var(--sq-text-tertiary, #94a3b8); padding: 0 2px;
    }
    .toast__close:hover { color: var(--sq-text, #1e293b); }
    @keyframes slideIn { from { transform: translateX(100%); opacity: 0; } to { transform: translateX(0); opacity: 1; } }
  `],
})
export class NotificationToastComponent {
  private notificationService = inject(NotificationService);

  readonly toasts = this.notificationService.toasts;

  dismiss(id: string): void {
    this.notificationService.dismissToast(id);
  }
}
