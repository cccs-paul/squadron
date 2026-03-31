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
    id: 'u1',
    username: 'fry',
    email: 'fry@planetexpress.com',
    displayName: 'Philip J. Fry',
    tenantId: 't1',
    tenantName: 'Planet Express',
    roles: ['developer'],
    permissions: [],
  };

  const mockConnections: PlatformConnection[] = [
    {
      id: 'pc-1', tenantId: 't1', name: 'Jira Cloud - Production',
      platformType: PlatformConnectionType.JIRA,
      baseUrl: 'https://myorg.atlassian.net',
      status: ConnectionStatus.CONNECTED, config: {},
      createdAt: new Date().toISOString(),
    },
    {
      id: 'pc-2', tenantId: 't1', name: 'GitHub - Organization',
      platformType: PlatformConnectionType.GITHUB,
      baseUrl: 'https://api.github.com',
      status: ConnectionStatus.CONNECTED, config: {},
      createdAt: new Date().toISOString(),
    },
  ];

  const mockProjects: Project[] = [
    {
      id: 'p1', tenantId: 't1', name: 'project-alpha',
      description: 'Alpha project', defaultBranch: 'main',
      connectionId: 'pc-1', externalProjectId: 'SQ',
      taskCount: 10, activeTaskCount: 3, members: [],
      createdAt: new Date().toISOString(),
    },
    {
      id: 'p2', tenantId: 't1', name: 'project-beta',
      description: 'Beta project', defaultBranch: 'main',
      taskCount: 5, activeTaskCount: 1, members: [],
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
      'getProjectsByTenant', 'getWorkflowStates', 'getWorkflowMappings', 'saveWorkflowMappings',
    ]);
    platformServiceSpy = jasmine.createSpyObj('PlatformService', [
      'getConnectionsByTenant', 'getProjectStatuses',
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
    platformServiceSpy.getConnectionsByTenant.and.returnValue(of(mockConnections));
    platformServiceSpy.getProjectStatuses.and.returnValue(of(mockRemoteStatuses));

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

  it('should_loadProjectsStatesAndConnections_when_initialized', () => {
    fixture.detectChanges();

    expect(projectServiceSpy.getProjectsByTenant).toHaveBeenCalledWith('t1');
    expect(projectServiceSpy.getWorkflowStates).toHaveBeenCalled();
    expect(platformServiceSpy.getConnectionsByTenant).toHaveBeenCalledWith('t1');
    expect(component.projectStates().length).toBe(2);
    expect(component.workflowStates().length).toBe(8);
    expect(component.connections().length).toBe(2);
    expect(component.loading()).toBeFalse();
  });

  it('should_resolveConnectionName_when_projectHasConnectionId', () => {
    fixture.detectChanges();

    // project-alpha has connectionId: 'pc-1' -> 'Jira Cloud - Production'
    expect(component.projectStates()[0].connectionName).toBe('Jira Cloud - Production');
    // project-beta has no connectionId
    expect(component.projectStates()[1].connectionName).toBeNull();
  });

  it('should_fallbackToMockData_when_apiErrors', () => {
    projectServiceSpy.getProjectsByTenant.and.returnValue(throwError(() => new Error('fail')));
    projectServiceSpy.getWorkflowStates.and.returnValue(throwError(() => new Error('fail')));
    platformServiceSpy.getConnectionsByTenant.and.returnValue(throwError(() => new Error('fail')));

    fixture.detectChanges();

    expect(component.loading()).toBeFalse();
    expect(component.projectStates().length).toBeGreaterThan(0);
    expect(component.workflowStates().length).toBe(8);
    expect(component.connections().length).toBeGreaterThan(0);
  });

  it('should_toggleProjectExpanded_when_headerClicked', () => {
    fixture.detectChanges();

    expect(component.projectStates()[0].expanded).toBeFalse();
    component.toggleProject(0);
    expect(component.projectStates()[0].expanded).toBeTrue();
  });

  it('should_loadMappings_when_projectExpanded', () => {
    fixture.detectChanges();

    component.toggleProject(0);

    expect(projectServiceSpy.getWorkflowMappings).toHaveBeenCalledWith('p1');
  });

  it('should_addMapping_when_addClicked', () => {
    fixture.detectChanges();
    component.toggleProject(0);
    fixture.detectChanges();

    const initialLength = component.projectStates()[0].mappings.length;
    component.addMapping(0);
    expect(component.projectStates()[0].mappings.length).toBe(initialLength + 1);
  });

  it('should_removeMapping_when_removeClicked', () => {
    fixture.detectChanges();
    component.toggleProject(0);
    fixture.detectChanges();

    component.removeMapping(0, 0);
    expect(component.projectStates()[0].mappings.length).toBe(1);
  });

  it('should_saveMappings_when_saveClicked', () => {
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

  it('should_filterAvailableStates_excludingUsedOnes', () => {
    fixture.detectChanges();
    component.toggleProject(0);
    fixture.detectChanges();

    const available = component.getAvailableStates(0, 'BACKLOG');
    expect(available).toContain('BACKLOG');
    expect(available).not.toContain('REVIEW');
    expect(available).toContain('PLANNING');
  });

  it('should_showErrorState_when_notAuthenticated', () => {
    (authServiceSpy.user as jasmine.Spy).and.returnValue(null);
    fixture.detectChanges();

    expect(component.loadError()).toBe('Not authenticated');
    expect(component.loading()).toBeFalse();
  });

  // --- New tests for platform connection integration ---

  it('should_updateProjectConnection_when_connectionSelected', () => {
    fixture.detectChanges();

    component.updateProjectConnection(1, 'pc-2');

    expect(component.projectStates()[1].project.connectionId).toBe('pc-2');
    expect(component.projectStates()[1].connectionName).toBe('GitHub - Organization');
  });

  it('should_clearConnectionAndStatuses_when_noConnectionSelected', () => {
    fixture.detectChanges();

    // First set a connection, then clear it
    component.updateProjectConnection(0, '');

    expect(component.projectStates()[0].project.connectionId).toBeUndefined();
    expect(component.projectStates()[0].connectionName).toBeNull();
    expect(component.projectStates()[0].remoteStatuses.length).toBe(0);
  });

  it('should_updateExternalProjectId_when_valueChanged', () => {
    fixture.detectChanges();

    component.updateExternalProjectId(0, 'NEW-KEY');

    expect(component.projectStates()[0].project.externalProjectId).toBe('NEW-KEY');
    expect(component.projectStates()[0].remoteStatuses.length).toBe(0);
  });

  it('should_fetchRemoteStatuses_when_projectLinked', () => {
    fixture.detectChanges();

    // project-alpha has connectionId and externalProjectId
    component.fetchRemoteStatuses(0);

    expect(platformServiceSpy.getProjectStatuses).toHaveBeenCalledWith('pc-1', 'SQ');
    expect(component.projectStates()[0].remoteStatuses).toEqual(mockRemoteStatuses);
    expect(component.projectStates()[0].fetchingStatuses).toBeFalse();
  });

  it('should_showError_when_fetchingStatusesWithoutConnection', fakeAsync(() => {
    fixture.detectChanges();

    // project-beta has no connectionId
    component.fetchRemoteStatuses(1);

    expect(component.projectStates()[1].fetchError).toBeTruthy();
    expect(platformServiceSpy.getProjectStatuses).not.toHaveBeenCalled();

    tick(5000);
    expect(component.projectStates()[1].fetchError).toBeNull();
  }));

  it('should_canFetchStatuses_returnTrueOnlyWhenLinked', () => {
    fixture.detectChanges();

    // project-alpha: has connectionId + externalProjectId -> true
    expect(component.canFetchStatuses(component.projectStates()[0])).toBeTrue();
    // project-beta: no connectionId -> false
    expect(component.canFetchStatuses(component.projectStates()[1])).toBeFalse();
  });

  it('should_returnPlatformIcon_forKnownTypes', () => {
    expect(component.platformIcon('GITHUB')).toBe('GitHub');
    expect(component.platformIcon('JIRA')).toBe('Jira');
    expect(component.platformIcon('JIRA_CLOUD')).toBe('Jira');
    expect(component.platformIcon('GITLAB')).toBe('GitLab');
    expect(component.platformIcon('AZURE_DEVOPS')).toBe('Azure DevOps');
    expect(component.platformIcon('BITBUCKET')).toBe('Bitbucket');
    expect(component.platformIcon('OTHER')).toBe('OTHER');
  });

  it('should_fallbackToMockStatuses_when_statusFetchFails', () => {
    platformServiceSpy.getProjectStatuses.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    component.fetchRemoteStatuses(0);

    // Should fallback to Jira mock statuses since connection pc-1 is JIRA type
    expect(component.projectStates()[0].remoteStatuses.length).toBeGreaterThan(0);
    expect(component.projectStates()[0].fetchingStatuses).toBeFalse();
    expect(component.projectStates()[0].fetchError).toBeNull();
  });

  it('should_showSaveSuccess_thenClearAfterTimeout', fakeAsync(() => {
    fixture.detectChanges();
    component.toggleProject(0);
    fixture.detectChanges();

    component.saveMappings(0);

    expect(component.projectStates()[0].saveSuccess).toBeTrue();

    tick(3000);
    expect(component.projectStates()[0].saveSuccess).toBeFalse();
  }));

  it('should_showSaveError_whenSaveFails_thenClearAfterTimeout', fakeAsync(() => {
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
