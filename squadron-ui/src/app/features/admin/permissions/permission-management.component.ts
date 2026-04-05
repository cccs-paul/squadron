import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
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
  imports: [FormsModule, TimeAgoPipe, TranslateModule],
  templateUrl: './permission-management.component.html',
  styleUrl: './permission-management.component.scss',
})
export class PermissionManagementComponent implements OnInit {
  private permissionService = inject(PermissionService);
  private translate = inject(TranslateService);

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
        console.error('Failed to load permissions');
        this.permissions.set([]);
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
        console.error('Failed to grant permission');
        this.closeModal();
      },
    });
  }

  revokePermission(perm: Permission): void {
    if (!confirm(this.translate.instant('admin.permissions.confirmRevoke', { accessLevel: perm.accessLevel, name: perm.granteeName, resourceType: perm.resourceType }))) return;
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

}
