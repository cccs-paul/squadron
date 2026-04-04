import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { HeaderComponent } from './header.component';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthUser } from '../../../core/auth/auth.models';
import { NotificationService } from '../../../core/services/notification.service';
import { I18nService } from '../../../core/services/i18n.service';

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

    const i18nServiceMock = {
      currentLang: signal('en'),
      currentLangFlag: signal('EN'),
      currentLangLabel: signal('English'),
      supportedLanguages: [
        { code: 'en', label: 'English', flag: 'EN' },
        { code: 'fr', label: 'Francais', flag: 'FR' },
      ],
      switchLanguage: jasmine.createSpy('switchLanguage'),
      init: jasmine.createSpy('init'),
    };

    await TestBed.configureTestingModule({
      imports: [HeaderComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: NotificationService, useValue: notificationServiceSpy },
        { provide: I18nService, useValue: i18nServiceMock },
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
    // With TranslateModule.forRoot() and no translations loaded, the pipe returns the key
    expect(searchInput.placeholder).toBe('header.searchPlaceholder');
  });

  it('should_containSettingsLink_when_profileMenuOpen', () => {
    component.profileMenuOpen = true;
    fixture.detectChanges();

    const settingsLink = fixture.nativeElement.querySelector('.profile-menu a[href="/settings"]');
    expect(settingsLink).toBeTruthy();
    // Translate pipe returns the key when no translations are loaded
    expect(settingsLink.textContent).toContain('header.settings');
  });

  it('should_toggleProfileMenuOnProfileClick_when_profileAreaClicked', () => {
    expect(component.profileMenuOpen).toBeFalse();

    const profileDiv = fixture.nativeElement.querySelector('.header__profile');
    profileDiv.click();

    expect(component.profileMenuOpen).toBeTrue();
  });

  it('should_toggleLangMenu_when_toggleLangMenuCalled', () => {
    expect(component.langMenuOpen).toBeFalse();

    component.toggleLangMenu();
    expect(component.langMenuOpen).toBeTrue();

    component.toggleLangMenu();
    expect(component.langMenuOpen).toBeFalse();
  });

  it('should_closeLangMenu_when_profileMenuOpened', () => {
    component.langMenuOpen = true;
    component.toggleProfileMenu();
    expect(component.langMenuOpen).toBeFalse();
    expect(component.profileMenuOpen).toBeTrue();
  });

  it('should_closeProfileMenu_when_langMenuOpened', () => {
    component.profileMenuOpen = true;
    component.toggleLangMenu();
    expect(component.profileMenuOpen).toBeFalse();
    expect(component.langMenuOpen).toBeTrue();
  });

  it('should_showLangMenu_when_langMenuOpenIsTrue', () => {
    component.langMenuOpen = true;
    fixture.detectChanges();

    const langMenu = fixture.nativeElement.querySelector('.lang-menu');
    expect(langMenu).toBeTruthy();
  });

  it('should_renderLanguageOptions_when_langMenuOpen', () => {
    component.langMenuOpen = true;
    fixture.detectChanges();

    const langItems = fixture.nativeElement.querySelectorAll('.lang-menu__item');
    expect(langItems.length).toBe(2);
  });
});
