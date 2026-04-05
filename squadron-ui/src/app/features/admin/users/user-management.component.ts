import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { UserService } from '../../../core/services/user.service';
import { User, UserRole, UserStatus } from '../../../core/models/user.model';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';

@Component({
  selector: 'sq-user-management',
  standalone: true,
  imports: [FormsModule, TimeAgoPipe, AvatarComponent, TranslateModule],
  templateUrl: './user-management.component.html',
  styleUrl: './user-management.component.scss',
})
export class UserManagementComponent implements OnInit {
  private userService = inject(UserService);
  private translate = inject(TranslateService);

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
      error: (err) => {
        console.error('Failed to load users', err);
        this.users.set([]);
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
        error: (err) => {
          console.error('Failed to update user', err);
          this.closeModal();
        },
      });
    } else {
      this.userService.createUser(payload).subscribe({
        next: () => {
          this.closeModal();
          this.loadUsers();
        },
        error: (err) => {
          console.error('Failed to create user', err);
          this.closeModal();
        },
      });
    }
  }

  deleteUser(user: User): void {
    if (!confirm(this.translate.instant('admin.users.confirmDelete', { name: user.displayName }))) return;
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

}
