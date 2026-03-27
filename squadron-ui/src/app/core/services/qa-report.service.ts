import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { QAReport, QAGateResult } from '../models/qa-report.model';

@Injectable({ providedIn: 'root' })
export class QAReportService extends ApiService {
  getReports(taskId: string): Observable<QAReport[]> {
    return this.get<QAReport[]>('/qa-reports', { taskId });
  }

  getLatestReport(taskId: string): Observable<QAReport> {
    return this.get<QAReport>('/qa-reports/latest', { taskId });
  }

  checkGate(taskId: string): Observable<QAGateResult> {
    return this.get<QAGateResult>('/qa-reports/gate', { taskId });
  }

  createReport(report: Partial<QAReport>): Observable<QAReport> {
    return this.post<QAReport>('/qa-reports', report);
  }
}
