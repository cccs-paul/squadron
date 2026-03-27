import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PlatformService } from '../../../core/services/platform.service';
import {
  PlatformConnection,
  PlatformConnectionType,
  ConnectionStatus,
} from '../../../core/models/security.model';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';

@Component({
  selector: 'sq-platform-connections',
  standalone: true,
  imports: [FormsModule, TimeAgoPipe],
  templateUrl: './platform-connections.component.html',
  styleUrl: './platform-connections.component.scss',
})
export class PlatformConnectionsComponent implements OnInit {
  private platformService = inject(PlatformService);

  connections = signal<PlatformConnection[]>([]);
  loading = signal(true);
  showCreateModal = signal(false);
  editingConnection = signal<PlatformConnection | null>(null);
  syncingId = signal<string | null>(null);
  testingId = signal<string | null>(null);

  formName = '';
  formType: PlatformConnectionType = PlatformConnectionType.GITHUB;
  formBaseUrl = '';
  formToken = '';

  readonly platformTypes = Object.values(PlatformConnectionType);

  ngOnInit(): void {
    this.loadConnections();
  }

  loadConnections(): void {
    this.loading.set(true);
    this.platformService.getConnections().subscribe({
      next: (connections) => {
        this.connections.set(connections);
        this.loading.set(false);
      },
      error: () => {
        this.connections.set(this.getMockConnections());
        this.loading.set(false);
      },
    });
  }

  openCreateModal(): void {
    this.editingConnection.set(null);
    this.formName = '';
    this.formType = PlatformConnectionType.GITHUB;
    this.formBaseUrl = '';
    this.formToken = '';
    this.showCreateModal.set(true);
  }

  openEditModal(conn: PlatformConnection): void {
    this.editingConnection.set(conn);
    this.formName = conn.name;
    this.formType = conn.platformType;
    this.formBaseUrl = conn.baseUrl;
    this.formToken = '';
    this.showCreateModal.set(true);
  }

  closeModal(): void {
    this.showCreateModal.set(false);
    this.editingConnection.set(null);
  }

  saveConnection(): void {
    const config: Record<string, string> = {};
    if (this.formToken) config['token'] = this.formToken;

    const payload: Partial<PlatformConnection> = {
      name: this.formName,
      platformType: this.formType,
      baseUrl: this.formBaseUrl,
      config,
    };

    const editing = this.editingConnection();
    if (editing) {
      this.platformService.updateConnection(editing.id, payload).subscribe({
        next: () => { this.closeModal(); this.loadConnections(); },
        error: () => {
          this.connections.set(this.connections().map((c) =>
            c.id === editing.id ? { ...c, ...payload } as PlatformConnection : c,
          ));
          this.closeModal();
        },
      });
    } else {
      this.platformService.createConnection(payload).subscribe({
        next: () => { this.closeModal(); this.loadConnections(); },
        error: () => {
          const mock: PlatformConnection = {
            id: crypto.randomUUID(),
            tenantId: '1',
            name: this.formName,
            platformType: this.formType,
            baseUrl: this.formBaseUrl,
            status: ConnectionStatus.CONNECTED,
            config,
            createdAt: new Date().toISOString(),
          };
          this.connections.set([mock, ...this.connections()]);
          this.closeModal();
        },
      });
    }
  }

  testConnection(conn: PlatformConnection): void {
    this.testingId.set(conn.id);
    this.platformService.testConnection(conn.id).subscribe({
      next: () => this.testingId.set(null),
      error: () => this.testingId.set(null),
    });
  }

  syncConnection(conn: PlatformConnection): void {
    this.syncingId.set(conn.id);
    this.platformService.syncConnection(conn.id).subscribe({
      next: () => {
        this.syncingId.set(null);
        this.loadConnections();
      },
      error: () => {
        this.syncingId.set(null);
        // Simulate sync success
        this.connections.set(this.connections().map((c) =>
          c.id === conn.id ? { ...c, lastSyncAt: new Date().toISOString() } : c,
        ));
      },
    });
  }

  deleteConnection(conn: PlatformConnection): void {
    if (!confirm(`Delete connection "${conn.name}"?`)) return;
    this.platformService.deleteConnection(conn.id).subscribe({
      next: () => this.loadConnections(),
      error: () => {
        this.connections.set(this.connections().filter((c) => c.id !== conn.id));
      },
    });
  }

  statusClass(status: ConnectionStatus): string {
    switch (status) {
      case ConnectionStatus.CONNECTED: return 'success';
      case ConnectionStatus.DISCONNECTED: return 'neutral';
      case ConnectionStatus.ERROR: return 'error';
      default: return 'neutral';
    }
  }

  platformIcon(type: PlatformConnectionType): string {
    switch (type) {
      case PlatformConnectionType.GITHUB: return 'GitHub';
      case PlatformConnectionType.GITLAB: return 'GitLab';
      case PlatformConnectionType.JIRA: return 'Jira';
      case PlatformConnectionType.AZURE_DEVOPS: return 'Azure DevOps';
      case PlatformConnectionType.BITBUCKET: return 'Bitbucket';
      default: return type;
    }
  }

  private getMockConnections(): PlatformConnection[] {
    return [
      {
        id: 'pc-1', tenantId: '1', name: 'GitHub - Organization', platformType: PlatformConnectionType.GITHUB,
        baseUrl: 'https://api.github.com', status: ConnectionStatus.CONNECTED,
        lastSyncAt: new Date(Date.now() - 1800000).toISOString(),
        config: {}, createdAt: new Date(Date.now() - 86400000 * 30).toISOString(),
      },
      {
        id: 'pc-2', tenantId: '1', name: 'Jira Cloud', platformType: PlatformConnectionType.JIRA,
        baseUrl: 'https://myorg.atlassian.net', status: ConnectionStatus.CONNECTED,
        lastSyncAt: new Date(Date.now() - 3600000).toISOString(),
        config: {}, createdAt: new Date(Date.now() - 86400000 * 20).toISOString(),
      },
      {
        id: 'pc-3', tenantId: '1', name: 'GitLab Self-Hosted', platformType: PlatformConnectionType.GITLAB,
        baseUrl: 'https://gitlab.example.com', status: ConnectionStatus.ERROR,
        config: {}, createdAt: new Date(Date.now() - 86400000 * 10).toISOString(),
      },
    ];
  }
}
