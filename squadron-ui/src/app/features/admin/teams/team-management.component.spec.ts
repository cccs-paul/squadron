import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { TeamManagementComponent } from './team-management.component';
import { UserService } from '../../../core/services/user.service';
import { Team, User, UserRole, UserStatus } from '../../../core/models/user.model';
import { PageResponse } from '../../../core/services/api.service';

describe('TeamManagementComponent', () => {
  let component: TeamManagementComponent;
  let fixture: ComponentFixture<TeamManagementComponent>;
  let userServiceSpy: jasmine.SpyObj<UserService>;

  const mockMembers: User[] = [
    {
      id: 'u1', tenantId: 't1', username: 'jdoe', email: 'john@example.com',
      displayName: 'John Doe', role: UserRole.ADMIN, teams: [],
      status: UserStatus.ACTIVE, createdAt: new Date().toISOString(),
    },
  ];

  const mockTeams: Team[] = [
    {
      id: 'team-1', tenantId: 't1', name: 'Backend Team',
      description: 'API development', memberCount: 3, members: mockMembers,
      createdAt: new Date().toISOString(),
    },
    {
      id: 'team-2', tenantId: 't1', name: 'Frontend Team',
      description: 'UI development', memberCount: 2, members: [],
      createdAt: new Date().toISOString(),
    },
  ];

  const mockPage: PageResponse<Team> = {
    content: mockTeams,
    totalElements: 2,
    totalPages: 1,
    page: 0,
    size: 50,
  };

  beforeEach(async () => {
    userServiceSpy = jasmine.createSpyObj('UserService', [
      'getTeams', 'createTeam', 'updateTeam', 'deleteTeam',
    ]);
    userServiceSpy.getTeams.and.returnValue(of(mockPage));

    await TestBed.configureTestingModule({
      imports: [TeamManagementComponent, FormsModule, TranslateModule.forRoot()],
      providers: [
        { provide: UserService, useValue: userServiceSpy },
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TeamManagementComponent);
    component = fixture.componentInstance;
  });

  it('should_create', () => {
    expect(component).toBeTruthy();
  });

  it('should_loadTeams_when_initialized', () => {
    fixture.detectChanges();

    expect(userServiceSpy.getTeams).toHaveBeenCalledWith(0, 50);
    expect(component.teams()).toEqual(mockTeams);
    expect(component.loading()).toBeFalse();
  });

  it('should_toggleExpand_when_toggleExpandCalled', () => {
    fixture.detectChanges();

    component.toggleExpand('team-1');
    expect(component.expandedTeamId()).toBe('team-1');

    // Toggle same team collapses it
    component.toggleExpand('team-1');
    expect(component.expandedTeamId()).toBeNull();

    // Expand one, then another replaces it
    component.toggleExpand('team-1');
    component.toggleExpand('team-2');
    expect(component.expandedTeamId()).toBe('team-2');
  });

  it('should_openCreateModal_when_openCreateModalCalled', () => {
    component.openCreateModal();

    expect(component.showCreateModal()).toBeTrue();
    expect(component.editingTeam()).toBeNull();
    expect(component.formName).toBe('');
    expect(component.formDescription).toBe('');
  });

  it('should_closeModal_when_closeModalCalled', () => {
    component.openCreateModal();
    component.closeModal();

    expect(component.showCreateModal()).toBeFalse();
    expect(component.editingTeam()).toBeNull();
  });

  it('should_populateForm_when_openEditModalCalled', () => {
    const team = mockTeams[0];
    component.openEditModal(team);

    expect(component.showCreateModal()).toBeTrue();
    expect(component.editingTeam()).toBe(team);
    expect(component.formName).toBe('Backend Team');
    expect(component.formDescription).toBe('API development');
  });

  it('should_callCreateTeam_when_saveTeamCalledWithoutEditing', () => {
    fixture.detectChanges();
    userServiceSpy.createTeam.and.returnValue(of(mockTeams[0]));
    userServiceSpy.getTeams.and.returnValue(of(mockPage));

    component.openCreateModal();
    component.formName = 'New Team';
    component.formDescription = 'A new team';
    component.saveTeam();

    expect(userServiceSpy.createTeam).toHaveBeenCalledWith({
      name: 'New Team',
      description: 'A new team',
    });
    expect(component.showCreateModal()).toBeFalse();
  });

  it('should_callUpdateTeam_when_saveTeamCalledWhileEditing', () => {
    fixture.detectChanges();
    userServiceSpy.updateTeam.and.returnValue(of(mockTeams[0]));
    userServiceSpy.getTeams.and.returnValue(of(mockPage));

    component.openEditModal(mockTeams[0]);
    component.formName = 'Backend Team Updated';
    component.saveTeam();

    expect(userServiceSpy.updateTeam).toHaveBeenCalledWith('team-1', {
      name: 'Backend Team Updated',
      description: 'API development',
    });
    expect(component.showCreateModal()).toBeFalse();
  });

  it('should_callDeleteTeam_when_deleteTeamCalledAndConfirmed', () => {
    fixture.detectChanges();
    spyOn(window, 'confirm').and.returnValue(true);
    userServiceSpy.deleteTeam.and.returnValue(of(void 0));
    userServiceSpy.getTeams.and.returnValue(of(mockPage));

    component.deleteTeam(mockTeams[0]);

    expect(userServiceSpy.deleteTeam).toHaveBeenCalledWith('team-1');
  });

  it('should_notCallDeleteTeam_when_confirmCancelled', () => {
    spyOn(window, 'confirm').and.returnValue(false);

    component.deleteTeam(mockTeams[0]);

    expect(userServiceSpy.deleteTeam).not.toHaveBeenCalled();
  });

  it('should_showEmptyState_when_getTeamsFails', () => {
    userServiceSpy.getTeams.and.returnValue(throwError(() => new Error('API error')));

    fixture.detectChanges();

    expect(component.teams().length).toBe(0);
    expect(component.loading()).toBeFalse();
  });
});
