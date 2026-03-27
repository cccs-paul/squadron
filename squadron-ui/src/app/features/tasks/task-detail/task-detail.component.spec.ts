import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TaskDetailComponent } from './task-detail.component';
import { TaskService } from '../../../core/services/task.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { Task, TaskState, TaskPriority } from '../../../core/models/task.model';

describe('TaskDetailComponent', () => {
  let component: TaskDetailComponent;
  let fixture: ComponentFixture<TaskDetailComponent>;
  let taskServiceSpy: jasmine.SpyObj<TaskService>;

  const mockTask: Task = {
    id: 'task-1',
    tenantId: '1',
    projectId: '1',
    title: 'Implement dashboard',
    description: 'Build the main dashboard',
    state: TaskState.IN_PROGRESS,
    priority: TaskPriority.HIGH,
    labels: ['feature', 'frontend'],
    tokenUsage: 24500,
    assigneeName: 'AI Agent',
    externalId: 'SQ-42',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };

  beforeEach(async () => {
    taskServiceSpy = jasmine.createSpyObj('TaskService', ['getTask', 'transitionTask']);

    await TestBed.configureTestingModule({
      imports: [TaskDetailComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TaskService, useValue: taskServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({ id: 'task-1' }) },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TaskDetailComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should load task from service on init', () => {
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    fixture.detectChanges();
    expect(taskServiceSpy.getTask).toHaveBeenCalledWith('task-1');
    expect(component.task()!.title).toBe('Implement dashboard');
    expect(component.loading()).toBeFalse();
  });

  it('should fall back to mock task on service error', () => {
    taskServiceSpy.getTask.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.task()).toBeTruthy();
    expect(component.task()!.id).toBe('task-1');
    expect(component.loading()).toBeFalse();
  });

  it('should return available transitions for IN_PROGRESS state', () => {
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    fixture.detectChanges();
    const transitions = component.getAvailableTransitions();
    expect(transitions).toContain(TaskState.REVIEW);
    expect(transitions).toContain(TaskState.PLANNING);
  });

  it('should return empty transitions for DONE state', () => {
    const doneTask = { ...mockTask, state: TaskState.DONE };
    taskServiceSpy.getTask.and.returnValue(of(doneTask));
    fixture.detectChanges();
    expect(component.getAvailableTransitions()).toEqual([]);
  });

  it('should transition task via service', () => {
    const updatedTask = { ...mockTask, state: TaskState.REVIEW };
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    taskServiceSpy.transitionTask.and.returnValue(of(updatedTask));
    fixture.detectChanges();

    component.transitionTo(TaskState.REVIEW);
    expect(taskServiceSpy.transitionTask).toHaveBeenCalledWith('task-1', TaskState.REVIEW);
    expect(component.task()!.state).toBe(TaskState.REVIEW);
  });

  it('should optimistically update state on transition error', () => {
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    taskServiceSpy.transitionTask.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    component.transitionTo(TaskState.REVIEW);
    expect(component.task()!.state).toBe(TaskState.REVIEW);
  });

  it('should return correct priority class', () => {
    expect(component.priorityClass(TaskPriority.CRITICAL)).toBe('error');
    expect(component.priorityClass(TaskPriority.HIGH)).toBe('warning');
    expect(component.priorityClass(TaskPriority.MEDIUM)).toBe('primary');
    expect(component.priorityClass(TaskPriority.LOW)).toBe('neutral');
  });

  it('should return correct state color', () => {
    expect(component.stateColor(TaskState.IN_PROGRESS)).toBe('#06B6D4');
    expect(component.stateColor(TaskState.DONE)).toBe('#10B981');
    expect(component.stateColor(TaskState.BACKLOG)).toBe('#9CA3AF');
  });
});
