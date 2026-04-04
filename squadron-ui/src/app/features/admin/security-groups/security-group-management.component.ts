import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SecurityGroupService } from '../../../core/services/security-group.service';
import { SecurityGroup, MemberType } from '../../../core/models/security.model';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';

@Component({
  selector: 'sq-security-group-management',
  standalone: true,
  imports: [FormsModule, TimeAgoPipe],
  templateUrl: './security-group-management.component.html',
  styleUrl: './security-group-management.component.scss',
})
export class SecurityGroupManagementComponent implements OnInit {
  private sgService = inject(SecurityGroupService);

  groups = signal<SecurityGroup[]>([]);
  loading = signal(true);
  showCreateModal = signal(false);
  editingGroup = signal<SecurityGroup | null>(null);
  selectedGroup = signal<SecurityGroup | null>(null);

  formName = '';
  formDescription = '';

  ngOnInit(): void {
    this.loadGroups();
  }

  loadGroups(): void {
    this.loading.set(true);
    this.sgService.getGroups(0, 50).subscribe({
      next: (res) => {
        this.groups.set(res.content);
        this.loading.set(false);
      },
      error: () => {
        console.error('Failed to load security groups');
        this.groups.set([]);
        this.loading.set(false);
      },
    });
  }

  selectGroup(group: SecurityGroup): void {
    this.selectedGroup.set(this.selectedGroup()?.id === group.id ? null : group);
  }

  openCreateModal(): void {
    this.editingGroup.set(null);
    this.formName = '';
    this.formDescription = '';
    this.showCreateModal.set(true);
  }

  openEditModal(group: SecurityGroup): void {
    this.editingGroup.set(group);
    this.formName = group.name;
    this.formDescription = group.description || '';
    this.showCreateModal.set(true);
  }

  closeModal(): void {
    this.showCreateModal.set(false);
    this.editingGroup.set(null);
  }

  saveGroup(): void {
    const payload = { name: this.formName, description: this.formDescription };
    const editing = this.editingGroup();

    if (editing) {
      this.sgService.updateGroup(editing.id, payload).subscribe({
        next: () => { this.closeModal(); this.loadGroups(); },
        error: () => {
          this.groups.set(this.groups().map((g) =>
            g.id === editing.id ? { ...g, ...payload } : g,
          ));
          this.closeModal();
        },
      });
    } else {
      this.sgService.createGroup(payload).subscribe({
        next: () => { this.closeModal(); this.loadGroups(); },
        error: () => {
          console.error('Failed to create security group');
          this.closeModal();
        },
      });
    }
  }

  deleteGroup(group: SecurityGroup): void {
    if (!confirm(`Delete security group "${group.name}"?`)) return;
    this.sgService.deleteGroup(group.id).subscribe({
      next: () => this.loadGroups(),
      error: () => {
        this.groups.set(this.groups().filter((g) => g.id !== group.id));
        if (this.selectedGroup()?.id === group.id) {
          this.selectedGroup.set(null);
        }
      },
    });
  }

  memberTypeLabel(type: MemberType): string {
    return type === MemberType.USER ? 'User' : 'Team';
  }

}
