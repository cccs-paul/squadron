import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ProjectConfigComponent, WizardStep } from './project-config.component';
import { ProjectService } from '../../../core/services/project.service';
import { PlatformService } from '../../../core/services/platform.service';
import { SshKeyService } from '../../../core/services/ssh-key.service';
import { AuthService } from '../../../core/auth/auth.service';
import { of, throwError } from 'rxjs';
import { Project, RemoteProject, WorkflowMapping } from '../../../core/models/project.model';
import {
  PlatformConnection,
  PlatformConnectionType,
  ConnectionStatus,
  SshKey,
} from '../../../core/models/security.model';

describe('ProjectConfigComponent', () => {
  let component: ProjectConfigComponent;
  let fixture: ComponentFixture<ProjectConfigComponent>;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;
  let platformServiceSpy: jasmine.SpyObj<PlatformService>;
  let sshKeyServiceSpy: jasmine.SpyObj<SshKeyService>;
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
      'saveWorkflowMappings', 'createProject',
    ]);
    platformServiceSpy = jasmine.createSpyObj('PlatformService', [
      'getConnectionsByTenant', 'getProjectStatuses', 'createConnectionFromRequest',
      'deleteConnection', 'getRemoteProjects',
    ]);
    sshKeyServiceSpy = jasmine.createSpyObj('SshKeyService', [
      'createSshKey', 'getSshKey', 'getSshKeysByTenant', 'getSshKeysByConnection', 'deleteSshKey',
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
    platformServiceSpy.createConnectionFromRequest.and.returnValue(of(mockGitConnection));
    platformServiceSpy.deleteConnection.and.returnValue(of(void 0));
    platformServiceSpy.getRemoteProjects.and.returnValue(of(mockRemoteProjects));
    sshKeyServiceSpy.getSshKeysByTenant.and.returnValue(of(mockSshKeys));
    sshKeyServiceSpy.createSshKey.and.returnValue(of(mockSshKeys[0]));
    sshKeyServiceSpy.deleteSshKey.and.returnValue(of(void 0));

    await TestBed.configureTestingModule({
      imports: [ProjectConfigComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ProjectService, useValue: projectServiceSpy },
        { provide: PlatformService, useValue: platformServiceSpy },
        { provide: SshKeyService, useValue: sshKeyServiceSpy },
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
    expect(component.loadError()).toBe('Not authenticated');
    expect(component.loading()).toBeFalse();
  });

  it('should_handleApiError_withFallbackData', () => {
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
    expect(component.workflowStates().length).toBe(8);
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

  it('should_isStepComplete_returnFalse_when_noMappingsConfigured', () => {
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
    expect(component.platformIcon('GITHUB')).toBe('GitHub');
    expect(component.platformIcon('JIRA_CLOUD')).toBe('Jira Cloud');
    expect(component.platformIcon('JIRA_SERVER')).toBe('Jira Server / DC');
    expect(component.platformIcon('GITLAB')).toBe('GitLab');
    expect(component.platformIcon('AZURE_DEVOPS')).toBe('Azure DevOps');
    expect(component.platformIcon('BITBUCKET')).toBe('Bitbucket');
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
    expect(options[0].label).toBe('API Token');
    expect(options[1].label).toBe('OAuth 2.0');
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
    expect(options[0].label).toBe('PAT');
    expect(options[1].label).toBe('App');
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
      keyType: 'ED25519',
    };
    expect(component.canSaveSshKey()).toBeTrue();
  });

  it('should_saveSshKey_andAddToList', () => {
    fixture.detectChanges();
    const initialCount = component.sshKeys().length;
    component.sshKeyForm = {
      connectionId: 'pc-2', name: 'deploy-key',
      publicKey: 'ssh-ed25519 AAAA...', privateKey: '-----BEGIN OPENSSH PRIVATE KEY-----...',
      keyType: 'ED25519',
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
      keyType: 'ED25519',
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
      keyType: 'ED25519',
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

    component.updateCandidateBranch(1, 'develop');
    expect(component.importCandidates()[1].defaultBranch).toBe('develop');

    component.updateCandidateRepoUrl(1, 'https://github.com/org/repo');
    expect(component.importCandidates()[1].repositoryUrl).toBe('https://github.com/org/repo');
  });

  it('should_setDefaultCandidateValues_fromRemoteProject', () => {
    fixture.detectChanges();
    component.onImportConnectionChange('pc-1');
    component.fetchRemoteProjects();

    const candidate = component.importCandidates()[1]; // DEV
    expect(candidate.name).toBe('DevTools');
    expect(candidate.description).toBe('Dev utilities');
    expect(candidate.defaultBranch).toBe('main');
    expect(candidate.repositoryUrl).toBe('https://jira.example.com/browse/DEV');
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
    expect(component.importSaveError()).toContain('1 project(s) failed');
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
    component.toggleProject(0);
    expect(projectServiceSpy.getWorkflowMappings).toHaveBeenCalledWith('p1');
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

  // --- Mapping label ---

  it('should_getMappingLabel_returnNotConfigured_when_notExpanded', () => {
    fixture.detectChanges();
    const ps = component.projectStates()[0];
    expect(ps.expanded).toBeFalse();
    expect(component.getMappingLabel(ps)).toBe('Not configured');
  });

  it('should_getMappingLabel_returnMappingCount_when_expanded', () => {
    fixture.detectChanges();
    component.toggleProject(0);
    fixture.detectChanges();
    const ps = component.projectStates()[0];
    expect(ps.expanded).toBeTrue();
    expect(component.getMappingLabel(ps)).toBe('2 mappings');
  });

  it('should_getMappingLabel_useSingular_when_oneMapping', () => {
    fixture.detectChanges();
    component.toggleProject(0);
    fixture.detectChanges();
    component.removeMapping(0, 0);
    const ps = component.projectStates()[0];
    expect(component.getMappingLabel(ps)).toBe('1 mapping');
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
});
