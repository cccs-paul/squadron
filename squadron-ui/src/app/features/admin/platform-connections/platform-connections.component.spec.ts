import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PlatformConnectionsComponent } from './platform-connections.component';
import { PlatformService } from '../../../core/services/platform.service';
import {
  PlatformConnection,
  PlatformConnectionType,
  ConnectionStatus,
} from '../../../core/models/security.model';

describe('PlatformConnectionsComponent', () => {
  let component: PlatformConnectionsComponent;
  let fixture: ComponentFixture<PlatformConnectionsComponent>;
  let platformServiceSpy: jasmine.SpyObj<PlatformService>;

  const mockConnections: PlatformConnection[] = [
    {
      id: 'pc-1', tenantId: 't1', name: 'GitHub Org',
      platformType: PlatformConnectionType.GITHUB,
      baseUrl: 'https://api.github.com', status: ConnectionStatus.CONNECTED,
      lastSyncAt: new Date().toISOString(),
      config: {}, createdAt: new Date().toISOString(),
    },
    {
      id: 'pc-2', tenantId: 't1', name: 'Jira Cloud',
      platformType: PlatformConnectionType.JIRA,
      baseUrl: 'https://myorg.atlassian.net', status: ConnectionStatus.CONNECTED,
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

    await TestBed.configureTestingModule({
      imports: [PlatformConnectionsComponent, FormsModule],
      providers: [
        { provide: PlatformService, useValue: platformServiceSpy },
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

    expect(platformServiceSpy.getConnections).toHaveBeenCalled();
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
    platformServiceSpy.testConnection.and.returnValue(of({ success: true, message: 'OK' }));

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
    expect(component.statusClass(ConnectionStatus.CONNECTED)).toBe('success');
    expect(component.statusClass(ConnectionStatus.DISCONNECTED)).toBe('neutral');
    expect(component.statusClass(ConnectionStatus.ERROR)).toBe('error');
  });

  it('should_returnCorrectPlatformIcon_when_platformIconCalled', () => {
    expect(component.platformIcon(PlatformConnectionType.GITHUB)).toBe('GitHub');
    expect(component.platformIcon(PlatformConnectionType.GITLAB)).toBe('GitLab');
    expect(component.platformIcon(PlatformConnectionType.JIRA)).toBe('Jira');
    expect(component.platformIcon(PlatformConnectionType.AZURE_DEVOPS)).toBe('Azure DevOps');
    expect(component.platformIcon(PlatformConnectionType.BITBUCKET)).toBe('Bitbucket');
  });

  it('should_fallbackToMockData_when_getConnectionsFails', () => {
    platformServiceSpy.getConnections.and.returnValue(throwError(() => new Error('API error')));

    fixture.detectChanges();

    expect(component.connections().length).toBeGreaterThan(0);
    expect(component.loading()).toBeFalse();
    expect(component.connections().some(c => c.platformType === PlatformConnectionType.GITHUB)).toBeTrue();
  });
});
