import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { UserService } from '../../../core/services/user.service';
import { Team } from '../../../core/models/user.model';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';

@Component({
  selector: 'sq-team-management',
  standalone: true,
  imports: [FormsModule, AvatarComponent, TranslateModule],
  templateUrl: './team-management.component.html',
  styleUrl: './team-management.component.scss',
})
export class TeamManagementComponent implements OnInit {
  private userService = inject(UserService);
  private translate = inject(TranslateService);

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
      error: (err) => {
        console.error('Failed to load teams', err);
        this.teams.set([]);
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
        error: (err) => {
          console.error('Failed to create team', err);
          this.closeModal();
        },
      });
    }
  }

  deleteTeam(team: Team): void {
    if (!confirm(this.translate.instant('admin.teams.confirmDelete', { name: team.name }))) return;
    this.userService.deleteTeam(team.id).subscribe({
      next: () => this.loadTeams(),
      error: () => {
        this.teams.set(this.teams().filter((t) => t.id !== team.id));
      },
    });
  }

}
