import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from '../../shared/components/header/header.component';
import { SidebarComponent } from '../../shared/components/sidebar/sidebar.component';
import { NotificationToastComponent } from '../../shared/components/notification-toast/notification-toast.component';

@Component({
  selector: 'sq-main-layout',
  standalone: true,
  imports: [RouterOutlet, HeaderComponent, SidebarComponent, NotificationToastComponent],
  templateUrl: './main-layout.component.html',
  styleUrl: './main-layout.component.scss',
})
export class MainLayoutComponent {
  sidebarCollapsed = false;

  toggleSidebar(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }
}
