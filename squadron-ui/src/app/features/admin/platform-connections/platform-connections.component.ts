import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PlatformService } from '../../../core/services/platform.service';
import { AuthService } from '../../../core/auth/auth.service';
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
  private authService = inject(AuthService);

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
    const user = this.authService.user();
    if (!user) {
      this.connections.set([]);
      this.loading.set(false);
      return;
    }
    this.platformService.getConnections(user.tenantId).subscribe({
      next: (connections) => {
        this.connections.set(connections);
        this.loading.set(false);
      },
      error: () => {
        console.error('Failed to load platform connections');
        this.connections.set([]);
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
          console.error('Failed to create platform connection');
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
        console.error('Failed to sync connection');
        this.syncingId.set(null);
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
      case ConnectionStatus.ACTIVE: return 'success';
      case ConnectionStatus.ERROR: return 'error';
      default: return 'neutral';
    }
  }

  platformIcon(type: PlatformConnectionType): string {
    switch (type) {
      case PlatformConnectionType.GITHUB: return 'GitHub';
      case PlatformConnectionType.GITLAB: return 'GitLab';
      case PlatformConnectionType.JIRA_CLOUD: return 'Jira Cloud';
      case PlatformConnectionType.JIRA_SERVER: return 'Jira Server / DC';
      case PlatformConnectionType.AZURE_DEVOPS: return 'Azure DevOps';
      case PlatformConnectionType.BITBUCKET: return 'Bitbucket';
      default: return type;
    }
  }

}
