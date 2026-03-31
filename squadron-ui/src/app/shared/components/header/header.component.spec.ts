import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { HeaderComponent } from './header.component';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthUser } from '../../../core/auth/auth.models';
import { NotificationService } from '../../../core/services/notification.service';

describe('HeaderComponent', () => {
  let component: HeaderComponent;
  let fixture: ComponentFixture<HeaderComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const mockUser: AuthUser = {
    id: 'user-1',
    username: 'jdoe',
    email: 'jdoe@example.com',
    displayName: 'John Doe',
    avatarUrl: 'https://example.com/avatar.png',
    tenantId: 'tenant-1',
    tenantName: 'Acme Corp',
    roles: ['USER'],
    permissions: ['READ'],
  };

  const userSignal = signal<AuthUser | null>(mockUser);

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['logout'], {
      user: userSignal.asReadonly(),
      isAuthenticated: signal(true).asReadonly(),
      isAdmin: signal(false).asReadonly(),
    });

    const notificationServiceSpy = jasmine.createSpyObj('NotificationService', ['getNotifications', 'markAsRead', 'markAllAsRead', 'connectWebSocket', 'disconnectWebSocket'], {
      unreadCount: signal(0),
      notifications: signal([]),
      toasts: signal([]),
    });

    await TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: NotificationService, useValue: notificationServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should_beCreated', () => {
    expect(component).toBeTruthy();
  });

  it('should_displayUserDisplayName_when_userIsSet', () => {
    const profileName = fixture.nativeElement.querySelector('.header__profile-name');
    expect(profileName.textContent).toContain('John Doe');
  });

  it('should_displayUserTenantName_when_userIsSet', () => {
    const profileTenant = fixture.nativeElement.querySelector('.header__profile-tenant');
    expect(profileTenant.textContent).toContain('Acme Corp');
  });

  it('should_toggleProfileMenu_when_toggleProfileMenuCalled', () => {
    expect(component.profileMenuOpen).toBeFalse();

    component.toggleProfileMenu();
    expect(component.profileMenuOpen).toBeTrue();

    component.toggleProfileMenu();
    expect(component.profileMenuOpen).toBeFalse();
  });

  it('should_showProfileMenu_when_profileMenuOpenIsTrue', () => {
    component.profileMenuOpen = true;
    fixture.detectChanges();

    const profileMenu = fixture.nativeElement.querySelector('.profile-menu');
    expect(profileMenu).toBeTruthy();
  });

  it('should_hideProfileMenu_when_profileMenuOpenIsFalse', () => {
    component.profileMenuOpen = false;
    fixture.detectChanges();

    const profileMenu = fixture.nativeElement.querySelector('.profile-menu');
    expect(profileMenu).toBeNull();
  });

  it('should_emitMenuToggle_when_menuToggleButtonClicked', () => {
    const emitSpy = spyOn(component.menuToggle, 'emit');

    const button = fixture.nativeElement.querySelector('.header__menu-toggle');
    button.click();

    expect(emitSpy).toHaveBeenCalled();
  });

  it('should_callAuthServiceLogout_when_logoutClicked', () => {
    component.profileMenuOpen = true;
    fixture.detectChanges();

    const logoutButton = fixture.nativeElement.querySelector('.profile-menu__item--danger');
    logoutButton.click();

    expect(authServiceSpy.logout).toHaveBeenCalled();
  });

  it('should_haveSearchInput_when_rendered', () => {
    const searchInput = fixture.nativeElement.querySelector('.header__search-input');
    expect(searchInput).toBeTruthy();
    expect(searchInput.placeholder).toContain('Search tasks');
  });

  it('should_containSettingsLink_when_profileMenuOpen', () => {
    component.profileMenuOpen = true;
    fixture.detectChanges();

    const settingsLink = fixture.nativeElement.querySelector('.profile-menu a[href="/settings"]');
    expect(settingsLink).toBeTruthy();
    expect(settingsLink.textContent).toContain('Settings');
  });

  it('should_toggleProfileMenuOnProfileClick_when_profileAreaClicked', () => {
    expect(component.profileMenuOpen).toBeFalse();

    const profileDiv = fixture.nativeElement.querySelector('.header__profile');
    profileDiv.click();

    expect(component.profileMenuOpen).toBeTrue();
  });
});
