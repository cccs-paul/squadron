import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';
import { SecurityGroupManagementComponent } from './security-group-management.component';
import { SecurityGroupService } from '../../../core/services/security-group.service';
import { SecurityGroup, MemberType } from '../../../core/models/security.model';
import { PageResponse } from '../../../core/services/api.service';

describe('SecurityGroupManagementComponent', () => {
  let component: SecurityGroupManagementComponent;
  let fixture: ComponentFixture<SecurityGroupManagementComponent>;
  let sgServiceSpy: jasmine.SpyObj<SecurityGroupService>;

  const mockGroups: SecurityGroup[] = [
    {
      id: 'sg-1', tenantId: 't1', name: 'Engineering',
      description: 'All engineering staff',
      members: [
        { id: 'm1', memberType: MemberType.TEAM, memberId: 'team-1', memberName: 'Backend Team', addedAt: new Date().toISOString() },
        { id: 'm2', memberType: MemberType.USER, memberId: 'u1', memberName: 'John Doe', addedAt: new Date().toISOString() },
      ],
      permissions: [],
      createdAt: new Date().toISOString(),
    },
    {
      id: 'sg-2', tenantId: 't1', name: 'Administrators',
      description: 'Full system access',
      members: [
        { id: 'm3', memberType: MemberType.USER, memberId: 'u1', memberName: 'John Doe', addedAt: new Date().toISOString() },
      ],
      permissions: [],
      createdAt: new Date().toISOString(),
    },
  ];

  const mockPage: PageResponse<SecurityGroup> = {
    content: mockGroups,
    totalElements: 2,
    totalPages: 1,
    page: 0,
    size: 50,
  };

  beforeEach(async () => {
    sgServiceSpy = jasmine.createSpyObj('SecurityGroupService', [
      'getGroups', 'createGroup', 'updateGroup', 'deleteGroup',
    ]);
    sgServiceSpy.getGroups.and.returnValue(of(mockPage));

    await TestBed.configureTestingModule({
      imports: [SecurityGroupManagementComponent, FormsModule, TranslateModule.forRoot()],
      providers: [
        { provide: SecurityGroupService, useValue: sgServiceSpy },
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SecurityGroupManagementComponent);
    component = fixture.componentInstance;
  });

  it('should_create', () => {
    expect(component).toBeTruthy();
  });

  it('should_loadGroups_when_initialized', () => {
    fixture.detectChanges();

    expect(sgServiceSpy.getGroups).toHaveBeenCalledWith(0, 50);
    expect(component.groups()).toEqual(mockGroups);
    expect(component.loading()).toBeFalse();
  });

  it('should_selectGroup_when_selectGroupCalled', () => {
    fixture.detectChanges();

    component.selectGroup(mockGroups[0]);
    expect(component.selectedGroup()).toBe(mockGroups[0]);

    // Selecting same group deselects it
    component.selectGroup(mockGroups[0]);
    expect(component.selectedGroup()).toBeNull();

    // Select a different group
    component.selectGroup(mockGroups[0]);
    component.selectGroup(mockGroups[1]);
    expect(component.selectedGroup()).toBe(mockGroups[1]);
  });

  it('should_openCreateModal_when_openCreateModalCalled', () => {
    component.openCreateModal();

    expect(component.showCreateModal()).toBeTrue();
    expect(component.editingGroup()).toBeNull();
    expect(component.formName).toBe('');
    expect(component.formDescription).toBe('');
  });

  it('should_closeModal_when_closeModalCalled', () => {
    component.openCreateModal();
    component.closeModal();

    expect(component.showCreateModal()).toBeFalse();
    expect(component.editingGroup()).toBeNull();
  });

  it('should_populateForm_when_openEditModalCalled', () => {
    const group = mockGroups[0];
    component.openEditModal(group);

    expect(component.showCreateModal()).toBeTrue();
    expect(component.editingGroup()).toBe(group);
    expect(component.formName).toBe('Engineering');
    expect(component.formDescription).toBe('All engineering staff');
  });

  it('should_callCreateGroup_when_saveGroupCalledWithoutEditing', () => {
    fixture.detectChanges();
    sgServiceSpy.createGroup.and.returnValue(of(mockGroups[0]));
    sgServiceSpy.getGroups.and.returnValue(of(mockPage));

    component.openCreateModal();
    component.formName = 'New Group';
    component.formDescription = 'A new group';
    component.saveGroup();

    expect(sgServiceSpy.createGroup).toHaveBeenCalledWith({
      name: 'New Group',
      description: 'A new group',
    });
    expect(component.showCreateModal()).toBeFalse();
  });

  it('should_callDeleteGroup_when_deleteGroupCalledAndConfirmed', () => {
    fixture.detectChanges();
    spyOn(window, 'confirm').and.returnValue(true);
    sgServiceSpy.deleteGroup.and.returnValue(of(void 0));
    sgServiceSpy.getGroups.and.returnValue(of(mockPage));

    component.deleteGroup(mockGroups[0]);

    expect(sgServiceSpy.deleteGroup).toHaveBeenCalledWith('sg-1');
  });

  it('should_returnCorrectMemberTypeLabel_when_memberTypeLabelCalled', () => {
    expect(component.memberTypeLabel(MemberType.USER)).toBe('admin.securityGroups.memberType.user');
    expect(component.memberTypeLabel(MemberType.TEAM)).toBe('admin.securityGroups.memberType.team');
  });

  it('should_showEmptyState_when_getGroupsFails', () => {
    sgServiceSpy.getGroups.and.returnValue(throwError(() => new Error('API error')));

    fixture.detectChanges();

    expect(component.groups().length).toBe(0);
    expect(component.loading()).toBeFalse();
  });
});
