import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Tenant, TenantSettings } from '../models/tenant.model';

@Injectable({ providedIn: 'root' })
export class TenantService extends ApiService {
  getTenant(): Observable<Tenant> {
    return this.get<Tenant>('/tenants/current');
  }

  updateTenantSettings(settings: Partial<TenantSettings>): Observable<Tenant> {
    return this.patch<Tenant>('/tenants/current/settings', settings);
  }
}
