import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { PlatformService } from './platform.service';
import { environment } from '../../../environments/environment';

describe('PlatformService', () => {
  let service: PlatformService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(PlatformService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_getConnections_when_called', () => {
    const mockConnections = [{ id: 'c1', type: 'JIRA' }];

    service.getConnections().subscribe((connections) => {
      expect(connections).toEqual(mockConnections as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/connections`);
    expect(req.request.method).toBe('GET');
    req.flush(mockConnections);
  });

  it('should_getConnection_when_calledWithId', () => {
    const mockConnection = { id: 'c1', type: 'GITHUB' };

    service.getConnection('c1').subscribe((connection) => {
      expect(connection).toEqual(mockConnection as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/connections/c1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockConnection);
  });

  it('should_createConnection_when_calledWithData', () => {
    const connectionData = { type: 'JIRA', name: 'My JIRA' };
    const mockResponse = { id: 'c2', ...connectionData };

    service.createConnection(connectionData as any).subscribe((connection) => {
      expect(connection).toEqual(mockResponse as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/connections`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(connectionData);
    req.flush(mockResponse);
  });

  it('should_createConnectionFromRequest_when_calledWithRequest', () => {
    const request = {
      tenantId: 't1',
      name: 'My JIRA',
      platformType: 'JIRA',
      baseUrl: 'https://myorg.atlassian.net',
      authType: 'PAT',
      credentials: { pat: 'my-token' },
    };
    const mockConnection = { id: 'c3', tenantId: 't1', name: 'My JIRA', platformType: 'JIRA', baseUrl: 'https://myorg.atlassian.net' };

    service.createConnectionFromRequest(request).subscribe((connection) => {
      expect(connection).toEqual(mockConnection as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/connections`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ success: true, data: mockConnection, message: 'Created', timestamp: new Date().toISOString() });
  });

  it('should_updateConnection_when_calledWithIdAndData', () => {
    const updateData = { name: 'Updated JIRA' };
    const mockResponse = { id: 'c1', name: 'Updated JIRA' };

    service.updateConnection('c1', updateData as any).subscribe((connection) => {
      expect(connection).toEqual(mockResponse as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/connections/c1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(updateData);
    req.flush(mockResponse);
  });

  it('should_deleteConnection_when_calledWithId', () => {
    service.deleteConnection('c1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/platforms/connections/c1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should_testConnection_when_calledWithId', () => {
    const mockResult = { success: true, message: 'Connection successful' };

    service.testConnection('c1').subscribe((result) => {
      expect(result).toEqual(mockResult);
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/connections/c1/test`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(mockResult);
  });

  it('should_syncConnection_when_calledWithId', () => {
    service.syncConnection('c1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/platforms/connections/c1/sync`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(null);
  });

  it('should_getProjectStatuses_when_calledWithConnectionIdAndProjectKey', () => {
    const mockStatuses = ['To Do', 'In Progress', 'Done'];

    service.getProjectStatuses('c1', 'SQ').subscribe((statuses) => {
      expect(statuses).toEqual(mockStatuses);
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/connections/c1/statuses?projectKey=SQ`);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: mockStatuses, message: 'OK', timestamp: new Date().toISOString() });
  });
});
