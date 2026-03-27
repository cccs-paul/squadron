import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { UserService } from '../../../core/services/user.service';
import { User, UserRole, UserStatus } from '../../../core/models/user.model';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';

@Component({
  selector: 'sq-user-management',
  standalone: true,
  imports: [FormsModule, TimeAgoPipe, AvatarComponent],
  templateUrl: './user-management.component.html',
  styleUrl: './user-management.component.scss',
})
export class UserManagementComponent implements OnInit {
  private userService = inject(UserService);

  users = signal<User[]>([]);
  loading = signal(true);
  searchQuery = '';
  showCreateModal = signal(false);
  editingUser = signal<User | null>(null);

  // Create/edit form fields
  formUsername = '';
  formEmail = '';
  formDisplayName = '';
  formRole: UserRole = UserRole.DEVELOPER;

  readonly roles = Object.values(UserRole);
  readonly statuses = Object.values(UserStatus);

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading.set(true);
    this.userService.getUsers(0, 50, this.searchQuery || undefined).subscribe({
      next: (res) => {
        this.users.set(res.content);
        this.loading.set(false);
      },
      error: () => {
        this.users.set(this.getMockUsers());
        this.loading.set(false);
      },
    });
  }

  onSearch(): void {
    this.loadUsers();
  }

  openCreateModal(): void {
    this.editingUser.set(null);
    this.formUsername = '';
    this.formEmail = '';
    this.formDisplayName = '';
    this.formRole = UserRole.DEVELOPER;
    this.showCreateModal.set(true);
  }

  openEditModal(user: User): void {
    this.editingUser.set(user);
    this.formUsername = user.username;
    this.formEmail = user.email;
    this.formDisplayName = user.displayName;
    this.formRole = user.role;
    this.showCreateModal.set(true);
  }

  closeModal(): void {
    this.showCreateModal.set(false);
    this.editingUser.set(null);
  }

  saveUser(): void {
    const payload = {
      username: this.formUsername,
      email: this.formEmail,
      displayName: this.formDisplayName,
      role: this.formRole,
    };

    const editing = this.editingUser();
    if (editing) {
      this.userService.updateUser(editing.id, payload).subscribe({
        next: () => {
          this.closeModal();
          this.loadUsers();
        },
        error: () => {
          // Optimistic update for demo
          const updated = this.users().map((u) =>
            u.id === editing.id ? { ...u, ...payload } : u,
          );
          this.users.set(updated);
          this.closeModal();
        },
      });
    } else {
      this.userService.createUser(payload).subscribe({
        next: () => {
          this.closeModal();
          this.loadUsers();
        },
        error: () => {
          // Add mock user for demo
          const mockUser: User = {
            id: crypto.randomUUID(),
            tenantId: '1',
            username: this.formUsername,
            email: this.formEmail,
            displayName: this.formDisplayName,
            role: this.formRole,
            teams: [],
            status: UserStatus.ACTIVE,
            createdAt: new Date().toISOString(),
          };
          this.users.set([mockUser, ...this.users()]);
          this.closeModal();
        },
      });
    }
  }

  deleteUser(user: User): void {
    if (!confirm(`Delete user "${user.displayName}"?`)) return;
    this.userService.deleteUser(user.id).subscribe({
      next: () => this.loadUsers(),
      error: () => {
        this.users.set(this.users().filter((u) => u.id !== user.id));
      },
    });
  }

  statusClass(status: UserStatus): string {
    switch (status) {
      case UserStatus.ACTIVE:
        return 'success';
      case UserStatus.INACTIVE:
        return 'neutral';
      case UserStatus.SUSPENDED:
        return 'error';
      default:
        return 'neutral';
    }
  }

  private getMockUsers(): User[] {
    return [
      { id: '1', tenantId: '1', username: 'jdoe', email: 'john.doe@example.com', displayName: 'John Doe', role: UserRole.ADMIN, teams: ['team-1'], status: UserStatus.ACTIVE, lastLoginAt: new Date(Date.now() - 3600000).toISOString(), createdAt: new Date(Date.now() - 86400000 * 30).toISOString() },
      { id: '2', tenantId: '1', username: 'jsmith', email: 'jane.smith@example.com', displayName: 'Jane Smith', role: UserRole.DEVELOPER, teams: ['team-1', 'team-2'], status: UserStatus.ACTIVE, lastLoginAt: new Date(Date.now() - 7200000).toISOString(), createdAt: new Date(Date.now() - 86400000 * 25).toISOString() },
      { id: '3', tenantId: '1', username: 'bwilson', email: 'bob.wilson@example.com', displayName: 'Bob Wilson', role: UserRole.MANAGER, teams: ['team-2'], status: UserStatus.ACTIVE, lastLoginAt: new Date(Date.now() - 86400000).toISOString(), createdAt: new Date(Date.now() - 86400000 * 20).toISOString() },
      { id: '4', tenantId: '1', username: 'alee', email: 'alice.lee@example.com', displayName: 'Alice Lee', role: UserRole.DEVELOPER, teams: ['team-1'], status: UserStatus.INACTIVE, createdAt: new Date(Date.now() - 86400000 * 15).toISOString() },
      { id: '5', tenantId: '1', username: 'mchen', email: 'mike.chen@example.com', displayName: 'Mike Chen', role: UserRole.VIEWER, teams: [], status: UserStatus.SUSPENDED, createdAt: new Date(Date.now() - 86400000 * 10).toISOString() },
    ];
  }
}
