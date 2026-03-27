import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProjectListComponent } from './project-list.component';
import { ProjectService } from '../../../core/services/project.service';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { Project } from '../../../core/models/project.model';

describe('ProjectListComponent', () => {
  let component: ProjectListComponent;
  let fixture: ComponentFixture<ProjectListComponent>;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;

  beforeEach(async () => {
    projectServiceSpy = jasmine.createSpyObj('ProjectService', ['getProjects']);
    projectServiceSpy.getProjects.and.returnValue(throwError(() => new Error('api down')));

    await TestBed.configureTestingModule({
      imports: [ProjectListComponent],
      providers: [
        { provide: ProjectService, useValue: projectServiceSpy },
        provideRouter([]),
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

  it('should load mock projects on API error', () => {
    fixture.detectChanges();
    expect(component.projects().length).toBe(3);
    expect(component.loading()).toBeFalse();
  });

  it('should include squadron-api in mock projects', () => {
    fixture.detectChanges();
    const names = component.projects().map((p) => p.name);
    expect(names).toContain('squadron-api');
    expect(names).toContain('squadron-ui');
    expect(names).toContain('squadron-infra');
  });

  it('should load real projects on API success', () => {
    const projects: Project[] = [
      {
        id: 'p1', tenantId: '1', name: 'my-project', description: 'test',
        repositoryUrl: 'https://github.com/org/repo', defaultBranch: 'main',
        taskCount: 10, activeTaskCount: 3, members: [], createdAt: new Date().toISOString(),
      } as Project,
    ];
    projectServiceSpy.getProjects.and.returnValue(of({
      content: projects, totalElements: 1, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();

    expect(component.projects().length).toBe(1);
    expect(component.projects()[0].name).toBe('my-project');
  });

  it('should set loading to false after load', () => {
    fixture.detectChanges();
    expect(component.loading()).toBeFalse();
  });

  it('should render project cards', () => {
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const cards = el.querySelectorAll('.project-card');
    expect(cards.length).toBe(3);
  });

  it('should display task counts in mock projects', () => {
    fixture.detectChanges();
    const api = component.projects().find((p) => p.name === 'squadron-api');
    expect(api!.taskCount).toBe(24);
    expect(api!.activeTaskCount).toBe(8);
  });
});
