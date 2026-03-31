import { Component, inject, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

interface NavItem {
  label: string;
  route: string;
  icon: string;
  adminOnly?: boolean;
}

@Component({
  selector: 'sq-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent {
  private authService = inject(AuthService);

  readonly collapsed = input<boolean>(false);
  readonly isAdmin = this.authService.isAdmin;

  readonly navItems: NavItem[] = [
    { label: 'Dashboard', route: '/dashboard', icon: 'dashboard' },
    { label: 'Tasks', route: '/tasks', icon: 'tasks' },
    { label: 'Projects', route: '/projects', icon: 'projects' },
    { label: 'Reviews', route: '/reviews', icon: 'reviews' },
    { label: 'Settings', route: '/settings', icon: 'settings' },
    { label: 'Project Config', route: '/settings/projects', icon: 'settings' },
    { label: 'My Squadron', route: '/settings/squadron', icon: 'agents' },
  ];

  readonly adminItems: NavItem[] = [
    { label: 'Users', route: '/admin/users', icon: 'users', adminOnly: true },
    { label: 'Teams', route: '/admin/teams', icon: 'teams', adminOnly: true },
    { label: 'Security Groups', route: '/admin/security-groups', icon: 'security', adminOnly: true },
    { label: 'Permissions', route: '/admin/permissions', icon: 'permissions', adminOnly: true },
    { label: 'Auth Providers', route: '/admin/auth-providers', icon: 'auth', adminOnly: true },
    { label: 'Platforms', route: '/admin/platforms', icon: 'platforms', adminOnly: true },
  ];
}
