import { TestBed } from '@angular/core/testing';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { authGuard, adminGuard } from './auth.guard';

describe('Auth Guards', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  const mockRoute = {} as ActivatedRouteSnapshot;
  const mockState = {} as RouterStateSnapshot;

  beforeEach(() => {
    localStorage.clear();

    authServiceSpy = jasmine.createSpyObj('AuthService', ['getAccessToken'], {
      isAuthenticated: jasmine.createSpy('isAuthenticated').and.returnValue(false),
      isAdmin: jasmine.createSpy('isAdmin').and.returnValue(false),
    });

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });

    router = TestBed.inject(Router);
  });

  afterEach(() => {
    localStorage.clear();
  });

  describe('authGuard', () => {
    it('should_returnTrue_when_userIsAuthenticated', () => {
      (authServiceSpy.isAuthenticated as jasmine.Spy).and.returnValue(true);

      const result = TestBed.runInInjectionContext(() => authGuard(mockRoute, mockState));

      expect(result).toBeTrue();
    });

    it('should_returnFalse_when_userIsNotAuthenticated', () => {
      (authServiceSpy.isAuthenticated as jasmine.Spy).and.returnValue(false);

      const result = TestBed.runInInjectionContext(() => authGuard(mockRoute, mockState));

      expect(result).toBeFalse();
    });

    it('should_navigateToLogin_when_userIsNotAuthenticated', () => {
      (authServiceSpy.isAuthenticated as jasmine.Spy).and.returnValue(false);
      const navigateSpy = spyOn(router, 'navigate');

      TestBed.runInInjectionContext(() => authGuard(mockRoute, mockState));

      expect(navigateSpy).toHaveBeenCalledWith(['/login']);
    });

    it('should_notNavigate_when_userIsAuthenticated', () => {
      (authServiceSpy.isAuthenticated as jasmine.Spy).and.returnValue(true);
      const navigateSpy = spyOn(router, 'navigate');

      TestBed.runInInjectionContext(() => authGuard(mockRoute, mockState));

      expect(navigateSpy).not.toHaveBeenCalled();
    });
  });

  describe('adminGuard', () => {
    it('should_returnTrue_when_userIsAuthenticatedAndAdmin', () => {
      (authServiceSpy.isAuthenticated as jasmine.Spy).and.returnValue(true);
      (authServiceSpy.isAdmin as jasmine.Spy).and.returnValue(true);

      const result = TestBed.runInInjectionContext(() => adminGuard(mockRoute, mockState));

      expect(result).toBeTrue();
    });

    it('should_returnFalse_when_userIsNotAuthenticated', () => {
      (authServiceSpy.isAuthenticated as jasmine.Spy).and.returnValue(false);

      const result = TestBed.runInInjectionContext(() => adminGuard(mockRoute, mockState));

      expect(result).toBeFalse();
    });

    it('should_navigateToLogin_when_userIsNotAuthenticated', () => {
      (authServiceSpy.isAuthenticated as jasmine.Spy).and.returnValue(false);
      const navigateSpy = spyOn(router, 'navigate');

      TestBed.runInInjectionContext(() => adminGuard(mockRoute, mockState));

      expect(navigateSpy).toHaveBeenCalledWith(['/login']);
    });

    it('should_navigateToDashboard_when_authenticatedButNotAdmin', () => {
      (authServiceSpy.isAuthenticated as jasmine.Spy).and.returnValue(true);
      (authServiceSpy.isAdmin as jasmine.Spy).and.returnValue(false);
      const navigateSpy = spyOn(router, 'navigate');

      TestBed.runInInjectionContext(() => adminGuard(mockRoute, mockState));

      expect(navigateSpy).toHaveBeenCalledWith(['/dashboard']);
    });

    it('should_returnFalse_when_authenticatedButNotAdmin', () => {
      (authServiceSpy.isAuthenticated as jasmine.Spy).and.returnValue(true);
      (authServiceSpy.isAdmin as jasmine.Spy).and.returnValue(false);
      spyOn(router, 'navigate');

      const result = TestBed.runInInjectionContext(() => adminGuard(mockRoute, mockState));

      expect(result).toBeFalse();
    });

    it('should_notNavigate_when_userIsAuthenticatedAndAdmin', () => {
      (authServiceSpy.isAuthenticated as jasmine.Spy).and.returnValue(true);
      (authServiceSpy.isAdmin as jasmine.Spy).and.returnValue(true);
      const navigateSpy = spyOn(router, 'navigate');

      TestBed.runInInjectionContext(() => adminGuard(mockRoute, mockState));

      expect(navigateSpy).not.toHaveBeenCalled();
    });
  });
});
