import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ReviewService } from './review.service';
import { environment } from '../../../environments/environment';

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

  it('should_getReview_when_calledWithId', () => {
    const mockReview = { id: 'rev1', status: 'PENDING' };

    service.getReview('rev1').subscribe((review) => {
      expect(review).toEqual(mockReview as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/reviews/rev1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockReview);
  });

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

  it('should_rejectReview_when_calledWithIdAndReason', () => {
    service.rejectReview('rev1', 'Needs more work').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/reviews/rev1/reject`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Needs more work' });
    req.flush({ id: 'rev1', status: 'REJECTED' });
  });

  it('should_requestChanges_when_calledWithIdAndComments', () => {
    service.requestChanges('rev1', 'Please fix the formatting').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/reviews/rev1/request-changes`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ comments: 'Please fix the formatting' });
    req.flush({ id: 'rev1', status: 'CHANGES_REQUESTED' });
  });

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

  it('should_resolveComment_when_calledWithReviewIdAndCommentId', () => {
    service.resolveComment('rev1', 'cmt1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/reviews/rev1/comments/cmt1/resolve`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(null);
  });
});
