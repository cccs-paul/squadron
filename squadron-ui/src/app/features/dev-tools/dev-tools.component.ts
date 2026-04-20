import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { DatePipe, KeyValuePipe, LowerCasePipe } from '@angular/common';
import { AuthService } from '../../core/auth/auth.service';
import { environment } from '../../../environments/environment';

interface ServiceHealth {
  status: string;
}

interface HealthResponse {
  status: string;
  timestamp: string;
  services: Record<string, ServiceHealth>;
  infrastructure: Record<string, ServiceHealth>;
}

interface ServiceInfo {
  name: string;
  port: number;
  description: string;
  swaggerUrl: string;
  actuatorUrl: string;
  status: string;
}

interface InfraInfo {
  name: string;
  description: string;
  status: string;
  defaultPort: number;
}

@Component({
  selector: 'sq-dev-tools',
  standalone: true,
  imports: [TranslateModule, DatePipe, KeyValuePipe, LowerCasePipe],
  templateUrl: './dev-tools.component.html',
  styleUrl: './dev-tools.component.scss',
})
export class DevToolsComponent implements OnInit {
  private http = inject(HttpClient);
  private authService = inject(AuthService);

  loading = signal(true);
  healthData = signal<HealthResponse | null>(null);
  lastRefreshed = signal<Date | null>(null);
  expandedSection = signal<string>('services');

  readonly user = this.authService.user;
  readonly isAdmin = this.authService.isAdmin;

  /** Base URL of the API gateway (for Swagger/Actuator links) */
  readonly gatewayBaseUrl = this.resolveGatewayBaseUrl();

  readonly services: ServiceInfo[] = [
    { name: 'Gateway', port: 8443, description: 'API Gateway (Spring Cloud Gateway) - routes, rate limiting, SSL termination', swaggerUrl: '/swagger-ui.html', actuatorUrl: '/actuator/health', status: '' },
    { name: 'Identity', port: 8081, description: 'Tenant & user management, authentication, Keycloak integration', swaggerUrl: '/swagger-ui.html', actuatorUrl: '/actuator/health', status: '' },
    { name: 'Config', port: 8082, description: 'Centralized hierarchical configuration (tenant > team > user)', swaggerUrl: '/swagger-ui.html', actuatorUrl: '/actuator/health', status: '' },
    { name: 'Orchestrator', port: 8083, description: 'Task workflow engine, PostgreSQL state machine, ticketless tasks', swaggerUrl: '/swagger-ui.html', actuatorUrl: '/actuator/health', status: '' },
    { name: 'Platform', port: 8084, description: 'Ticketing platform adapters (JIRA, GitHub Issues, GitLab, Azure DevOps)', swaggerUrl: '/swagger-ui.html', actuatorUrl: '/actuator/health', status: '' },
    { name: 'Agent', port: 8085, description: 'AI agent service (Spring AI) - LLM orchestration, code generation', swaggerUrl: '/swagger-ui.html', actuatorUrl: '/actuator/health', status: '' },
    { name: 'Workspace', port: 8086, description: 'Sandboxed container management (Kubernetes / Docker)', swaggerUrl: '/swagger-ui.html', actuatorUrl: '/actuator/health', status: '' },
    { name: 'Git', port: 8087, description: 'Git operations, branch strategies, platform APIs (GitHub, GitLab, Bitbucket)', swaggerUrl: '/swagger-ui.html', actuatorUrl: '/actuator/health', status: '' },
    { name: 'Review', port: 8088, description: 'Code review orchestration, diff analysis, review comments', swaggerUrl: '/swagger-ui.html', actuatorUrl: '/actuator/health', status: '' },
    { name: 'Notification', port: 8089, description: 'Event-driven notifications (email, Slack, Teams, in-app, webhooks)', swaggerUrl: '/swagger-ui.html', actuatorUrl: '/actuator/health', status: '' },
  ];

  readonly infrastructure: InfraInfo[] = [
    { name: 'PostgreSQL', description: 'Primary database (v17) with row-level multi-tenancy. Each service has its own database.', status: '', defaultPort: 5432 },
    { name: 'Redis', description: 'Cache and session store (v7.x). Used by the gateway for rate limiting and session management.', status: '', defaultPort: 6379 },
    { name: 'NATS', description: 'Message broker with JetStream for async inter-service events.', status: '', defaultPort: 4222 },
    { name: 'Keycloak', description: 'Identity provider (v26.x). Handles OIDC/SAML federation and external IdP integration.', status: '', defaultPort: 8080 },
  ];

  readonly apiEndpoints = [
    { method: 'POST', path: '/api/auth/login', description: 'Authenticate with username/password' },
    { method: 'POST', path: '/api/auth/oidc/{provider}/authorize', description: 'Initiate OIDC login flow' },
    { method: 'GET', path: '/api/health/status', description: 'Aggregated health status (no auth required)' },
    { method: 'GET', path: '/api/projects', description: 'List all projects for the tenant' },
    { method: 'POST', path: '/api/tasks/ticketless', description: 'Create a ticketless task' },
    { method: 'GET', path: '/api/tasks', description: 'List tasks (filterable by state, project)' },
    { method: 'GET', path: '/api/tasks/{id}', description: 'Get task details' },
    { method: 'POST', path: '/api/agent/{taskId}/start', description: 'Start an agent session for a task' },
    { method: 'GET', path: '/api/agent/{taskId}/conversation', description: 'Get agent conversation messages' },
    { method: 'GET', path: '/api/reviews', description: 'List code reviews' },
    { method: 'GET', path: '/api/platforms/connections', description: 'List configured platform connections' },
    { method: 'GET', path: '/api/config/tenant', description: 'Get tenant configuration' },
    { method: 'GET', path: '/api/users/{id}/preferences', description: 'Get user preferences' },
    { method: 'PATCH', path: '/api/users/{id}/preferences', description: 'Update user preferences' },
    { method: 'GET', path: '/api/notifications', description: 'List notifications for the user' },
    { method: 'GET', path: '/api/workspaces', description: 'List workspaces' },
    { method: 'GET', path: '/api/git/branches/{projectId}', description: 'List branches for a project' },
  ];

  readonly techStack = [
    { category: 'Backend', items: 'Spring Boot 3.5.3, Java 21 LTS, Spring AI 1.0.0' },
    { category: 'Frontend', items: 'Angular 21.x, TypeScript, SCSS' },
    { category: 'Database', items: 'PostgreSQL 17 + PgBouncer + HikariCP, Flyway migrations' },
    { category: 'Cache', items: 'Redis 7.x' },
    { category: 'Messaging', items: 'NATS with JetStream' },
    { category: 'Identity', items: 'Keycloak 26.x (OIDC/SAML)' },
    { category: 'Monitoring', items: 'Prometheus + Grafana' },
    { category: 'API Docs', items: 'Springdoc OpenAPI 2.8.6 (Swagger UI)' },
    { category: 'Containers', items: 'Kubernetes (preferred) / Docker (fallback)' },
    { category: 'Certificates', items: 'HashiCorp Vault + cert-manager' },
    { category: 'Build', items: 'Maven 3.9.x' },
    { category: 'Real-time', items: 'STOMP over WebSocket' },
  ];

  ngOnInit(): void {
    this.refreshHealth();
  }

  refreshHealth(): void {
    this.loading.set(true);
    this.http.get<HealthResponse>('/api/health/status').subscribe({
      next: (data) => {
        this.healthData.set(data);
        this.lastRefreshed.set(new Date());
        this.loading.set(false);
      },
      error: () => {
        this.healthData.set(null);
        this.lastRefreshed.set(new Date());
        this.loading.set(false);
      },
    });
  }

  toggleSection(section: string): void {
    this.expandedSection.set(this.expandedSection() === section ? '' : section);
  }

  getServiceStatus(serviceName: string): string {
    const data = this.healthData();
    if (!data?.services) return 'UNKNOWN';
    const key = serviceName.toLowerCase();
    return data.services[key]?.status ?? 'UNKNOWN';
  }

  getInfraStatus(infraName: string): string {
    const data = this.healthData();
    if (!data?.infrastructure) return 'UNKNOWN';
    const key = infraName.toLowerCase();
    return data.infrastructure[key]?.status ?? 'UNKNOWN';
  }

  statusClass(status: string): string {
    switch (status) {
      case 'UP': return 'status--up';
      case 'DOWN': return 'status--down';
      case 'DEGRADED': return 'status--degraded';
      default: return 'status--unknown';
    }
  }

  methodClass(method: string): string {
    switch (method) {
      case 'GET': return 'method--get';
      case 'POST': return 'method--post';
      case 'PUT': return 'method--put';
      case 'PATCH': return 'method--patch';
      case 'DELETE': return 'method--delete';
      default: return '';
    }
  }

  openSwagger(port: number): void {
    // In production, the gateway serves Swagger; in dev, go directly to the service port
    if (environment.production) {
      window.open(`${this.gatewayBaseUrl}/swagger-ui.html`, '_blank');
    } else {
      window.open(`http://localhost:${port}/swagger-ui.html`, '_blank');
    }
  }

  openActuator(port: number): void {
    if (environment.production) {
      window.open(`${this.gatewayBaseUrl}/actuator/health`, '_blank');
    } else {
      window.open(`http://localhost:${port}/actuator/health`, '_blank');
    }
  }

  copyToClipboard(text: string): void {
    navigator.clipboard.writeText(text);
  }

  private resolveGatewayBaseUrl(): string {
    const apiUrl = environment.apiUrl;
    // apiUrl is either '/api' (prod, same origin) or 'http://localhost:8443/api' (dev)
    if (apiUrl.startsWith('http')) {
      return apiUrl.replace(/\/api$/, '');
    }
    // Same-origin: gateway is the current host
    return window.location.origin;
  }
}
