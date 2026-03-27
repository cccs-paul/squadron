import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { UserManagementComponent } from './user-management.component';
import { UserService } from '../../../core/services/user.service';
import { User, UserRole, UserStatus } from '../../../core/models/user.model';
import { PageResponse } from '../../../core/services/api.service';

describe('UserManagementComponent', () => {
  let component: UserManagementComponent;
  let fixture: ComponentFixture<UserManagementComponent>;
  let userServiceSpy: jasmine.SpyObj<UserService>;

  const mockUsers: User[] = [
    {
      id: 'u1', tenantId: 't1', username: 'jdoe', email: 'john@example.com',
      displayName: 'John Doe', role: UserRole.ADMIN, teams: ['team-1'],
      status: UserStatus.ACTIVE, lastLoginAt: new Date().toISOString(),
      createdAt: new Date().toISOString(),
    },
    {
      id: 'u2', tenantId: 't1', username: 'jsmith', email: 'jane@example.com',
      displayName: 'Jane Smith', role: UserRole.DEVELOPER, teams: [],
      status: UserStatus.INACTIVE, createdAt: new Date().toISOString(),
    },
  ];

  const mockPage: PageResponse<User> = {
    content: mockUsers,
    totalElements: 2,
    totalPages: 1,
    page: 0,
    size: 50,
  };

  beforeEach(async () => {
    userServiceSpy = jasmine.createSpyObj('UserService', [
      'getUsers', 'createUser', 'updateUser', 'deleteUser',
    ]);
    userServiceSpy.getUsers.and.returnValue(of(mockPage));

    await TestBed.configureTestingModule({
      imports: [UserManagementComponent, FormsModule],
      providers: [
        { provide: UserService, useValue: userServiceSpy },
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(UserManagementComponent);
    component = fixture.componentInstance;
  });

  it('should_create', () => {
    expect(component).toBeTruthy();
  });

  it('should_loadUsers_when_initialized', () => {
    fixture.detectChanges(); // triggers ngOnInit

    expect(userServiceSpy.getUsers).toHaveBeenCalledWith(0, 50, undefined);
    expect(component.users()).toEqual(mockUsers);
    expect(component.loading()).toBeFalse();
  });

  it('should_searchUsers_when_onSearchCalled', () => {
    fixture.detectChanges();
    component.searchQuery = 'john';
    userServiceSpy.getUsers.and.returnValue(of({ ...mockPage, content: [mockUsers[0]] }));

    component.onSearch();

    expect(userServiceSpy.getUsers).toHaveBeenCalledWith(0, 50, 'john');
    expect(component.users().length).toBe(1);
  });

  it('should_openCreateModal_when_openCreateModalCalled', () => {
    component.openCreateModal();

    expect(component.showCreateModal()).toBeTrue();
    expect(component.editingUser()).toBeNull();
    expect(component.formUsername).toBe('');
    expect(component.formEmail).toBe('');
    expect(component.formDisplayName).toBe('');
    expect(component.formRole).toBe(UserRole.DEVELOPER);
  });

  it('should_closeModal_when_closeModalCalled', () => {
    component.openCreateModal();
    component.closeModal();

    expect(component.showCreateModal()).toBeFalse();
    expect(component.editingUser()).toBeNull();
  });

  it('should_populateForm_when_openEditModalCalled', () => {
    const user = mockUsers[0];
    component.openEditModal(user);

    expect(component.showCreateModal()).toBeTrue();
    expect(component.editingUser()).toBe(user);
    expect(component.formUsername).toBe('jdoe');
    expect(component.formEmail).toBe('john@example.com');
    expect(component.formDisplayName).toBe('John Doe');
    expect(component.formRole).toBe(UserRole.ADMIN);
  });

  it('should_callCreateUser_when_saveUserCalledWithoutEditing', () => {
    fixture.detectChanges();
    userServiceSpy.createUser.and.returnValue(of(mockUsers[0]));
    userServiceSpy.getUsers.and.returnValue(of(mockPage));

    component.openCreateModal();
    component.formUsername = 'newuser';
    component.formEmail = 'new@example.com';
    component.formDisplayName = 'New User';
    component.formRole = UserRole.VIEWER;
    component.saveUser();

    expect(userServiceSpy.createUser).toHaveBeenCalledWith({
      username: 'newuser',
      email: 'new@example.com',
      displayName: 'New User',
      role: UserRole.VIEWER,
    });
    expect(component.showCreateModal()).toBeFalse();
  });

  it('should_callUpdateUser_when_saveUserCalledWhileEditing', () => {
    fixture.detectChanges();
    userServiceSpy.updateUser.and.returnValue(of(mockUsers[0]));
    userServiceSpy.getUsers.and.returnValue(of(mockPage));

    component.openEditModal(mockUsers[0]);
    component.formDisplayName = 'John Updated';
    component.saveUser();

    expect(userServiceSpy.updateUser).toHaveBeenCalledWith('u1', {
      username: 'jdoe',
      email: 'john@example.com',
      displayName: 'John Updated',
      role: UserRole.ADMIN,
    });
    expect(component.showCreateModal()).toBeFalse();
  });

  it('should_callDeleteUser_when_deleteUserCalledAndConfirmed', () => {
    fixture.detectChanges();
    spyOn(window, 'confirm').and.returnValue(true);
    userServiceSpy.deleteUser.and.returnValue(of(void 0));
    userServiceSpy.getUsers.and.returnValue(of(mockPage));

    component.deleteUser(mockUsers[0]);

    expect(userServiceSpy.deleteUser).toHaveBeenCalledWith('u1');
  });

  it('should_notCallDeleteUser_when_confirmCancelled', () => {
    spyOn(window, 'confirm').and.returnValue(false);

    component.deleteUser(mockUsers[0]);

    expect(userServiceSpy.deleteUser).not.toHaveBeenCalled();
  });

  it('should_fallbackToMockData_when_getUsersFails', () => {
    userServiceSpy.getUsers.and.returnValue(throwError(() => new Error('API error')));

    fixture.detectChanges();

    expect(component.users().length).toBeGreaterThan(0);
    expect(component.loading()).toBeFalse();
    // Mock data should have user with username 'jdoe'
    expect(component.users().some(u => u.username === 'jdoe')).toBeTrue();
  });

  it('should_returnCorrectStatusClass_when_statusClassCalled', () => {
    expect(component.statusClass(UserStatus.ACTIVE)).toBe('success');
    expect(component.statusClass(UserStatus.INACTIVE)).toBe('neutral');
    expect(component.statusClass(UserStatus.SUSPENDED)).toBe('error');
  });
});
