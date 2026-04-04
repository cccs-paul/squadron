import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ProjectDetailComponent } from './project-detail.component';
import { ProjectService } from '../../../core/services/project.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Project } from '../../../core/models/project.model';

describe('ProjectDetailComponent', () => {
  let component: ProjectDetailComponent;
  let fixture: ComponentFixture<ProjectDetailComponent>;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;

  const mockProject: Project = {
    id: 'p1',
    tenantId: '1',
    name: 'squadron-api',
    description: 'Main backend API service',
    repositoryUrl: 'https://github.com/org/squadron-api',
    defaultBranch: 'main',
    taskCount: 24,
    activeTaskCount: 8,
    members: [],
    createdAt: new Date().toISOString(),
  } as Project;

  beforeEach(async () => {
    projectServiceSpy = jasmine.createSpyObj('ProjectService', ['getProject']);

    await TestBed.configureTestingModule({
      imports: [ProjectDetailComponent],
      providers: [
        { provide: ProjectService, useValue: projectServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({ id: 'p1' }) },
          },
        },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(ProjectDetailComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    projectServiceSpy.getProject.and.returnValue(of(mockProject));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should load project from service', () => {
    projectServiceSpy.getProject.and.returnValue(of(mockProject));
    fixture.detectChanges();
    expect(projectServiceSpy.getProject).toHaveBeenCalledWith('p1');
    expect(component.project()!.name).toBe('squadron-api');
  });

  it('should show empty state on error', () => {
    projectServiceSpy.getProject.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.project()).toBeNull();
  });

  it('should render project name', () => {
    projectServiceSpy.getProject.and.returnValue(of(mockProject));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('squadron-api');
  });

  it('should render repository URL', () => {
    projectServiceSpy.getProject.and.returnValue(of(mockProject));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('https://github.com/org/squadron-api');
  });

  it('should render default branch', () => {
    projectServiceSpy.getProject.and.returnValue(of(mockProject));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('main');
  });

  it('should render back link', () => {
    projectServiceSpy.getProject.and.returnValue(of(mockProject));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.back-link')).toBeTruthy();
    expect(el.textContent).toContain('Back to Projects');
  });
});
