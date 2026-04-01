import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { SettingsComponent, SettingsTab } from './settings.component';
import { AuthService } from '../../core/auth/auth.service';
import { TenantService } from '../../core/services/tenant.service';
import { ProjectService } from '../../core/services/project.service';
import { PlatformService } from '../../core/services/platform.service';
import { UserSquadronService } from '../../core/services/user-squadron.service';
import { NotificationPreferenceService } from '../../core/services/notification-preference.service';
import { AgentConfigService } from '../../core/services/agent-config.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { Tenant, TenantPlan } from '../../core/models/tenant.model';

describe('SettingsComponent', () => {
  let component: SettingsComponent;
  let fixture: ComponentFixture<SettingsComponent>;
  let tenantServiceSpy: jasmine.SpyObj<TenantService>;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;
  let platformServiceSpy: jasmine.SpyObj<PlatformService>;
  let squadronServiceSpy: jasmine.SpyObj<UserSquadronService>;
  let notificationPrefServiceSpy: jasmine.SpyObj<NotificationPreferenceService>;
  let agentConfigServiceSpy: jasmine.SpyObj<AgentConfigService>;
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

    // ProjectConfigComponent dependencies
    projectServiceSpy = jasmine.createSpyObj('ProjectService', [
      'getProjectsByTenant', 'getWorkflowStates', 'getWorkflowMappings',
      'saveWorkflowMappings', 'createProject',
    ]);
    platformServiceSpy = jasmine.createSpyObj('PlatformService', [
      'getConnectionsByTenant', 'getProjectStatuses', 'createConnectionFromRequest', 'deleteConnection',
    ]);

    // SquadronConfigComponent dependencies
    squadronServiceSpy = jasmine.createSpyObj('UserSquadronService', [
      'getMySquadron', 'getLimits', 'addAgent', 'updateAgent', 'removeAgent', 'resetToDefaults',
    ]);

    // NotificationPreferencesComponent dependencies
    notificationPrefServiceSpy = jasmine.createSpyObj('NotificationPreferenceService', [
      'getPreferences', 'updatePreferences',
    ]);

    // AgentConfigComponent dependencies
    agentConfigServiceSpy = jasmine.createSpyObj('AgentConfigService', [
      'getConfig', 'updateConfig',
    ]);

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
      isAuthenticated: signal(true),
      isAdmin: signal(false),
      getAccessToken: jasmine.createSpy('getAccessToken').and.returnValue('mock-token'),
    };

    // Default return values so child components don't error on init
    projectServiceSpy.getProjectsByTenant.and.returnValue(of([]));
    projectServiceSpy.getWorkflowStates.and.returnValue(of([]));
    projectServiceSpy.getWorkflowMappings.and.returnValue(of([]));
    projectServiceSpy.createProject.and.returnValue(of({} as any));
    platformServiceSpy.getConnectionsByTenant.and.returnValue(of([]));
    platformServiceSpy.getProjectStatuses.and.returnValue(of([]));
    platformServiceSpy.createConnectionFromRequest.and.returnValue(of({} as any));
    platformServiceSpy.deleteConnection.and.returnValue(of(void 0));
    squadronServiceSpy.getMySquadron.and.returnValue(of([]));
    squadronServiceSpy.getLimits.and.returnValue(of({ maxAgentsPerUser: 8 }));
    notificationPrefServiceSpy.getPreferences.and.returnValue(throwError(() => new Error('mock')));
    agentConfigServiceSpy.getConfig.and.returnValue(throwError(() => new Error('mock')));

    await TestBed.configureTestingModule({
      imports: [SettingsComponent],
      providers: [
        { provide: TenantService, useValue: tenantServiceSpy },
        { provide: AuthService, useValue: authServiceStub },
        { provide: ProjectService, useValue: projectServiceSpy },
        { provide: PlatformService, useValue: platformServiceSpy },
        { provide: UserSquadronService, useValue: squadronServiceSpy },
        { provide: NotificationPreferenceService, useValue: notificationPrefServiceSpy },
        { provide: AgentConfigService, useValue: agentConfigServiceSpy },
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SettingsComponent);
    component = fixture.componentInstance;
  });

  // --- Component creation ---

  it('should create', () => {
    tenantServiceSpy.getTenant.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  // --- Tab system ---

  it('should have 5 tabs defined', () => {
    tenantServiceSpy.getTenant.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.tabs.length).toBe(5);
    expect(component.tabs.map(t => t.id)).toEqual([
      'general', 'providers-projects', 'squadron', 'notifications', 'agent-config',
    ]);
  });

  it('should default to general tab', () => {
    tenantServiceSpy.getTenant.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.activeTab()).toBe('general');
  });

  it('should switch tabs via setTab()', () => {
    tenantServiceSpy.getTenant.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    component.setTab('squadron');
    expect(component.activeTab()).toBe('squadron');

    component.setTab('notifications');
    expect(component.activeTab()).toBe('notifications');

    component.setTab('agent-config');
    expect(component.activeTab()).toBe('agent-config');

    component.setTab('providers-projects');
    expect(component.activeTab()).toBe('providers-projects');

    component.setTab('general');
    expect(component.activeTab()).toBe('general');
  });

  it('should render tab buttons in DOM', () => {
    tenantServiceSpy.getTenant.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const tabButtons = el.querySelectorAll('.settings-page__tab');
    expect(tabButtons.length).toBe(5);
    expect(tabButtons[0].textContent).toContain('General');
    expect(tabButtons[1].textContent).toContain('Providers & Projects');
    expect(tabButtons[2].textContent).toContain('Agent Squadron');
    expect(tabButtons[3].textContent).toContain('Notifications');
    expect(tabButtons[4].textContent).toContain('Agent Config');
  });

  it('should mark active tab button', () => {
    tenantServiceSpy.getTenant.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const activeTab = el.querySelector('.settings-page__tab--active');
    expect(activeTab).toBeTruthy();
    expect(activeTab!.textContent).toContain('General');
  });

  it('should switch active tab button on click', () => {
    tenantServiceSpy.getTenant.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const tabButtons = el.querySelectorAll('.settings-page__tab');

    (tabButtons[2] as HTMLElement).click();
    fixture.detectChanges();

    const activeTab = el.querySelector('.settings-page__tab--active');
    expect(activeTab!.textContent).toContain('Agent Squadron');
  });

  // --- Sub-component rendering ---

  it('should render sq-project-config when providers-projects tab is active', () => {
    tenantServiceSpy.getTenant.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.setTab('providers-projects');
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('sq-project-config')).toBeTruthy();
  });

  it('should render sq-squadron-config when squadron tab is active', () => {
    tenantServiceSpy.getTenant.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.setTab('squadron');
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('sq-squadron-config')).toBeTruthy();
  });

  it('should render sq-notification-preferences when notifications tab is active', () => {
    tenantServiceSpy.getTenant.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.setTab('notifications');
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('sq-notification-preferences')).toBeTruthy();
  });

  it('should render sq-agent-config when agent-config tab is active', () => {
    tenantServiceSpy.getTenant.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.setTab('agent-config');
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('sq-agent-config')).toBeTruthy();
  });

  it('should not render sub-components when general tab is active', () => {
    tenantServiceSpy.getTenant.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('sq-project-config')).toBeNull();
    expect(el.querySelector('sq-squadron-config')).toBeNull();
    expect(el.querySelector('sq-notification-preferences')).toBeNull();
    expect(el.querySelector('sq-agent-config')).toBeNull();
  });

  // --- General tab: loading settings ---

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

  // --- General tab: saving ---

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

  // --- General tab: DOM rendering ---

  it('should render settings header', () => {
    tenantServiceSpy.getTenant.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Settings');
  });

  it('should show loading spinner while loading', () => {
    // Use NEVER so the observable never completes, keeping loading=true
    tenantServiceSpy.getTenant.and.returnValue(NEVER);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(component.loading()).toBeTrue();
    expect(el.querySelector('.settings-page__loading')).toBeTruthy();
  });

  it('should show success alert after saving', () => {
    tenantServiceSpy.getTenant.and.returnValue(of(mockTenant));
    fixture.detectChanges();
    tenantServiceSpy.updateTenantSettings.and.returnValue(of(mockTenant));
    component.saveSettings();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.alert--success')).toBeTruthy();
    expect(el.textContent).toContain('Settings saved successfully');
  });
});
