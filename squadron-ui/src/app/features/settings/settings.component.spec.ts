import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { SettingsComponent } from './settings.component';
import { AuthService } from '../../core/auth/auth.service';
import { TenantService } from '../../core/services/tenant.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { Tenant, TenantPlan } from '../../core/models/tenant.model';

describe('SettingsComponent', () => {
  let component: SettingsComponent;
  let fixture: ComponentFixture<SettingsComponent>;
  let tenantServiceSpy: jasmine.SpyObj<TenantService>;
  let authServiceStub: any;

  const mockTenant: Tenant = {
    id: '1',
    name: 'Acme Corp',
    slug: 'acme',
    plan: TenantPlan.TEAM,
    settings: {
      maxUsers: 100,
      maxProjects: 50,
      aiEnabled: true,
      defaultBranch: 'develop',
      autoReview: false,
    },
    createdAt: new Date().toISOString(),
  };

  beforeEach(async () => {
    tenantServiceSpy = jasmine.createSpyObj('TenantService', ['getTenant', 'updateTenantSettings']);
    authServiceStub = {
      user: signal({
        id: 'u1',
        username: 'admin',
        email: 'admin@acme.com',
        displayName: 'Admin User',
        tenantId: '1',
        tenantName: 'Acme Corp',
        roles: ['ADMIN'],
        permissions: [],
      }),
    };

    await TestBed.configureTestingModule({
      imports: [SettingsComponent],
      providers: [
        { provide: TenantService, useValue: tenantServiceSpy },
        { provide: AuthService, useValue: authServiceStub },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SettingsComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    tenantServiceSpy.getTenant.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should load tenant settings from service', () => {
    tenantServiceSpy.getTenant.and.returnValue(of(mockTenant));
    fixture.detectChanges();
    expect(component.tenant()!.name).toBe('Acme Corp');
    expect(component.settingsDefaultBranch).toBe('develop');
    expect(component.settingsAutoReview).toBeFalse();
    expect(component.settingsMaxUsers).toBe(100);
    expect(component.loading()).toBeFalse();
  });

  it('should fall back to mock settings on error', () => {
    tenantServiceSpy.getTenant.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.tenant()).toBeTruthy();
    expect(component.tenant()!.name).toBe('Acme Corp');
    expect(component.settingsDefaultBranch).toBe('main');
    expect(component.loading()).toBeFalse();
  });

  it('should populate profile form from user', () => {
    tenantServiceSpy.getTenant.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.profileDisplayName).toBe('Admin User');
    expect(component.profileEmail).toBe('admin@acme.com');
  });

  it('should save settings via service', () => {
    tenantServiceSpy.getTenant.and.returnValue(of(mockTenant));
    fixture.detectChanges();

    const updatedTenant = { ...mockTenant };
    tenantServiceSpy.updateTenantSettings.and.returnValue(of(updatedTenant));
    component.saveSettings();
    expect(tenantServiceSpy.updateTenantSettings).toHaveBeenCalled();
    expect(component.saving()).toBeFalse();
    expect(component.saveSuccess()).toBeTrue();
  });

  it('should set saveSuccess to false after 3 seconds', fakeAsync(() => {
    tenantServiceSpy.getTenant.and.returnValue(of(mockTenant));
    fixture.detectChanges();

    tenantServiceSpy.updateTenantSettings.and.returnValue(of(mockTenant));
    component.saveSettings();
    expect(component.saveSuccess()).toBeTrue();
    tick(3000);
    expect(component.saveSuccess()).toBeFalse();
  }));

  it('should handle save error with optimistic success', () => {
    tenantServiceSpy.getTenant.and.returnValue(of(mockTenant));
    fixture.detectChanges();

    tenantServiceSpy.updateTenantSettings.and.returnValue(throwError(() => new Error('fail')));
    component.saveSettings();
    expect(component.saving()).toBeFalse();
    expect(component.saveSuccess()).toBeTrue();
  });

  it('should render settings header', () => {
    tenantServiceSpy.getTenant.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Settings');
  });
});
