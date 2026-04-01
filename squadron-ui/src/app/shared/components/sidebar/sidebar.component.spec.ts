import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { SidebarComponent } from './sidebar.component';
import { AuthService } from '../../../core/auth/auth.service';

// Test host to control the collapsed input
@Component({
  standalone: true,
  imports: [SidebarComponent],
  template: `<sq-sidebar [collapsed]="collapsed" />`,
})
class TestHostComponent {
  collapsed = false;
}

describe('SidebarComponent', () => {
  let hostFixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;
  let isAdminSignal: ReturnType<typeof signal<boolean>>;

  function setupWithAdmin(isAdmin: boolean) {
    isAdminSignal = signal(isAdmin);

    const authServiceSpy = jasmine.createSpyObj('AuthService', ['getAccessToken'], {
      isAuthenticated: signal(true).asReadonly(),
      isAdmin: isAdminSignal.asReadonly(),
      user: signal(null).asReadonly(),
    });

    TestBed.configureTestingModule({
      imports: [TestHostComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });

    hostFixture = TestBed.createComponent(TestHostComponent);
    host = hostFixture.componentInstance;
    hostFixture.detectChanges();
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should_beCreated', () => {
    setupWithAdmin(false);
    const sidebar = hostFixture.nativeElement.querySelector('sq-sidebar');
    expect(sidebar).toBeTruthy();
  });

  it('should_renderNavItems_when_created', () => {
    setupWithAdmin(false);

    const navLinks = hostFixture.nativeElement.querySelectorAll('.sidebar__link');
    // 5 standard nav items (Dashboard, Tasks, Projects, Reviews, Settings)
    expect(navLinks.length).toBe(5);
  });

  it('should_displayNavLabels_when_notCollapsed', () => {
    setupWithAdmin(false);
    host.collapsed = false;
    hostFixture.detectChanges();

    const labels = hostFixture.nativeElement.querySelectorAll('.sidebar__label');
    expect(labels.length).toBe(5);
    expect(labels[0].textContent.trim()).toBe('Dashboard');
    expect(labels[1].textContent.trim()).toBe('Tasks');
    expect(labels[2].textContent.trim()).toBe('Projects');
    expect(labels[3].textContent.trim()).toBe('Reviews');
    expect(labels[4].textContent.trim()).toBe('Settings');
  });

  it('should_hideLabels_when_collapsed', () => {
    setupWithAdmin(false);
    host.collapsed = true;
    hostFixture.detectChanges();

    const labels = hostFixture.nativeElement.querySelectorAll('.sidebar__label');
    expect(labels.length).toBe(0);
  });

  it('should_showAdminItems_when_isAdmin', () => {
    setupWithAdmin(true);

    const navLinks = hostFixture.nativeElement.querySelectorAll('.sidebar__link');
    // 5 standard + 5 admin items = 10
    expect(navLinks.length).toBe(10);
  });

  it('should_notShowAdminItems_when_notAdmin', () => {
    setupWithAdmin(false);

    const navLinks = hostFixture.nativeElement.querySelectorAll('.sidebar__link');
    expect(navLinks.length).toBe(5);
  });

  it('should_haveCorrectRoutes_when_navItemsRendered', () => {
    setupWithAdmin(false);

    const navLinks = hostFixture.nativeElement.querySelectorAll('.sidebar__link');
    const routes = Array.from(navLinks).map((el: any) => el.getAttribute('href'));

    expect(routes).toEqual(['/dashboard', '/tasks', '/projects', '/reviews', '/settings']);
  });

  it('should_addCollapsedClass_when_collapsed', () => {
    setupWithAdmin(false);
    host.collapsed = true;
    hostFixture.detectChanges();

    const aside = hostFixture.nativeElement.querySelector('.sidebar');
    expect(aside.classList.contains('sidebar--collapsed')).toBeTrue();
  });

  it('should_notHaveCollapsedClass_when_notCollapsed', () => {
    setupWithAdmin(false);
    host.collapsed = false;
    hostFixture.detectChanges();

    const aside = hostFixture.nativeElement.querySelector('.sidebar');
    expect(aside.classList.contains('sidebar--collapsed')).toBeFalse();
  });

  it('should_showLogo_when_rendered', () => {
    setupWithAdmin(false);

    const logo = hostFixture.nativeElement.querySelector('.sidebar__logo-img');
    expect(logo).toBeTruthy();
  });

  it('should_showLogoText_when_notCollapsed', () => {
    setupWithAdmin(false);
    host.collapsed = false;
    hostFixture.detectChanges();

    const logoText = hostFixture.nativeElement.querySelector('.sidebar__logo-text');
    expect(logoText).toBeTruthy();
    expect(logoText.textContent.trim()).toBe('Squadron');
  });

  it('should_hideLogoText_when_collapsed', () => {
    setupWithAdmin(false);
    host.collapsed = true;
    hostFixture.detectChanges();

    const logoText = hostFixture.nativeElement.querySelector('.sidebar__logo-text');
    expect(logoText).toBeNull();
  });
});
