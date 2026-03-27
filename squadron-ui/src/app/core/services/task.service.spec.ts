import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TaskService } from './task.service';
import { environment } from '../../../environments/environment';

describe('TaskService', () => {
  let service: TaskService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(TaskService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_getTasks_when_calledWithNoFilter', () => {
    service.getTasks().subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/tasks` &&
      r.params.get('page') === '0' &&
      r.params.get('size') === '50'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 50 });
  });

  it('should_getTasks_when_calledWithStateFilter', () => {
    service.getTasks({ state: 'IN_PROGRESS' } as any).subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/tasks` &&
      r.params.get('state') === 'IN_PROGRESS' &&
      r.params.get('page') === '0' &&
      r.params.get('size') === '50'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 50 });
  });

  it('should_getTasks_when_calledWithAllFilters', () => {
    const filter = {
      state: 'PLANNING',
      priority: 'HIGH',
      assigneeId: 'u1',
      projectId: 'p1',
      search: 'bug fix',
    };

    service.getTasks(filter as any, 1, 25).subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/tasks` &&
      r.params.get('state') === 'PLANNING' &&
      r.params.get('priority') === 'HIGH' &&
      r.params.get('assigneeId') === 'u1' &&
      r.params.get('projectId') === 'p1' &&
      r.params.get('search') === 'bug fix' &&
      r.params.get('page') === '1' &&
      r.params.get('size') === '25'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 1, size: 25 });
  });

  it('should_getTask_when_calledWithId', () => {
    const mockTask = { id: 'task1', title: 'Fix bug' };

    service.getTask('task1').subscribe((task) => {
      expect(task).toEqual(mockTask as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/tasks/task1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockTask);
  });

  it('should_createTask_when_calledWithTaskData', () => {
    const taskData = { title: 'New task', description: 'Details' };
    const mockResponse = { id: 'task2', ...taskData };

    service.createTask(taskData as any).subscribe((task) => {
      expect(task).toEqual(mockResponse as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/tasks`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(taskData);
    req.flush(mockResponse);
  });

  it('should_updateTask_when_calledWithIdAndData', () => {
    const updateData = { title: 'Updated task' };

    service.updateTask('task1', updateData as any).subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/tasks/task1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(updateData);
    req.flush({ id: 'task1', ...updateData });
  });

  it('should_transitionTask_when_calledWithIdAndState', () => {
    service.transitionTask('task1', 'IN_PROGRESS' as any).subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/tasks/task1/transition`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ toState: 'IN_PROGRESS' });
    req.flush({ id: 'task1', state: 'IN_PROGRESS' });
  });

  it('should_getTasksByState_when_called', () => {
    const mockData = { PLANNING: [], IN_PROGRESS: [{ id: 'task1' }] };

    service.getTasksByState().subscribe((result) => {
      expect(result).toEqual(mockData as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/tasks/by-state`);
    expect(req.request.method).toBe('GET');
    req.flush(mockData);
  });

  it('should_getTaskStats_when_called', () => {
    const mockStats = { total: 42, byState: { PLANNING: 10 }, byPriority: { HIGH: 5 } };

    service.getTaskStats().subscribe((stats) => {
      expect(stats).toEqual(mockStats as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/tasks/stats`);
    expect(req.request.method).toBe('GET');
    req.flush(mockStats);
  });

  it('should_notIncludeOptionalFilterParams_when_filterFieldsUndefined', () => {
    service.getTasks({} as any).subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/tasks` &&
      !r.params.has('state') &&
      !r.params.has('priority') &&
      !r.params.has('assigneeId') &&
      !r.params.has('projectId') &&
      !r.params.has('search')
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 50 });
  });
});
