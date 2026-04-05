import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { OidcCallbackComponent } from './oidc-callback.component';
import { AuthService } from '../../../core/auth/auth.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';

describe('OidcCallbackComponent', () => {
  let component: OidcCallbackComponent;
  let fixture: ComponentFixture<OidcCallbackComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  function createComponent(queryParams: Record<string, string>) {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: {
        snapshot: {
          queryParamMap: convertToParamMap(queryParams),
        },
      },
    });

    fixture = TestBed.createComponent(OidcCallbackComponent);
    component = fixture.componentInstance;
  }

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['handleOidcCallback']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [OidcCallbackComponent, TranslateModule.forRoot()],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap({ code: 'abc', state: 'xyz' }),
            },
          },
        },
      ],
    }).compileComponents();
  });

  it('should create', () => {
    createComponent({ code: 'abc', state: 'xyz' });
    expect(component).toBeTruthy();
  });

  it('should call handleOidcCallback with code and state', () => {
    authServiceSpy.handleOidcCallback.and.returnValue(of({} as any));
    createComponent({ code: 'abc', state: 'xyz' });
    fixture.detectChanges();
    expect(authServiceSpy.handleOidcCallback).toHaveBeenCalledWith('abc', 'xyz');
  });

  it('should navigate to dashboard on successful callback', () => {
    authServiceSpy.handleOidcCallback.and.returnValue(of({} as any));
    createComponent({ code: 'abc', state: 'xyz' });
    fixture.detectChanges();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('should navigate to login on callback error', () => {
    authServiceSpy.handleOidcCallback.and.returnValue(throwError(() => new Error('fail')));
    createComponent({ code: 'abc', state: 'xyz' });
    fixture.detectChanges();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('should navigate to login when code is missing', () => {
    createComponent({ state: 'xyz' });
    fixture.detectChanges();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('should navigate to login when state is missing', () => {
    createComponent({ code: 'abc' });
    fixture.detectChanges();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('should navigate to login when both code and state are missing', () => {
    createComponent({});
    fixture.detectChanges();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('should render spinner text', () => {
    authServiceSpy.handleOidcCallback.and.returnValue(of({} as any));
    createComponent({ code: 'abc', state: 'xyz' });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('auth.oidcCallback.completingSignIn');
  });
});
