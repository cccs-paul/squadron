import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ProjectConfigComponent } from './project-config.component';
import { ProjectService } from '../../../core/services/project.service';
import { PlatformService } from '../../../core/services/platform.service';
import { AuthService } from '../../../core/auth/auth.service';
import { of, throwError } from 'rxjs';
import { Project, WorkflowMapping } from '../../../core/models/project.model';
import { PlatformConnection, PlatformConnectionType, ConnectionStatus } from '../../../core/models/security.model';

describe('ProjectConfigComponent', () => {
  let component: ProjectConfigComponent;
  let fixture: ComponentFixture<ProjectConfigComponent>;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;
  let platformServiceSpy: jasmine.SpyObj<PlatformService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const mockUser = {
    id: 'u1', username: 'fry', email: 'fry@planetexpress.com',
    displayName: 'Philip J. Fry', tenantId: 't1', tenantName: 'Planet Express',
    roles: ['developer'], permissions: [],
  };

  const mockConnections: PlatformConnection[] = [
    {
      id: 'pc-1', tenantId: 't1', name: 'Jira Cloud - Production',
      platformType: PlatformConnectionType.JIRA, baseUrl: 'https://myorg.atlassian.net',
      status: ConnectionStatus.CONNECTED, config: {}, createdAt: new Date().toISOString(),
    },
    {
      id: 'pc-2', tenantId: 't1', name: 'GitHub - Organization',
      platformType: PlatformConnectionType.GITHUB, baseUrl: 'https://api.github.com',
      status: ConnectionStatus.CONNECTED, config: {}, createdAt: new Date().toISOString(),
    },
  ];

  const mockProjects: Project[] = [
    {
      id: 'p1', tenantId: 't1', name: 'project-alpha', description: 'Alpha project',
      defaultBranch: 'main', connectionId: 'pc-1', externalProjectId: 'SQ',
      taskCount: 10, activeTaskCount: 3, members: [], createdAt: new Date().toISOString(),
    },
    {
      id: 'p2', tenantId: 't1', name: 'project-beta', description: 'Beta project',
      defaultBranch: 'main', taskCount: 5, activeTaskCount: 1, members: [],
      createdAt: new Date().toISOString(),
    },
  ];

  const mockStates = ['BACKLOG', 'PRIORITIZED', 'PLANNING', 'PROPOSE_CODE', 'REVIEW', 'QA', 'MERGE', 'DONE'];

  const mockMappings: WorkflowMapping[] = [
    { internalState: 'BACKLOG', externalStatus: 'To Do' },
    { internalState: 'REVIEW', externalStatus: 'Code Review' },
  ];

  const mockRemoteStatuses = ['To Do', 'In Progress', 'Code Review', 'QA Testing', 'Done'];

  beforeEach(async () => {
    projectServiceSpy = jasmine.createSpyObj('ProjectService', [
      'getProjectsByTenant', 'getWorkflowStates', 'getWorkflowMappings',
      'saveWorkflowMappings', 'createProject',
    ]);
    platformServiceSpy = jasmine.createSpyObj('PlatformService', [
      'getConnectionsByTenant', 'getProjectStatuses', 'createConnectionFromRequest', 'deleteConnection',
    ]);
    authServiceSpy = jasmine.createSpyObj('AuthService', ['getAccessToken'], {
      user: jasmine.createSpy('user').and.returnValue(mockUser),
      isAuthenticated: jasmine.createSpy('isAuthenticated').and.returnValue(true),
      isAdmin: jasmine.createSpy('isAdmin').and.returnValue(false),
    });

    projectServiceSpy.getProjectsByTenant.and.returnValue(of(mockProjects));
    projectServiceSpy.getWorkflowStates.and.returnValue(of(mockStates));
    projectServiceSpy.getWorkflowMappings.and.returnValue(of(mockMappings));
    projectServiceSpy.saveWorkflowMappings.and.returnValue(of(mockMappings));
    projectServiceSpy.createProject.and.returnValue(of(mockProjects[0]));
    platformServiceSpy.getConnectionsByTenant.and.returnValue(of(mockConnections));
    platformServiceSpy.getProjectStatuses.and.returnValue(of(mockRemoteStatuses));
    platformServiceSpy.createConnectionFromRequest.and.returnValue(of(mockConnections[0]));
    platformServiceSpy.deleteConnection.and.returnValue(of(void 0));

    await TestBed.configureTestingModule({
      imports: [ProjectConfigComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ProjectService, useValue: projectServiceSpy },
        { provide: PlatformService, useValue: platformServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProjectConfigComponent);
    component = fixture.componentInstance;
  });

  it('should_create', () => {
    expect(component).toBeTruthy();
  });

  it('should_loadDataOnInit', () => {
    fixture.detectChanges();

    expect(projectServiceSpy.getProjectsByTenant).toHaveBeenCalledWith('t1');
    expect(projectServiceSpy.getWorkflowStates).toHaveBeenCalled();
    expect(platformServiceSpy.getConnectionsByTenant).toHaveBeenCalledWith('t1');
    expect(component.loading()).toBeFalse();
    expect(component.connections().length).toBe(2);
    expect(component.projectStates().length).toBe(2);
    expect(component.workflowStates().length).toBe(8);
  });

  it('should_defaultToProvidersTab', () => {
    fixture.detectChanges();
    expect(component.activeTab()).toBe('providers');
  });

  it('should_switchTabs', () => {
    fixture.detectChanges();
    component.setTab('projects');
    expect(component.activeTab()).toBe('projects');
    component.setTab('providers');
    expect(component.activeTab()).toBe('providers');
  });

  it('should_showErrorState_when_notAuthenticated', () => {
    (authServiceSpy.user as jasmine.Spy).and.returnValue(null);
    fixture.detectChanges();
    expect(component.loadError()).toBe('Not authenticated');
    expect(component.loading()).toBeFalse();
  });

  it('should_handleApiError_withEmptyData', () => {
    projectServiceSpy.getProjectsByTenant.and.returnValue(throwError(() => new Error('fail')));
    projectServiceSpy.getWorkflowStates.and.returnValue(throwError(() => new Error('fail')));
    platformServiceSpy.getConnectionsByTenant.and.returnValue(throwError(() => new Error('fail')));

    fixture.detectChanges();

    expect(component.loading()).toBeFalse();
    expect(component.connections().length).toBe(0);
    expect(component.projectStates().length).toBe(0);
    expect(component.workflowStates().length).toBe(8);
  });

  // --- Provider tab tests ---

  it('should_toggleProviderForm', () => {
    fixture.detectChanges();
    expect(component.showProviderForm()).toBeFalse();
    component.toggleProviderForm();
    expect(component.showProviderForm()).toBeTrue();
    component.toggleProviderForm();
    expect(component.showProviderForm()).toBeFalse();
  });

  it('should_returnAuthTypeOptions_forPlatformType', () => {
    fixture.detectChanges();
    component.providerForm.platformType = 'JIRA';
    const options = component.getAuthTypeOptions();
    expect(options.length).toBe(2);
    expect(options[0].label).toBe('API Token');
    expect(options[1].label).toBe('PAT');
  });

  it('should_returnAuthFields_forSelectedAuthType', () => {
    fixture.detectChanges();
    component.providerForm.platformType = 'JIRA';
    component.providerForm.authType = 'API Token';
    const fields = component.getAuthFields();
    expect(fields.length).toBe(2);
    expect(fields[0].key).toBe('email');
    expect(fields[1].key).toBe('apiToken');
  });

  it('should_resetAuthType_when_platformTypeChanges', () => {
    fixture.detectChanges();
    component.providerForm.platformType = 'GITHUB';
    component.providerForm.authType = 'PAT';
    component.providerForm.credentials = { pat: 'abc' };

    component.onPlatformTypeChange();

    expect(component.providerForm.authType).toBe('PAT');
    expect(component.providerForm.credentials).toEqual({});
  });

  it('should_resetCredentials_when_authTypeChanges', () => {
    fixture.detectChanges();
    component.providerForm.credentials = { email: 'test@test.com', apiToken: 'abc' };
    component.onAuthTypeChange();
    expect(component.providerForm.credentials).toEqual({});
  });

  it('should_canSaveProvider_returnFalse_when_formIncomplete', () => {
    fixture.detectChanges();
    expect(component.canSaveProvider()).toBeFalse();
  });

  it('should_canSaveProvider_returnTrue_when_formComplete', () => {
    fixture.detectChanges();
    component.providerForm = {
      name: 'My Jira', platformType: 'JIRA', baseUrl: 'https://myorg.atlassian.net',
      authType: 'API Token', credentials: { email: 'me@test.com', apiToken: 'abc123' },
    };
    expect(component.canSaveProvider()).toBeTrue();
  });

  it('should_saveProvider_andAddToConnectionsList', () => {
    fixture.detectChanges();
    const initialCount = component.connections().length;
    component.providerForm = {
      name: 'My Jira', platformType: 'JIRA', baseUrl: 'https://myorg.atlassian.net',
      authType: 'API Token', credentials: { email: 'me@test.com', apiToken: 'abc123' },
    };

    component.saveProvider();

    expect(platformServiceSpy.createConnectionFromRequest).toHaveBeenCalled();
    expect(component.connections().length).toBe(initialCount + 1);
    expect(component.showProviderForm()).toBeFalse();
    expect(component.savingProvider()).toBeFalse();
  });

  it('should_showError_when_saveProviderFails', () => {
    platformServiceSpy.createConnectionFromRequest.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.providerForm = {
      name: 'My Jira', platformType: 'JIRA', baseUrl: 'https://myorg.atlassian.net',
      authType: 'API Token', credentials: { email: 'me@test.com', apiToken: 'abc123' },
    };

    component.saveProvider();

    expect(component.providerSaveError()).toBeTruthy();
    expect(component.savingProvider()).toBeFalse();
  });

  it('should_deleteConnection', () => {
    fixture.detectChanges();
    const initialCount = component.connections().length;

    component.deleteConnection('pc-1');

    expect(platformServiceSpy.deleteConnection).toHaveBeenCalledWith('pc-1');
    expect(component.connections().length).toBe(initialCount - 1);
    expect(component.deletingConnectionId()).toBeNull();
  });

  // --- Projects tab tests ---

  it('should_resolveConnectionName_when_projectHasConnectionId', () => {
    fixture.detectChanges();
    expect(component.projectStates()[0].connectionName).toBe('Jira Cloud - Production');
    expect(component.projectStates()[1].connectionName).toBeNull();
  });

  it('should_toggleProjectExpanded', () => {
    fixture.detectChanges();
    component.setTab('projects');
    expect(component.projectStates()[0].expanded).toBeFalse();
    component.toggleProject(0);
    expect(component.projectStates()[0].expanded).toBeTrue();
  });

  it('should_loadMappings_when_projectExpanded', () => {
    fixture.detectChanges();
    component.toggleProject(0);
    expect(projectServiceSpy.getWorkflowMappings).toHaveBeenCalledWith('p1');
  });

  it('should_toggleProjectForm', () => {
    fixture.detectChanges();
    expect(component.showProjectForm()).toBeFalse();
    component.toggleProjectForm();
    expect(component.showProjectForm()).toBeTrue();
    component.toggleProjectForm();
    expect(component.showProjectForm()).toBeFalse();
  });

  it('should_canSaveProject_returnFalse_when_incomplete', () => {
    fixture.detectChanges();
    expect(component.canSaveProject()).toBeFalse();
  });

  it('should_canSaveProject_returnTrue_when_formValid', () => {
    fixture.detectChanges();
    component.projectForm = {
      name: 'new-project', description: '', defaultBranch: 'main',
      connectionId: 'pc-1', externalProjectId: 'NP',
    };
    expect(component.canSaveProject()).toBeTrue();
  });

  it('should_saveProject_andAddToProjectList', () => {
    fixture.detectChanges();
    const initialCount = component.projectStates().length;
    component.projectForm = {
      name: 'new-project', description: 'Test', defaultBranch: 'main',
      connectionId: 'pc-1', externalProjectId: 'NP',
    };

    component.saveProject();

    expect(projectServiceSpy.createProject).toHaveBeenCalled();
    expect(component.projectStates().length).toBe(initialCount + 1);
    expect(component.showProjectForm()).toBeFalse();
  });

  it('should_showError_when_saveProjectFails', () => {
    projectServiceSpy.createProject.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.projectForm = {
      name: 'new-project', description: '', defaultBranch: 'main',
      connectionId: 'pc-1', externalProjectId: 'NP',
    };

    component.saveProject();

    expect(component.projectSaveError()).toBeTruthy();
    expect(component.savingProject()).toBeFalse();
  });

  it('should_addMapping', () => {
    fixture.detectChanges();
    component.toggleProject(0);
    fixture.detectChanges();
    const initialLength = component.projectStates()[0].mappings.length;
    component.addMapping(0);
    expect(component.projectStates()[0].mappings.length).toBe(initialLength + 1);
  });

  it('should_removeMapping', () => {
    fixture.detectChanges();
    component.toggleProject(0);
    fixture.detectChanges();
    component.removeMapping(0, 0);
    expect(component.projectStates()[0].mappings.length).toBe(1);
  });

  it('should_saveMappings', () => {
    fixture.detectChanges();
    component.toggleProject(0);
    fixture.detectChanges();
    component.saveMappings(0);
    expect(projectServiceSpy.saveWorkflowMappings).toHaveBeenCalledWith('p1', jasmine.any(Array));
  });

  it('should_formatState_correctly', () => {
    expect(component.formatState('PROPOSE_CODE')).toBe('Propose Code');
    expect(component.formatState('BACKLOG')).toBe('Backlog');
    expect(component.formatState('QA')).toBe('QA');
  });

  it('should_filterAvailableStates', () => {
    fixture.detectChanges();
    component.toggleProject(0);
    fixture.detectChanges();
    const available = component.getAvailableStates(0, 'BACKLOG');
    expect(available).toContain('BACKLOG');
    expect(available).not.toContain('REVIEW');
    expect(available).toContain('PLANNING');
  });

  it('should_updateProjectConnection', () => {
    fixture.detectChanges();
    component.updateProjectConnection(1, 'pc-2');
    expect(component.projectStates()[1].project.connectionId).toBe('pc-2');
    expect(component.projectStates()[1].connectionName).toBe('GitHub - Organization');
  });

  it('should_clearConnectionAndStatuses_when_noConnectionSelected', () => {
    fixture.detectChanges();
    component.updateProjectConnection(0, '');
    expect(component.projectStates()[0].project.connectionId).toBeUndefined();
    expect(component.projectStates()[0].connectionName).toBeNull();
    expect(component.projectStates()[0].remoteStatuses.length).toBe(0);
  });

  it('should_updateExternalProjectId', () => {
    fixture.detectChanges();
    component.updateExternalProjectId(0, 'NEW-KEY');
    expect(component.projectStates()[0].project.externalProjectId).toBe('NEW-KEY');
    expect(component.projectStates()[0].remoteStatuses.length).toBe(0);
  });

  it('should_fetchRemoteStatuses', () => {
    fixture.detectChanges();
    component.fetchRemoteStatuses(0);
    expect(platformServiceSpy.getProjectStatuses).toHaveBeenCalledWith('pc-1', 'SQ');
    expect(component.projectStates()[0].remoteStatuses).toEqual(mockRemoteStatuses);
  });

  it('should_showFetchError_when_noConnection', fakeAsync(() => {
    fixture.detectChanges();
    component.fetchRemoteStatuses(1);
    expect(component.projectStates()[1].fetchError).toBeTruthy();
    expect(platformServiceSpy.getProjectStatuses).not.toHaveBeenCalled();
    tick(5000);
    expect(component.projectStates()[1].fetchError).toBeNull();
  }));

  it('should_canFetchStatuses_checkConditions', () => {
    fixture.detectChanges();
    expect(component.canFetchStatuses(component.projectStates()[0])).toBeTrue();
    expect(component.canFetchStatuses(component.projectStates()[1])).toBeFalse();
  });

  it('should_returnPlatformIcon', () => {
    expect(component.platformIcon('GITHUB')).toBe('GitHub');
    expect(component.platformIcon('JIRA')).toBe('Jira');
    expect(component.platformIcon('GITLAB')).toBe('GitLab');
    expect(component.platformIcon('AZURE_DEVOPS')).toBe('Azure DevOps');
    expect(component.platformIcon('BITBUCKET')).toBe('Bitbucket');
    expect(component.platformIcon('OTHER')).toBe('OTHER');
  });

  it('should_fallbackToMockStatuses_when_fetchFails', () => {
    platformServiceSpy.getProjectStatuses.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.fetchRemoteStatuses(0);
    expect(component.projectStates()[0].remoteStatuses.length).toBeGreaterThan(0);
    expect(component.projectStates()[0].fetchingStatuses).toBeFalse();
  });

  it('should_showSaveSuccess_thenClear', fakeAsync(() => {
    fixture.detectChanges();
    component.toggleProject(0);
    fixture.detectChanges();
    component.saveMappings(0);
    expect(component.projectStates()[0].saveSuccess).toBeTrue();
    tick(3000);
    expect(component.projectStates()[0].saveSuccess).toBeFalse();
  }));

  it('should_showSaveError_thenClear', fakeAsync(() => {
    projectServiceSpy.saveWorkflowMappings.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.toggleProject(0);
    fixture.detectChanges();
    component.saveMappings(0);
    expect(component.projectStates()[0].saveError).toBeTruthy();
    tick(5000);
    expect(component.projectStates()[0].saveError).toBeNull();
  }));
});
