import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService, PageResponse } from './api.service';
import { SecurityGroup, SecurityGroupMember } from '../models/security.model';

@Injectable({ providedIn: 'root' })
export class SecurityGroupService extends ApiService {
  getGroups(page = 0, size = 20): Observable<PageResponse<SecurityGroup>> {
    return this.get<PageResponse<SecurityGroup>>('/security-groups', { page, size });
  }

  getGroup(id: string): Observable<SecurityGroup> {
    return this.get<SecurityGroup>(`/security-groups/${id}`);
  }

  createGroup(group: Partial<SecurityGroup>): Observable<SecurityGroup> {
    return this.post<SecurityGroup>('/security-groups', group);
  }

  updateGroup(id: string, group: Partial<SecurityGroup>): Observable<SecurityGroup> {
    return this.put<SecurityGroup>(`/security-groups/${id}`, group);
  }

  deleteGroup(id: string): Observable<void> {
    return this.delete<void>(`/security-groups/${id}`);
  }

  addMember(groupId: string, member: Partial<SecurityGroupMember>): Observable<void> {
    return this.post<void>(`/security-groups/${groupId}/members`, member);
  }

  removeMember(groupId: string, memberId: string): Observable<void> {
    return this.delete<void>(`/security-groups/${groupId}/members/${memberId}`);
  }
}
