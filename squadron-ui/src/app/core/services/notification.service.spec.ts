import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { NotificationService, ToastNotification } from './notification.service';
import { Notification, NotificationType } from '../models/notification.model';
import { environment } from '../../../environments/environment';

describe('NotificationService', () => {
  let service: NotificationService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(NotificationService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    service.disconnectWebSocket();
  });

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_initializeSignalsWithDefaults', () => {
    expect(service.unreadCount()).toBe(0);
    expect(service.notifications()).toEqual([]);
  });

  it('should_initializeToastsWithEmptyArray', () => {
    expect(service.toasts()).toEqual([]);
  });

  it('should_getNotifications_when_calledWithDefaults', () => {
    const mockPage = {
      content: [
        { id: 'n1', message: 'Task assigned', read: false },
        { id: 'n2', message: 'Review complete', read: true },
      ],
      totalElements: 2,
      totalPages: 1,
      page: 0,
      size: 20,
    };

    service.getNotifications().subscribe((result) => {
      expect(result).toEqual(mockPage as any);
    });

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/notifications` &&
      r.params.get('page') === '0' &&
      r.params.get('size') === '20'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should_updateNotificationsSignal_when_getNotificationsCompletes', () => {
    const mockPage = {
      content: [
        { id: 'n1', message: 'Task assigned', read: false },
        { id: 'n2', message: 'Review complete', read: true },
      ],
      totalElements: 2,
      totalPages: 1,
      page: 0,
      size: 20,
    };

    service.getNotifications().subscribe();

    const req = httpTesting.expectOne((r) => r.url === `${apiUrl}/notifications`);
    req.flush(mockPage);

    expect(service.notifications()).toEqual(mockPage.content as any);
  });

  it('should_updateUnreadCountSignal_when_getNotificationsCompletes', () => {
    const mockPage = {
      content: [
        { id: 'n1', message: 'Task assigned', read: false },
        { id: 'n2', message: 'Review complete', read: true },
        { id: 'n3', message: 'New comment', read: false },
      ],
      totalElements: 3,
      totalPages: 1,
      page: 0,
      size: 20,
    };

    service.getNotifications().subscribe();

    const req = httpTesting.expectOne((r) => r.url === `${apiUrl}/notifications`);
    req.flush(mockPage);

    expect(service.unreadCount()).toBe(2);
  });

  it('should_getNotifications_when_calledWithCustomPageAndSize', () => {
    service.getNotifications(2, 10).subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/notifications` &&
      r.params.get('page') === '2' &&
      r.params.get('size') === '10'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 2, size: 10 });
  });

  it('should_markAsRead_when_calledWithId', () => {
    // Pre-populate notifications signal
    const mockPage = {
      content: [
        { id: 'n1', message: 'Task assigned', read: false },
        { id: 'n2', message: 'Review complete', read: false },
      ],
      totalElements: 2,
      totalPages: 1,
      page: 0,
      size: 20,
    };
    service.getNotifications().subscribe();
    httpTesting.expectOne((r) => r.url === `${apiUrl}/notifications`).flush(mockPage);

    expect(service.unreadCount()).toBe(2);

    // Now mark one as read
    service.markAsRead('n1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/notifications/n1/read`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(null);
  });

  it('should_updateNotificationToRead_when_markAsReadCompletes', () => {
    // Pre-populate
    const mockPage = {
      content: [
        { id: 'n1', message: 'Task assigned', read: false },
        { id: 'n2', message: 'Review complete', read: false },
      ],
      totalElements: 2,
      totalPages: 1,
      page: 0,
      size: 20,
    };
    service.getNotifications().subscribe();
    httpTesting.expectOne((r) => r.url === `${apiUrl}/notifications`).flush(mockPage);

    service.markAsRead('n1').subscribe();
    httpTesting.expectOne(`${apiUrl}/notifications/n1/read`).flush(null);

    const updated = service.notifications();
    expect(updated.find((n: any) => n.id === 'n1')!.read).toBe(true);
    expect(updated.find((n: any) => n.id === 'n2')!.read).toBe(false);
  });

  it('should_decrementUnreadCount_when_markAsReadCompletes', () => {
    // Pre-populate
    const mockPage = {
      content: [
        { id: 'n1', message: 'Task assigned', read: false },
        { id: 'n2', message: 'Review complete', read: false },
      ],
      totalElements: 2,
      totalPages: 1,
      page: 0,
      size: 20,
    };
    service.getNotifications().subscribe();
    httpTesting.expectOne((r) => r.url === `${apiUrl}/notifications`).flush(mockPage);

    expect(service.unreadCount()).toBe(2);

    service.markAsRead('n1').subscribe();
    httpTesting.expectOne(`${apiUrl}/notifications/n1/read`).flush(null);

    expect(service.unreadCount()).toBe(1);
  });

  it('should_notDecrementBelowZero_when_unreadCountAlreadyZero', () => {
    // Pre-populate with all read
    const mockPage = {
      content: [{ id: 'n1', message: 'Task', read: true }],
      totalElements: 1,
      totalPages: 1,
      page: 0,
      size: 20,
    };
    service.getNotifications().subscribe();
    httpTesting.expectOne((r) => r.url === `${apiUrl}/notifications`).flush(mockPage);

    expect(service.unreadCount()).toBe(0);

    service.markAsRead('n1').subscribe();
    httpTesting.expectOne(`${apiUrl}/notifications/n1/read`).flush(null);

    expect(service.unreadCount()).toBe(0);
  });

  it('should_markAllAsRead_when_called', () => {
    service.markAllAsRead().subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/notifications/read-all`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(null);
  });

  it('should_setAllNotificationsToRead_when_markAllAsReadCompletes', () => {
    // Pre-populate
    const mockPage = {
      content: [
        { id: 'n1', message: 'Task assigned', read: false },
        { id: 'n2', message: 'Review complete', read: false },
        { id: 'n3', message: 'New comment', read: true },
      ],
      totalElements: 3,
      totalPages: 1,
      page: 0,
      size: 20,
    };
    service.getNotifications().subscribe();
    httpTesting.expectOne((r) => r.url === `${apiUrl}/notifications`).flush(mockPage);

    expect(service.unreadCount()).toBe(2);

    service.markAllAsRead().subscribe();
    httpTesting.expectOne(`${apiUrl}/notifications/read-all`).flush(null);

    expect(service.unreadCount()).toBe(0);
    expect(service.notifications().every((n: any) => n.read)).toBe(true);
  });

  // --- Toast tests ---

  it('should_dismissToast_when_calledWithId', () => {
    // Manually set toasts
    (service as any).toasts.set([
      { id: 't1', title: 'Toast 1', message: 'msg', type: 'info', createdAt: '' },
      { id: 't2', title: 'Toast 2', message: 'msg', type: 'success', createdAt: '' },
    ] as ToastNotification[]);

    service.dismissToast('t1');

    expect(service.toasts().length).toBe(1);
    expect(service.toasts()[0].id).toBe('t2');
  });

  it('should_notChangeToasts_when_dismissingNonexistentId', () => {
    (service as any).toasts.set([
      { id: 't1', title: 'Toast 1', message: 'msg', type: 'info', createdAt: '' },
    ] as ToastNotification[]);

    service.dismissToast('nonexistent');

    expect(service.toasts().length).toBe(1);
  });

  // --- mapBackendNotification tests ---

  it('should_mapBackendNotification_when_backendFieldsProvided', () => {
    const raw = {
      id: 'abc-123',
      tenantId: 'tenant-1',
      userId: 'user-1',
      subject: 'Task Assigned',
      body: 'You have a new task',
      eventType: 'TASK_ASSIGNED',
      readAt: null,
      createdAt: '2026-03-31T12:00:00Z',
    };

    const mapped = service.mapBackendNotification(raw);

    expect(mapped.id).toBe('abc-123');
    expect(mapped.title).toBe('Task Assigned');
    expect(mapped.message).toBe('You have a new task');
    expect(mapped.type).toBe('TASK_ASSIGNED');
    expect(mapped.read).toBe(false);
    expect(mapped.createdAt).toBe('2026-03-31T12:00:00Z');
  });

  it('should_mapBackendNotification_when_readAtIsSet', () => {
    const raw = {
      id: 'abc-124',
      tenantId: 'tenant-1',
      userId: 'user-1',
      subject: 'Review Done',
      body: 'Review completed',
      eventType: 'REVIEW_COMPLETED',
      readAt: '2026-03-31T13:00:00Z',
      createdAt: '2026-03-31T12:00:00Z',
    };

    const mapped = service.mapBackendNotification(raw);

    expect(mapped.read).toBe(true);
    expect(mapped.type).toBe('REVIEW_COMPLETED');
  });

  it('should_mapBackendNotification_when_frontendFieldsProvided', () => {
    // If the payload already has frontend-style fields (title/message/read)
    const raw = {
      id: 'abc-125',
      tenantId: 'tenant-1',
      userId: 'user-1',
      title: 'Already Mapped',
      message: 'Already mapped message',
      eventType: 'SYSTEM',
      read: true,
      createdAt: '2026-03-31T12:00:00Z',
    };

    const mapped = service.mapBackendNotification(raw);

    // Falls back to title since subject is missing
    expect(mapped.title).toBe('Already Mapped');
    expect(mapped.message).toBe('Already mapped message');
    expect(mapped.read).toBe(true);
  });

  it('should_mapBackendNotification_withDefaults_when_fieldsAreMissing', () => {
    const raw = { id: 'abc-126' };

    const mapped = service.mapBackendNotification(raw);

    expect(mapped.id).toBe('abc-126');
    expect(mapped.title).toBe('New Notification');
    expect(mapped.message).toBe('');
    expect(mapped.type).toBe('SYSTEM');
    expect(mapped.read).toBe(false);
  });

  // --- connectWebSocket / disconnectWebSocket tests ---

  it('should_notThrow_when_disconnectWebSocketCalledWithoutConnection', () => {
    expect(() => service.disconnectWebSocket()).not.toThrow();
  });

  it('should_exposeOnNotificationObservable', () => {
    expect(service.onNotification$).toBeTruthy();
    expect(typeof service.onNotification$.subscribe).toBe('function');
  });

  it('should_buildNotificationWsUrl_when_wsUrlStartsWithWs', () => {
    // Access private method via type assertion
    const url = (service as any).buildNotificationWsUrl();
    // environment.wsUrl is 'ws://localhost:8443/ws' in dev
    expect(url).toContain('/notifications/websocket');
  });
});
