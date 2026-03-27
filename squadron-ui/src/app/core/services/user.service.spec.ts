import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { UserService } from './user.service';
import { environment } from '../../../environments/environment';

describe('UserService', () => {
  let service: UserService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(UserService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_getUsers_when_calledWithDefaults', () => {
    const mockPage = { content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 };

    service.getUsers().subscribe((result) => {
      expect(result).toEqual(mockPage);
    });

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/users` &&
      r.params.get('page') === '0' &&
      r.params.get('size') === '20'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should_getUsers_when_calledWithCustomPageAndSearch', () => {
    service.getUsers(2, 10, 'john').subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/users` &&
      r.params.get('page') === '2' &&
      r.params.get('size') === '10' &&
      r.params.get('search') === 'john'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 2, size: 10 });
  });

  it('should_notIncludeSearchParam_when_searchUndefined', () => {
    service.getUsers(0, 20).subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/users` &&
      !r.params.has('search')
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 });
  });

  it('should_getUser_when_calledWithId', () => {
    const mockUser = { id: 'u1', name: 'John' };

    service.getUser('u1').subscribe((user) => {
      expect(user).toEqual(mockUser as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/users/u1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockUser);
  });

  it('should_createUser_when_calledWithUserData', () => {
    const userData = { name: 'Jane', email: 'jane@test.com' };
    const mockResponse = { id: 'u2', ...userData };

    service.createUser(userData as any).subscribe((user) => {
      expect(user).toEqual(mockResponse as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/users`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(userData);
    req.flush(mockResponse);
  });

  it('should_updateUser_when_calledWithIdAndData', () => {
    const updateData = { name: 'Jane Updated' };
    const mockResponse = { id: 'u1', name: 'Jane Updated' };

    service.updateUser('u1', updateData as any).subscribe((user) => {
      expect(user).toEqual(mockResponse as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/users/u1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(updateData);
    req.flush(mockResponse);
  });

  it('should_deleteUser_when_calledWithId', () => {
    service.deleteUser('u1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/users/u1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should_getCurrentUser_when_called', () => {
    const mockUser = { id: 'me', name: 'Current User' };

    service.getCurrentUser().subscribe((user) => {
      expect(user).toEqual(mockUser as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/users/me`);
    expect(req.request.method).toBe('GET');
    req.flush(mockUser);
  });

  it('should_getTeams_when_calledWithDefaults', () => {
    service.getTeams().subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/teams` &&
      r.params.get('page') === '0' &&
      r.params.get('size') === '20'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 });
  });

  it('should_getTeam_when_calledWithId', () => {
    const mockTeam = { id: 't1', name: 'Alpha' };

    service.getTeam('t1').subscribe((team) => {
      expect(team).toEqual(mockTeam as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/teams/t1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockTeam);
  });

  it('should_createTeam_when_calledWithTeamData', () => {
    const teamData = { name: 'Beta' };

    service.createTeam(teamData as any).subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/teams`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(teamData);
    req.flush({ id: 't2', ...teamData });
  });

  it('should_updateTeam_when_calledWithIdAndData', () => {
    const teamData = { name: 'Beta Updated' };

    service.updateTeam('t1', teamData as any).subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/teams/t1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(teamData);
    req.flush({ id: 't1', ...teamData });
  });

  it('should_deleteTeam_when_calledWithId', () => {
    service.deleteTeam('t1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/teams/t1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should_addTeamMember_when_calledWithTeamIdAndUserId', () => {
    service.addTeamMember('t1', 'u1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/teams/t1/members`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ userId: 'u1' });
    req.flush(null);
  });

  it('should_removeTeamMember_when_calledWithTeamIdAndUserId', () => {
    service.removeTeamMember('t1', 'u1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/teams/t1/members/u1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
