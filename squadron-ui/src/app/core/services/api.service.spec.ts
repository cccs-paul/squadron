import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ApiService, PageResponse } from './api.service';
import { environment } from '../../../environments/environment';

// ApiService has protected methods, so we create a test subclass to expose them.
class TestApiService extends ApiService {
  public testGet<T>(path: string, params?: Record<string, string | number | boolean>) {
    return this.get<T>(path, params);
  }
  public testPost<T>(path: string, body: unknown) {
    return this.post<T>(path, body);
  }
  public testPut<T>(path: string, body: unknown) {
    return this.put<T>(path, body);
  }
  public testPatch<T>(path: string, body: unknown) {
    return this.patch<T>(path, body);
  }
  public testDelete<T>(path: string) {
    return this.delete<T>(path);
  }
}

describe('ApiService', () => {
  let service: TestApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        TestApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(TestApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_haveCorrectBaseUrl', () => {
    expect((service as any).baseUrl).toBe('http://localhost:8080/api');
  });

  it('should_makeGetRequest_when_calledWithPath', () => {
    const mockData = { id: '1', name: 'test' };

    service.testGet<{ id: string; name: string }>('/test').subscribe((data) => {
      expect(data).toEqual(mockData);
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/test`);
    expect(req.request.method).toBe('GET');
    req.flush(mockData);
  });

  it('should_appendQueryParams_when_paramsProvided', () => {
    service.testGet('/test', { page: 0, size: 20, active: true }).subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${environment.apiUrl}/test` &&
      r.params.get('page') === '0' &&
      r.params.get('size') === '20' &&
      r.params.get('active') === 'true'
    );
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('should_skipNullAndUndefinedParams_when_paramsContainNullish', () => {
    service.testGet('/test', { page: 0, size: undefined as any, filter: null as any }).subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${environment.apiUrl}/test` &&
      r.params.get('page') === '0' &&
      !r.params.has('size') &&
      !r.params.has('filter')
    );
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('should_makePostRequest_when_calledWithPathAndBody', () => {
    const body = { name: 'new item' };
    const mockResponse = { id: '1', name: 'new item' };

    service.testPost('/test', body).subscribe((data) => {
      expect(data).toEqual(mockResponse);
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/test`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(mockResponse);
  });

  it('should_makePutRequest_when_calledWithPathAndBody', () => {
    const body = { name: 'updated' };
    const mockResponse = { id: '1', name: 'updated' };

    service.testPut('/test/1', body).subscribe((data) => {
      expect(data).toEqual(mockResponse);
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/test/1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    req.flush(mockResponse);
  });

  it('should_makePatchRequest_when_calledWithPathAndBody', () => {
    const body = { name: 'patched' };
    const mockResponse = { id: '1', name: 'patched' };

    service.testPatch('/test/1', body).subscribe((data) => {
      expect(data).toEqual(mockResponse);
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/test/1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(body);
    req.flush(mockResponse);
  });

  it('should_makeDeleteRequest_when_calledWithPath', () => {
    service.testDelete('/test/1').subscribe();

    const req = httpTesting.expectOne(`${environment.apiUrl}/test/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
