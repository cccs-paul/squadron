import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { SecurityGroupService } from './security-group.service';
import { environment } from '../../../environments/environment';

describe('SecurityGroupService', () => {
  let service: SecurityGroupService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(SecurityGroupService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_getGroups_when_calledWithDefaults', () => {
    service.getGroups().subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/security-groups` &&
      r.params.get('page') === '0' &&
      r.params.get('size') === '20'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 });
  });

  it('should_getGroups_when_calledWithCustomPageAndSize', () => {
    service.getGroups(3, 50).subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/security-groups` &&
      r.params.get('page') === '3' &&
      r.params.get('size') === '50'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 3, size: 50 });
  });

  it('should_getGroup_when_calledWithId', () => {
    const mockGroup = { id: 'sg1', name: 'Admins' };

    service.getGroup('sg1').subscribe((group) => {
      expect(group).toEqual(mockGroup as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/security-groups/sg1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockGroup);
  });

  it('should_createGroup_when_calledWithGroupData', () => {
    const groupData = { name: 'Developers', description: 'Dev team' };
    const mockResponse = { id: 'sg2', ...groupData };

    service.createGroup(groupData as any).subscribe((group) => {
      expect(group).toEqual(mockResponse as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/security-groups`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(groupData);
    req.flush(mockResponse);
  });

  it('should_updateGroup_when_calledWithIdAndData', () => {
    const updateData = { name: 'Senior Developers' };
    const mockResponse = { id: 'sg1', name: 'Senior Developers' };

    service.updateGroup('sg1', updateData as any).subscribe((group) => {
      expect(group).toEqual(mockResponse as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/security-groups/sg1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(updateData);
    req.flush(mockResponse);
  });

  it('should_deleteGroup_when_calledWithId', () => {
    service.deleteGroup('sg1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/security-groups/sg1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should_addMember_when_calledWithGroupIdAndMemberData', () => {
    const memberData = { userId: 'u1', role: 'MEMBER' };

    service.addMember('sg1', memberData as any).subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/security-groups/sg1/members`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(memberData);
    req.flush(null);
  });

  it('should_removeMember_when_calledWithGroupIdAndMemberId', () => {
    service.removeMember('sg1', 'mem1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/security-groups/sg1/members/mem1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
