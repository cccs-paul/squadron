import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { DiffService } from './diff.service';
import { DiffResult, CodeGenerationStatus } from '../models/diff.model';
import { environment } from '../../../environments/environment';

describe('DiffService', () => {
  let service: DiffService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(DiffService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should_be_created', () => {
    expect(service).toBeTruthy();
  });

  it('should_getTaskDiff', () => {
    const mockDiff: DiffResult = {
      files: [
        { filename: 'src/app.ts', status: 'modified', additions: 10, deletions: 3, patch: '@@ -1,5 +1,12 @@\n+import { foo } from "bar";' },
        { filename: 'src/utils.ts', status: 'added', additions: 25, deletions: 0, patch: '@@ -0,0 +1,25 @@\n+export function helper() {}' },
      ],
      totalAdditions: 35,
      totalDeletions: 3,
    };

    service.getTaskDiff('task-123').subscribe((result) => {
      expect(result).toEqual(mockDiff);
      expect(result.files.length).toBe(2);
      expect(result.totalAdditions).toBe(35);
    });

    const req = httpTesting.expectOne(`${apiUrl}/api/git/pull-requests/task/task-123/diff`);
    expect(req.request.method).toBe('GET');
    req.flush(mockDiff);
  });

  it('should_getCodeGenerationStatus', () => {
    const mockStatus: CodeGenerationStatus = {
      taskId: 'task-123',
      status: 'COMPLETED',
      branchName: 'feature/sq-123',
      prUrl: 'https://github.com/org/repo/pull/42',
      prId: 'pr-42',
      summary: 'Added user dashboard feature',
    };

    service.getCodeGenerationStatus('task-123').subscribe((status) => {
      expect(status).toEqual(mockStatus);
      expect(status.status).toBe('COMPLETED');
      expect(status.branchName).toBe('feature/sq-123');
    });

    const req = httpTesting.expectOne(`${apiUrl}/api/agents/coding/status/task-123`);
    expect(req.request.method).toBe('GET');
    req.flush(mockStatus);
  });

  it('should_getPullRequestDiff', () => {
    const mockDiff: DiffResult = {
      files: [
        { filename: 'README.md', status: 'modified', additions: 5, deletions: 1, patch: '@@ -1,3 +1,7 @@\n+## New Section' },
      ],
      totalAdditions: 5,
      totalDeletions: 1,
    };

    service.getPullRequestDiff('pr-42').subscribe((result) => {
      expect(result).toEqual(mockDiff);
      expect(result.files.length).toBe(1);
    });

    const req = httpTesting.expectOne(`${apiUrl}/api/git/pull-requests/pr-42/diff`);
    expect(req.request.method).toBe('GET');
    req.flush(mockDiff);
  });

  it('should_handle_error_on_getTaskDiff', () => {
    let errorResponse: any;

    service.getTaskDiff('bad-task').subscribe({
      next: () => fail('Expected an error'),
      error: (err) => { errorResponse = err; },
    });

    const req = httpTesting.expectOne(`${apiUrl}/api/git/pull-requests/task/bad-task/diff`);
    req.flush('Not Found', { status: 404, statusText: 'Not Found' });

    expect(errorResponse).toBeTruthy();
    expect(errorResponse.status).toBe(404);
  });

  it('should_handle_error_on_getCodeGenerationStatus', () => {
    let errorResponse: any;

    service.getCodeGenerationStatus('bad-task').subscribe({
      next: () => fail('Expected an error'),
      error: (err) => { errorResponse = err; },
    });

    const req = httpTesting.expectOne(`${apiUrl}/api/agents/coding/status/bad-task`);
    req.flush('Internal Server Error', { status: 500, statusText: 'Internal Server Error' });

    expect(errorResponse).toBeTruthy();
    expect(errorResponse.status).toBe(500);
  });

  it('should_return_empty_files_when_no_changes', () => {
    const emptyDiff: DiffResult = {
      files: [],
      totalAdditions: 0,
      totalDeletions: 0,
    };

    service.getTaskDiff('no-changes').subscribe((result) => {
      expect(result.files).toEqual([]);
      expect(result.totalAdditions).toBe(0);
      expect(result.totalDeletions).toBe(0);
    });

    const req = httpTesting.expectOne(`${apiUrl}/api/git/pull-requests/task/no-changes/diff`);
    req.flush(emptyDiff);
  });

  it('should_handle_error_on_getPullRequestDiff', () => {
    let errorResponse: any;

    service.getPullRequestDiff('bad-pr').subscribe({
      next: () => fail('Expected an error'),
      error: (err) => { errorResponse = err; },
    });

    const req = httpTesting.expectOne(`${apiUrl}/api/git/pull-requests/bad-pr/diff`);
    req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });

    expect(errorResponse).toBeTruthy();
    expect(errorResponse.status).toBe(403);
  });
});
