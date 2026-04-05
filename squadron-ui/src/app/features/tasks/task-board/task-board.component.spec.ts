import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TaskBoardComponent } from './task-board.component';
import { TaskService } from '../../../core/services/task.service';
import { Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { TaskState, TaskPriority, Task } from '../../../core/models/task.model';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { TranslateModule } from '@ngx-translate/core';

describe('TaskBoardComponent', () => {
  let component: TaskBoardComponent;
  let fixture: ComponentFixture<TaskBoardComponent>;
  let taskServiceSpy: jasmine.SpyObj<TaskService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    taskServiceSpy = jasmine.createSpyObj('TaskService', ['getTasksByState', 'transitionTask']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    taskServiceSpy.getTasksByState.and.returnValue(throwError(() => new Error('api down')));

    await TestBed.configureTestingModule({
      imports: [TaskBoardComponent, TranslateModule.forRoot()],
      providers: [
        { provide: TaskService, useValue: taskServiceSpy },
        { provide: Router, useValue: routerSpy },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TaskBoardComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should create 6 empty columns on error', () => {
    fixture.detectChanges();
    expect(component.columns().length).toBe(6);
    const labels = component.columns().map((c) => c.label);
    expect(labels).toEqual(['tasks.board.column.backlog', 'tasks.board.column.planning', 'tasks.board.column.inProgress', 'tasks.board.column.review', 'tasks.board.column.qa', 'tasks.board.column.done']);
  });

  it('should have no tasks in any column on error', () => {
    fixture.detectChanges();
    for (const col of component.columns()) {
      expect(col.tasks.length).toBe(0);
    }
  });

  it('should set loading to false after load', () => {
    fixture.detectChanges();
    expect(component.loading()).toBeFalse();
  });

  it('should create columns from API data on success', () => {
    const apiData: Record<TaskState, Task[]> = {
      [TaskState.BACKLOG]: [],
      [TaskState.PLANNING]: [],
      [TaskState.IN_PROGRESS]: [{
        id: 'x1', tenantId: '1', projectId: '1', title: 'Test Task',
        state: TaskState.IN_PROGRESS, priority: TaskPriority.HIGH, labels: [],
        tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
      }],
      [TaskState.REVIEW]: [],
      [TaskState.QA]: [],
      [TaskState.DONE]: [],
    };
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    const inProgress = component.columns().find((c) => c.state === TaskState.IN_PROGRESS);
    expect(inProgress!.tasks.length).toBe(1);
    expect(inProgress!.tasks[0].title).toBe('Test Task');
  });

  it('should navigate to task detail on openTask', () => {
    fixture.detectChanges();
    const mockTask = { id: 'task-123' } as Task;
    component.openTask(mockTask);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/tasks', 'task-123']);
  });

  it('should have empty QA column on error', () => {
    fixture.detectChanges();
    const qa = component.columns().find((c) => c.state === TaskState.QA);
    expect(qa!.tasks.length).toBe(0);
  });

  it('should initialize filter fields as empty', () => {
    expect(component.filterPriority()).toBe('');
    expect(component.filterAssignee()).toBe('');
    expect(component.searchQuery()).toBe('');
  });

  it('should return all columns from filteredColumns when no filters set', () => {
    fixture.detectChanges();
    const filtered = component.filteredColumns();
    expect(filtered.length).toBe(6);
    expect(filtered).toEqual(component.columns());
  });

  it('should filter by priority', () => {
    const apiData: Record<TaskState, Task[]> = {
      [TaskState.BACKLOG]: [
        { id: '1', tenantId: '1', projectId: '1', title: 'Add export feature', state: TaskState.BACKLOG, priority: TaskPriority.LOW, labels: [], tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
        { id: '2', tenantId: '1', projectId: '1', title: 'Refactor module', state: TaskState.BACKLOG, priority: TaskPriority.MEDIUM, labels: [], tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      ],
      [TaskState.PLANNING]: [],
      [TaskState.IN_PROGRESS]: [
        { id: '3', tenantId: '1', projectId: '1', title: 'Implement dashboard', state: TaskState.IN_PROGRESS, priority: TaskPriority.HIGH, labels: ['feature'], externalId: 'SQ-42', tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      ],
      [TaskState.REVIEW]: [],
      [TaskState.QA]: [],
      [TaskState.DONE]: [],
    };
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    component.filterPriority.set('HIGH');
    const filtered = component.filteredColumns();
    for (const col of filtered) {
      for (const task of col.tasks) {
        expect(task.priority).toBe(TaskPriority.HIGH);
      }
    }
    const backlog = filtered.find(c => c.state === TaskState.BACKLOG);
    expect(backlog!.tasks.length).toBe(0);
  });

  it('should filter by search query matching title', () => {
    const apiData: Record<TaskState, Task[]> = {
      [TaskState.BACKLOG]: [
        { id: '1', tenantId: '1', projectId: '1', title: 'Add export feature', state: TaskState.BACKLOG, priority: TaskPriority.LOW, labels: [], tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
        { id: '2', tenantId: '1', projectId: '1', title: 'Refactor module', state: TaskState.BACKLOG, priority: TaskPriority.MEDIUM, labels: [], tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      ],
      [TaskState.PLANNING]: [],
      [TaskState.IN_PROGRESS]: [],
      [TaskState.REVIEW]: [],
      [TaskState.QA]: [],
      [TaskState.DONE]: [],
    };
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    component.searchQuery.set('export');
    const filtered = component.filteredColumns();
    const backlog = filtered.find(c => c.state === TaskState.BACKLOG);
    expect(backlog!.tasks.length).toBe(1);
    expect(backlog!.tasks[0].title).toContain('export');
  });

  it('should filter by search query matching labels', () => {
    const apiData: Record<TaskState, Task[]> = {
      [TaskState.BACKLOG]: [],
      [TaskState.PLANNING]: [],
      [TaskState.IN_PROGRESS]: [
        { id: '3', tenantId: '1', projectId: '1', title: 'Fix login bug', state: TaskState.IN_PROGRESS, priority: TaskPriority.HIGH, labels: ['bug'], tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
        { id: '4', tenantId: '1', projectId: '1', title: 'Implement dashboard', state: TaskState.IN_PROGRESS, priority: TaskPriority.HIGH, labels: ['feature'], tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      ],
      [TaskState.REVIEW]: [],
      [TaskState.QA]: [],
      [TaskState.DONE]: [],
    };
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    component.searchQuery.set('bug');
    const filtered = component.filteredColumns();
    const inProgress = filtered.find(c => c.state === TaskState.IN_PROGRESS);
    expect(inProgress!.tasks.length).toBe(1);
    expect(inProgress!.tasks[0].labels).toContain('bug');
  });

  it('should filter by search query matching externalId', () => {
    const apiData: Record<TaskState, Task[]> = {
      [TaskState.BACKLOG]: [],
      [TaskState.PLANNING]: [],
      [TaskState.IN_PROGRESS]: [
        { id: '3', tenantId: '1', projectId: '1', title: 'Implement dashboard', state: TaskState.IN_PROGRESS, priority: TaskPriority.HIGH, labels: ['feature'], externalId: 'SQ-42', tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
        { id: '4', tenantId: '1', projectId: '1', title: 'Other task', state: TaskState.IN_PROGRESS, priority: TaskPriority.MEDIUM, labels: [], tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      ],
      [TaskState.REVIEW]: [],
      [TaskState.QA]: [],
      [TaskState.DONE]: [],
    };
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    component.searchQuery.set('SQ-42');
    const filtered = component.filteredColumns();
    const totalTasks = filtered.reduce((sum, col) => sum + col.tasks.length, 0);
    expect(totalTasks).toBe(1);
    expect(filtered.find(c => c.state === TaskState.IN_PROGRESS)!.tasks[0].externalId).toBe('SQ-42');
  });

  it('should combine search and priority filters', () => {
    const apiData: Record<TaskState, Task[]> = {
      [TaskState.BACKLOG]: [],
      [TaskState.PLANNING]: [],
      [TaskState.IN_PROGRESS]: [
        { id: '3', tenantId: '1', projectId: '1', title: 'Implement dashboard', state: TaskState.IN_PROGRESS, priority: TaskPriority.HIGH, labels: ['feature'], tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
        { id: '4', tenantId: '1', projectId: '1', title: 'Dashboard bugfix', state: TaskState.IN_PROGRESS, priority: TaskPriority.LOW, labels: [], tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      ],
      [TaskState.REVIEW]: [],
      [TaskState.QA]: [],
      [TaskState.DONE]: [],
    };
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    component.searchQuery.set('dashboard');
    component.filterPriority.set('HIGH');
    const filtered = component.filteredColumns();
    const totalTasks = filtered.reduce((sum, col) => sum + col.tasks.length, 0);
    expect(totalTasks).toBe(1);
  });

  it('should compute connectedDropLists with correct IDs', () => {
    fixture.detectChanges();
    const lists = component.connectedDropLists();
    expect(lists.length).toBe(6);
    expect(lists).toEqual([
      'drop-list-BACKLOG',
      'drop-list-PLANNING',
      'drop-list-IN_PROGRESS',
      'drop-list-REVIEW',
      'drop-list-QA',
      'drop-list-DONE',
    ]);
  });

  it('should reorder within same column on drop', () => {
    const apiData: Record<TaskState, Task[]> = {
      [TaskState.BACKLOG]: [
        { id: '1', tenantId: '1', projectId: '1', title: 'Add export feature', state: TaskState.BACKLOG, priority: TaskPriority.LOW, labels: [], tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
        { id: '2', tenantId: '1', projectId: '1', title: 'Refactor module', state: TaskState.BACKLOG, priority: TaskPriority.MEDIUM, labels: [], tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      ],
      [TaskState.PLANNING]: [],
      [TaskState.IN_PROGRESS]: [],
      [TaskState.REVIEW]: [],
      [TaskState.QA]: [],
      [TaskState.DONE]: [],
    };
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    const columns = component.columns();
    const backlogCol = columns.find(c => c.state === TaskState.BACKLOG)!;
    const containerData = [...backlogCol.tasks];
    const container = { data: containerData } as any;

    const event: Partial<CdkDragDrop<Task[]>> = {
      previousContainer: container,
      container: container,
      previousIndex: 0,
      currentIndex: 1,
      item: {} as any,
      isPointerOverContainer: true,
      distance: { x: 0, y: 0 },
      dropPoint: { x: 0, y: 0 },
      event: {} as any,
    };

    component.drop(event as CdkDragDrop<Task[]>, backlogCol);
    // moveItemInArray should have swapped positions
    expect(containerData[0].title).toBe('Refactor module');
    expect(containerData[1].title).toBe('Add export feature');
  });

  it('should call transitionTask when task dropped to different column', () => {
    const apiData: Record<TaskState, Task[]> = {
      [TaskState.BACKLOG]: [
        { id: '1', tenantId: '1', projectId: '1', title: 'Add export feature', state: TaskState.BACKLOG, priority: TaskPriority.LOW, labels: [], tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      ],
      [TaskState.PLANNING]: [
        { id: '5', tenantId: '1', projectId: '1', title: 'Plan sprint', state: TaskState.PLANNING, priority: TaskPriority.MEDIUM, labels: [], tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      ],
      [TaskState.IN_PROGRESS]: [],
      [TaskState.REVIEW]: [],
      [TaskState.QA]: [],
      [TaskState.DONE]: [],
    };
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    const columns = component.columns();
    const sourceCol = columns.find(c => c.state === TaskState.BACKLOG)!;
    const targetCol = columns.find(c => c.state === TaskState.PLANNING)!;
    const sourceData = [...sourceCol.tasks];
    const targetData = [...targetCol.tasks];
    const sourceContainer = { data: sourceData } as any;
    const targetContainer = { data: targetData } as any;

    taskServiceSpy.transitionTask.and.returnValue(of({} as Task));

    const event: Partial<CdkDragDrop<Task[]>> = {
      previousContainer: sourceContainer,
      container: targetContainer,
      previousIndex: 0,
      currentIndex: 0,
      item: {} as any,
      isPointerOverContainer: true,
      distance: { x: 0, y: 0 },
      dropPoint: { x: 0, y: 0 },
      event: {} as any,
    };

    component.drop(event as CdkDragDrop<Task[]>, targetCol);
    expect(taskServiceSpy.transitionTask).toHaveBeenCalledWith('1', TaskState.PLANNING);
    // Task should have moved from source to target
    expect(sourceData.length).toBe(0);
    expect(targetData.length).toBe(2);
  });

  it('should revert transfer on failed transition', () => {
    const apiData: Record<TaskState, Task[]> = {
      [TaskState.BACKLOG]: [
        { id: '1', tenantId: '1', projectId: '1', title: 'Add export feature', state: TaskState.BACKLOG, priority: TaskPriority.LOW, labels: [], tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      ],
      [TaskState.PLANNING]: [],
      [TaskState.IN_PROGRESS]: [
        { id: '3', tenantId: '1', projectId: '1', title: 'Implement dashboard', state: TaskState.IN_PROGRESS, priority: TaskPriority.HIGH, labels: [], tokenUsage: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      ],
      [TaskState.REVIEW]: [],
      [TaskState.QA]: [],
      [TaskState.DONE]: [],
    };
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    const columns = component.columns();
    const sourceCol = columns.find(c => c.state === TaskState.BACKLOG)!;
    const targetCol = columns.find(c => c.state === TaskState.IN_PROGRESS)!;
    const sourceData = [...sourceCol.tasks];
    const targetData = [...targetCol.tasks];
    const originalSourceLength = sourceData.length;
    const originalTargetLength = targetData.length;
    const sourceContainer = { data: sourceData } as any;
    const targetContainer = { data: targetData } as any;

    taskServiceSpy.transitionTask.and.returnValue(throwError(() => new Error('Server error')));

    const event: Partial<CdkDragDrop<Task[]>> = {
      previousContainer: sourceContainer,
      container: targetContainer,
      previousIndex: 0,
      currentIndex: 0,
      item: {} as any,
      isPointerOverContainer: true,
      distance: { x: 0, y: 0 },
      dropPoint: { x: 0, y: 0 },
      event: {} as any,
    };

    component.drop(event as CdkDragDrop<Task[]>, targetCol);
    // After revert, counts should be back to original
    expect(sourceData.length).toBe(originalSourceLength);
    expect(targetData.length).toBe(originalTargetLength);
  });
});
