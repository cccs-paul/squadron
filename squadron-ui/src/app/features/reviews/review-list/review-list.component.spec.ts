import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReviewListComponent } from './review-list.component';
import { ReviewService } from '../../../core/services/review.service';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { ReviewStatus } from '../../../core/models/review.model';

describe('ReviewListComponent', () => {
  let component: ReviewListComponent;
  let fixture: ComponentFixture<ReviewListComponent>;
  let reviewServiceSpy: jasmine.SpyObj<ReviewService>;

  beforeEach(async () => {
    reviewServiceSpy = jasmine.createSpyObj('ReviewService', ['getReviews']);
    reviewServiceSpy.getReviews.and.returnValue(throwError(() => new Error('api down')));

    await TestBed.configureTestingModule({
      imports: [ReviewListComponent],
      providers: [
        { provide: ReviewService, useValue: reviewServiceSpy },
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ReviewListComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should show empty state on API error', () => {
    fixture.detectChanges();
    expect(component.reviews().length).toBe(0);
    expect(component.loading()).toBeFalse();
  });

  it('should have no reviews on API error', () => {
    fixture.detectChanges();
    const pending = component.reviews().find((r) => r.status === ReviewStatus.PENDING);
    expect(pending).toBeUndefined();
  });

  it('should load real reviews on API success', () => {
    reviewServiceSpy.getReviews.and.returnValue(of({
      content: [{ id: 'r1', status: ReviewStatus.APPROVED, taskTitle: 'Test', pullRequestNumber: 99 } as any],
      totalElements: 1, totalPages: 1, page: 0, size: 20,
    }));
    fixture.detectChanges();
    expect(component.reviews().length).toBe(1);
    expect(component.reviews()[0].pullRequestNumber).toBe(99);
  });

  it('should call loadReviews with filter status', () => {
    fixture.detectChanges();
    component.filterStatus = 'APPROVED';
    component.loadReviews();
    expect(reviewServiceSpy.getReviews).toHaveBeenCalledWith(ReviewStatus.APPROVED);
  });

  it('should call loadReviews without status when filter is empty', () => {
    fixture.detectChanges();
    component.filterStatus = '';
    component.loadReviews();
    expect(reviewServiceSpy.getReviews).toHaveBeenCalledWith(undefined);
  });

  it('should return correct statusClass for each status', () => {
    expect(component.statusClass(ReviewStatus.APPROVED)).toBe('success');
    expect(component.statusClass(ReviewStatus.CHANGES_REQUESTED)).toBe('warning');
    expect(component.statusClass(ReviewStatus.REJECTED)).toBe('error');
    expect(component.statusClass(ReviewStatus.IN_PROGRESS)).toBe('primary');
    expect(component.statusClass(ReviewStatus.PENDING)).toBe('neutral');
  });

  it('should render review table rows', () => {
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const rows = el.querySelectorAll('tbody tr');
    expect(rows.length).toBe(0);
  });
});
