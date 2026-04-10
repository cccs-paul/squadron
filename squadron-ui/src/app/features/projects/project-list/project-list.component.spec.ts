import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProjectListComponent } from './project-list.component';
import { ProjectService } from '../../../core/services/project.service';
import { TaskService } from '../../../core/services/task.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { Project, ProjectSummary } from '../../../core/models/project.model';
import { TaskSyncResult } from '../../../core/models/task.model';
import { TranslateModule } from '@ngx-translate/core';

describe('ProjectListComponent', () => {
  let component: ProjectListComponent;
  let fixture: ComponentFixture<ProjectListComponent>;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;
  let taskServiceSpy: jasmine.SpyObj<TaskService>;
  let authServiceStub: { user: any };
  let routerSpy: jasmine.SpyObj<Router>;

  const defaultUser = {
    id: 'u1',
    tenantId: 't1',
    username: 'testuser',
    email: 'test@example.com',
    displayName: 'Test User',
    tenantName: 'Test Tenant',
    roles: [],
    permissions: [],
  };

  function makeProject(overrides: Partial<Project> = {}): Project {
    return {
      id: 'p1',
      tenantId: 't1',
      name: 'My Project',
      description: 'A test project',
      repositoryUrl: 'https://github.com/org/repo',
      connectionId: 'conn1',
      externalProjectId: 'EXT-1',
      defaultBranch: 'main',
      taskCount: 10,
      activeTaskCount: 3,
      members: [],
      createdAt: '2025-01-01T00:00:00Z',
      ...overrides,
    };
  }

  function makeSummary(overrides: Partial<ProjectSummary> = {}): ProjectSummary {
    return {
      id: 'p1',
      tenantId: 't1',
      name: 'My Project',
      defaultBranch: 'main',
      totalTasks: 10,
      activeTasks: 3,
      taskCountsByState: { IN_PROGRESS: 2, DONE: 5, BACKLOG: 3 },
      workflowMappingsConfigured: true,
      workflowMappingCount: 4,
      createdAt: '2025-01-01T00:00:00Z',
      ...overrides,
    };
  }

  function makeSyncResult(overrides: Partial<TaskSyncResult> = {}): TaskSyncResult {
    return {
      created: 0,
      updated: 0,
      unchanged: 5,
      failed: 0,
      errors: [],
      ...overrides,
    };
  }

  beforeEach(async () => {
    projectServiceSpy = jasmine.createSpyObj('ProjectService', [
      'getProjectsByTenant',
      'getProjectSummaries',
    ]);
    taskServiceSpy = jasmine.createSpyObj('TaskService', ['syncTasks']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    authServiceStub = {
      user: (() => defaultUser) as any,
    };

    // Default: services return empty arrays
    projectServiceSpy.getProjectsByTenant.and.returnValue(of([]));
    projectServiceSpy.getProjectSummaries.and.returnValue(of([]));
    taskServiceSpy.syncTasks.and.returnValue(of(makeSyncResult()));

    await TestBed.configureTestingModule({
      imports: [ProjectListComponent, TranslateModule.forRoot()],
      providers: [
        { provide: ProjectService, useValue: projectServiceSpy },
        { provide: TaskService, useValue: taskServiceSpy },
        { provide: AuthService, useValue: authServiceStub },
        { provide: Router, useValue: routerSpy },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProjectListComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should show empty state when no tenantId', () => {
    authServiceStub.user = (() => null) as any;
    fixture.detectChanges();

    expect(component.projects()).toEqual([]);
    expect(component.loading()).toBeFalse();
    expect(projectServiceSpy.getProjectsByTenant).not.toHaveBeenCalled();
  });

  it('should load projects and summaries on init', () => {
    const projects = [makeProject(), makeProject({ id: 'p2', name: 'Second' })];
    const summaries = [makeSummary(), makeSummary({ id: 'p2', name: 'Second', totalTasks: 5 })];
    projectServiceSpy.getProjectsByTenant.and.returnValue(of(projects));
    projectServiceSpy.getProjectSummaries.and.returnValue(of(summaries));

    fixture.detectChanges();

    expect(component.projects()).toEqual(projects);
    expect(component.summaries()).toEqual(summaries);
    expect(projectServiceSpy.getProjectsByTenant).toHaveBeenCalledWith('t1');
    expect(projectServiceSpy.getProjectSummaries).toHaveBeenCalledWith('t1');
  });

  it('should set loading false after load', () => {
    projectServiceSpy.getProjectsByTenant.and.returnValue(of([makeProject()]));
    projectServiceSpy.getProjectSummaries.and.returnValue(of([makeSummary()]));

    expect(component.loading()).toBeTrue();
    fixture.detectChanges();
    expect(component.loading()).toBeFalse();
  });

  it('should set loading false on error', () => {
    // Both inner observables have catchError → of([]), so the forkJoin won't error.
    // Force the forkJoin error path by making getProjectsByTenant return an error
    // that somehow bypasses catchError. In reality, individual catchError handles it,
    // so we test the catchError-to-empty fallback instead.
    projectServiceSpy.getProjectsByTenant.and.returnValue(throwError(() => new Error('fail')));
    projectServiceSpy.getProjectSummaries.and.returnValue(throwError(() => new Error('fail')));

    fixture.detectChanges();

    expect(component.loading()).toBeFalse();
    // Due to individual catchError, projects and summaries should be empty arrays
    expect(component.projects()).toEqual([]);
    expect(component.summaries()).toEqual([]);
  });

  it('should handle empty projects', () => {
    projectServiceSpy.getProjectsByTenant.and.returnValue(of([]));
    projectServiceSpy.getProjectSummaries.and.returnValue(of([]));

    fixture.detectChanges();

    expect(component.projects()).toEqual([]);
    expect(component.summaries()).toEqual([]);
    expect(component.totalProjects()).toBe(0);
    expect(component.totalTasks()).toBe(0);
  });

  it('should filter projects by search query matching name', () => {
    const projects = [
      makeProject({ id: 'p1', name: 'Frontend App' }),
      makeProject({ id: 'p2', name: 'Backend API' }),
    ];
    projectServiceSpy.getProjectsByTenant.and.returnValue(of(projects));
    fixture.detectChanges();

    component.searchQuery.set('frontend');

    expect(component.filteredProjects().length).toBe(1);
    expect(component.filteredProjects()[0].name).toBe('Frontend App');
  });

  it('should filter projects by search query matching description', () => {
    const projects = [
      makeProject({ id: 'p1', name: 'Alpha', description: 'Handles payments' }),
      makeProject({ id: 'p2', name: 'Beta', description: 'User management' }),
    ];
    projectServiceSpy.getProjectsByTenant.and.returnValue(of(projects));
    fixture.detectChanges();

    component.searchQuery.set('payments');

    expect(component.filteredProjects().length).toBe(1);
    expect(component.filteredProjects()[0].id).toBe('p1');
  });

  it('should filter projects by search query matching externalProjectId', () => {
    const projects = [
      makeProject({ id: 'p1', name: 'Alpha', externalProjectId: 'JIRA-100' }),
      makeProject({ id: 'p2', name: 'Beta', externalProjectId: 'GH-200' }),
    ];
    projectServiceSpy.getProjectsByTenant.and.returnValue(of(projects));
    fixture.detectChanges();

    component.searchQuery.set('jira');

    expect(component.filteredProjects().length).toBe(1);
    expect(component.filteredProjects()[0].externalProjectId).toBe('JIRA-100');
  });

  it('should return all projects when search is empty', () => {
    const projects = [
      makeProject({ id: 'p1', name: 'Alpha' }),
      makeProject({ id: 'p2', name: 'Beta' }),
      makeProject({ id: 'p3', name: 'Gamma' }),
    ];
    projectServiceSpy.getProjectsByTenant.and.returnValue(of(projects));
    fixture.detectChanges();

    component.searchQuery.set('');

    expect(component.filteredProjects().length).toBe(3);
  });

  it('should compute totalProjects correctly', () => {
    const projects = [
      makeProject({ id: 'p1', name: 'One' }),
      makeProject({ id: 'p2', name: 'Two' }),
    ];
    projectServiceSpy.getProjectsByTenant.and.returnValue(of(projects));
    fixture.detectChanges();

    expect(component.totalProjects()).toBe(2);
  });

  it('should compute totalTasks from summaries', () => {
    const summaries = [
      makeSummary({ id: 'p1', totalTasks: 10 }),
      makeSummary({ id: 'p2', totalTasks: 25 }),
    ];
    projectServiceSpy.getProjectSummaries.and.returnValue(of(summaries));
    fixture.detectChanges();

    expect(component.totalTasks()).toBe(35);
  });

  it('should compute totalActiveTasks from summaries', () => {
    const summaries = [
      makeSummary({ id: 'p1', activeTasks: 3 }),
      makeSummary({ id: 'p2', activeTasks: 7 }),
    ];
    projectServiceSpy.getProjectSummaries.and.returnValue(of(summaries));
    fixture.detectChanges();

    expect(component.totalActiveTasks()).toBe(10);
  });

  it('should compute configuredProjects from summaries', () => {
    const summaries = [
      makeSummary({ id: 'p1', workflowMappingsConfigured: true }),
      makeSummary({ id: 'p2', workflowMappingsConfigured: false }),
      makeSummary({ id: 'p3', workflowMappingsConfigured: true }),
    ];
    projectServiceSpy.getProjectSummaries.and.returnValue(of(summaries));
    fixture.detectChanges();

    expect(component.configuredProjects()).toBe(2);
  });

  it('should build summaryMap from summaries', () => {
    const summaries = [
      makeSummary({ id: 'p1', name: 'Alpha' }),
      makeSummary({ id: 'p2', name: 'Beta' }),
    ];
    projectServiceSpy.getProjectSummaries.and.returnValue(of(summaries));
    fixture.detectChanges();

    const map = component.summaryMap();
    expect(Object.keys(map).length).toBe(2);
    expect(map['p1'].name).toBe('Alpha');
    expect(map['p2'].name).toBe('Beta');
  });

  it('should getSummary return correct summary for project', () => {
    const summaries = [
      makeSummary({ id: 'p1', name: 'Alpha', totalTasks: 15 }),
      makeSummary({ id: 'p2', name: 'Beta', totalTasks: 8 }),
    ];
    projectServiceSpy.getProjectSummaries.and.returnValue(of(summaries));
    fixture.detectChanges();

    const summary = component.getSummary('p1');
    expect(summary).toBeDefined();
    expect(summary!.name).toBe('Alpha');
    expect(summary!.totalTasks).toBe(15);
  });

  it('should getSummary return undefined for unknown project', () => {
    const summaries = [makeSummary({ id: 'p1' })];
    projectServiceSpy.getProjectSummaries.and.returnValue(of(summaries));
    fixture.detectChanges();

    expect(component.getSummary('nonexistent')).toBeUndefined();
  });

  it('should getTaskStateEntries filter zero counts and sort descending', () => {
    const summary = makeSummary({
      taskCountsByState: {
        IN_PROGRESS: 5,
        DONE: 10,
        BACKLOG: 0,
        REVIEW: 3,
      },
    });

    const entries = component.getTaskStateEntries(summary);

    // Zero-count BACKLOG should be filtered out
    expect(entries.length).toBe(3);
    // Sorted descending by count
    expect(entries[0]).toEqual({ state: 'DONE', count: 10 });
    expect(entries[1]).toEqual({ state: 'IN_PROGRESS', count: 5 });
    expect(entries[2]).toEqual({ state: 'REVIEW', count: 3 });
  });

  it('should return correct stateColor for each state', () => {
    expect(component.stateColor('BACKLOG')).toBe('#94A3B8');
    expect(component.stateColor('PRIORITIZED')).toBe('#A78BFA');
    expect(component.stateColor('PLANNING')).toBe('#818CF8');
    expect(component.stateColor('PROPOSE_CODE')).toBe('#38BDF8');
    expect(component.stateColor('IN_PROGRESS')).toBe('#06B6D4');
    expect(component.stateColor('REVIEW')).toBe('#F59E0B');
    expect(component.stateColor('QA')).toBe('#8B5CF6');
    expect(component.stateColor('MERGE')).toBe('#22C55E');
    expect(component.stateColor('DONE')).toBe('#10B981');
    expect(component.stateColor('UNKNOWN_STATE')).toBe('#94A3B8');
  });

  it('should set viewMode to grid by default', () => {
    fixture.detectChanges();
    expect(component.viewMode()).toBe('grid');
  });

  it('should toggle viewMode to list', () => {
    fixture.detectChanges();
    component.setViewMode('list');
    expect(component.viewMode()).toBe('list');

    component.setViewMode('grid');
    expect(component.viewMode()).toBe('grid');
  });

  it('should syncProject call taskService.syncTasks with correct request', () => {
    const project = makeProject({
      id: 'p1',
      teamId: 'team1',
      connectionId: 'conn1',
      externalProjectId: 'EXT-1',
    });
    taskServiceSpy.syncTasks.and.returnValue(of(makeSyncResult()));
    fixture.detectChanges();

    component.syncProject(project);

    expect(taskServiceSpy.syncTasks).toHaveBeenCalledWith({
      tenantId: 't1',
      teamId: 'team1',
      projectId: 'p1',
      platformConnectionId: 'conn1',
      projectKey: 'EXT-1',
    });
  });

  it('should syncProject store result on success', () => {
    const project = makeProject({ id: 'p1' });
    const result = makeSyncResult({ created: 2, updated: 1 });
    taskServiceSpy.syncTasks.and.returnValue(of(result));
    fixture.detectChanges();

    component.syncProject(project);

    expect(component.syncResults()['p1']).toEqual(result);
    expect(component.syncingProjectId()).toBeNull();
  });

  it('should syncProject store error on failure', () => {
    const project = makeProject({ id: 'p1' });
    taskServiceSpy.syncTasks.and.returnValue(
      throwError(() => new Error('Network error')),
    );
    fixture.detectChanges();

    component.syncProject(project);

    expect(component.syncErrors()['p1']).toBe('Network error');
    expect(component.syncingProjectId()).toBeNull();
  });

  it('should syncProject not call when no connectionId', () => {
    const project = makeProject({ id: 'p1', connectionId: undefined });
    fixture.detectChanges();

    component.syncProject(project);

    expect(taskServiceSpy.syncTasks).not.toHaveBeenCalled();
  });

  it('should syncProject reload data when tasks created', () => {
    const project = makeProject({ id: 'p1' });
    const result = makeSyncResult({ created: 3 });
    taskServiceSpy.syncTasks.and.returnValue(of(result));
    fixture.detectChanges();

    // Reset call counts after initial loadData from ngOnInit
    projectServiceSpy.getProjectsByTenant.calls.reset();
    projectServiceSpy.getProjectSummaries.calls.reset();

    component.syncProject(project);

    // loadData should be called again because created > 0
    expect(projectServiceSpy.getProjectsByTenant).toHaveBeenCalledWith('t1');
    expect(projectServiceSpy.getProjectSummaries).toHaveBeenCalledWith('t1');
  });

  it('should syncProject not reload when no changes', () => {
    const project = makeProject({ id: 'p1' });
    const result = makeSyncResult({ created: 0, updated: 0, unchanged: 5 });
    taskServiceSpy.syncTasks.and.returnValue(of(result));
    fixture.detectChanges();

    projectServiceSpy.getProjectsByTenant.calls.reset();
    projectServiceSpy.getProjectSummaries.calls.reset();

    component.syncProject(project);

    expect(projectServiceSpy.getProjectsByTenant).not.toHaveBeenCalled();
    expect(projectServiceSpy.getProjectSummaries).not.toHaveBeenCalled();
  });

  it('should canSync return true when connectionId and externalProjectId set', () => {
    const project = makeProject({ connectionId: 'conn1', externalProjectId: 'EXT-1' });
    expect(component.canSync(project)).toBeTrue();
  });

  it('should canSync return false when connectionId missing', () => {
    const project = makeProject({ connectionId: undefined, externalProjectId: 'EXT-1' });
    expect(component.canSync(project)).toBeFalse();

    const project2 = makeProject({ connectionId: 'conn1', externalProjectId: undefined });
    expect(component.canSync(project2)).toBeFalse();
  });

  it('should navigate to project detail on openProject', () => {
    fixture.detectChanges();
    const project = makeProject({ id: 'p42' });

    component.openProject(project);

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/projects', 'p42']);
  });

  it('should navigate to settings on openSettings', () => {
    fixture.detectChanges();

    component.openSettings();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/settings']);
  });

  it('should reload data on refresh', () => {
    fixture.detectChanges();

    projectServiceSpy.getProjectsByTenant.calls.reset();
    projectServiceSpy.getProjectSummaries.calls.reset();

    component.refresh();

    expect(projectServiceSpy.getProjectsByTenant).toHaveBeenCalledWith('t1');
    expect(projectServiceSpy.getProjectSummaries).toHaveBeenCalledWith('t1');
  });
});
