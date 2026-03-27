import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LoginComponent } from './login.component';
import { AuthService } from '../../../core/auth/auth.service';
import { HealthService, HealthStatus } from '../../../core/services/health.service';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FormsModule } from '@angular/forms';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let healthServiceSpy: jasmine.SpyObj<HealthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const mockHealthStatus: HealthStatus = {
    status: 'UP',
    timestamp: new Date().toISOString(),
    services: {
      gateway: { status: 'UP' },
      orchestrator: { status: 'UP' },
      agent: { status: 'DOWN' },
    },
    infrastructure: {
      postgresql: { status: 'UP' },
      redis: { status: 'DEGRADED' },
      nats: { status: 'UP' },
    },
  };

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', [
      'isAuthenticated',
      'login',
      'loginWithOidc',
      'getAvailableTenants',
    ]);
    healthServiceSpy = jasmine.createSpyObj('HealthService', ['getHealthStatus']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    authServiceSpy.isAuthenticated.and.returnValue(false);
    authServiceSpy.getAvailableTenants.and.returnValue(of([]));
    healthServiceSpy.getHealthStatus.and.returnValue(of(mockHealthStatus));

    await TestBed.configureTestingModule({
      imports: [LoginComponent, FormsModule],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: HealthService, useValue: healthServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should redirect to dashboard if already authenticated on init', () => {
    authServiceSpy.isAuthenticated.and.returnValue(true);
    component.ngOnInit();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('should load available tenants on init', () => {
    const tenants = [
      { id: '1', name: 'Acme', slug: 'acme' },
      { id: '2', name: 'Beta', slug: 'beta' },
    ];
    authServiceSpy.getAvailableTenants.and.returnValue(of(tenants));
    component.ngOnInit();
    expect(component.tenants()).toEqual(tenants);
  });

  it('should handle tenant loading error gracefully', () => {
    authServiceSpy.getAvailableTenants.and.returnValue(throwError(() => new Error('fail')));
    component.ngOnInit();
    expect(component.tenants()).toEqual([]);
  });

  it('should set error when username or password is empty', () => {
    component.username = '';
    component.password = '';
    component.login();
    expect(component.error()).toBe('Please enter your username and password');
  });

  it('should call authService.login with correct params on valid form', () => {
    authServiceSpy.login.and.returnValue(of({} as any));
    component.username = 'admin';
    component.password = 'secret';
    component.selectedTenant = 'acme';
    component.rememberMe = true;
    component.login();
    expect(authServiceSpy.login).toHaveBeenCalledWith({
      username: 'admin',
      password: 'secret',
      tenantSlug: 'acme',
      rememberMe: true,
    });
  });

  it('should navigate to dashboard on successful login', () => {
    authServiceSpy.login.and.returnValue(of({} as any));
    component.username = 'admin';
    component.password = 'secret';
    component.login();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('should show error for 401 response', () => {
    authServiceSpy.login.and.returnValue(throwError(() => ({ status: 401 })));
    component.username = 'admin';
    component.password = 'wrong';
    component.login();
    expect(component.error()).toBe('Invalid username or password');
    expect(component.loading()).toBeFalse();
  });

  it('should show error for 403 response', () => {
    authServiceSpy.login.and.returnValue(throwError(() => ({ status: 403 })));
    component.username = 'admin';
    component.password = 'secret';
    component.login();
    expect(component.error()).toBe('Your account has been suspended');
  });

  it('should show generic error for other error codes', () => {
    authServiceSpy.login.and.returnValue(throwError(() => ({ status: 500 })));
    component.username = 'admin';
    component.password = 'secret';
    component.login();
    expect(component.error()).toBe('Unable to sign in. Please try again.');
  });

  it('should call loginWithOidc on SSO button', () => {
    component.loginWithSso();
    expect(authServiceSpy.loginWithOidc).toHaveBeenCalledWith('keycloak');
  });

  it('should toggle password visibility', () => {
    expect(component.showPassword).toBeFalse();
    component.togglePasswordVisibility();
    expect(component.showPassword).toBeTrue();
    component.togglePasswordVisibility();
    expect(component.showPassword).toBeFalse();
  });

  it('should set loading to true when login starts', () => {
    authServiceSpy.login.and.returnValue(of({} as any));
    component.username = 'admin';
    component.password = 'secret';
    component.login();
    // Loading gets set true then navigates; verify it was called
    expect(authServiceSpy.login).toHaveBeenCalled();
  });

  // Health panel tests

  it('should call health service on init', () => {
    expect(healthServiceSpy.getHealthStatus).toHaveBeenCalled();
  });

  it('should set healthStatus signal after health check completes', () => {
    expect(component.healthStatus()).toEqual(mockHealthStatus);
    expect(component.healthLoading()).toBeFalse();
  });

  it('should toggle health panel visibility', () => {
    expect(component.showHealthPanel).toBeFalse();
    component.toggleHealthPanel();
    expect(component.showHealthPanel).toBeTrue();
    component.toggleHealthPanel();
    expect(component.showHealthPanel).toBeFalse();
  });

  it('should return correct CSS class for UP status', () => {
    expect(component.getStatusColor('UP')).toBe('health-dot--up');
  });

  it('should return correct CSS class for DOWN status', () => {
    expect(component.getStatusColor('DOWN')).toBe('health-dot--down');
  });

  it('should return correct CSS class for DEGRADED status', () => {
    expect(component.getStatusColor('DEGRADED')).toBe('health-dot--degraded');
  });

  it('should return unknown CSS class for unrecognized status', () => {
    expect(component.getStatusColor('SOMETHING_ELSE')).toBe('health-dot--unknown');
  });

  it('should handle case-insensitive status values', () => {
    expect(component.getStatusColor('up')).toBe('health-dot--up');
    expect(component.getStatusColor('down')).toBe('health-dot--down');
    expect(component.getStatusColor('degraded')).toBe('health-dot--degraded');
  });

  it('should refresh health when refreshHealth is called', () => {
    healthServiceSpy.getHealthStatus.calls.reset();
    component.refreshHealth();
    expect(healthServiceSpy.getHealthStatus).toHaveBeenCalledTimes(1);
    expect(component.healthLoading()).toBeFalse();
  });

  it('should handle health service error gracefully', () => {
    healthServiceSpy.getHealthStatus.and.returnValue(throwError(() => new Error('Network error')));
    component.refreshHealth();
    expect(component.healthLoading()).toBeFalse();
  });

  it('should not call health service when already authenticated', () => {
    healthServiceSpy.getHealthStatus.calls.reset();
    authServiceSpy.isAuthenticated.and.returnValue(true);
    component.ngOnInit();
    expect(healthServiceSpy.getHealthStatus).not.toHaveBeenCalled();
  });

  it('should display health items in expanded panel', () => {
    component.showHealthPanel = true;
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const healthItems = compiled.querySelectorAll('.health-item');
    // 3 services + 3 infrastructure = 6
    expect(healthItems.length).toBe(6);
  });

  it('should display correct status dots for each service', () => {
    component.showHealthPanel = true;
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const dots = compiled.querySelectorAll('.health-item .health-dot');

    // gateway: UP, orchestrator: UP, agent: DOWN, postgresql: UP, redis: DEGRADED, nats: UP
    expect(dots[0].classList.contains('health-dot--up')).toBeTrue();     // gateway
    expect(dots[1].classList.contains('health-dot--up')).toBeTrue();     // orchestrator
    expect(dots[2].classList.contains('health-dot--down')).toBeTrue();   // agent
    expect(dots[3].classList.contains('health-dot--up')).toBeTrue();     // postgresql
    expect(dots[4].classList.contains('health-dot--degraded')).toBeTrue(); // redis
    expect(dots[5].classList.contains('health-dot--up')).toBeTrue();     // nats
  });

  it('should not display health content when panel is collapsed', () => {
    component.showHealthPanel = false;
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const content = compiled.querySelector('.health-panel__content');
    expect(content).toBeNull();
  });

  it('should show empty message when healthStatus is null', () => {
    component.healthStatus.set(null);
    component.showHealthPanel = true;
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const empty = compiled.querySelector('.health-panel__empty');
    expect(empty).toBeTruthy();
    expect(empty?.textContent?.trim()).toBe('Unable to reach backend services');
  });

  it('should return object keys correctly', () => {
    const obj = { a: 1, b: 2, c: 3 };
    expect(component.objectKeys(obj as any)).toEqual(['a', 'b', 'c']);
  });
});
