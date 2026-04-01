import { Routes } from '@angular/router';
import { authGuard, adminGuard } from './core/auth/auth.guard';
import { MainLayoutComponent } from './layout/main-layout/main-layout.component';
import { AuthLayoutComponent } from './layout/auth-layout/auth-layout.component';

export const routes: Routes = [
  {
    path: '',
    component: AuthLayoutComponent,
    children: [
      { path: '', redirectTo: 'login', pathMatch: 'full' },
      {
        path: 'login',
        loadComponent: () =>
          import('./features/auth/login/login.component').then((m) => m.LoginComponent),
      },
      {
        path: 'auth/callback',
        loadComponent: () =>
          import('./features/auth/oidc-callback/oidc-callback.component').then(
            (m) => m.OidcCallbackComponent,
          ),
      },
    ],
  },
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'tasks',
        loadComponent: () =>
          import('./features/tasks/task-board/task-board.component').then(
            (m) => m.TaskBoardComponent,
          ),
      },
      {
        path: 'tasks/:taskId/diff',
        loadComponent: () =>
          import('./features/diff-viewer/diff-viewer.component').then(
            (m) => m.DiffViewerComponent,
          ),
      },
      {
        path: 'tasks/:id',
        loadComponent: () =>
          import('./features/tasks/task-detail/task-detail.component').then(
            (m) => m.TaskDetailComponent,
          ),
      },
      {
        path: 'projects',
        loadComponent: () =>
          import('./features/projects/project-list/project-list.component').then(
            (m) => m.ProjectListComponent,
          ),
      },
      {
        path: 'projects/:id',
        loadComponent: () =>
          import('./features/projects/project-detail/project-detail.component').then(
            (m) => m.ProjectDetailComponent,
          ),
      },
      {
        path: 'reviews',
        loadComponent: () =>
          import('./features/reviews/review-list/review-list.component').then(
            (m) => m.ReviewListComponent,
          ),
      },
      {
        path: 'reviews/:id',
        loadComponent: () =>
          import('./features/reviews/review-detail/review-detail.component').then(
            (m) => m.ReviewDetailComponent,
          ),
      },
      {
        path: 'tasks/:taskId/qa-report',
        loadComponent: () =>
          import('./features/qa-report/qa-report.component').then((m) => m.QAReportComponent),
      },
      {
        path: 'agent/:taskId',
        loadComponent: () =>
          import('./features/agent-chat/agent-chat.component').then((m) => m.AgentChatComponent),
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/settings/settings.component').then((m) => m.SettingsComponent),
      },
      {
        path: 'admin',
        canActivate: [adminGuard],
        loadChildren: () =>
          import('./features/admin/admin.routes').then((m) => m.adminRoutes),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
