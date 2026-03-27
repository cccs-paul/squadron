import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, catchError, map } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface HealthStatus {
  status: string;
  timestamp: string;
  services: Record<string, { status: string; url?: string }>;
  infrastructure: Record<string, { status: string }>;
}

@Injectable({ providedIn: 'root' })
export class HealthService {
  constructor(private http: HttpClient) {}

  getHealthStatus(): Observable<HealthStatus> {
    return this.http.get<HealthStatus>(`${environment.apiUrl}/health/status`).pipe(
      catchError(() => of({
        status: 'DOWN',
        timestamp: new Date().toISOString(),
        services: { gateway: { status: 'DOWN' } },
        infrastructure: {}
      }))
    );
  }
}
