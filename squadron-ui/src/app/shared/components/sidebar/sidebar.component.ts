import { Component, inject, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../../core/auth/auth.service';

interface NavItem {
  labelKey: string;
  route: string;
  icon: string;
  adminOnly?: boolean;
}

@Component({
  selector: 'sq-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, TranslateModule],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent {
  private authService = inject(AuthService);

  readonly collapsed = input<boolean>(false);
  readonly isAdmin = this.authService.isAdmin;

  readonly navItems: NavItem[] = [
    { labelKey: 'sidebar.dashboard', route: '/dashboard', icon: 'dashboard' },
    { labelKey: 'sidebar.tasks', route: '/tasks', icon: 'tasks' },
    { labelKey: 'sidebar.projects', route: '/projects', icon: 'projects' },
    { labelKey: 'sidebar.reviews', route: '/reviews', icon: 'reviews' },
    { labelKey: 'sidebar.settings', route: '/settings', icon: 'settings' },
  ];

  readonly adminItems: NavItem[] = [
    { labelKey: 'sidebar.users', route: '/admin/users', icon: 'users', adminOnly: true },
    { labelKey: 'sidebar.teams', route: '/admin/teams', icon: 'teams', adminOnly: true },
    { labelKey: 'sidebar.securityGroups', route: '/admin/security-groups', icon: 'security', adminOnly: true },
    { labelKey: 'sidebar.permissions', route: '/admin/permissions', icon: 'permissions', adminOnly: true },
    { labelKey: 'sidebar.authProviders', route: '/admin/auth-providers', icon: 'auth', adminOnly: true },
  ];
}
