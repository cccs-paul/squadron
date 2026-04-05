import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';
import { PlatformConnectionsComponent } from './platform-connections.component';
import { PlatformService } from '../../../core/services/platform.service';
import { AuthService } from '../../../core/auth/auth.service';
import {
  PlatformConnection,
  PlatformConnectionType,
  ConnectionStatus,
} from '../../../core/models/security.model';

describe('PlatformConnectionsComponent', () => {
  let component: PlatformConnectionsComponent;
  let fixture: ComponentFixture<PlatformConnectionsComponent>;
  let platformServiceSpy: jasmine.SpyObj<PlatformService>;
  let authServiceMock: Partial<AuthService>;

  const mockConnections: PlatformConnection[] = [
    {
      id: 'pc-1', tenantId: 't1', name: 'GitHub Org',
      platformType: PlatformConnectionType.GITHUB,
      baseUrl: 'https://api.github.com', status: ConnectionStatus.ACTIVE,
      lastSyncAt: new Date().toISOString(),
      config: {}, createdAt: new Date().toISOString(),
    },
    {
      id: 'pc-2', tenantId: 't1', name: 'Jira Cloud',
      platformType: PlatformConnectionType.JIRA_CLOUD,
      baseUrl: 'https://myorg.atlassian.net', status: ConnectionStatus.ACTIVE,
      config: {}, createdAt: new Date().toISOString(),
    },
    {
      id: 'pc-3', tenantId: 't1', name: 'GitLab Self-Hosted',
      platformType: PlatformConnectionType.GITLAB,
      baseUrl: 'https://gitlab.example.com', status: ConnectionStatus.ERROR,
      config: {}, createdAt: new Date().toISOString(),
    },
  ];

  beforeEach(async () => {
    platformServiceSpy = jasmine.createSpyObj('PlatformService', [
      'getConnections', 'createConnection', 'updateConnection',
      'deleteConnection', 'testConnection', 'syncConnection',
    ]);
    platformServiceSpy.getConnections.and.returnValue(of(mockConnections));

    authServiceMock = {
      user: signal({
        id: 'u1',
        username: 'testuser',
        email: 'test@example.com',
        displayName: 'Test User',
        tenantId: 't1',
        tenantName: 'Test Tenant',
        roles: ['squadron-admin'],
        permissions: [],
      }),
      isAuthenticated: signal(true),
      isAdmin: signal(true),
    } as any;

    await TestBed.configureTestingModule({
      imports: [PlatformConnectionsComponent, FormsModule, TranslateModule.forRoot()],
      providers: [
        { provide: PlatformService, useValue: platformServiceSpy },
        { provide: AuthService, useValue: authServiceMock },
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PlatformConnectionsComponent);
    component = fixture.componentInstance;
  });

  it('should_create', () => {
    expect(component).toBeTruthy();
  });

  it('should_loadConnections_when_initialized', () => {
    fixture.detectChanges();

    expect(platformServiceSpy.getConnections).toHaveBeenCalledWith('t1');
    expect(component.connections()).toEqual(mockConnections);
    expect(component.loading()).toBeFalse();
  });

  it('should_openCreateModal_when_openCreateModalCalled', () => {
    component.openCreateModal();

    expect(component.showCreateModal()).toBeTrue();
    expect(component.editingConnection()).toBeNull();
    expect(component.formName).toBe('');
    expect(component.formType).toBe(PlatformConnectionType.GITHUB);
    expect(component.formBaseUrl).toBe('');
    expect(component.formToken).toBe('');
  });

  it('should_closeModal_when_closeModalCalled', () => {
    component.openCreateModal();
    component.closeModal();

    expect(component.showCreateModal()).toBeFalse();
    expect(component.editingConnection()).toBeNull();
  });

  it('should_populateForm_when_openEditModalCalled', () => {
    const conn = mockConnections[0];
    component.openEditModal(conn);

    expect(component.showCreateModal()).toBeTrue();
    expect(component.editingConnection()).toBe(conn);
    expect(component.formName).toBe('GitHub Org');
    expect(component.formType).toBe(PlatformConnectionType.GITHUB);
    expect(component.formBaseUrl).toBe('https://api.github.com');
    expect(component.formToken).toBe('');
  });

  it('should_callCreateConnection_when_saveConnectionCalledWithoutEditing', () => {
    fixture.detectChanges();
    platformServiceSpy.createConnection.and.returnValue(of(mockConnections[0]));
    platformServiceSpy.getConnections.and.returnValue(of(mockConnections));

    component.openCreateModal();
    component.formName = 'Bitbucket Cloud';
    component.formType = PlatformConnectionType.BITBUCKET;
    component.formBaseUrl = 'https://api.bitbucket.org';
    component.formToken = 'my-token';
    component.saveConnection();

    expect(platformServiceSpy.createConnection).toHaveBeenCalledWith(jasmine.objectContaining({
      name: 'Bitbucket Cloud',
      platformType: PlatformConnectionType.BITBUCKET,
      baseUrl: 'https://api.bitbucket.org',
    }));
    expect(component.showCreateModal()).toBeFalse();
  });

  it('should_callUpdateConnection_when_saveConnectionCalledWhileEditing', () => {
    fixture.detectChanges();
    platformServiceSpy.updateConnection.and.returnValue(of(mockConnections[0]));
    platformServiceSpy.getConnections.and.returnValue(of(mockConnections));

    component.openEditModal(mockConnections[0]);
    component.formName = 'GitHub Updated';
    component.saveConnection();

    expect(platformServiceSpy.updateConnection).toHaveBeenCalledWith('pc-1', jasmine.objectContaining({
      name: 'GitHub Updated',
    }));
    expect(component.showCreateModal()).toBeFalse();
  });

  it('should_callTestConnection_when_testConnectionCalled', () => {
    fixture.detectChanges();
    platformServiceSpy.testConnection.and.returnValue(of(true));

    component.testConnection(mockConnections[0]);

    expect(platformServiceSpy.testConnection).toHaveBeenCalledWith('pc-1');
    expect(component.testingId()).toBeNull();
  });

  it('should_callSyncConnection_when_syncConnectionCalled', () => {
    fixture.detectChanges();
    platformServiceSpy.syncConnection.and.returnValue(of(void 0));
    platformServiceSpy.getConnections.and.returnValue(of(mockConnections));

    component.syncConnection(mockConnections[0]);

    expect(platformServiceSpy.syncConnection).toHaveBeenCalledWith('pc-1');
    expect(component.syncingId()).toBeNull();
  });

  it('should_updateLastSyncAt_when_syncConnectionFails', () => {
    fixture.detectChanges();
    platformServiceSpy.syncConnection.and.returnValue(throwError(() => new Error('fail')));

    component.syncConnection(mockConnections[1]);

    expect(component.syncingId()).toBeNull();
    const updatedConn = component.connections().find(c => c.id === 'pc-2');
    expect(updatedConn?.lastSyncAt).toBeTruthy();
  });

  it('should_callDeleteConnection_when_deleteConnectionCalledAndConfirmed', () => {
    fixture.detectChanges();
    spyOn(window, 'confirm').and.returnValue(true);
    platformServiceSpy.deleteConnection.and.returnValue(of(void 0));
    platformServiceSpy.getConnections.and.returnValue(of(mockConnections));

    component.deleteConnection(mockConnections[0]);

    expect(platformServiceSpy.deleteConnection).toHaveBeenCalledWith('pc-1');
  });

  it('should_notCallDeleteConnection_when_confirmCancelled', () => {
    spyOn(window, 'confirm').and.returnValue(false);

    component.deleteConnection(mockConnections[0]);

    expect(platformServiceSpy.deleteConnection).not.toHaveBeenCalled();
  });

  it('should_returnCorrectStatusClass_when_statusClassCalled', () => {
    expect(component.statusClass(ConnectionStatus.ACTIVE)).toBe('success');
    expect(component.statusClass(ConnectionStatus.ERROR)).toBe('error');
  });

  it('should_returnCorrectPlatformIcon_when_platformIconCalled', () => {
    expect(component.platformIcon(PlatformConnectionType.GITHUB)).toBe('admin.platformConnections.platforms.github');
    expect(component.platformIcon(PlatformConnectionType.GITLAB)).toBe('admin.platformConnections.platforms.gitlab');
    expect(component.platformIcon(PlatformConnectionType.JIRA_CLOUD)).toBe('admin.platformConnections.platforms.jiraCloud');
    expect(component.platformIcon(PlatformConnectionType.JIRA_SERVER)).toBe('admin.platformConnections.platforms.jiraServer');
    expect(component.platformIcon(PlatformConnectionType.AZURE_DEVOPS)).toBe('admin.platformConnections.platforms.azureDevops');
    expect(component.platformIcon(PlatformConnectionType.BITBUCKET)).toBe('admin.platformConnections.platforms.bitbucket');
  });

  it('should_showEmptyState_when_getConnectionsFails', () => {
    platformServiceSpy.getConnections.and.returnValue(throwError(() => new Error('API error')));

    fixture.detectChanges();

    expect(component.connections().length).toBe(0);
    expect(component.loading()).toBeFalse();
  });
});
