import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { NotificationBellComponent } from './notification-bell.component';
import { NotificationService } from '../../../core/services/notification.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Notification, NotificationType } from '../../../core/models/notification.model';

describe('NotificationBellComponent', () => {
  let component: NotificationBellComponent;
  let fixture: ComponentFixture<NotificationBellComponent>;
  let notificationServiceSpy: jasmine.SpyObj<NotificationService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const unreadCountSignal = signal(0);
  const notificationsSignal = signal<Notification[]>([]);
  const userSignal = signal<{ id: string; username: string; email: string; displayName: string; tenantId: string; tenantName: string; roles: string[]; permissions: string[] } | null>({
    id: 'user-1',
    username: 'fry',
    email: 'fry@planetexpress.com',
    displayName: 'Philip J. Fry',
    tenantId: 't1',
    tenantName: 'Planet Express',
    roles: ['user'],
    permissions: [],
  });

  const mockNotifications: Notification[] = [
    {
      id: 'n1',
      tenantId: 't1',
      userId: 'u1',
      type: NotificationType.TASK_ASSIGNED,
      title: 'New Task',
      message: 'You have been assigned a new task',
      read: false,
      createdAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
    },
    {
      id: 'n2',
      tenantId: 't1',
      userId: 'u1',
      type: NotificationType.REVIEW_REQUESTED,
      title: 'Review Requested',
      message: 'Please review pull request #42',
      read: true,
      createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
    },
    {
      id: 'n3',
      tenantId: 't1',
      userId: 'u1',
      type: NotificationType.AGENT_COMPLETED,
      title: 'Agent Done',
      message: 'Agent completed task SQ-123 successfully',
      read: false,
      createdAt: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
    },
  ];

  beforeEach(async () => {
    unreadCountSignal.set(0);
    notificationsSignal.set([]);
    userSignal.set({
      id: 'user-1',
      username: 'fry',
      email: 'fry@planetexpress.com',
      displayName: 'Philip J. Fry',
      tenantId: 't1',
      tenantName: 'Planet Express',
      roles: ['user'],
      permissions: [],
    });

    notificationServiceSpy = jasmine.createSpyObj('NotificationService', [
      'getNotifications',
      'markAsRead',
      'markAllAsRead',
      'connectWebSocket',
      'disconnectWebSocket',
    ], {
      unreadCount: unreadCountSignal.asReadonly(),
      notifications: notificationsSignal.asReadonly(),
    });

    notificationServiceSpy.getNotifications.and.returnValue(of({
      content: mockNotifications,
      totalElements: 3,
      totalPages: 1,
      page: 0,
      size: 10,
    }));
    notificationServiceSpy.markAsRead.and.returnValue(of(undefined as any));
    notificationServiceSpy.markAllAsRead.and.returnValue(of(undefined as any));

    authServiceSpy = jasmine.createSpyObj('AuthService', [], {
      user: userSignal.asReadonly(),
    });

    await TestBed.configureTestingModule({
      imports: [NotificationBellComponent, TranslateModule.forRoot()],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: notificationServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NotificationBellComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should_beCreated', () => {
    expect(component).toBeTruthy();
  });

  it('should_showBellButton_when_rendered', () => {
    const bellBtn = fixture.nativeElement.querySelector('.bell-btn');
    expect(bellBtn).toBeTruthy();
  });

  it('should_notShowBadge_when_unreadCountIsZero', () => {
    unreadCountSignal.set(0);
    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('.badge');
    expect(badge).toBeNull();
  });

  it('should_showBadge_when_unreadCountGreaterThanZero', () => {
    unreadCountSignal.set(3);
    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('.badge');
    expect(badge).toBeTruthy();
    expect(badge.textContent.trim()).toBe('3');
  });

  it('should_showNinePlus_when_unreadCountGreaterThan9', () => {
    unreadCountSignal.set(15);
    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('.badge');
    expect(badge).toBeTruthy();
    expect(badge.textContent.trim()).toBe('9+');
  });

  it('should_toggleDropdown_when_bellClicked', () => {
    expect(component.dropdownOpen).toBeFalse();

    component.toggleDropdown();
    fixture.detectChanges();

    expect(component.dropdownOpen).toBeTrue();

    const dropdown = fixture.nativeElement.querySelector('.dropdown');
    expect(dropdown).toBeTruthy();
  });

  it('should_fetchNotifications_when_dropdownOpened', () => {
    component.toggleDropdown();

    expect(notificationServiceSpy.getNotifications).toHaveBeenCalledWith(0, 10);
  });

  it('should_notFetchNotifications_when_dropdownClosed', () => {
    component.dropdownOpen = true;
    notificationServiceSpy.getNotifications.calls.reset();

    component.toggleDropdown();

    expect(notificationServiceSpy.getNotifications).not.toHaveBeenCalled();
  });

  it('should_renderNotificationItems_when_dropdownOpenWithNotifications', () => {
    notificationsSignal.set(mockNotifications);
    component.dropdownOpen = true;
    fixture.detectChanges();

    const items = fixture.nativeElement.querySelectorAll('.notification-item');
    expect(items.length).toBe(3);
  });

  it('should_showEmptyMessage_when_dropdownOpenWithNoNotifications', () => {
    notificationsSignal.set([]);
    component.dropdownOpen = true;
    fixture.detectChanges();

    const emptyMsg = fixture.nativeElement.querySelector('.dropdown__empty');
    expect(emptyMsg).toBeTruthy();
    expect(emptyMsg.textContent).toContain('notifications.dropdown.empty');
  });

  it('should_callMarkAsRead_when_notificationClicked', () => {
    notificationsSignal.set(mockNotifications);
    component.dropdownOpen = true;
    fixture.detectChanges();

    component.markAsRead('n1');

    expect(notificationServiceSpy.markAsRead).toHaveBeenCalledWith('n1');
  });

  it('should_callMarkAllAsRead_when_markAllReadClicked', () => {
    unreadCountSignal.set(2);
    notificationsSignal.set(mockNotifications);
    component.dropdownOpen = true;
    fixture.detectChanges();

    const markAllBtn = fixture.nativeElement.querySelector('.mark-all');
    expect(markAllBtn).toBeTruthy();

    component.markAllAsRead();

    expect(notificationServiceSpy.markAllAsRead).toHaveBeenCalled();
  });

  it('should_hideMarkAllButton_when_noUnreadNotifications', () => {
    unreadCountSignal.set(0);
    component.dropdownOpen = true;
    fixture.detectChanges();

    const markAllBtn = fixture.nativeElement.querySelector('.mark-all');
    expect(markAllBtn).toBeNull();
  });

  it('should_closeDropdown_when_closeDropdownCalled', () => {
    component.dropdownOpen = true;

    component.closeDropdown();

    expect(component.dropdownOpen).toBeFalse();
  });

  it('should_applyUnreadClass_when_notificationIsUnread', () => {
    notificationsSignal.set(mockNotifications);
    component.dropdownOpen = true;
    fixture.detectChanges();

    const items = fixture.nativeElement.querySelectorAll('.notification-item');
    // First notification is unread
    expect(items[0].classList.contains('notification-item--unread')).toBeTrue();
    // Second notification is read
    expect(items[1].classList.contains('notification-item--unread')).toBeFalse();
  });

  it('should_hideDropdown_when_dropdownClosedAfterOpen', () => {
    component.dropdownOpen = true;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.dropdown')).toBeTruthy();

    component.dropdownOpen = false;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.dropdown')).toBeNull();
  });

  // --- WebSocket integration tests ---

  it('should_connectWebSocket_when_userIsLoggedIn_onInit', () => {
    // ngOnInit was already called by fixture.detectChanges() in beforeEach
    expect(notificationServiceSpy.connectWebSocket).toHaveBeenCalledWith('user-1');
  });

  it('should_notConnectWebSocket_when_userIsNull_onInit', () => {
    // Reset and create fresh component with null user
    notificationServiceSpy.connectWebSocket.calls.reset();
    userSignal.set(null);

    const newFixture = TestBed.createComponent(NotificationBellComponent);
    newFixture.detectChanges();

    expect(notificationServiceSpy.connectWebSocket).not.toHaveBeenCalled();
  });

  it('should_disconnectWebSocket_when_componentDestroyed', () => {
    component.ngOnDestroy();

    expect(notificationServiceSpy.disconnectWebSocket).toHaveBeenCalled();
  });
});
