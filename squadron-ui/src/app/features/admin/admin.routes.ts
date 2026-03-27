import { Routes } from '@angular/router';

export const adminRoutes: Routes = [
  { path: '', redirectTo: 'users', pathMatch: 'full' },
  {
    path: 'users',
    loadComponent: () =>
      import('./users/user-management.component').then((m) => m.UserManagementComponent),
  },
  {
    path: 'teams',
    loadComponent: () =>
      import('./teams/team-management.component').then((m) => m.TeamManagementComponent),
  },
  {
    path: 'security-groups',
    loadComponent: () =>
      import('./security-groups/security-group-management.component').then(
        (m) => m.SecurityGroupManagementComponent,
      ),
  },
  {
    path: 'permissions',
    loadComponent: () =>
      import('./permissions/permission-management.component').then(
        (m) => m.PermissionManagementComponent,
      ),
  },
  {
    path: 'auth-providers',
    loadComponent: () =>
      import('./auth-providers/auth-provider-config.component').then(
        (m) => m.AuthProviderConfigComponent,
      ),
  },
  {
    path: 'platforms',
    loadComponent: () =>
      import('./platform-connections/platform-connections.component').then(
        (m) => m.PlatformConnectionsComponent,
      ),
  },
  {
    path: 'usage',
    loadComponent: () =>
      import('./usage-dashboard/usage-dashboard.component').then(
        (m) => m.UsageDashboardComponent,
      ),
  },
];
