import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReviewDetailComponent } from './review-detail.component';
import { ReviewService } from '../../../core/services/review.service';
import { DiffService } from '../../../core/services/diff.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { Review, ReviewStatus } from '../../../core/models/review.model';
import { DiffResult } from '../../../core/models/diff.model';

describe('ReviewDetailComponent', () => {
  let component: ReviewDetailComponent;
  let fixture: ComponentFixture<ReviewDetailComponent>;
  let reviewServiceSpy: jasmine.SpyObj<ReviewService>;
  let diffServiceSpy: jasmine.SpyObj<DiffService>;

  const mockDiffResult: DiffResult = {
    files: [{ filename: 'src/app.ts', status: 'modified', additions: 10, deletions: 3, patch: '@@ -1,5 +1,12 @@\n-old\n+new' }],
    totalAdditions: 10,
    totalDeletions: 3,
  };

  beforeEach(async () => {
    reviewServiceSpy = jasmine.createSpyObj('ReviewService', ['getReview', 'approveReview']);
    diffServiceSpy = jasmine.createSpyObj('DiffService', ['getTaskDiff', 'getCodeGenerationStatus', 'getPullRequestDiff']);
    diffServiceSpy.getTaskDiff.and.returnValue(of(mockDiffResult));

    await TestBed.configureTestingModule({
      imports: [ReviewDetailComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ReviewService, useValue: reviewServiceSpy },
        { provide: DiffService, useValue: diffServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({ id: 'r1' }) },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ReviewDetailComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should load review from service', () => {
    const mockReview = {
      id: 'r1', tenantId: '1', taskId: '7', taskTitle: 'RBAC', pullRequestUrl: 'url',
      pullRequestNumber: 35, repositoryName: 'org/repo', status: ReviewStatus.PENDING,
      severity: 'MAJOR', comments: [], filesChanged: 5, linesAdded: 100, linesRemoved: 10,
      reviewerType: 'AI', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
    } as any as Review;
    reviewServiceSpy.getReview.and.returnValue(of(mockReview));
    fixture.detectChanges();
    expect(component.review()!.taskTitle).toBe('RBAC');
    expect(component.loading()).toBeFalse();
  });

  it('should show empty state on service error', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.review()).toBeNull();
    expect(component.loading()).toBeFalse();
  });

  it('should approve review via service', () => {
    const mockReview = {
      id: 'r1', tenantId: '1', taskId: '7', taskTitle: 'RBAC', pullRequestUrl: 'url',
      pullRequestNumber: 35, repositoryName: 'org/repo', status: ReviewStatus.PENDING,
      severity: 'MAJOR', comments: [], filesChanged: 5, linesAdded: 100, linesRemoved: 10,
      reviewerType: 'AI', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
    } as any as Review;
    reviewServiceSpy.getReview.and.returnValue(of(mockReview));
    fixture.detectChanges();

    const approved = { ...component.review()!, status: ReviewStatus.APPROVED };
    reviewServiceSpy.approveReview.and.returnValue(of(approved));
    component.approveReview();
    expect(reviewServiceSpy.approveReview).toHaveBeenCalledWith('r1');
    expect(component.review()!.status).toBe(ReviewStatus.APPROVED);
  });

  it('should optimistically update on approve error', () => {
    const mockReview = {
      id: 'r1', tenantId: '1', taskId: '7', taskTitle: 'RBAC', pullRequestUrl: 'url',
      pullRequestNumber: 35, repositoryName: 'org/repo', status: ReviewStatus.PENDING,
      severity: 'MAJOR', comments: [], filesChanged: 5, linesAdded: 100, linesRemoved: 10,
      reviewerType: 'AI', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
    } as any as Review;
    reviewServiceSpy.getReview.and.returnValue(of(mockReview));
    fixture.detectChanges();

    reviewServiceSpy.approveReview.and.returnValue(throwError(() => new Error('fail')));
    component.approveReview();
    expect(component.review()!.status).toBe(ReviewStatus.APPROVED);
  });

  it('should reject review by updating status locally', () => {
    const mockReview = {
      id: 'r1', tenantId: '1', taskId: '7', taskTitle: 'RBAC', pullRequestUrl: 'url',
      pullRequestNumber: 35, repositoryName: 'org/repo', status: ReviewStatus.PENDING,
      severity: 'MAJOR', comments: [], filesChanged: 5, linesAdded: 100, linesRemoved: 10,
      reviewerType: 'AI', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
    } as any as Review;
    reviewServiceSpy.getReview.and.returnValue(of(mockReview));
    fixture.detectChanges();

    component.rejectReview();
    expect(component.review()!.status).toBe(ReviewStatus.REJECTED);
  });

  it('should return correct severityClass', () => {
    expect(component.severityClass('CRITICAL')).toBe('error');
    expect(component.severityClass('MAJOR')).toBe('warning');
    expect(component.severityClass('MINOR')).toBe('primary');
    expect(component.severityClass('INFO')).toBe('neutral');
  });

  it('should render review comments in comments tab', () => {
    const mockReview = {
      id: 'r1', tenantId: '1', taskId: '7', taskTitle: 'RBAC', pullRequestUrl: 'url',
      pullRequestNumber: 35, repositoryName: 'org/repo', status: ReviewStatus.PENDING,
      severity: 'MAJOR', comments: [
        { id: 'c1', body: 'Fix this', resolved: false, filePath: 'a.ts', lineNumber: 1, severity: 'MAJOR', author: 'AI', createdAt: new Date().toISOString() },
        { id: 'c2', body: 'Improve naming', resolved: false, filePath: 'b.ts', lineNumber: 5, severity: 'MINOR', author: 'AI', createdAt: new Date().toISOString() },
        { id: 'c3', body: 'Good pattern', resolved: true, filePath: 'c.ts', lineNumber: 10, severity: 'INFO', author: 'AI', createdAt: new Date().toISOString() },
      ], filesChanged: 5, linesAdded: 100, linesRemoved: 10,
      reviewerType: 'AI', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
    } as any as Review;
    reviewServiceSpy.getReview.and.returnValue(of(mockReview));
    fixture.detectChanges();
    component.setTab('comments');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const comments = el.querySelectorAll('.comment');
    expect(comments.length).toBe(3);
  });

  it('should switch between tabs', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    expect(component.activeTab()).toBe('diff');
    expect(component.review()).toBeNull();

    component.setTab('comments');
    expect(component.activeTab()).toBe('comments');

    component.setTab('diff');
    expect(component.activeTab()).toBe('diff');
  });

  it('should show diff viewer in diff tab', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    component.setTab('diff');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const diffViewer = el.querySelector('sq-diff-viewer');
    expect(diffViewer).toBeTruthy();
  });

  it('should compute unresolved and resolved counts', () => {
    const mockReview = {
      id: 'r1', tenantId: '1', taskId: '7', taskTitle: 'RBAC', pullRequestUrl: 'url',
      pullRequestNumber: 35, repositoryName: 'org/repo', status: ReviewStatus.PENDING,
      severity: 'MAJOR', comments: [
        { id: 'c1', body: 'Fix this', resolved: false, filePath: 'a.ts', lineNumber: 1, severity: 'MAJOR', author: 'AI', createdAt: new Date().toISOString() },
        { id: 'c2', body: 'Improve naming', resolved: false, filePath: 'b.ts', lineNumber: 5, severity: 'MINOR', author: 'AI', createdAt: new Date().toISOString() },
        { id: 'c3', body: 'Good pattern', resolved: true, filePath: 'c.ts', lineNumber: 10, severity: 'INFO', author: 'AI', createdAt: new Date().toISOString() },
      ], filesChanged: 5, linesAdded: 100, linesRemoved: 10,
      reviewerType: 'AI', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
    } as any as Review;
    reviewServiceSpy.getReview.and.returnValue(of(mockReview));
    fixture.detectChanges();

    expect(component.unresolvedCount()).toBe(2);
    expect(component.resolvedCount()).toBe(1);
  });

  it('should render tabs', () => {
    reviewServiceSpy.getReview.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const tabs = el.querySelectorAll('.review-tab');
    expect(tabs.length).toBe(2);
  });
});
