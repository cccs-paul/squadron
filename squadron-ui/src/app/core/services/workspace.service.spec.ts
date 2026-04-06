import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { WorkspaceService, Workspace } from './workspace.service';
import { environment } from '../../../environments/environment';

describe('WorkspaceService', () => {
  let service: WorkspaceService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(WorkspaceService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  function mockWorkspace(overrides: Partial<Workspace> = {}): Workspace {
    return {
      id: 'ws-1',
      tenantId: 't1',
      taskId: 'task-1',
      status: 'READY',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      ...overrides,
    };
  }

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should get workspace by task ID', () => {
    const expected = mockWorkspace({ taskId: 'task-42' });

    service.getWorkspaceByTask('task-42').subscribe((workspace) => {
      expect(workspace.id).toBe('ws-1');
      expect(workspace.taskId).toBe('task-42');
      expect(workspace.status).toBe('READY');
    });

    const req = httpTesting.expectOne(`${apiUrl}/workspaces/task/task-42`);
    expect(req.request.method).toBe('GET');
    req.flush(expected);
  });

  it('should return workspace with RUNNING status', () => {
    const expected = mockWorkspace({ status: 'RUNNING', containerId: 'c-123' });

    service.getWorkspaceByTask('task-1').subscribe((workspace) => {
      expect(workspace.status).toBe('RUNNING');
      expect(workspace.containerId).toBe('c-123');
    });

    const req = httpTesting.expectOne(`${apiUrl}/workspaces/task/task-1`);
    req.flush(expected);
  });

  it('should destroy workspace by ID', () => {
    service.destroyWorkspace('ws-1').subscribe(() => {
      // Expect no error
    });

    const req = httpTesting.expectOne(`${apiUrl}/workspaces/ws-1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should handle error when workspace not found', () => {
    let errorOccurred = false;

    service.getWorkspaceByTask('nonexistent').subscribe({
      error: (err) => {
        errorOccurred = true;
        expect(err.status).toBe(404);
      },
    });

    const req = httpTesting.expectOne(`${apiUrl}/workspaces/task/nonexistent`);
    req.flush('Not found', { status: 404, statusText: 'Not Found' });
    expect(errorOccurred).toBeTrue();
  });

  it('should handle error when destroy fails', () => {
    let errorOccurred = false;

    service.destroyWorkspace('ws-bad').subscribe({
      error: (err) => {
        errorOccurred = true;
        expect(err.status).toBe(500);
      },
    });

    const req = httpTesting.expectOne(`${apiUrl}/workspaces/ws-bad`);
    req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });
    expect(errorOccurred).toBeTrue();
  });
});
