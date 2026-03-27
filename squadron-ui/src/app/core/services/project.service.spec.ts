import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ProjectService } from './project.service';
import { environment } from '../../../environments/environment';

describe('ProjectService', () => {
  let service: ProjectService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(ProjectService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_getProjects_when_calledWithDefaults', () => {
    service.getProjects().subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/projects` &&
      r.params.get('page') === '0' &&
      r.params.get('size') === '20'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 });
  });

  it('should_getProjects_when_calledWithSearchTerm', () => {
    service.getProjects(1, 10, 'squadron').subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/projects` &&
      r.params.get('page') === '1' &&
      r.params.get('size') === '10' &&
      r.params.get('search') === 'squadron'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 1, size: 10 });
  });

  it('should_notIncludeSearchParam_when_searchUndefined', () => {
    service.getProjects(0, 20).subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/projects` &&
      !r.params.has('search')
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 });
  });

  it('should_getProject_when_calledWithId', () => {
    const mockProject = { id: 'p1', name: 'My Project' };

    service.getProject('p1').subscribe((project) => {
      expect(project).toEqual(mockProject as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/projects/p1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockProject);
  });

  it('should_createProject_when_calledWithProjectData', () => {
    const projectData = { name: 'New Project', description: 'A new project' };
    const mockResponse = { id: 'p2', ...projectData };

    service.createProject(projectData as any).subscribe((project) => {
      expect(project).toEqual(mockResponse as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/projects`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(projectData);
    req.flush(mockResponse);
  });

  it('should_updateProject_when_calledWithIdAndData', () => {
    const updateData = { name: 'Updated Project' };
    const mockResponse = { id: 'p1', name: 'Updated Project' };

    service.updateProject('p1', updateData as any).subscribe((project) => {
      expect(project).toEqual(mockResponse as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/projects/p1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(updateData);
    req.flush(mockResponse);
  });

  it('should_deleteProject_when_calledWithId', () => {
    service.deleteProject('p1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/projects/p1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
