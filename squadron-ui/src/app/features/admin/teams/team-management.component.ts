import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { UserService } from '../../../core/services/user.service';
import { Team, User, UserRole, UserStatus } from '../../../core/models/user.model';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';

@Component({
  selector: 'sq-team-management',
  standalone: true,
  imports: [FormsModule, AvatarComponent],
  templateUrl: './team-management.component.html',
  styleUrl: './team-management.component.scss',
})
export class TeamManagementComponent implements OnInit {
  private userService = inject(UserService);

  teams = signal<Team[]>([]);
  loading = signal(true);
  showCreateModal = signal(false);
  editingTeam = signal<Team | null>(null);
  expandedTeamId = signal<string | null>(null);

  formName = '';
  formDescription = '';

  ngOnInit(): void {
    this.loadTeams();
  }

  loadTeams(): void {
    this.loading.set(true);
    this.userService.getTeams(0, 50).subscribe({
      next: (res) => {
        this.teams.set(res.content);
        this.loading.set(false);
      },
      error: () => {
        this.teams.set(this.getMockTeams());
        this.loading.set(false);
      },
    });
  }

  toggleExpand(teamId: string): void {
    this.expandedTeamId.set(this.expandedTeamId() === teamId ? null : teamId);
  }

  openCreateModal(): void {
    this.editingTeam.set(null);
    this.formName = '';
    this.formDescription = '';
    this.showCreateModal.set(true);
  }

  openEditModal(team: Team): void {
    this.editingTeam.set(team);
    this.formName = team.name;
    this.formDescription = team.description || '';
    this.showCreateModal.set(true);
  }

  closeModal(): void {
    this.showCreateModal.set(false);
    this.editingTeam.set(null);
  }

  saveTeam(): void {
    const payload = { name: this.formName, description: this.formDescription };
    const editing = this.editingTeam();

    if (editing) {
      this.userService.updateTeam(editing.id, payload).subscribe({
        next: () => { this.closeModal(); this.loadTeams(); },
        error: () => {
          const updated = this.teams().map((t) =>
            t.id === editing.id ? { ...t, ...payload } : t,
          );
          this.teams.set(updated);
          this.closeModal();
        },
      });
    } else {
      this.userService.createTeam(payload).subscribe({
        next: () => { this.closeModal(); this.loadTeams(); },
        error: () => {
          const mockTeam: Team = {
            id: crypto.randomUUID(),
            tenantId: '1',
            name: this.formName,
            description: this.formDescription,
            memberCount: 0,
            members: [],
            createdAt: new Date().toISOString(),
          };
          this.teams.set([mockTeam, ...this.teams()]);
          this.closeModal();
        },
      });
    }
  }

  deleteTeam(team: Team): void {
    if (!confirm(`Delete team "${team.name}"?`)) return;
    this.userService.deleteTeam(team.id).subscribe({
      next: () => this.loadTeams(),
      error: () => {
        this.teams.set(this.teams().filter((t) => t.id !== team.id));
      },
    });
  }

  private getMockTeams(): Team[] {
    const mockUsers: User[] = [
      { id: '1', tenantId: '1', username: 'jdoe', email: 'john@example.com', displayName: 'John Doe', role: UserRole.ADMIN, teams: [], status: UserStatus.ACTIVE, createdAt: new Date().toISOString() },
      { id: '2', tenantId: '1', username: 'jsmith', email: 'jane@example.com', displayName: 'Jane Smith', role: UserRole.DEVELOPER, teams: [], status: UserStatus.ACTIVE, createdAt: new Date().toISOString() },
      { id: '3', tenantId: '1', username: 'bwilson', email: 'bob@example.com', displayName: 'Bob Wilson', role: UserRole.DEVELOPER, teams: [], status: UserStatus.ACTIVE, createdAt: new Date().toISOString() },
    ];
    return [
      { id: 'team-1', tenantId: '1', name: 'Backend Team', description: 'Handles API and microservices development', memberCount: 5, members: mockUsers, createdAt: new Date(Date.now() - 86400000 * 60).toISOString() },
      { id: 'team-2', tenantId: '1', name: 'Frontend Team', description: 'UI/UX and Angular development', memberCount: 4, members: [mockUsers[1]], createdAt: new Date(Date.now() - 86400000 * 45).toISOString() },
      { id: 'team-3', tenantId: '1', name: 'DevOps', description: 'Infrastructure, CI/CD, and Kubernetes', memberCount: 2, members: [mockUsers[2]], createdAt: new Date(Date.now() - 86400000 * 30).toISOString() },
    ];
  }
}
