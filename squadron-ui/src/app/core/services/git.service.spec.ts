import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { GitService } from './git.service';
import { environment } from '../../../environments/environment';

describe('GitService', () => {
  let service: GitService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(GitService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_getRepositories_when_calledWithConnectionId', () => {
    const mockRepos = [
      { id: 'r1', name: 'repo1', fullName: 'org/repo1', url: 'https://github.com/org/repo1', defaultBranch: 'main', platform: 'GITHUB' },
    ];

    service.getRepositories('conn1').subscribe((repos) => {
      expect(repos).toEqual(mockRepos);
    });

    const req = httpTesting.expectOne(`${apiUrl}/git/connections/conn1/repositories`);
    expect(req.request.method).toBe('GET');
    req.flush(mockRepos);
  });

  it('should_getBranches_when_calledWithConnectionIdAndRepoName', () => {
    const mockBranches = [
      { name: 'main', sha: 'abc123', isDefault: true },
      { name: 'develop', sha: 'def456', isDefault: false },
    ];

    service.getBranches('conn1', 'my-repo').subscribe((branches) => {
      expect(branches).toEqual(mockBranches);
    });

    const req = httpTesting.expectOne(`${apiUrl}/git/connections/conn1/repositories/my-repo/branches`);
    expect(req.request.method).toBe('GET');
    req.flush(mockBranches);
  });

  it('should_encodeRepoName_when_repoNameContainsSlashes', () => {
    service.getBranches('conn1', 'org/repo').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/git/connections/conn1/repositories/${encodeURIComponent('org/repo')}/branches`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('should_getPullRequestStatus_when_calledWithTaskId', () => {
    const mockStatus = { url: 'https://github.com/org/repo/pull/1', state: 'open', mergeable: true };

    service.getPullRequestStatus('task1').subscribe((status) => {
      expect(status).toEqual(mockStatus);
    });

    const req = httpTesting.expectOne(`${apiUrl}/git/tasks/task1/pull-request`);
    expect(req.request.method).toBe('GET');
    req.flush(mockStatus);
  });

  it('should_returnPullRequestMergeableAsFalse_when_prNotMergeable', () => {
    const mockStatus = { url: 'https://github.com/org/repo/pull/2', state: 'open', mergeable: false };

    service.getPullRequestStatus('task2').subscribe((status) => {
      expect(status.mergeable).toBe(false);
    });

    const req = httpTesting.expectOne(`${apiUrl}/git/tasks/task2/pull-request`);
    req.flush(mockStatus);
  });

  it('should_returnEmptyArray_when_noRepositoriesExist', () => {
    service.getRepositories('conn-empty').subscribe((repos) => {
      expect(repos).toEqual([]);
      expect(repos.length).toBe(0);
    });

    const req = httpTesting.expectOne(`${apiUrl}/git/connections/conn-empty/repositories`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('should_returnEmptyArray_when_noBranchesExist', () => {
    service.getBranches('conn1', 'empty-repo').subscribe((branches) => {
      expect(branches).toEqual([]);
    });

    const req = httpTesting.expectOne(`${apiUrl}/git/connections/conn1/repositories/empty-repo/branches`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
