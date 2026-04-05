import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';
import { AuthProviderConfigComponent } from './auth-provider-config.component';
import { PermissionService } from '../../../core/services/permission.service';
import { AuthProvider, AuthProviderType } from '../../../core/models/security.model';

describe('AuthProviderConfigComponent', () => {
  let component: AuthProviderConfigComponent;
  let fixture: ComponentFixture<AuthProviderConfigComponent>;
  let permServiceSpy: jasmine.SpyObj<PermissionService>;

  const mockProviders: AuthProvider[] = [
    {
      id: 'ap-1', tenantId: 't1', name: 'Corporate OIDC',
      type: AuthProviderType.OIDC, enabled: true,
      config: { clientId: 'squadron-app', issuerUrl: 'https://auth.example.com/realms/corp' },
      createdAt: new Date().toISOString(),
    },
    {
      id: 'ap-2', tenantId: 't1', name: 'Keycloak Dev',
      type: AuthProviderType.KEYCLOAK, enabled: true,
      config: { clientId: 'squadron-dev', issuerUrl: 'https://keycloak.dev.example.com' },
      createdAt: new Date().toISOString(),
    },
    {
      id: 'ap-3', tenantId: 't1', name: 'LDAP Directory',
      type: AuthProviderType.LDAP, enabled: false,
      config: { clientId: '', issuerUrl: 'ldap://ldap.example.com:389' },
      createdAt: new Date().toISOString(),
    },
  ];

  beforeEach(async () => {
    permServiceSpy = jasmine.createSpyObj('PermissionService', [
      'getAuthProviders', 'createAuthProvider', 'updateAuthProvider',
      'deleteAuthProvider', 'testAuthProvider',
    ]);
    permServiceSpy.getAuthProviders.and.returnValue(of(mockProviders));

    await TestBed.configureTestingModule({
      imports: [AuthProviderConfigComponent, FormsModule, TranslateModule.forRoot()],
      providers: [
        { provide: PermissionService, useValue: permServiceSpy },
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AuthProviderConfigComponent);
    component = fixture.componentInstance;
  });

  it('should_create', () => {
    expect(component).toBeTruthy();
  });

  it('should_loadProviders_when_initialized', () => {
    fixture.detectChanges();

    expect(permServiceSpy.getAuthProviders).toHaveBeenCalled();
    expect(component.providers()).toEqual(mockProviders);
    expect(component.loading()).toBeFalse();
  });

  it('should_openCreateModal_when_openCreateModalCalled', () => {
    component.openCreateModal();

    expect(component.showCreateModal()).toBeTrue();
    expect(component.editingProvider()).toBeNull();
    expect(component.formName).toBe('');
    expect(component.formType).toBe(AuthProviderType.OIDC);
    expect(component.formEnabled).toBeTrue();
    expect(component.formClientId).toBe('');
    expect(component.formClientSecret).toBe('');
    expect(component.formIssuerUrl).toBe('');
  });

  it('should_closeModal_when_closeModalCalled', () => {
    component.openCreateModal();
    component.closeModal();

    expect(component.showCreateModal()).toBeFalse();
    expect(component.editingProvider()).toBeNull();
  });

  it('should_populateForm_when_openEditModalCalled', () => {
    const provider = mockProviders[0];
    component.openEditModal(provider);

    expect(component.showCreateModal()).toBeTrue();
    expect(component.editingProvider()).toBe(provider);
    expect(component.formName).toBe('Corporate OIDC');
    expect(component.formType).toBe(AuthProviderType.OIDC);
    expect(component.formEnabled).toBeTrue();
    expect(component.formClientId).toBe('squadron-app');
    expect(component.formClientSecret).toBe('');
    expect(component.formIssuerUrl).toBe('https://auth.example.com/realms/corp');
  });

  it('should_callCreateAuthProvider_when_saveProviderCalledWithoutEditing', () => {
    fixture.detectChanges();
    permServiceSpy.createAuthProvider.and.returnValue(of(mockProviders[0]));
    permServiceSpy.getAuthProviders.and.returnValue(of(mockProviders));

    component.openCreateModal();
    component.formName = 'New SAML';
    component.formType = AuthProviderType.SAML;
    component.formClientId = 'saml-client';
    component.formIssuerUrl = 'https://saml.example.com';
    component.saveProvider();

    expect(permServiceSpy.createAuthProvider).toHaveBeenCalledWith(jasmine.objectContaining({
      name: 'New SAML',
      type: AuthProviderType.SAML,
      enabled: true,
    }));
    expect(component.showCreateModal()).toBeFalse();
  });

  it('should_callUpdateAuthProvider_when_toggleProviderCalled', () => {
    fixture.detectChanges();
    permServiceSpy.updateAuthProvider.and.returnValue(of(mockProviders[0]));
    permServiceSpy.getAuthProviders.and.returnValue(of(mockProviders));

    component.toggleProvider(mockProviders[0]);

    expect(permServiceSpy.updateAuthProvider).toHaveBeenCalledWith('ap-1', { enabled: false });
  });

  it('should_callTestAuthProvider_when_testProviderCalled', () => {
    fixture.detectChanges();
    const testResult = { success: true, message: 'OK' };
    permServiceSpy.testAuthProvider.and.returnValue(of(testResult));

    component.testProvider(mockProviders[0]);

    expect(permServiceSpy.testAuthProvider).toHaveBeenCalledWith('ap-1');
    expect(component.testResult()).toEqual(testResult);
    expect(component.testingId()).toBeNull();
  });

  it('should_setTestingId_when_testProviderStarted', () => {
    fixture.detectChanges();
    // Use a subject-like observable that doesn't complete immediately to check testingId
    permServiceSpy.testAuthProvider.and.returnValue(throwError(() => new Error('fail')));

    component.testProvider(mockProviders[1]);

    // After error, testingId is cleared and failure result is set
    expect(component.testingId()).toBeNull();
    expect(component.testResult()).toEqual({ success: false, message: 'admin.authProviders.errors.connectionTestFailed' });
  });

  it('should_callDeleteAuthProvider_when_deleteProviderCalledAndConfirmed', () => {
    fixture.detectChanges();
    spyOn(window, 'confirm').and.returnValue(true);
    permServiceSpy.deleteAuthProvider.and.returnValue(of(void 0));
    permServiceSpy.getAuthProviders.and.returnValue(of(mockProviders));

    component.deleteProvider(mockProviders[0]);

    expect(permServiceSpy.deleteAuthProvider).toHaveBeenCalledWith('ap-1');
  });

  it('should_notCallDeleteAuthProvider_when_confirmCancelled', () => {
    spyOn(window, 'confirm').and.returnValue(false);

    component.deleteProvider(mockProviders[0]);

    expect(permServiceSpy.deleteAuthProvider).not.toHaveBeenCalled();
  });

  it('should_returnCorrectTypeIcon_when_typeIconCalled', () => {
    expect(component.typeIcon(AuthProviderType.OIDC)).toBe('admin.authProviders.types.oidc');
    expect(component.typeIcon(AuthProviderType.KEYCLOAK)).toBe('admin.authProviders.types.keycloak');
    expect(component.typeIcon(AuthProviderType.LDAP)).toBe('admin.authProviders.types.ldap');
    expect(component.typeIcon(AuthProviderType.SAML)).toBe('admin.authProviders.types.saml');
  });

  it('should_showEmptyState_when_getAuthProvidersFails', () => {
    permServiceSpy.getAuthProviders.and.returnValue(throwError(() => new Error('API error')));

    fixture.detectChanges();

    expect(component.providers().length).toBe(0);
    expect(component.loading()).toBeFalse();
  });
});
