import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { ProjectConfigComponent, WizardStep } from './project-config.component';
import { ProjectService } from '../../../core/services/project.service';
import { PlatformService } from '../../../core/services/platform.service';
import { SshKeyService } from '../../../core/services/ssh-key.service';
import { ReviewBotConfigService } from '../../../core/services/review-bot-config.service';
import { WorkspaceService, TestGitAccessResult } from '../../../core/services/workspace.service';
import { AuthService } from '../../../core/auth/auth.service';
import { of, throwError, Subject } from 'rxjs';
import { Project, RemoteProject, WorkflowMapping } from '../../../core/models/project.model';
import {
  PlatformConnection,
  PlatformConnectionType,
  ConnectionStatus,
  SshKey,
  ReviewBotConfig,
} from '../../../core/models/security.model';

describe('ProjectConfigComponent', () => {
  let component: ProjectConfigComponent;
  let fixture: ComponentFixture<ProjectConfigComponent>;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;
  let platformServiceSpy: jasmine.SpyObj<PlatformService>;
  let sshKeyServiceSpy: jasmine.SpyObj<SshKeyService>;
  let reviewBotConfigServiceSpy: jasmine.SpyObj<ReviewBotConfigService>;
  let workspaceServiceSpy: jasmine.SpyObj<WorkspaceService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const mockUser = {
    id: 'u1', username: 'fry', email: 'fry@planetexpress.com',
    displayName: 'Philip J. Fry', tenantId: 't1', tenantName: 'Planet Express',
    roles: ['developer'], permissions: [],
  };

  const mockTicketConnection: PlatformConnection = {
    id: 'pc-1', tenantId: 't1', name: 'Jira Cloud - Production',
    platformType: PlatformConnectionType.JIRA_CLOUD, platformCategory: 'TICKET_PROVIDER',
    baseUrl: 'https://myorg.atlassian.net',
    status: ConnectionStatus.ACTIVE, config: {}, createdAt: new Date().toISOString(),
  };

  const mockGitConnection: PlatformConnection = {
    id: 'pc-2', tenantId: 't1', name: 'GitHub - Organization',
    platformType: PlatformConnectionType.GITHUB, platformCategory: 'GIT_REMOTE',
    baseUrl: 'https://api.github.com',
    status: ConnectionStatus.ACTIVE, config: {}, createdAt: new Date().toISOString(),
  };

  const mockConnections: PlatformConnection[] = [mockTicketConnection, mockGitConnection];

  const mockSshKeys: SshKey[] = [
    {
      id: 'sk-1', tenantId: 't1', connectionId: 'pc-2', name: 'deploy-key-prod',
      publicKey: 'ssh-ed25519 AAAAC...', fingerprint: 'SHA256:abc123',
      keyType: 'ED25519', createdAt: new Date().toISOString(),
    },
  ];

  const mockReviewBotConfigs: ReviewBotConfig[] = [
    {
      id: 'bot-1', tenantId: 't1', connectionId: 'pc-2', botUsername: 'squadron-bot',
      enabled: true, autoAssign: true, createdAt: new Date().toISOString(),
    },
  ];

  const mockProjects: Project[] = [
    {
      id: 'p1', tenantId: 't1', name: 'project-alpha', description: 'Alpha project',
      defaultBranch: 'main', connectionId: 'pc-1', externalProjectId: 'SQ',
      branchNamingTemplate: '{strategy}/{ticket}-{description}',
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

  const mockRemoteProjects: RemoteProject[] = [
    { key: 'SQ', name: 'Squadron', description: 'Main project', url: 'https://jira.example.com/browse/SQ' },
    { key: 'DEV', name: 'DevTools', description: 'Dev utilities', url: 'https://jira.example.com/browse/DEV' },
    { key: 'OPS', name: 'Operations', url: 'https://jira.example.com/browse/OPS' },
  ];

  beforeEach(async () => {
    projectServiceSpy = jasmine.createSpyObj('ProjectService', [
      'getProjectsByTenant', 'getWorkflowStates', 'getWorkflowMappings',
      'saveWorkflowMappings', 'createProject', 'updateProject', 'deleteProject',
    ]);
    platformServiceSpy = jasmine.createSpyObj('PlatformService', [
      'getConnectionsByTenant', 'getProjectStatuses', 'createConnectionFromRequest',
      'updateConnection', 'deleteConnection', 'getRemoteProjects', 'testConnection',
    ]);
    sshKeyServiceSpy = jasmine.createSpyObj('SshKeyService', [
      'createSshKey', 'getSshKey', 'getSshKeysByTenant', 'getSshKeysByConnection', 'deleteSshKey', 'generateDeployKey',
    ]);
    reviewBotConfigServiceSpy = jasmine.createSpyObj('ReviewBotConfigService', [
      'getConfigsByTenant', 'createConfig', 'updateConfig', 'deleteConfig',
    ]);
    workspaceServiceSpy = jasmine.createSpyObj('WorkspaceService', [
      'getWorkspaceByTask', 'destroyWorkspace', 'testGitAccess',
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
    projectServiceSpy.updateProject.and.returnValue(of(mockProjects[0]));
    projectServiceSpy.deleteProject.and.returnValue(of(void 0));
    platformServiceSpy.getConnectionsByTenant.and.returnValue(of(mockConnections));
    platformServiceSpy.getProjectStatuses.and.returnValue(of(mockRemoteStatuses));
    platformServiceSpy.createConnectionFromRequest.and.returnValue(of(mockGitConnection));
    platformServiceSpy.updateConnection.and.returnValue(of(mockTicketConnection));
    platformServiceSpy.deleteConnection.and.returnValue(of(void 0));
    platformServiceSpy.getRemoteProjects.and.returnValue(of(mockRemoteProjects));
    platformServiceSpy.testConnection.and.returnValue(of(true));
    sshKeyServiceSpy.getSshKeysByTenant.and.returnValue(of(mockSshKeys));
    sshKeyServiceSpy.createSshKey.and.returnValue(of(mockSshKeys[0]));
    sshKeyServiceSpy.deleteSshKey.and.returnValue(of(void 0));
    sshKeyServiceSpy.generateDeployKey.and.returnValue(of(mockSshKeys[0]));
    reviewBotConfigServiceSpy.getConfigsByTenant.and.returnValue(of(mockReviewBotConfigs));
    reviewBotConfigServiceSpy.createConfig.and.returnValue(of(mockReviewBotConfigs[0]));
    reviewBotConfigServiceSpy.updateConfig.and.returnValue(of({ ...mockReviewBotConfigs[0], enabled: false }));
    reviewBotConfigServiceSpy.deleteConfig.and.returnValue(of(void 0));
    workspaceServiceSpy.testGitAccess.and.returnValue(of({ success: true, message: 'OK', branch: 'main', durationMs: 250 }));

    await TestBed.configureTestingModule({
      imports: [ProjectConfigComponent, TranslateModule.forRoot()],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ProjectService, useValue: projectServiceSpy },
        { provide: PlatformService, useValue: platformServiceSpy },
        { provide: SshKeyService, useValue: sshKeyServiceSpy },
        { provide: ReviewBotConfigService, useValue: reviewBotConfigServiceSpy },
        { provide: WorkspaceService, useValue: workspaceServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProjectConfigComponent);
    component = fixture.componentInstance;
  });

  // --- Component creation ---

  it('should_create', () => {
    expect(component).toBeTruthy();
  });

  // --- Initialization & data loading ---

  it('should_loadDataOnInit', () => {
    fixture.detectChanges();

    expect(projectServiceSpy.getProjectsByTenant).toHaveBeenCalledWith('t1');
    expect(projectServiceSpy.getWorkflowStates).toHaveBeenCalled();
    expect(platformServiceSpy.getConnectionsByTenant).toHaveBeenCalledWith('t1');
    expect(sshKeyServiceSpy.getSshKeysByTenant).toHaveBeenCalledWith('t1');
    expect(component.loading()).toBeFalse();
    expect(component.allConnections().length).toBe(2);
    expect(component.projectStates().length).toBe(2);
    expect(component.workflowStates().length).toBe(8);
    expect(component.sshKeys().length).toBe(1);
  });

  it('should_categorizeConnections_intoTicketProvidersAndGitRemotes', () => {
    fixture.detectChanges();

    expect(component.ticketProviders().length).toBe(1);
    expect(component.ticketProviders()[0].id).toBe('pc-1');
    expect(component.gitRemotes().length).toBe(1);
    expect(component.gitRemotes()[0].id).toBe('pc-2');
  });

  it('should_defaultToTicketProvidersStep', () => {
    fixture.detectChanges();
    expect(component.activeStep()).toBe('ticket-providers');
  });

  it('should_showErrorState_when_notAuthenticated', () => {
    (authServiceSpy.user as jasmine.Spy).and.returnValue(null);
    fixture.detectChanges();
    expect(component.loadError()).toBe('projectConfig.errors.notAuthenticated');
    expect(component.loading()).toBeFalse();
  });

  it('should_handleApiError_withEmptyState', () => {
    projectServiceSpy.getProjectsByTenant.and.returnValue(throwError(() => new Error('fail')));
    projectServiceSpy.getWorkflowStates.and.returnValue(throwError(() => new Error('fail')));
    platformServiceSpy.getConnectionsByTenant.and.returnValue(throwError(() => new Error('fail')));
    sshKeyServiceSpy.getSshKeysByTenant.and.returnValue(throwError(() => new Error('fail')));

    fixture.detectChanges();

    expect(component.loading()).toBeFalse();
    expect(component.allConnections().length).toBe(0);
    expect(component.ticketProviders().length).toBe(0);
    expect(component.gitRemotes().length).toBe(0);
    expect(component.sshKeys().length).toBe(0);
    expect(component.projectStates().length).toBe(0);
    expect(component.workflowStates().length).toBe(0);
  });

  // --- Wizard step navigation ---

  it('should_setStep', () => {
    fixture.detectChanges();
    component.setStep('git-remotes');
    expect(component.activeStep()).toBe('git-remotes');
    component.setStep('projects');
    expect(component.activeStep()).toBe('projects');
    component.setStep('branch-workflow');
    expect(component.activeStep()).toBe('branch-workflow');
    component.setStep('ticket-providers');
    expect(component.activeStep()).toBe('ticket-providers');
  });

  it('should_nextStep_advanceToNextWizardStep', () => {
    fixture.detectChanges();
    expect(component.activeStep()).toBe('ticket-providers');
    component.nextStep();
    expect(component.activeStep()).toBe('git-remotes');
    component.nextStep();
    expect(component.activeStep()).toBe('projects');
    component.nextStep();
    expect(component.activeStep()).toBe('branch-workflow');
  });

  it('should_nextStep_notAdvancePastLastStep', () => {
    fixture.detectChanges();
    component.setStep('branch-workflow');
    component.nextStep();
    expect(component.activeStep()).toBe('branch-workflow');
  });

  it('should_prevStep_goToPreviousWizardStep', () => {
    fixture.detectChanges();
    component.setStep('branch-workflow');
    component.prevStep();
    expect(component.activeStep()).toBe('projects');
    component.prevStep();
    expect(component.activeStep()).toBe('git-remotes');
    component.prevStep();
    expect(component.activeStep()).toBe('ticket-providers');
  });

  it('should_prevStep_notGoBeforeFirstStep', () => {
    fixture.detectChanges();
    component.prevStep();
    expect(component.activeStep()).toBe('ticket-providers');
  });

  it('should_getStepIndex_returnCorrectIndex', () => {
    expect(component.getStepIndex('ticket-providers')).toBe(0);
    expect(component.getStepIndex('git-remotes')).toBe(1);
    expect(component.getStepIndex('projects')).toBe(2);
    expect(component.getStepIndex('branch-workflow')).toBe(3);
  });

  it('should_isStepComplete_returnTrue_when_ticketProvidersExist', () => {
    fixture.detectChanges();
    expect(component.isStepComplete('ticket-providers')).toBeTrue();
  });

  it('should_isStepComplete_returnTrue_when_gitRemotesExist', () => {
    fixture.detectChanges();
    expect(component.isStepComplete('git-remotes')).toBeTrue();
  });

  it('should_isStepComplete_returnTrue_when_projectsExist', () => {
    fixture.detectChanges();
    expect(component.isStepComplete('projects')).toBeTrue();
  });

  it('should_isStepComplete_returnTrue_when_mappingsEagerLoaded', () => {
    fixture.detectChanges();
    // Mappings are eagerly fetched on loadData, so branch-workflow step is complete
    expect(component.isStepComplete('branch-workflow')).toBeTrue();
  });

  it('should_isStepComplete_returnFalse_when_noMappingsExist', () => {
    projectServiceSpy.getWorkflowMappings.and.returnValue(of([]));
    fixture.detectChanges();
    expect(component.isStepComplete('branch-workflow')).toBeFalse();
  });

  // --- Platform type helpers ---

  it('should_isCloudPlatform_returnTrue_forCloudPlatforms', () => {
    expect(component.isCloudPlatform('GITHUB')).toBeTrue();
    expect(component.isCloudPlatform('GITLAB')).toBeTrue();
    expect(component.isCloudPlatform('JIRA_CLOUD')).toBeTrue();
  });

  it('should_isCloudPlatform_returnFalse_forSelfHosted', () => {
    expect(component.isCloudPlatform('JIRA_SERVER')).toBeFalse();
    expect(component.isCloudPlatform('BITBUCKET')).toBeFalse();
    expect(component.isCloudPlatform('AZURE_DEVOPS')).toBeFalse();
  });

  it('should_returnPlatformIcon', () => {
    expect(component.platformIcon('GITHUB')).toBe('projectConfig.platforms.github');
    expect(component.platformIcon('JIRA_CLOUD')).toBe('projectConfig.platforms.jiraCloud');
    expect(component.platformIcon('JIRA_SERVER')).toBe('projectConfig.platforms.jiraServer');
    expect(component.platformIcon('GITLAB')).toBe('projectConfig.platforms.gitlab');
    expect(component.platformIcon('AZURE_DEVOPS')).toBe('projectConfig.platforms.azureDevops');
    expect(component.platformIcon('BITBUCKET')).toBe('projectConfig.platforms.bitbucket');
    expect(component.platformIcon('OTHER')).toBe('OTHER');
  });

  it('should_formatState_correctly', () => {
    expect(component.formatState('PROPOSE_CODE')).toBe('Propose Code');
    expect(component.formatState('BACKLOG')).toBe('Backlog');
    expect(component.formatState('QA')).toBe('QA');
  });

  // ===== STEP 1: Ticket Providers =====

  it('should_toggleTicketForm', () => {
    fixture.detectChanges();
    expect(component.showTicketForm()).toBeFalse();
    component.toggleTicketForm();
    expect(component.showTicketForm()).toBeTrue();
    component.toggleTicketForm();
    expect(component.showTicketForm()).toBeFalse();
  });

  it('should_resetTicketForm_when_formClosed', () => {
    fixture.detectChanges();
    component.toggleTicketForm();
    component.ticketForm.name = 'test';
    component.ticketForm.credentials = { email: 'a@b.com' };
    component.toggleTicketForm();
    expect(component.ticketForm.name).toBe('');
    expect(component.ticketForm.credentials).toEqual({});
  });

  it('should_returnTicketAuthTypeOptions_forPlatformType', () => {
    fixture.detectChanges();
    component.ticketForm.platformType = 'JIRA_CLOUD';
    const options = component.getTicketAuthTypeOptions();
    expect(options.length).toBe(2);
    expect(options[0].label).toBe('projectConfig.authTypes.apiToken');
    expect(options[1].label).toBe('projectConfig.authTypes.oauth2');
  });

  it('should_returnTicketAuthFields_forSelectedAuthType', () => {
    fixture.detectChanges();
    component.ticketForm.platformType = 'JIRA_CLOUD';
     component.ticketForm.authType = 'API Token';
    const fields = component.getTicketAuthFields();
    expect(fields.length).toBe(2);
    expect(fields[0].key).toBe('email');
    expect(fields[1].key).toBe('apiToken');
  });

  it('should_resetAuthTypeAndCredentials_when_ticketPlatformTypeChanges', () => {
    fixture.detectChanges();
    component.ticketForm.platformType = 'JIRA_CLOUD';
     component.ticketForm.authType = 'API Token';
    component.ticketForm.credentials = { email: 'test@test.com', apiToken: 'abc' };

    component.ticketForm.platformType = 'AZURE_DEVOPS';
    component.onTicketPlatformTypeChange();

    expect(component.ticketForm.authType).toBe('PAT');
    expect(component.ticketForm.credentials).toEqual({});
  });

  it('should_resetCredentials_when_ticketAuthTypeChanges', () => {
    fixture.detectChanges();
    component.ticketForm.credentials = { email: 'test@test.com', apiToken: 'abc' };
    component.onTicketAuthTypeChange();
    expect(component.ticketForm.credentials).toEqual({});
  });

  it('should_canSaveTicketProvider_returnFalse_when_formIncomplete', () => {
    fixture.detectChanges();
    expect(component.canSaveTicketProvider()).toBeFalse();
  });

  it('should_canSaveTicketProvider_returnTrue_when_formComplete', () => {
    fixture.detectChanges();
    component.ticketForm = {
      name: 'My Jira', platformType: 'JIRA_CLOUD', baseUrl: 'https://myorg.atlassian.net',
      authType: 'API Token', credentials: { email: 'me@test.com', apiToken: 'abc123' },
    };
    expect(component.canSaveTicketProvider()).toBeTrue();
  });

  it('should_canSaveTicketProvider_requireBaseUrl_forSelfHosted', () => {
    fixture.detectChanges();
    component.ticketForm = {
      name: 'My Jira', platformType: 'JIRA_SERVER', baseUrl: '',
      authType: 'PAT', credentials: { pat: 'abc123' },
    };
    expect(component.canSaveTicketProvider()).toBeFalse();

    component.ticketForm.baseUrl = 'https://jira.mycompany.com';
    expect(component.canSaveTicketProvider()).toBeTrue();
  });

  it('should_saveTicketProvider_andAddToList', () => {
    platformServiceSpy.createConnectionFromRequest.and.returnValue(of(mockTicketConnection));
    fixture.detectChanges();
    const initialCount = component.ticketProviders().length;
    component.ticketForm = {
      name: 'My Jira', platformType: 'JIRA_CLOUD', baseUrl: 'https://myorg.atlassian.net',
      authType: 'API Token', credentials: { email: 'me@test.com', apiToken: 'abc123' },
    };

    component.saveTicketProvider();

    expect(platformServiceSpy.createConnectionFromRequest).toHaveBeenCalled();
    expect(component.ticketProviders().length).toBe(initialCount + 1);
    expect(component.allConnections().length).toBe(3);
    expect(component.showTicketForm()).toBeFalse();
    expect(component.savingTicketProvider()).toBeFalse();
  });

  it('should_showError_when_saveTicketProviderFails', () => {
    platformServiceSpy.createConnectionFromRequest.and.returnValue(
      throwError(() => ({ error: { success: false, message: 'SSL certificate not trusted' } })),
    );
    fixture.detectChanges();
    component.ticketForm = {
      name: 'My Jira', platformType: 'JIRA_CLOUD', baseUrl: 'https://myorg.atlassian.net',
      authType: 'API Token', credentials: { email: 'me@test.com', apiToken: 'abc123' },
    };

    component.saveTicketProvider();

    expect(component.ticketSaveError()).toBe('SSL certificate not trusted');
    expect(component.savingTicketProvider()).toBeFalse();
  });

  it('should_showTicketSaveSuccess_thenClear', fakeAsync(() => {
    platformServiceSpy.createConnectionFromRequest.and.returnValue(of(mockTicketConnection));
    fixture.detectChanges();
    component.ticketForm = {
      name: 'My Jira', platformType: 'JIRA_CLOUD', baseUrl: 'https://myorg.atlassian.net',
      authType: 'API Token', credentials: { email: 'me@test.com', apiToken: 'abc123' },
    };
    component.saveTicketProvider();
    expect(component.ticketSaveSuccess()).toBeTrue();
    tick(3000);
    expect(component.ticketSaveSuccess()).toBeFalse();
  }));

  it('should_deleteTicketProvider', () => {
    fixture.detectChanges();
    const initialTicketCount = component.ticketProviders().length;
    const initialAllCount = component.allConnections().length;

    component.deleteTicketProvider('pc-1');

    expect(platformServiceSpy.deleteConnection).toHaveBeenCalledWith('pc-1');
    expect(component.ticketProviders().length).toBe(initialTicketCount - 1);
    expect(component.allConnections().length).toBe(initialAllCount - 1);
    expect(component.deletingConnectionId()).toBeNull();
  });

  // ===== STEP 2: Git Remotes =====

  it('should_toggleGitForm', () => {
    fixture.detectChanges();
    expect(component.showGitForm()).toBeFalse();
    component.toggleGitForm();
    expect(component.showGitForm()).toBeTrue();
    component.toggleGitForm();
    expect(component.showGitForm()).toBeFalse();
  });

  it('should_returnGitAuthTypeOptions_forPlatformType', () => {
    fixture.detectChanges();
    component.gitForm.platformType = 'GITHUB';
    const options = component.getGitAuthTypeOptions();
    expect(options.length).toBe(2);
    expect(options[0].label).toBe('projectConfig.authTypes.pat');
    expect(options[1].label).toBe('projectConfig.authTypes.app');
  });

  it('should_returnGitAuthFields_forSelectedAuthType', () => {
    fixture.detectChanges();
    component.gitForm.platformType = 'GITHUB';
    component.gitForm.authType = 'PAT';
    const fields = component.getGitAuthFields();
    expect(fields.length).toBe(1);
    expect(fields[0].key).toBe('pat');
  });

  it('should_canSaveGitRemote_returnFalse_when_formIncomplete', () => {
    fixture.detectChanges();
    expect(component.canSaveGitRemote()).toBeFalse();
  });

  it('should_canSaveGitRemote_returnTrue_when_formComplete', () => {
    fixture.detectChanges();
    component.gitForm = {
      name: 'GitHub - MyOrg', platformType: 'GITHUB', baseUrl: 'https://api.github.com',
      authType: 'PAT', credentials: { pat: 'ghp_abc123' },
    };
    expect(component.canSaveGitRemote()).toBeTrue();
  });

  it('should_saveGitRemote_andAddToList', () => {
    fixture.detectChanges();
    const initialGitCount = component.gitRemotes().length;
    component.gitForm = {
      name: 'GitHub - MyOrg', platformType: 'GITHUB', baseUrl: 'https://api.github.com',
      authType: 'PAT', credentials: { pat: 'ghp_abc123' },
    };

    component.saveGitRemote();

    expect(platformServiceSpy.createConnectionFromRequest).toHaveBeenCalled();
    expect(component.gitRemotes().length).toBe(initialGitCount + 1);
    expect(component.showGitForm()).toBeFalse();
    expect(component.savingGitRemote()).toBeFalse();
  });

  it('should_showError_when_saveGitRemoteFails', () => {
    platformServiceSpy.createConnectionFromRequest.and.returnValue(
      throwError(() => ({ error: { message: 'Invalid token' } })),
    );
    fixture.detectChanges();
    component.gitForm = {
      name: 'GitHub', platformType: 'GITHUB', baseUrl: 'https://api.github.com',
      authType: 'PAT', credentials: { pat: 'bad' },
    };

    component.saveGitRemote();

    expect(component.gitSaveError()).toBe('Invalid token');
    expect(component.savingGitRemote()).toBeFalse();
  });

  it('should_deleteGitRemote_andRemoveAssociatedSshKeys', () => {
    fixture.detectChanges();
    expect(component.sshKeys().length).toBe(1);

    component.deleteGitRemote('pc-2');

    expect(platformServiceSpy.deleteConnection).toHaveBeenCalledWith('pc-2');
    expect(component.gitRemotes().length).toBe(0);
    expect(component.sshKeys().length).toBe(0);
    expect(component.deletingConnectionId()).toBeNull();
  });

  // ===== SSH Key Management =====

  it('should_toggleSshKeyForm', () => {
    fixture.detectChanges();
    expect(component.showSshKeyForm()).toBeFalse();
    component.toggleSshKeyForm();
    expect(component.showSshKeyForm()).toBeTrue();
    component.toggleSshKeyForm();
    expect(component.showSshKeyForm()).toBeFalse();
  });

  it('should_autoSelectConnection_when_singleGitRemote', () => {
    fixture.detectChanges();
    expect(component.gitRemotes().length).toBe(1);
    component.toggleSshKeyForm();
    expect(component.sshKeyForm.connectionId).toBe('pc-2');
  });

  it('should_getSshKeysForConnection_returnFilteredKeys', () => {
    fixture.detectChanges();
    expect(component.getSshKeysForConnection('pc-2').length).toBe(1);
    expect(component.getSshKeysForConnection('pc-1').length).toBe(0);
    expect(component.getSshKeysForConnection('nonexistent').length).toBe(0);
  });

  it('should_canSaveSshKey_returnFalse_when_formIncomplete', () => {
    fixture.detectChanges();
    expect(component.canSaveSshKey()).toBeFalse();
  });

  it('should_canSaveSshKey_returnTrue_when_formComplete', () => {
    fixture.detectChanges();
    component.sshKeyForm = {
      connectionId: 'pc-2', name: 'deploy-key',
      publicKey: 'ssh-ed25519 AAAA...', privateKey: '-----BEGIN OPENSSH PRIVATE KEY-----...',
      keyType: 'ED25519', keyUsage: 'USER_KEY',
    };
    expect(component.canSaveSshKey()).toBeTrue();
  });

  it('should_saveSshKey_andAddToList', () => {
    fixture.detectChanges();
    const initialCount = component.sshKeys().length;
    component.sshKeyForm = {
      connectionId: 'pc-2', name: 'deploy-key',
      publicKey: 'ssh-ed25519 AAAA...', privateKey: '-----BEGIN OPENSSH PRIVATE KEY-----...',
      keyType: 'ED25519', keyUsage: 'USER_KEY',
    };

    component.saveSshKey();

    expect(sshKeyServiceSpy.createSshKey).toHaveBeenCalled();
    expect(component.sshKeys().length).toBe(initialCount + 1);
    expect(component.showSshKeyForm()).toBeFalse();
    expect(component.savingSshKey()).toBeFalse();
  });

  it('should_showError_when_saveSshKeyFails', () => {
    sshKeyServiceSpy.createSshKey.and.returnValue(
      throwError(() => ({ error: { message: 'Duplicate fingerprint' } })),
    );
    fixture.detectChanges();
    component.sshKeyForm = {
      connectionId: 'pc-2', name: 'deploy-key',
      publicKey: 'ssh-ed25519 AAAA...', privateKey: '-----BEGIN OPENSSH PRIVATE KEY-----...',
      keyType: 'ED25519', keyUsage: 'USER_KEY',
    };

    component.saveSshKey();

    expect(component.sshKeySaveError()).toBe('Duplicate fingerprint');
    expect(component.savingSshKey()).toBeFalse();
  });

  it('should_showSshKeySaveSuccess_thenClear', fakeAsync(() => {
    fixture.detectChanges();
    component.sshKeyForm = {
      connectionId: 'pc-2', name: 'deploy-key',
      publicKey: 'ssh-ed25519 AAAA...', privateKey: '-----BEGIN OPENSSH PRIVATE KEY-----...',
      keyType: 'ED25519', keyUsage: 'USER_KEY',
    };
    component.saveSshKey();
    expect(component.sshKeySaveSuccess()).toBeTrue();
    tick(3000);
    expect(component.sshKeySaveSuccess()).toBeFalse();
  }));

  it('should_deleteSshKey', () => {
    fixture.detectChanges();
    expect(component.sshKeys().length).toBe(1);

    component.deleteSshKey('sk-1');

    expect(sshKeyServiceSpy.deleteSshKey).toHaveBeenCalledWith('sk-1');
    expect(component.sshKeys().length).toBe(0);
    expect(component.deletingSshKeyId()).toBeNull();
  });

  // ===== STEP 3: Projects =====

  it('should_toggleImportPanel', () => {
    fixture.detectChanges();
    expect(component.showImportPanel()).toBeFalse();
    component.toggleImportPanel();
    expect(component.showImportPanel()).toBeTrue();
    component.toggleImportPanel();
    expect(component.showImportPanel()).toBeFalse();
  });

  it('should_resetImportState_when_panelClosed', () => {
    fixture.detectChanges();
    component.toggleImportPanel();
    component.onImportConnectionChange('pc-1');
    component.fetchRemoteProjects();
    expect(component.importCandidates().length).toBeGreaterThan(0);

    component.toggleImportPanel();
    expect(component.importConnectionId()).toBe('');
    expect(component.importCandidates().length).toBe(0);
    expect(component.importError()).toBeNull();
  });

  it('should_onImportConnectionChange_autoFetch', () => {
    fixture.detectChanges();
    component.toggleImportPanel();
    component.onImportConnectionChange('pc-1');
    expect(platformServiceSpy.getRemoteProjects).toHaveBeenCalledWith('pc-1');
    expect(component.importCandidates().length).toBe(3);
  });

  it('should_notAutoFetch_when_connectionCleared', () => {
    fixture.detectChanges();
    component.onImportConnectionChange('pc-1');
    platformServiceSpy.getRemoteProjects.calls.reset();

    component.onImportConnectionChange('');

    expect(platformServiceSpy.getRemoteProjects).not.toHaveBeenCalled();
    expect(component.importCandidates().length).toBe(0);
  });

  it('should_fetchRemoteProjects_fromProvider', () => {
    fixture.detectChanges();
    component.onImportConnectionChange('pc-1');
    component.fetchRemoteProjects();

    expect(platformServiceSpy.getRemoteProjects).toHaveBeenCalledWith('pc-1');
    expect(component.importCandidates().length).toBe(3);
    expect(component.importLoading()).toBeFalse();
  });

  it('should_notFetchRemoteProjects_when_noConnectionSelected', () => {
    fixture.detectChanges();
    platformServiceSpy.getRemoteProjects.calls.reset();
    component.fetchRemoteProjects();
    expect(platformServiceSpy.getRemoteProjects).not.toHaveBeenCalled();
  });

  it('should_showError_when_fetchRemoteProjectsFails', () => {
    platformServiceSpy.getRemoteProjects.and.returnValue(
      throwError(() => ({ error: { success: false, message: 'SSL certificate not trusted' } })),
    );
    fixture.detectChanges();
    component.onImportConnectionChange('pc-1');
    component.fetchRemoteProjects();

    expect(component.importError()).toBeTruthy();
    expect(component.importError()).toBe('SSL certificate not trusted');
    expect(component.importLoading()).toBeFalse();
  });

  it('should_markAlreadyImportedProjects', () => {
    fixture.detectChanges();
    component.onImportConnectionChange('pc-1');
    component.fetchRemoteProjects();

    const candidates = component.importCandidates();
    expect(component.isAlreadyImported(candidates[0])).toBeTrue(); // SQ
    expect(component.isAlreadyImported(candidates[1])).toBeFalse(); // DEV
    expect(component.isAlreadyImported(candidates[2])).toBeFalse(); // OPS
  });

  it('should_toggleCandidateSelection', () => {
    fixture.detectChanges();
    component.onImportConnectionChange('pc-1');
    component.fetchRemoteProjects();

    expect(component.importCandidates()[1].selected).toBeFalse();
    component.toggleCandidateSelection(1);
    expect(component.importCandidates()[1].selected).toBeTrue();
    component.toggleCandidateSelection(1);
    expect(component.importCandidates()[1].selected).toBeFalse();
  });

  it('should_selectAllCandidates_exceptAlreadyImported', () => {
    fixture.detectChanges();
    component.onImportConnectionChange('pc-1');
    component.fetchRemoteProjects();

    component.selectAllCandidates();
    const candidates = component.importCandidates();
    expect(candidates[0].selected).toBeFalse(); // SQ already imported
    expect(candidates[1].selected).toBeTrue();
    expect(candidates[2].selected).toBeTrue();
  });

  it('should_deselectAllCandidates', () => {
    fixture.detectChanges();
    component.onImportConnectionChange('pc-1');
    component.fetchRemoteProjects();
    component.selectAllCandidates();

    component.deselectAllCandidates();
    expect(component.importCandidates().every((c) => !c.selected)).toBeTrue();
  });

  it('should_canImport_returnFalse_when_noneSelected', () => {
    fixture.detectChanges();
    component.onImportConnectionChange('pc-1');
    component.fetchRemoteProjects();
    expect(component.canImport()).toBeFalse();
  });

  it('should_canImport_returnTrue_when_someSelected', () => {
    fixture.detectChanges();
    component.onImportConnectionChange('pc-1');
    component.fetchRemoteProjects();
    component.toggleCandidateSelection(1);
    expect(component.canImport()).toBeTrue();
  });

  it('should_updateCandidateFields', () => {
    fixture.detectChanges();
    component.onImportConnectionChange('pc-1');
    component.fetchRemoteProjects();

    component.updateCandidateName(1, 'Custom Name');
    expect(component.importCandidates()[1].name).toBe('Custom Name');

    component.updateCandidateDescription(1, 'Custom Desc');
    expect(component.importCandidates()[1].description).toBe('Custom Desc');
  });

  it('should_setDefaultCandidateValues_fromRemoteProject', () => {
    fixture.detectChanges();
    component.onImportConnectionChange('pc-1');
    component.fetchRemoteProjects();

    const candidate = component.importCandidates()[1]; // DEV
    expect(candidate.name).toBe('DevTools');
    expect(candidate.description).toBe('Dev utilities');
  });

  it('should_importSelected_createProjectsAndUpdateList', () => {
    fixture.detectChanges();
    const initialCount = component.projectStates().length;
    component.onImportConnectionChange('pc-1');
    component.fetchRemoteProjects();
    component.toggleCandidateSelection(1); // DEV
    component.toggleCandidateSelection(2); // OPS

    component.importSelected();

    expect(projectServiceSpy.createProject).toHaveBeenCalledTimes(2);
    expect(component.projectStates().length).toBe(initialCount + 2);
    expect(component.importSaving()).toBeFalse();
    expect(component.showImportPanel()).toBeFalse();
  });

  it('should_showPartialError_when_someImportsFail', () => {
    let callCount = 0;
    projectServiceSpy.createProject.and.callFake(() => {
      callCount++;
      if (callCount === 1) return of(mockProjects[0]);
      return throwError(() => new Error('fail'));
    });

    fixture.detectChanges();
    component.onImportConnectionChange('pc-1');
    component.fetchRemoteProjects();
    component.toggleCandidateSelection(1);
    component.toggleCandidateSelection(2);

    component.importSelected();

    expect(component.importSaving()).toBeFalse();
    expect(component.importSaveError()).toBeTruthy();
    expect(component.importSaveError()).toContain('projectConfig.projects.errors.partialImportFailure');
  });

  it('should_getSelectedCandidates_returnOnlySelected', () => {
    fixture.detectChanges();
    component.onImportConnectionChange('pc-1');
    component.fetchRemoteProjects();
    component.toggleCandidateSelection(1);

    const selected = component.getSelectedCandidates();
    expect(selected.length).toBe(1);
    expect(selected[0].remote.key).toBe('DEV');
  });

  it('should_autoSelectConnection_when_singleProvider', () => {
    platformServiceSpy.getConnectionsByTenant.and.returnValue(of([mockTicketConnection]));
    sshKeyServiceSpy.getSshKeysByTenant.and.returnValue(of([]));
    fixture = TestBed.createComponent(ProjectConfigComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    component.toggleImportPanel();

    expect(component.importConnectionId()).toBe('pc-1');
    expect(platformServiceSpy.getRemoteProjects).toHaveBeenCalledWith('pc-1');
  });

  it('should_notAutoSelect_when_multipleProviders', () => {
    // Need 2+ ticket providers for auto-select to be skipped
    const secondTicket: PlatformConnection = {
      ...mockTicketConnection, id: 'pc-3', name: 'Jira Server - Staging',
      platformType: PlatformConnectionType.JIRA_SERVER, platformCategory: 'TICKET_PROVIDER',
      baseUrl: 'https://jira-staging.myorg.com',
    };
    platformServiceSpy.getConnectionsByTenant.and.returnValue(of([mockTicketConnection, secondTicket, mockGitConnection]));
    fixture = TestBed.createComponent(ProjectConfigComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    platformServiceSpy.getRemoteProjects.calls.reset();

    component.toggleImportPanel();

    expect(component.importConnectionId()).toBe('');
    expect(platformServiceSpy.getRemoteProjects).not.toHaveBeenCalled();
  });

  it('should_setImportFetchComplete_when_fetchSucceeds', () => {
    fixture.detectChanges();
    component.onImportConnectionChange('pc-1');
    expect(component.importFetchComplete()).toBeTrue();
  });

  it('should_setImportFetchComplete_when_fetchFails', () => {
    platformServiceSpy.getRemoteProjects.and.returnValue(
      throwError(() => ({ error: { message: 'Connection refused' } })),
    );
    fixture.detectChanges();
    component.onImportConnectionChange('pc-1');

    expect(component.importFetchComplete()).toBeTrue();
    expect(component.importError()).toBeTruthy();
  });

  // ===== STEP 4: Branch & Workflow =====

  it('should_resolveConnectionName_when_projectHasConnectionId', () => {
    fixture.detectChanges();
    expect(component.projectStates()[0].connectionName).toBe('Jira Cloud - Production');
    expect(component.projectStates()[1].connectionName).toBeNull();
  });

  it('should_toggleProjectExpanded', () => {
    fixture.detectChanges();
    expect(component.projectStates()[0].expanded).toBeFalse();
    component.toggleProject(0);
    expect(component.projectStates()[0].expanded).toBeTrue();
  });

  it('should_loadMappings_when_projectExpanded', () => {
    fixture.detectChanges();
    // Eager fetch already called getWorkflowMappings for all projects
    expect(projectServiceSpy.getWorkflowMappings).toHaveBeenCalledWith('p1');
    // mappingsLoaded should be true after eager fetch
    expect(component.projectStates()[0].mappingsLoaded).toBeTrue();
    // Expanding should NOT re-fetch since mappings are already loaded
    projectServiceSpy.getWorkflowMappings.calls.reset();
    component.toggleProject(0);
    expect(projectServiceSpy.getWorkflowMappings).not.toHaveBeenCalled();
    expect(component.projectStates()[0].expanded).toBeTrue();
  });

  it('should_loadMappings_lazily_when_eagerFetchFailed', () => {
    // Reset and re-create with eager fetch failing
    projectServiceSpy.getWorkflowMappings.and.returnValue(throwError(() => new Error('fail')));
    fixture = TestBed.createComponent(ProjectConfigComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    // After failed eager fetch, mappingsLoaded should still be true
    expect(component.projectStates()[0].mappingsLoaded).toBeTrue();
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
    expect(projectServiceSpy.updateProject).toHaveBeenCalledWith('p1', jasmine.objectContaining({
      branchNamingTemplate: jasmine.any(String),
      defaultBranch: jasmine.any(String),
      connectionId: 'pc-1',
      externalProjectId: 'SQ',
    }));
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

  it('should_updateBranchNamingTemplate', () => {
    fixture.detectChanges();
    component.updateBranchNamingTemplate(0, '{type}/{ticket}');
    expect(component.projectStates()[0].project.branchNamingTemplate).toBe('{type}/{ticket}');
  });

  it('should_updateDefaultBranch', () => {
    fixture.detectChanges();
    component.updateDefaultBranch(0, 'develop');
    expect(component.projectStates()[0].project.defaultBranch).toBe('develop');
  });

  it('should_updateDefaultBranch_fallbackToMain_when_empty', () => {
    fixture.detectChanges();
    component.updateDefaultBranch(0, '');
    expect(component.projectStates()[0].project.defaultBranch).toBe('main');
  });

  it('should_fetchRemoteStatuses', () => {
    fixture.detectChanges();
    component.fetchRemoteStatuses(0);
    expect(platformServiceSpy.getProjectStatuses).toHaveBeenCalledWith('pc-1', 'SQ');
    expect(component.projectStates()[0].remoteStatuses).toEqual(mockRemoteStatuses);
  });

  it('should_showFetchError_when_noConnection', fakeAsync(() => {
    fixture.detectChanges();
    component.fetchRemoteStatuses(1); // project-beta has no connectionId
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

  it('should_showEmptyStatuses_when_fetchFails', () => {
    platformServiceSpy.getProjectStatuses.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.fetchRemoteStatuses(0);
    expect(component.projectStates()[0].remoteStatuses.length).toBe(0);
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

  // --- Step 4: Git Configuration ---

  it('should_updateGitConnectionId', () => {
    fixture.detectChanges();
    component.updateGitConnectionId(0, 'pc-2');
    expect(component.projectStates()[0].project.gitConnectionId).toBe('pc-2');
  });

  it('should_clearGitConnectionId_when_empty', () => {
    fixture.detectChanges();
    component.updateGitConnectionId(0, '');
    expect(component.projectStates()[0].project.gitConnectionId).toBeUndefined();
  });

  it('should_updateCloneUrl', () => {
    fixture.detectChanges();
    component.updateCloneUrl(0, 'git@github.com:org/repo.git');
    expect(component.projectStates()[0].project.cloneUrl).toBe('git@github.com:org/repo.git');
  });

  it('should_clearCloneUrl_when_empty', () => {
    fixture.detectChanges();
    component.updateCloneUrl(0, '');
    expect(component.projectStates()[0].project.cloneUrl).toBeUndefined();
  });

  it('should_updateRepositoryUrl', () => {
    fixture.detectChanges();
    component.updateRepositoryUrl(0, 'https://github.com/org/repo');
    expect(component.projectStates()[0].project.repositoryUrl).toBe('https://github.com/org/repo');
  });

  it('should_clearRepositoryUrl_when_empty', () => {
    fixture.detectChanges();
    component.updateRepositoryUrl(0, '');
    expect(component.projectStates()[0].project.repositoryUrl).toBeUndefined();
  });

  it('should_testGitAccess_success', fakeAsync(() => {
    fixture.detectChanges();
    component.updateCloneUrl(0, 'git@github.com:org/repo.git');
    component.updateGitConnectionId(0, 'pc-2');
    component.testGitAccess(0);

    expect(workspaceServiceSpy.testGitAccess).toHaveBeenCalledWith(jasmine.objectContaining({
      cloneUrl: 'git@github.com:org/repo.git',
      branch: 'main',
      sshKeyId: 'sk-1',
    }));

    expect(component.testingGitAccessProjectId()).toBeNull();
    expect(component.testGitAccessResult()).toBeTruthy();
    expect(component.testGitAccessResult()!.result.success).toBeTrue();

    tick(10000);
    expect(component.testGitAccessResult()).toBeNull();
  }));

  it('should_testGitAccess_failure', fakeAsync(() => {
    workspaceServiceSpy.testGitAccess.and.returnValue(throwError(() => ({ error: { message: 'Auth failed' } })));
    fixture.detectChanges();
    component.updateCloneUrl(0, 'https://github.com/org/repo.git');
    component.testGitAccess(0);

    expect(component.testingGitAccessProjectId()).toBeNull();
    expect(component.testGitAccessResult()).toBeTruthy();
    expect(component.testGitAccessResult()!.result.success).toBeFalse();
    expect(component.testGitAccessResult()!.result.message).toBe('Auth failed');

    tick(10000);
    expect(component.testGitAccessResult()).toBeNull();
  }));

  it('should_notTestGitAccess_when_noCloneUrl', () => {
    fixture.detectChanges();
    component.testGitAccess(0);
    expect(workspaceServiceSpy.testGitAccess).not.toHaveBeenCalled();
  });

  it('should_getGitConnectionName', () => {
    fixture.detectChanges();
    expect(component.getGitConnectionName('pc-2')).toBe('GitHub - Organization');
    expect(component.getGitConnectionName('nonexistent')).toBeNull();
    expect(component.getGitConnectionName(undefined)).toBeNull();
  });

  it('should_saveMappings_includeGitFields', () => {
    fixture.detectChanges();
    component.toggleProject(0);
    fixture.detectChanges();
    component.updateGitConnectionId(0, 'pc-2');
    component.updateCloneUrl(0, 'git@github.com:org/repo.git');
    component.updateRepositoryUrl(0, 'https://github.com/org/repo');
    component.saveMappings(0);

    expect(projectServiceSpy.updateProject).toHaveBeenCalledWith('p1', jasmine.objectContaining({
      gitConnectionId: 'pc-2',
      cloneUrl: 'git@github.com:org/repo.git',
      repositoryUrl: 'https://github.com/org/repo',
    }));
  });

  // --- Mapping label ---

  it('should_getMappingLabel_returnLoading_when_mappingsNotLoaded', () => {
    projectServiceSpy.getWorkflowMappings.and.returnValue(of([]));
    fixture.detectChanges();
    // Before eager load completes (simulated by not setting mappingsLoaded)
    const states = component.projectStates();
    // After detectChanges, the eager load should have completed (mocked as sync)
    // So mappingsLoaded should be true. To test the loading state, manually set it:
    const testState = { ...states[0], mappingsLoaded: false, mappings: [] as WorkflowMapping[] };
    expect(component.getMappingLabel(testState)).toBe('projectConfig.branchWorkflow.loadingMappings');
  });

  it('should_getMappingLabel_returnNotConfigured_when_noMappingsExist', () => {
    projectServiceSpy.getWorkflowMappings.and.returnValue(of([]));
    fixture.detectChanges();
    const ps = component.projectStates()[0];
    // After eager load, mappingsLoaded should be true and mappings empty
    expect(ps.mappingsLoaded).toBeTrue();
    expect(ps.mappings.length).toBe(0);
    expect(component.getMappingLabel(ps)).toBe('projectConfig.branchWorkflow.notConfigured');
  });

  it('should_getMappingLabel_returnMappingCount_when_eagerLoaded', () => {
    fixture.detectChanges();
    const ps = component.projectStates()[0];
    expect(ps.mappingsLoaded).toBeTrue();
    expect(ps.mappings.length).toBeGreaterThan(0);
    expect(component.getMappingLabel(ps)).toBe('projectConfig.branchWorkflow.mappingCount');
  });

  it('should_getMappingLabel_returnMappingCount_when_expanded', () => {
    fixture.detectChanges();
    component.toggleProject(0);
    fixture.detectChanges();
    const ps = component.projectStates()[0];
    expect(ps.expanded).toBeTrue();
    expect(ps.mappings.length).toBeGreaterThan(0);
    expect(component.getMappingLabel(ps)).toBe('projectConfig.branchWorkflow.mappingCount');
  });

  it('should_getMappingLabel_returnMappingCount_when_collapsedWithMappings', () => {
    fixture.detectChanges();
    // Expand
    component.toggleProject(0);
    fixture.detectChanges();
    // Collapse again
    component.toggleProject(0);
    fixture.detectChanges();
    const ps = component.projectStates()[0];
    expect(ps.expanded).toBeFalse();
    expect(ps.mappings.length).toBeGreaterThan(0);
    expect(component.getMappingLabel(ps)).toBe('projectConfig.branchWorkflow.mappingCount');
  });

  it('should_getMappingLabel_useSingular_when_oneMapping', () => {
    fixture.detectChanges();
    component.toggleProject(0);
    fixture.detectChanges();
    component.removeMapping(0, 0);
    const ps = component.projectStates()[0];
    expect(component.getMappingLabel(ps)).toBe('projectConfig.branchWorkflow.mappingCount');
  });

  // --- Connection helpers ---

  it('should_getConnectionPlatformType_returnType_when_connectionExists', () => {
    fixture.detectChanges();
    expect(component.getConnectionPlatformType('pc-1')).toBe('JIRA_CLOUD');
    expect(component.getConnectionPlatformType('pc-2')).toBe('GITHUB');
  });

  it('should_getConnectionPlatformType_returnNull_when_noConnection', () => {
    fixture.detectChanges();
    expect(component.getConnectionPlatformType(undefined)).toBeNull();
    expect(component.getConnectionPlatformType('nonexistent')).toBeNull();
  });

  it('should_getConnectionStatus_returnStatus_when_connectionExists', () => {
    fixture.detectChanges();
    expect(component.getConnectionStatus('pc-1')).toBe('ACTIVE');
    expect(component.getConnectionStatus('pc-2')).toBe('ACTIVE');
  });

  it('should_getConnectionStatus_returnNull_when_noConnection', () => {
    fixture.detectChanges();
    expect(component.getConnectionStatus(undefined)).toBeNull();
    expect(component.getConnectionStatus('nonexistent')).toBeNull();
  });

  // --- Wizard step definitions ---

  it('should_have4WizardSteps', () => {
    expect(component.steps.length).toBe(4);
    expect(component.steps.map((s) => s.id)).toEqual([
      'ticket-providers', 'git-remotes', 'projects', 'branch-workflow',
    ]);
  });

  // ===== Epic 10.1: GitHub App installationId field =====

  it('should_includeInstallationIdField_forGitHubAppAuthType', () => {
    fixture.detectChanges();
    component.gitForm.platformType = 'GITHUB';
    const options = component.getGitAuthTypeOptions();
    const appOption = options.find((o) => o.label === 'projectConfig.authTypes.app');
    expect(appOption).toBeTruthy();
    expect(appOption!.fields.length).toBe(3);
    expect(appOption!.fields[0].key).toBe('appId');
    expect(appOption!.fields[1].key).toBe('installationId');
    expect(appOption!.fields[2].key).toBe('privateKey');
  });

  // ===== Epic 10.2: Deploy Key UI =====

  it('should_initializeSshKeyForm_withKeyUsage', () => {
    fixture.detectChanges();
    component.toggleSshKeyForm();
    expect(component.sshKeyForm.keyUsage).toBe('USER_KEY');
  });

  it('should_passKeyUsage_whenSavingSshKey', () => {
    fixture.detectChanges();
    component.sshKeyForm = {
      connectionId: 'pc-2', name: 'deploy-key',
      publicKey: 'ssh-ed25519 AAAA...', privateKey: '-----BEGIN OPENSSH PRIVATE KEY-----...',
      keyType: 'ED25519', keyUsage: 'USER_KEY',
    };

    component.saveSshKey();

    expect(sshKeyServiceSpy.createSshKey).toHaveBeenCalled();
    const callArgs = sshKeyServiceSpy.createSshKey.calls.mostRecent().args[0];
    expect(callArgs.keyUsage).toBe('USER_KEY');
  });

  it('should_generateDeployKey_andShowPublicKey', () => {
    fixture.detectChanges();
    component.generateDeployKey('pc-2');

    expect(sshKeyServiceSpy.generateDeployKey).toHaveBeenCalledWith('pc-2', 't1', jasmine.any(String));
    expect(component.generatingDeployKey()).toBeFalse();
    expect(component.generatedPublicKey()).toBe('ssh-ed25519 AAAAC...');
    expect(component.sshKeys().length).toBe(2); // original + generated
  });

  it('should_notGenerateDeployKey_when_noUser', () => {
    (authServiceSpy.user as jasmine.Spy).and.returnValue(null);
    fixture.detectChanges();
    component.generateDeployKey('pc-2');
    expect(sshKeyServiceSpy.generateDeployKey).not.toHaveBeenCalled();
  });

  it('should_showError_when_generateDeployKeyFails', () => {
    sshKeyServiceSpy.generateDeployKey.and.returnValue(
      throwError(() => ({ error: { message: 'Key generation failed' } })),
    );
    fixture.detectChanges();
    component.generateDeployKey('pc-2');

    expect(component.sshKeySaveError()).toBe('Key generation failed');
    expect(component.generatingDeployKey()).toBeFalse();
  });

  it('should_dismissGeneratedKey', () => {
    fixture.detectChanges();
    component.generateDeployKey('pc-2');
    expect(component.generatedPublicKey()).toBeTruthy();
    component.dismissGeneratedKey();
    expect(component.generatedPublicKey()).toBeNull();
    expect(component.copiedKey()).toBeFalse();
  });

  it('should_copyGeneratedKey_toClipboard', async () => {
    fixture.detectChanges();
    component.generateDeployKey('pc-2');
    expect(component.generatedPublicKey()).toBeTruthy();

    spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.resolve());
    component.copyGeneratedKey();
    await fixture.whenStable();
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('ssh-ed25519 AAAAC...');
    expect(component.copiedKey()).toBeTrue();
  });

  it('should_notCopy_when_noGeneratedKey', () => {
    fixture.detectChanges();
    spyOn(navigator.clipboard, 'writeText');
    component.copyGeneratedKey();
    expect(navigator.clipboard.writeText).not.toHaveBeenCalled();
  });

  it('should_returnKeyUsageLabel', () => {
    expect(component.getKeyUsageLabel('DEPLOY_KEY')).toBe('projectConfig.sshKeys.keyUsage.deployKey');
    expect(component.getKeyUsageLabel('USER_KEY')).toBe('projectConfig.sshKeys.keyUsage.userKey');
    expect(component.getKeyUsageLabel(undefined)).toBe('projectConfig.sshKeys.keyUsage.userKey');
  });

  // ===== Epic 10.3: Review Bot Configuration =====

  it('should_loadReviewBotConfigs_onInit', () => {
    fixture.detectChanges();
    expect(reviewBotConfigServiceSpy.getConfigsByTenant).toHaveBeenCalledWith('t1');
    expect(component.reviewBotConfigs().length).toBe(1);
  });

  it('should_toggleReviewBotForm', () => {
    fixture.detectChanges();
    expect(component.showReviewBotForm()).toBeFalse();
    component.toggleReviewBotForm();
    expect(component.showReviewBotForm()).toBeTrue();
    component.toggleReviewBotForm();
    expect(component.showReviewBotForm()).toBeFalse();
  });

  it('should_autoSelectConnection_when_singleGitRemote_forReviewBot', () => {
    fixture.detectChanges();
    component.toggleReviewBotForm();
    expect(component.reviewBotForm.connectionId).toBe('pc-2');
  });

  it('should_canSaveReviewBot_returnFalse_when_formIncomplete', () => {
    fixture.detectChanges();
    expect(component.canSaveReviewBot()).toBeFalse();
  });

  it('should_canSaveReviewBot_returnTrue_when_formComplete', () => {
    fixture.detectChanges();
    component.reviewBotForm = {
      connectionId: 'pc-2', botUsername: 'my-bot', botAccessToken: 'ghp_abc',
      enabled: true, autoAssign: true,
    };
    expect(component.canSaveReviewBot()).toBeTrue();
  });

  it('should_saveReviewBot_andAddToList', () => {
    fixture.detectChanges();
    const initialCount = component.reviewBotConfigs().length;
    component.reviewBotForm = {
      connectionId: 'pc-2', botUsername: 'my-bot', botAccessToken: 'ghp_abc',
      enabled: true, autoAssign: true,
    };

    component.saveReviewBot();

    expect(reviewBotConfigServiceSpy.createConfig).toHaveBeenCalled();
    expect(component.reviewBotConfigs().length).toBe(initialCount + 1);
    expect(component.showReviewBotForm()).toBeFalse();
    expect(component.savingReviewBot()).toBeFalse();
  });

  it('should_showError_when_saveReviewBotFails', () => {
    reviewBotConfigServiceSpy.createConfig.and.returnValue(
      throwError(() => ({ error: { message: 'Duplicate config' } })),
    );
    fixture.detectChanges();
    component.reviewBotForm = {
      connectionId: 'pc-2', botUsername: 'my-bot', botAccessToken: 'ghp_abc',
      enabled: true, autoAssign: true,
    };

    component.saveReviewBot();

    expect(component.reviewBotSaveError()).toBe('Duplicate config');
    expect(component.savingReviewBot()).toBeFalse();
  });

  it('should_toggleReviewBotEnabled', () => {
    fixture.detectChanges();
    const bot = component.reviewBotConfigs()[0];
    expect(bot.enabled).toBeTrue();

    component.toggleReviewBotEnabled(bot);

    expect(reviewBotConfigServiceSpy.updateConfig).toHaveBeenCalledWith('bot-1', { enabled: false });
    expect(component.reviewBotConfigs()[0].enabled).toBeFalse();
  });

  it('should_deleteReviewBot', () => {
    fixture.detectChanges();
    expect(component.reviewBotConfigs().length).toBe(1);

    component.deleteReviewBot('bot-1');

    expect(reviewBotConfigServiceSpy.deleteConfig).toHaveBeenCalledWith('bot-1');
    expect(component.reviewBotConfigs().length).toBe(0);
    expect(component.deletingReviewBotId()).toBeNull();
  });

  it('should_getReviewBotForConnection', () => {
    fixture.detectChanges();
    expect(component.getReviewBotForConnection('pc-2')).toBeTruthy();
    expect(component.getReviewBotForConnection('pc-2')!.botUsername).toBe('squadron-bot');
    expect(component.getReviewBotForConnection('pc-1')).toBeUndefined();
  });

  // ===== Epic 10.4: Credential Status Indicators =====

  it('should_returnLinked_when_connectionIsActive', () => {
    fixture.detectChanges();
    expect(component.getCredentialStatus('pc-1')).toBe('LINKED');
    expect(component.getCredentialStatus('pc-2')).toBe('LINKED');
  });

  it('should_returnMissing_when_noConnectionId', () => {
    fixture.detectChanges();
    expect(component.getCredentialStatus(undefined)).toBe('MISSING');
  });

  it('should_returnMissing_when_connectionNotFound', () => {
    fixture.detectChanges();
    expect(component.getCredentialStatus('nonexistent')).toBe('MISSING');
  });

  it('should_returnExpired_when_connectionInError', () => {
    const errorConnection: PlatformConnection = {
      ...mockGitConnection, id: 'pc-err', status: ConnectionStatus.ERROR,
    };
    platformServiceSpy.getConnectionsByTenant.and.returnValue(of([...mockConnections, errorConnection]));
    reviewBotConfigServiceSpy.getConfigsByTenant.and.returnValue(of([]));
    fixture = TestBed.createComponent(ProjectConfigComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.getCredentialStatus('pc-err')).toBe('EXPIRED');
  });

  it('should_returnCredentialStatusLabel', () => {
    expect(component.getCredentialStatusLabel('LINKED')).toBe('projectConfig.credentialStatus.linked');
    expect(component.getCredentialStatusLabel('EXPIRED')).toBe('projectConfig.credentialStatus.expired');
    expect(component.getCredentialStatusLabel('MISSING')).toBe('projectConfig.credentialStatus.missing');
    expect(component.getCredentialStatusLabel('UNKNOWN')).toBe('UNKNOWN');
  });

  it('should_clearReviewBotConfigs_onApiError', () => {
    projectServiceSpy.getProjectsByTenant.and.returnValue(throwError(() => new Error('fail')));
    projectServiceSpy.getWorkflowStates.and.returnValue(throwError(() => new Error('fail')));
    platformServiceSpy.getConnectionsByTenant.and.returnValue(throwError(() => new Error('fail')));
    sshKeyServiceSpy.getSshKeysByTenant.and.returnValue(throwError(() => new Error('fail')));
    reviewBotConfigServiceSpy.getConfigsByTenant.and.returnValue(throwError(() => new Error('fail')));

    fixture.detectChanges();

    expect(component.reviewBotConfigs().length).toBe(0);
  });

  // ===== Provider Editing =====

  it('should_editTicketProvider_populateFormWithConnectionData', () => {
    fixture.detectChanges();
    const conn = component.ticketProviders()[0];
    component.editTicketProvider(conn);

    expect(component.editingTicketProviderId()).toBe('pc-1');
    expect(component.showTicketForm()).toBeTrue();
    expect(component.ticketForm.name).toBe('Jira Cloud - Production');
    expect(component.ticketForm.platformType).toBe('JIRA_CLOUD');
    expect(component.ticketForm.baseUrl).toBe('https://myorg.atlassian.net');
    expect(component.ticketForm.credentials).toEqual({});
  });

  it('should_editTicketProvider_resetEditingId_when_formClosed', () => {
    fixture.detectChanges();
    const conn = component.ticketProviders()[0];
    component.editTicketProvider(conn);
    expect(component.editingTicketProviderId()).toBe('pc-1');

    component.toggleTicketForm(); // close
    expect(component.editingTicketProviderId()).toBeNull();
    expect(component.showTicketForm()).toBeFalse();
  });

  it('should_canSaveTicketProvider_returnTrue_when_editing_withoutCredentials', () => {
    fixture.detectChanges();
    const conn = component.ticketProviders()[0];
    component.editTicketProvider(conn);

    // Credentials are empty but should still be savable in edit mode
    expect(component.ticketForm.credentials).toEqual({});
    expect(component.canSaveTicketProvider()).toBeTrue();
  });

  it('should_saveTicketProvider_callUpdateConnection_when_editing', () => {
    const updatedConn = { ...mockTicketConnection, name: 'Updated Jira' };
    platformServiceSpy.updateConnection.and.returnValue(of(updatedConn));
    fixture.detectChanges();

    const conn = component.ticketProviders()[0];
    component.editTicketProvider(conn);
    component.ticketForm.name = 'Updated Jira';

    component.saveTicketProvider();

    expect(platformServiceSpy.updateConnection).toHaveBeenCalledWith('pc-1', jasmine.objectContaining({
      name: 'Updated Jira',
      platformType: 'JIRA_CLOUD',
    }));
    expect(platformServiceSpy.createConnectionFromRequest).not.toHaveBeenCalled();
    expect(component.ticketProviders()[0].name).toBe('Updated Jira');
    expect(component.editingTicketProviderId()).toBeNull();
    expect(component.showTicketForm()).toBeFalse();
    expect(component.savingTicketProvider()).toBeFalse();
  });

  it('should_saveTicketProvider_includeCredentials_when_filledInEditMode', () => {
    platformServiceSpy.updateConnection.and.returnValue(of(mockTicketConnection));
    fixture.detectChanges();

    const conn = component.ticketProviders()[0];
    component.editTicketProvider(conn);
    component.ticketForm.credentials = { email: 'new@test.com', apiToken: 'new-token' };

    component.saveTicketProvider();

    const callArgs = platformServiceSpy.updateConnection.calls.mostRecent().args[1];
    expect((callArgs as any).credentials).toEqual({ email: 'new@test.com', apiToken: 'new-token' });
  });

  it('should_saveTicketProvider_omitCredentials_when_emptyInEditMode', () => {
    platformServiceSpy.updateConnection.and.returnValue(of(mockTicketConnection));
    fixture.detectChanges();

    const conn = component.ticketProviders()[0];
    component.editTicketProvider(conn);
    // credentials remain empty

    component.saveTicketProvider();

    const callArgs = platformServiceSpy.updateConnection.calls.mostRecent().args[1];
    expect((callArgs as any).credentials).toBeUndefined();
  });

  it('should_showError_when_updateTicketProviderFails', () => {
    platformServiceSpy.updateConnection.and.returnValue(
      throwError(() => ({ error: { message: 'Update failed' } })),
    );
    fixture.detectChanges();

    const conn = component.ticketProviders()[0];
    component.editTicketProvider(conn);
    component.saveTicketProvider();

    expect(component.ticketSaveError()).toBe('Update failed');
    expect(component.savingTicketProvider()).toBeFalse();
  });

  it('should_showTicketSaveSuccess_afterUpdate_thenClear', fakeAsync(() => {
    platformServiceSpy.updateConnection.and.returnValue(of(mockTicketConnection));
    fixture.detectChanges();

    const conn = component.ticketProviders()[0];
    component.editTicketProvider(conn);
    component.saveTicketProvider();
    expect(component.ticketSaveSuccess()).toBeTrue();
    tick(3000);
    expect(component.ticketSaveSuccess()).toBeFalse();
  }));

  it('should_editGitRemote_populateFormWithConnectionData', () => {
    fixture.detectChanges();
    const conn = component.gitRemotes()[0];
    component.editGitRemote(conn);

    expect(component.editingGitRemoteId()).toBe('pc-2');
    expect(component.showGitForm()).toBeTrue();
    expect(component.gitForm.name).toBe('GitHub - Organization');
    expect(component.gitForm.platformType).toBe('GITHUB');
    expect(component.gitForm.baseUrl).toBe('https://api.github.com');
    expect(component.gitForm.credentials).toEqual({});
  });

  it('should_editGitRemote_resetEditingId_when_formClosed', () => {
    fixture.detectChanges();
    const conn = component.gitRemotes()[0];
    component.editGitRemote(conn);
    expect(component.editingGitRemoteId()).toBe('pc-2');

    component.toggleGitForm(); // close
    expect(component.editingGitRemoteId()).toBeNull();
    expect(component.showGitForm()).toBeFalse();
  });

  it('should_canSaveGitRemote_returnTrue_when_editing_withoutCredentials', () => {
    fixture.detectChanges();
    const conn = component.gitRemotes()[0];
    component.editGitRemote(conn);

    expect(component.gitForm.credentials).toEqual({});
    expect(component.canSaveGitRemote()).toBeTrue();
  });

  it('should_saveGitRemote_callUpdateConnection_when_editing', () => {
    const updatedConn = { ...mockGitConnection, name: 'Updated GitHub' };
    platformServiceSpy.updateConnection.and.returnValue(of(updatedConn));
    fixture.detectChanges();

    const conn = component.gitRemotes()[0];
    component.editGitRemote(conn);
    component.gitForm.name = 'Updated GitHub';

    component.saveGitRemote();

    expect(platformServiceSpy.updateConnection).toHaveBeenCalledWith('pc-2', jasmine.objectContaining({
      name: 'Updated GitHub',
      platformType: 'GITHUB',
    }));
    expect(component.gitRemotes()[0].name).toBe('Updated GitHub');
    expect(component.editingGitRemoteId()).toBeNull();
    expect(component.showGitForm()).toBeFalse();
    expect(component.savingGitRemote()).toBeFalse();
  });

  it('should_saveGitRemote_includeCredentials_when_filledInEditMode', () => {
    platformServiceSpy.updateConnection.and.returnValue(of(mockGitConnection));
    fixture.detectChanges();

    const conn = component.gitRemotes()[0];
    component.editGitRemote(conn);
    component.gitForm.credentials = { pat: 'new-pat' };

    component.saveGitRemote();

    const callArgs = platformServiceSpy.updateConnection.calls.mostRecent().args[1];
    expect((callArgs as any).credentials).toEqual({ pat: 'new-pat' });
  });

  it('should_saveGitRemote_omitCredentials_when_emptyInEditMode', () => {
    platformServiceSpy.updateConnection.and.returnValue(of(mockGitConnection));
    fixture.detectChanges();

    const conn = component.gitRemotes()[0];
    component.editGitRemote(conn);

    component.saveGitRemote();

    const callArgs = platformServiceSpy.updateConnection.calls.mostRecent().args[1];
    expect((callArgs as any).credentials).toBeUndefined();
  });

  it('should_showError_when_updateGitRemoteFails', () => {
    platformServiceSpy.updateConnection.and.returnValue(
      throwError(() => ({ error: { message: 'Auth expired' } })),
    );
    fixture.detectChanges();

    const conn = component.gitRemotes()[0];
    component.editGitRemote(conn);
    component.saveGitRemote();

    expect(component.gitSaveError()).toBe('Auth expired');
    expect(component.savingGitRemote()).toBeFalse();
  });

  it('should_updateAllConnections_when_editingTicketProvider', () => {
    const updatedConn = { ...mockTicketConnection, name: 'Renamed Jira' };
    platformServiceSpy.updateConnection.and.returnValue(of(updatedConn));
    fixture.detectChanges();

    const conn = component.ticketProviders()[0];
    component.editTicketProvider(conn);
    component.ticketForm.name = 'Renamed Jira';
    component.saveTicketProvider();

    expect(component.allConnections().find((c) => c.id === 'pc-1')!.name).toBe('Renamed Jira');
  });

  it('should_updateAllConnections_when_editingGitRemote', () => {
    const updatedConn = { ...mockGitConnection, name: 'Renamed GitHub' };
    platformServiceSpy.updateConnection.and.returnValue(of(updatedConn));
    fixture.detectChanges();

    const conn = component.gitRemotes()[0];
    component.editGitRemote(conn);
    component.gitForm.name = 'Renamed GitHub';
    component.saveGitRemote();

    expect(component.allConnections().find((c) => c.id === 'pc-2')!.name).toBe('Renamed GitHub');
  });

  // ===== Test Connection =====

  it('should_testConnection_callServiceAndUpdateStatus_onSuccess', () => {
    platformServiceSpy.testConnection.and.returnValue(of(true));
    fixture.detectChanges();

    const conn = component.ticketProviders()[0];
    component.testConnection(conn);

    expect(platformServiceSpy.testConnection).toHaveBeenCalledWith('pc-1');
    expect(component.testingConnectionId()).toBeNull();
    expect(component.testConnectionResult()).toEqual({ id: 'pc-1', success: true });
    expect(component.ticketProviders()[0].status).toBe('ACTIVE');
    expect(component.allConnections().find((c) => c.id === 'pc-1')!.status).toBe('ACTIVE');
  });

  it('should_testConnection_setErrorStatus_onFailure', () => {
    platformServiceSpy.testConnection.and.returnValue(of(false));
    fixture.detectChanges();

    const conn = component.ticketProviders()[0];
    component.testConnection(conn);

    expect(component.testConnectionResult()).toEqual({ id: 'pc-1', success: false });
    expect(component.ticketProviders()[0].status).toBe('ERROR');
    expect(component.allConnections().find((c) => c.id === 'pc-1')!.status).toBe('ERROR');
  });

  it('should_testConnection_setErrorStatus_onNetworkError', () => {
    platformServiceSpy.testConnection.and.returnValue(throwError(() => new Error('Network error')));
    fixture.detectChanges();

    const conn = component.gitRemotes()[0];
    component.testConnection(conn);

    expect(component.testingConnectionId()).toBeNull();
    expect(component.testConnectionResult()).toEqual({ id: 'pc-2', success: false });
    expect(component.gitRemotes()[0].status).toBe('ERROR');
    expect(component.allConnections().find((c) => c.id === 'pc-2')!.status).toBe('ERROR');
  });

  it('should_testConnection_clearResult_after5Seconds', fakeAsync(() => {
    platformServiceSpy.testConnection.and.returnValue(of(true));
    fixture.detectChanges();

    const conn = component.ticketProviders()[0];
    component.testConnection(conn);
    expect(component.testConnectionResult()).toBeTruthy();

    tick(5000);
    expect(component.testConnectionResult()).toBeNull();
  }));

  it('should_testConnection_setTestingConnectionId_whileInProgress', () => {
    const subject = new Subject<boolean>();
    platformServiceSpy.testConnection.and.returnValue(subject.asObservable());
    fixture.detectChanges();

    const conn = component.ticketProviders()[0];
    component.testConnection(conn);

    expect(component.testingConnectionId()).toBe('pc-1');
    expect(component.testConnectionResult()).toBeNull();

    subject.next(true);
    subject.complete();

    expect(component.testingConnectionId()).toBeNull();
  });

  it('should_testConnection_updateAllConnectionLists', () => {
    platformServiceSpy.testConnection.and.returnValue(of(true));
    fixture.detectChanges();

    const conn = component.gitRemotes()[0];
    component.testConnection(conn);

    // Check all three lists are updated
    expect(component.gitRemotes()[0].status).toBe('ACTIVE');
    expect(component.allConnections().find((c) => c.id === 'pc-2')!.status).toBe('ACTIVE');
  });

  // ===== Import Dropdown Filter =====

  it('should_importDropdown_onlyShowTicketProviders', () => {
    fixture.detectChanges();
    // toggleImportPanel uses ticketProviders(), not allConnections()
    // With 1 ticket provider, it should auto-select
    component.toggleImportPanel();
    expect(component.importConnectionId()).toBe('pc-1');
    // The import connection should be a ticket provider, not a git remote
    const selectedConn = component.ticketProviders().find((c) => c.id === component.importConnectionId());
    expect(selectedConn).toBeTruthy();
    expect(selectedConn!.platformCategory).toBe('TICKET_PROVIDER');
  });
});
