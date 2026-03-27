import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PermissionManagementComponent } from './permission-management.component';
import { PermissionService } from '../../../core/services/permission.service';
import {
  Permission,
  ResourceType,
  GranteeType,
  AccessLevel,
} from '../../../core/models/security.model';
import { PageResponse } from '../../../core/services/api.service';

describe('PermissionManagementComponent', () => {
  let component: PermissionManagementComponent;
  let fixture: ComponentFixture<PermissionManagementComponent>;
  let permServiceSpy: jasmine.SpyObj<PermissionService>;

  const mockPermissions: Permission[] = [
    {
      id: 'p1', tenantId: 't1', resourceType: ResourceType.PROJECT,
      granteeType: GranteeType.SECURITY_GROUP, granteeId: 'sg-1',
      granteeName: 'Engineering', accessLevel: AccessLevel.WRITE,
      createdAt: new Date().toISOString(),
    },
    {
      id: 'p2', tenantId: 't1', resourceType: ResourceType.ADMIN,
      granteeType: GranteeType.USER, granteeId: 'u1',
      granteeName: 'John Doe', accessLevel: AccessLevel.ADMIN,
      createdAt: new Date().toISOString(),
    },
    {
      id: 'p3', tenantId: 't1', resourceType: ResourceType.TASK,
      granteeType: GranteeType.TEAM, granteeId: 'team-1',
      granteeName: 'Backend Team', accessLevel: AccessLevel.READ,
      createdAt: new Date().toISOString(),
    },
  ];

  const mockPage: PageResponse<Permission> = {
    content: mockPermissions,
    totalElements: 3,
    totalPages: 1,
    page: 0,
    size: 100,
  };

  beforeEach(async () => {
    permServiceSpy = jasmine.createSpyObj('PermissionService', [
      'getPermissions', 'grantPermission', 'revokePermission',
    ]);
    permServiceSpy.getPermissions.and.returnValue(of(mockPage));

    await TestBed.configureTestingModule({
      imports: [PermissionManagementComponent, FormsModule],
      providers: [
        { provide: PermissionService, useValue: permServiceSpy },
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PermissionManagementComponent);
    component = fixture.componentInstance;
  });

  it('should_create', () => {
    expect(component).toBeTruthy();
  });

  it('should_loadPermissions_when_initialized', () => {
    fixture.detectChanges();

    expect(permServiceSpy.getPermissions).toHaveBeenCalledWith(0, 100);
    expect(component.permissions()).toEqual(mockPermissions);
    expect(component.loading()).toBeFalse();
  });

  it('should_filterByResourceType_when_filterResourceSet', () => {
    fixture.detectChanges();

    component.filterResource = ResourceType.PROJECT;
    const filtered = component.filteredPermissions;

    expect(filtered.length).toBe(1);
    expect(filtered[0].resourceType).toBe(ResourceType.PROJECT);
  });

  it('should_filterByGranteeType_when_filterGranteeSet', () => {
    fixture.detectChanges();

    component.filterGrantee = GranteeType.USER;
    const filtered = component.filteredPermissions;

    expect(filtered.length).toBe(1);
    expect(filtered[0].granteeType).toBe(GranteeType.USER);
  });

  it('should_filterByBoth_when_bothFiltersSet', () => {
    fixture.detectChanges();

    component.filterResource = ResourceType.TASK;
    component.filterGrantee = GranteeType.TEAM;
    const filtered = component.filteredPermissions;

    expect(filtered.length).toBe(1);
    expect(filtered[0].id).toBe('p3');
  });

  it('should_openGrantModal_when_openGrantModalCalled', () => {
    component.openGrantModal();

    expect(component.showGrantModal()).toBeTrue();
    expect(component.formResourceType).toBe(ResourceType.PROJECT);
    expect(component.formGranteeType).toBe(GranteeType.USER);
    expect(component.formGranteeName).toBe('');
    expect(component.formAccessLevel).toBe(AccessLevel.READ);
  });

  it('should_closeModal_when_closeModalCalled', () => {
    component.openGrantModal();
    component.closeModal();

    expect(component.showGrantModal()).toBeFalse();
  });

  it('should_callGrantPermission_when_grantPermissionCalled', () => {
    fixture.detectChanges();
    permServiceSpy.grantPermission.and.returnValue(of(mockPermissions[0]));
    permServiceSpy.getPermissions.and.returnValue(of(mockPage));

    component.openGrantModal();
    component.formGranteeName = 'Test User';
    component.formGranteeId = 'u99';
    component.formResourceType = ResourceType.REVIEW;
    component.formAccessLevel = AccessLevel.WRITE;
    component.grantPermission();

    expect(permServiceSpy.grantPermission).toHaveBeenCalledWith(jasmine.objectContaining({
      granteeName: 'Test User',
      granteeId: 'u99',
      resourceType: ResourceType.REVIEW,
      accessLevel: AccessLevel.WRITE,
    }));
    expect(component.showGrantModal()).toBeFalse();
  });

  it('should_callRevokePermission_when_revokePermissionCalledAndConfirmed', () => {
    fixture.detectChanges();
    spyOn(window, 'confirm').and.returnValue(true);
    permServiceSpy.revokePermission.and.returnValue(of(void 0));
    permServiceSpy.getPermissions.and.returnValue(of(mockPage));

    component.revokePermission(mockPermissions[0]);

    expect(permServiceSpy.revokePermission).toHaveBeenCalledWith('p1');
  });

  it('should_notCallRevokePermission_when_confirmCancelled', () => {
    spyOn(window, 'confirm').and.returnValue(false);

    component.revokePermission(mockPermissions[0]);

    expect(permServiceSpy.revokePermission).not.toHaveBeenCalled();
  });

  it('should_returnCorrectAccessLevelClass_when_accessLevelClassCalled', () => {
    expect(component.accessLevelClass(AccessLevel.ADMIN)).toBe('error');
    expect(component.accessLevelClass(AccessLevel.WRITE)).toBe('warning');
    expect(component.accessLevelClass(AccessLevel.READ)).toBe('success');
  });

  it('should_fallbackToMockData_when_getPermissionsFails', () => {
    permServiceSpy.getPermissions.and.returnValue(throwError(() => new Error('API error')));

    fixture.detectChanges();

    expect(component.permissions().length).toBeGreaterThan(0);
    expect(component.loading()).toBeFalse();
    expect(component.permissions().some(p => p.granteeName === 'Engineering')).toBeTrue();
  });
});
