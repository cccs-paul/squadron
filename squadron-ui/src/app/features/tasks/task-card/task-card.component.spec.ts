import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TaskCardComponent } from './task-card.component';
import { Task, TaskState, TaskPriority } from '../../../core/models/task.model';

describe('TaskCardComponent', () => {
  let component: TaskCardComponent;
  let fixture: ComponentFixture<TaskCardComponent>;

  const mockTask: Task = {
    id: '1',
    tenantId: 't1',
    projectId: 'p1',
    title: 'Test Task',
    description: 'A description',
    state: TaskState.IN_PROGRESS,
    priority: TaskPriority.HIGH,
    labels: ['bug', 'frontend'],
    tokenUsage: 100,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    assigneeName: 'John Doe',
    externalId: 'SQ-1',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TaskCardComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TaskCardComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('task', mockTask);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display the task title', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.task-card__title')!.textContent).toContain('Test Task');
  });

  it('should display external id when present', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.task-card__external-id')!.textContent).toContain('SQ-1');
  });

  it('should emit cardClick with task when card is clicked', () => {
    let emittedTask: Task | undefined;
    component.cardClick.subscribe((t: Task) => (emittedTask = t));
    const cardEl = fixture.nativeElement.querySelector('.task-card') as HTMLElement;
    cardEl.click();
    expect(emittedTask).toEqual(mockTask);
  });

  it('should return "error" for CRITICAL priority', () => {
    expect(component.priorityClass(TaskPriority.CRITICAL)).toBe('error');
  });

  it('should return "warning" for HIGH priority', () => {
    expect(component.priorityClass(TaskPriority.HIGH)).toBe('warning');
  });

  it('should return "primary" for MEDIUM priority', () => {
    expect(component.priorityClass(TaskPriority.MEDIUM)).toBe('primary');
  });

  it('should return "neutral" for LOW priority', () => {
    expect(component.priorityClass(TaskPriority.LOW)).toBe('neutral');
  });

  it('should display labels', () => {
    const el = fixture.nativeElement as HTMLElement;
    const labels = el.querySelectorAll('.task-card__label');
    expect(labels.length).toBe(2);
    expect(labels[0].textContent).toContain('bug');
    expect(labels[1].textContent).toContain('frontend');
  });
});
