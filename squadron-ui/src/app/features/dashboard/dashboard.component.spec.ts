import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DashboardComponent } from './dashboard.component';
import { TaskService } from '../../core/services/task.service';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { TaskState } from '../../core/models/task.model';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let taskServiceSpy: jasmine.SpyObj<TaskService>;

  beforeEach(async () => {
    taskServiceSpy = jasmine.createSpyObj('TaskService', ['getTaskStats']);
    taskServiceSpy.getTaskStats.and.returnValue(throwError(() => new Error('api down')));

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: TaskService, useValue: taskServiceSpy },
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should load mock data on API error', () => {
    fixture.detectChanges();
    expect(component.loading()).toBeFalse();
    expect(component.stats()[0].value).toBe(47);
    expect(component.stats()[1].value).toBe(12);
    expect(component.stats()[2].value).toBe(8);
    expect(component.stats()[3].value).toBe(19);
  });

  it('should populate state distribution on API error', () => {
    fixture.detectChanges();
    expect(component.stateDistribution().length).toBeGreaterThan(0);
    const done = component.stateDistribution().find((d) => d.state === 'DONE');
    expect(done).toBeTruthy();
    expect(done!.count).toBe(19);
  });

  it('should populate recent activity on API error', () => {
    fixture.detectChanges();
    expect(component.recentActivity().length).toBe(5);
    expect(component.recentActivity()[0].title).toBe('Implement user auth flow');
  });

  it('should load real data on successful API response', () => {
    const statsData = {
      total: 100,
      byState: {
        [TaskState.BACKLOG]: 10,
        [TaskState.PLANNING]: 15,
        [TaskState.IN_PROGRESS]: 30,
        [TaskState.REVIEW]: 20,
        [TaskState.QA]: 5,
        [TaskState.DONE]: 20,
      },
      byPriority: {},
    };
    taskServiceSpy.getTaskStats.and.returnValue(of(statsData));
    fixture.detectChanges();

    expect(component.stats()[0].value).toBe(100);
    expect(component.stats()[1].value).toBe(30);
    expect(component.stats()[2].value).toBe(20);
    expect(component.stats()[3].value).toBe(20);
    expect(component.loading()).toBeFalse();
  });

  it('should compute state distribution from real data', () => {
    const statsData = {
      total: 50,
      byState: {
        [TaskState.BACKLOG]: 0,
        [TaskState.PLANNING]: 0,
        [TaskState.IN_PROGRESS]: 25,
        [TaskState.REVIEW]: 0,
        [TaskState.QA]: 0,
        [TaskState.DONE]: 25,
      } as Record<TaskState, number>,
      byPriority: {} as Record<string, number>,
    };
    taskServiceSpy.getTaskStats.and.returnValue(of(statsData));
    fixture.detectChanges();

    const inProgress = component.stateDistribution().find((d) => d.state === 'IN PROGRESS');
    expect(inProgress).toBeTruthy();
    expect(inProgress!.percentage).toBe(50);
  });

  it('should render stat cards in the template', () => {
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const statCards = el.querySelectorAll('.stat-card');
    expect(statCards.length).toBe(4);
  });

  it('should have correct stateColors mapping', () => {
    expect(component.stateColors['BACKLOG']).toBe('#9CA3AF');
    expect(component.stateColors['DONE']).toBe('#10B981');
    expect(component.stateColors['IN_PROGRESS']).toBe('#06B6D4');
  });
});
