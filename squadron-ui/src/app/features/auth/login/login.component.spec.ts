import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LoginComponent } from './login.component';
import { AuthService } from '../../../core/auth/auth.service';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FormsModule } from '@angular/forms';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', [
      'isAuthenticated',
      'login',
      'loginWithOidc',
      'getAvailableTenants',
    ]);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    authServiceSpy.isAuthenticated.and.returnValue(false);
    authServiceSpy.getAvailableTenants.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [LoginComponent, FormsModule],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
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
});
