import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ReviewService } from './review.service';
import { environment } from '../../../environments/environment';
import { ReviewStatus } from '../models/review.model';

describe('ReviewService', () => {
  let service: ReviewService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(ReviewService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  // --- getReviews ---

  it('should_getReviews_when_calledWithDefaults', () => {
    service.getReviews().subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/reviews` &&
      r.params.get('page') === '0' &&
      r.params.get('size') === '20' &&
      !r.params.has('status')
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 });
  });

  it('should_getReviews_when_calledWithStatusFilter', () => {
    service.getReviews('PENDING' as any, 1, 10).subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/reviews` &&
      r.params.get('status') === 'PENDING' &&
      r.params.get('page') === '1' &&
      r.params.get('size') === '10'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 1, size: 10 });
  });

  // --- getReview ---

  it('should_getReview_when_calledWithId', () => {
    const mockReview = { id: 'rev1', status: 'PENDING' };

    service.getReview('rev1').subscribe((review) => {
      expect(review).toEqual(mockReview as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/reviews/rev1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockReview);
  });

  // --- getReviewsForTask ---

  it('should_getReviewsForTask_when_calledWithTaskId', () => {
    const mockReviews = [{ id: 'rev1' }, { id: 'rev2' }];

    service.getReviewsForTask('task1').subscribe((reviews) => {
      expect(reviews.length).toBe(2);
    });

    const req = httpTesting.expectOne(`${apiUrl}/reviews/task/task1`);
    expect(req.request.method).toBe('GET');
    req.flush({ data: mockReviews, success: true });
  });

  // --- deleteReview ---

  it('should_deleteReview_when_calledWithId', () => {
    service.deleteReview('rev1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/reviews/rev1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  // --- approveReview ---

  it('should_approveReview_when_calledWithIdAndComment', () => {
    const mockResponse = { id: 'rev1', status: 'APPROVED' };

    service.approveReview('rev1', 'Looks good!').subscribe((review) => {
      expect(review).toEqual(mockResponse as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/reviews/rev1/approve`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ comment: 'Looks good!' });
    req.flush(mockResponse);
  });

  it('should_approveReview_when_calledWithoutComment', () => {
    service.approveReview('rev1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/reviews/rev1/approve`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ comment: undefined });
    req.flush({ id: 'rev1', status: 'APPROVED' });
  });

  // --- rejectReview ---

  it('should_rejectReview_when_calledWithIdAndReason', () => {
    service.rejectReview('rev1', 'Needs more work').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/reviews/rev1/reject`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Needs more work' });
    req.flush({ id: 'rev1', status: 'REJECTED' });
  });

  // --- requestChanges ---

  it('should_requestChanges_when_calledWithIdAndComments', () => {
    service.requestChanges('rev1', 'Please fix the formatting').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/reviews/rev1/request-changes`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ comments: 'Please fix the formatting' });
    req.flush({ id: 'rev1', status: 'CHANGES_REQUESTED' });
  });

  // --- submitReview ---

  it('should_submitReview_when_calledWithRequest', () => {
    const request = { reviewId: 'rev1', status: ReviewStatus.APPROVED, summary: 'LGTM' };

    service.submitReview(request).subscribe((review) => {
      expect(review).toEqual({ id: 'rev1', status: 'APPROVED' } as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/reviews/submit`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ data: { id: 'rev1', status: 'APPROVED' }, success: true });
  });

  // --- addComment ---

  it('should_addComment_when_calledWithReviewIdAndCommentData', () => {
    const comment = {
      filePath: 'src/main.ts',
      lineNumber: 42,
      body: 'This should use const',
      severity: 'WARNING',
      category: 'STYLE',
    };

    service.addComment('rev1', comment).subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/reviews/rev1/comments`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(comment);
    req.flush(null);
  });

  // --- resolveComment ---

  it('should_resolveComment_when_calledWithReviewIdAndCommentId', () => {
    service.resolveComment('rev1', 'cmt1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/reviews/rev1/comments/cmt1/resolve`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(null);
  });

  // --- checkReviewGate ---

  it('should_checkReviewGate_when_calledWithTaskIdAndTenantId', () => {
    const gate = { taskId: 'task1', totalReviews: 2, humanApprovals: 1, aiApproval: true, policyMet: true, reviews: [] };

    service.checkReviewGate('task1', 'tenant1').subscribe((result) => {
      expect(result.policyMet).toBeTrue();
    });

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/reviews/task/task1/gate` &&
      r.params.get('tenantId') === 'tenant1' &&
      !r.params.has('teamId')
    );
    expect(req.request.method).toBe('GET');
    req.flush({ data: gate, success: true });
  });

  it('should_checkReviewGate_when_calledWithTeamId', () => {
    const gate = { taskId: 'task1', totalReviews: 2, humanApprovals: 1, aiApproval: true, policyMet: false, reviews: [] };

    service.checkReviewGate('task1', 'tenant1', 'team1').subscribe((result) => {
      expect(result.policyMet).toBeFalse();
    });

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/reviews/task/task1/gate` &&
      r.params.get('tenantId') === 'tenant1' &&
      r.params.get('teamId') === 'team1'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ data: gate, success: true });
  });

  // --- orchestrateReview ---

  it('should_orchestrateReview_when_calledWithTaskIdAndTenantId', () => {
    const result = { taskId: 'task1', createdReviewIds: ['r1'], aiReviewCreated: true, pendingHumanReviews: 1, policyMet: false };

    service.orchestrateReview('task1', 'tenant1').subscribe((res) => {
      expect(res.createdReviewIds.length).toBe(1);
    });

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/reviews/orchestration/task/task1` &&
      r.params.get('tenantId') === 'tenant1' &&
      !r.params.has('teamId')
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeNull();
    req.flush({ data: result, success: true });
  });

  it('should_orchestrateReview_when_calledWithTeamId', () => {
    const result = { taskId: 'task1', createdReviewIds: ['r1', 'r2'], aiReviewCreated: true, pendingHumanReviews: 2, policyMet: false };

    service.orchestrateReview('task1', 'tenant1', 'team1').subscribe((res) => {
      expect(res.pendingHumanReviews).toBe(2);
    });

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/reviews/orchestration/task/task1` &&
      r.params.get('tenantId') === 'tenant1' &&
      r.params.get('teamId') === 'team1'
    );
    expect(req.request.method).toBe('POST');
    req.flush({ data: result, success: true });
  });

  // --- checkAndTransition ---

  it('should_checkAndTransition_when_calledWithTaskIdAndTenantId', () => {
    service.checkAndTransition('task1', 'tenant1').subscribe((result) => {
      expect(result).toBeTrue();
    });

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/reviews/orchestration/task/task1/check` &&
      r.params.get('tenantId') === 'tenant1' &&
      !r.params.has('teamId')
    );
    expect(req.request.method).toBe('GET');
    req.flush({ data: true, success: true });
  });

  it('should_checkAndTransition_when_calledWithTeamId', () => {
    service.checkAndTransition('task1', 'tenant1', 'team1').subscribe((result) => {
      expect(result).toBeFalse();
    });

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/reviews/orchestration/task/task1/check` &&
      r.params.get('tenantId') === 'tenant1' &&
      r.params.get('teamId') === 'team1'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ data: false, success: true });
  });

  // --- getQAReports ---

  it('should_getQAReports_when_calledWithTaskId', () => {
    const reports = [{ id: 'qa1', verdict: 'PASS' }, { id: 'qa2', verdict: 'FAIL' }];

    service.getQAReports('task1').subscribe((result) => {
      expect(result.length).toBe(2);
    });

    const req = httpTesting.expectOne(`${apiUrl}/qa-reports/task/task1`);
    expect(req.request.method).toBe('GET');
    req.flush({ data: reports, success: true });
  });

  // --- getLatestQAReport ---

  it('should_getLatestQAReport_when_calledWithTaskId', () => {
    const report = { id: 'qa1', verdict: 'PASS', lineCoverage: 0.85 };

    service.getLatestQAReport('task1').subscribe((result) => {
      expect(result.verdict).toBe('PASS' as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/qa-reports/task/task1/latest`);
    expect(req.request.method).toBe('GET');
    req.flush({ data: report, success: true });
  });

  // --- checkQAGate ---

  it('should_checkQAGate_when_calledWithTaskId', () => {
    service.checkQAGate('task1').subscribe((result) => {
      expect(result).toBeTrue();
    });

    const req = httpTesting.expectOne(`${apiUrl}/qa-reports/task/task1/gate`);
    expect(req.request.method).toBe('GET');
    req.flush({ data: true, success: true });
  });

  it('should_checkQAGateFalse_when_gateNotPassed', () => {
    service.checkQAGate('task1').subscribe((result) => {
      expect(result).toBeFalse();
    });

    const req = httpTesting.expectOne(`${apiUrl}/qa-reports/task/task1/gate`);
    expect(req.request.method).toBe('GET');
    req.flush({ data: false, success: true });
  });
});
