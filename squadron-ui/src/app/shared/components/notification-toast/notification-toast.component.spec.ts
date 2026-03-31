import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NotificationToastComponent } from './notification-toast.component';
import { NotificationService, ToastNotification } from '../../../core/services/notification.service';

describe('NotificationToastComponent', () => {
  let component: NotificationToastComponent;
  let fixture: ComponentFixture<NotificationToastComponent>;
  let notificationServiceSpy: jasmine.SpyObj<NotificationService>;

  const toastsSignal = signal<ToastNotification[]>([]);

  const mockToasts: ToastNotification[] = [
    { id: 't1', title: 'Task Assigned', message: 'You have a new task', type: 'info', createdAt: new Date().toISOString() },
    { id: 't2', title: 'Build Failed', message: 'Pipeline error', type: 'error', createdAt: new Date().toISOString() },
  ];

  beforeEach(async () => {
    toastsSignal.set([]);

    notificationServiceSpy = jasmine.createSpyObj('NotificationService', [
      'dismissToast',
    ], {
      toasts: toastsSignal.asReadonly(),
    });

    await TestBed.configureTestingModule({
      imports: [NotificationToastComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: notificationServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NotificationToastComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should_beCreated', () => {
    expect(component).toBeTruthy();
  });

  it('should_notRenderToasts_when_empty', () => {
    const container = fixture.nativeElement.querySelector('.toast-container');
    expect(container).toBeNull();
  });

  it('should_renderToasts_when_toastsExist', () => {
    toastsSignal.set(mockToasts);
    fixture.detectChanges();

    const toastElements = fixture.nativeElement.querySelectorAll('.toast');
    expect(toastElements.length).toBe(2);
  });

  it('should_displayToastTitle_when_toastRendered', () => {
    toastsSignal.set([mockToasts[0]]);
    fixture.detectChanges();

    const title = fixture.nativeElement.querySelector('.toast__title');
    expect(title.textContent).toContain('Task Assigned');
  });

  it('should_displayToastMessage_when_toastRendered', () => {
    toastsSignal.set([mockToasts[0]]);
    fixture.detectChanges();

    const message = fixture.nativeElement.querySelector('.toast__message');
    expect(message.textContent).toContain('You have a new task');
  });

  it('should_applyCorrectTypeClass_when_toastRendered', () => {
    toastsSignal.set([mockToasts[1]]);
    fixture.detectChanges();

    const toast = fixture.nativeElement.querySelector('.toast');
    expect(toast.classList.contains('toast--error')).toBeTrue();
  });

  it('should_callDismissToast_when_closeBtnClicked', () => {
    toastsSignal.set([mockToasts[0]]);
    fixture.detectChanges();

    component.dismiss('t1');
    expect(notificationServiceSpy.dismissToast).toHaveBeenCalledWith('t1');
  });

  it('should_showCloseButton_when_toastRendered', () => {
    toastsSignal.set([mockToasts[0]]);
    fixture.detectChanges();

    const closeBtn = fixture.nativeElement.querySelector('.toast__close');
    expect(closeBtn).toBeTruthy();
  });
});
