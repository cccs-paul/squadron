import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PermissionService } from '../../../core/services/permission.service';
import {
  Permission,
  ResourceType,
  GranteeType,
  AccessLevel,
} from '../../../core/models/security.model';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';

@Component({
  selector: 'sq-permission-management',
  standalone: true,
  imports: [FormsModule, TimeAgoPipe],
  templateUrl: './permission-management.component.html',
  styleUrl: './permission-management.component.scss',
})
export class PermissionManagementComponent implements OnInit {
  private permissionService = inject(PermissionService);

  permissions = signal<Permission[]>([]);
  loading = signal(true);
  showGrantModal = signal(false);
  filterResource = '';
  filterGrantee = '';

  formResourceType: ResourceType = ResourceType.PROJECT;
  formGranteeType: GranteeType = GranteeType.USER;
  formGranteeName = '';
  formGranteeId = '';
  formAccessLevel: AccessLevel = AccessLevel.READ;

  readonly resourceTypes = Object.values(ResourceType);
  readonly granteeTypes = Object.values(GranteeType);
  readonly accessLevels = Object.values(AccessLevel);

  ngOnInit(): void {
    this.loadPermissions();
  }

  loadPermissions(): void {
    this.loading.set(true);
    this.permissionService.getPermissions(0, 100).subscribe({
      next: (res) => {
        this.permissions.set(res.content);
        this.loading.set(false);
      },
      error: () => {
        this.permissions.set(this.getMockPermissions());
        this.loading.set(false);
      },
    });
  }

  get filteredPermissions(): Permission[] {
    let result = this.permissions();
    if (this.filterResource) {
      result = result.filter((p) => p.resourceType === this.filterResource);
    }
    if (this.filterGrantee) {
      result = result.filter((p) => p.granteeType === this.filterGrantee);
    }
    return result;
  }

  openGrantModal(): void {
    this.formResourceType = ResourceType.PROJECT;
    this.formGranteeType = GranteeType.USER;
    this.formGranteeName = '';
    this.formGranteeId = '';
    this.formAccessLevel = AccessLevel.READ;
    this.showGrantModal.set(true);
  }

  closeModal(): void {
    this.showGrantModal.set(false);
  }

  grantPermission(): void {
    const payload: Partial<Permission> = {
      resourceType: this.formResourceType,
      granteeType: this.formGranteeType,
      granteeId: this.formGranteeId || crypto.randomUUID(),
      granteeName: this.formGranteeName,
      accessLevel: this.formAccessLevel,
    };

    this.permissionService.grantPermission(payload).subscribe({
      next: () => {
        this.closeModal();
        this.loadPermissions();
      },
      error: () => {
        const mock: Permission = {
          id: crypto.randomUUID(),
          tenantId: '1',
          resourceType: this.formResourceType,
          granteeType: this.formGranteeType,
          granteeId: this.formGranteeId || crypto.randomUUID(),
          granteeName: this.formGranteeName,
          accessLevel: this.formAccessLevel,
          createdAt: new Date().toISOString(),
        };
        this.permissions.set([mock, ...this.permissions()]);
        this.closeModal();
      },
    });
  }

  revokePermission(perm: Permission): void {
    if (!confirm(`Revoke ${perm.accessLevel} access for "${perm.granteeName}" on ${perm.resourceType}?`)) return;
    this.permissionService.revokePermission(perm.id).subscribe({
      next: () => this.loadPermissions(),
      error: () => {
        this.permissions.set(this.permissions().filter((p) => p.id !== perm.id));
      },
    });
  }

  accessLevelClass(level: AccessLevel): string {
    switch (level) {
      case AccessLevel.ADMIN: return 'error';
      case AccessLevel.WRITE: return 'warning';
      case AccessLevel.READ: return 'success';
      default: return 'neutral';
    }
  }

  private getMockPermissions(): Permission[] {
    return [
      { id: 'p1', tenantId: '1', resourceType: ResourceType.PROJECT, granteeType: GranteeType.SECURITY_GROUP, granteeId: 'sg-1', granteeName: 'Engineering', accessLevel: AccessLevel.WRITE, createdAt: new Date(Date.now() - 86400000 * 5).toISOString() },
      { id: 'p2', tenantId: '1', resourceType: ResourceType.ADMIN, granteeType: GranteeType.SECURITY_GROUP, granteeId: 'sg-3', granteeName: 'Administrators', accessLevel: AccessLevel.ADMIN, createdAt: new Date(Date.now() - 86400000 * 30).toISOString() },
      { id: 'p3', tenantId: '1', resourceType: ResourceType.TASK, granteeType: GranteeType.TEAM, granteeId: 'team-1', granteeName: 'Backend Team', accessLevel: AccessLevel.WRITE, createdAt: new Date(Date.now() - 86400000 * 10).toISOString() },
      { id: 'p4', tenantId: '1', resourceType: ResourceType.REVIEW, granteeType: GranteeType.USER, granteeId: '2', granteeName: 'Jane Smith', accessLevel: AccessLevel.READ, createdAt: new Date(Date.now() - 86400000 * 3).toISOString() },
      { id: 'p5', tenantId: '1', resourceType: ResourceType.SETTINGS, granteeType: GranteeType.SECURITY_GROUP, granteeId: 'sg-2', granteeName: 'Project Managers', accessLevel: AccessLevel.READ, createdAt: new Date(Date.now() - 86400000 * 7).toISOString() },
    ];
  }
}
