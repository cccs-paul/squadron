import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService, PageResponse } from './api.service';
import { User, Team } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService extends ApiService {
  getUsers(page = 0, size = 20, search?: string): Observable<PageResponse<User>> {
    return this.get<PageResponse<User>>('/users', { page, size, ...(search ? { search } : {}) });
  }

  getUser(id: string): Observable<User> {
    return this.get<User>(`/users/${id}`);
  }

  createUser(user: Partial<User>): Observable<User> {
    return this.post<User>('/users', user);
  }

  updateUser(id: string, user: Partial<User>): Observable<User> {
    return this.put<User>(`/users/${id}`, user);
  }

  deleteUser(id: string): Observable<void> {
    return this.delete<void>(`/users/${id}`);
  }

  getCurrentUser(): Observable<User> {
    return this.get<User>('/users/me');
  }

  getTeams(page = 0, size = 20): Observable<PageResponse<Team>> {
    return this.get<PageResponse<Team>>('/teams', { page, size });
  }

  getTeam(id: string): Observable<Team> {
    return this.get<Team>(`/teams/${id}`);
  }

  createTeam(team: Partial<Team>): Observable<Team> {
    return this.post<Team>('/teams', team);
  }

  updateTeam(id: string, team: Partial<Team>): Observable<Team> {
    return this.put<Team>(`/teams/${id}`, team);
  }

  deleteTeam(id: string): Observable<void> {
    return this.delete<void>(`/teams/${id}`);
  }

  addTeamMember(teamId: string, userId: string): Observable<void> {
    return this.post<void>(`/teams/${teamId}/members`, { userId });
  }

  removeTeamMember(teamId: string, userId: string): Observable<void> {
    return this.delete<void>(`/teams/${teamId}/members/${userId}`);
  }
}
