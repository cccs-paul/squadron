import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../../core/auth/auth.service';
import { UserTokenService } from '../../../core/services/user-token.service';
import { UserPlatformToken, ConnectionInfo, PatLinkRequest } from '../../../core/models/user-token.model';

@Component({
  selector: 'sq-user-tokens',
  standalone: true,
  imports: [FormsModule, TranslateModule],
  templateUrl: './user-tokens.component.html',
  styleUrl: './user-tokens.component.scss',
})
export class UserTokensComponent implements OnInit {
  private authService = inject(AuthService);
  private userTokenService = inject(UserTokenService);

  tokens = signal<UserPlatformToken[]>([]);
  connections = signal<ConnectionInfo[]>([]);
  loading = signal(true);
  linking = signal(false);
  linkError = signal<string | null>(null);
  linkSuccess = signal(false);

  // Link PAT form
  showLinkForm = signal(false);
  selectedConnectionId = '';
  patValue = '';

  get userId(): string {
    return this.authService.user()?.id ?? '';
  }

  get tenantId(): string {
    return this.authService.user()?.tenantId ?? '';
  }

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    this.userTokenService.getTokensByUser(this.userId).subscribe({
      next: (tokens) => {
        this.tokens.set(tokens);
        this.loadConnections();
      },
      error: () => {
        this.tokens.set([]);
        this.loadConnections();
      },
    });
  }

  private loadConnections(): void {
    this.userTokenService.getAvailableConnections(this.tenantId).subscribe({
      next: (connections) => {
        this.connections.set(connections);
        this.loading.set(false);
      },
      error: () => {
        this.connections.set([]);
        this.loading.set(false);
      },
    });
  }

  /** Get the connection info for a token, by its connectionId */
  getConnectionName(connectionId: string): string {
    const conn = this.connections().find(c => c.id === connectionId);
    return conn ? conn.name : 'Unknown connection';
  }

  getConnectionPlatform(connectionId: string): string {
    const conn = this.connections().find(c => c.id === connectionId);
    return conn ? conn.platformType : '';
  }

  /** Connections that don't already have a linked token */
  getUnlinkedConnections(): ConnectionInfo[] {
    const linkedIds = new Set(this.tokens().map(t => t.connectionId));
    return this.connections().filter(c => !linkedIds.has(c.id));
  }

  toggleLinkForm(): void {
    this.showLinkForm.set(!this.showLinkForm());
    this.selectedConnectionId = '';
    this.patValue = '';
    this.linkError.set(null);
  }

  linkPat(): void {
    if (!this.selectedConnectionId || !this.patValue.trim()) {
      this.linkError.set('Please select a connection and enter a token.');
      return;
    }

    this.linking.set(true);
    this.linkError.set(null);
    this.linkSuccess.set(false);

    const request: PatLinkRequest = {
      userId: this.userId,
      connectionId: this.selectedConnectionId,
      accessToken: this.patValue.trim(),
    };

    this.userTokenService.linkPatAccount(request).subscribe({
      next: (token) => {
        this.tokens.set([...this.tokens(), token]);
        this.linking.set(false);
        this.linkSuccess.set(true);
        this.showLinkForm.set(false);
        this.selectedConnectionId = '';
        this.patValue = '';
        setTimeout(() => this.linkSuccess.set(false), 3000);
      },
      error: () => {
        this.linking.set(false);
        this.linkError.set('Failed to link token. Please check your token and try again.');
      },
    });
  }

  unlinkToken(token: UserPlatformToken): void {
    this.userTokenService.unlinkAccount(token.userId, token.connectionId).subscribe({
      next: () => {
        this.tokens.set(this.tokens().filter(t => t.id !== token.id));
      },
      error: () => {
        // Silent fail — could add error toast
      },
    });
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  }
}
