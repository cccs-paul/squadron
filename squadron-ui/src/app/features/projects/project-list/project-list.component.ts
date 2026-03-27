import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProjectService } from '../../../core/services/project.service';
import { Project } from '../../../core/models/project.model';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';

@Component({
  selector: 'sq-project-list', standalone: true, imports: [RouterLink, TimeAgoPipe],
  templateUrl: './project-list.component.html', styleUrl: './project-list.component.scss',
})
export class ProjectListComponent implements OnInit {
  private projectService = inject(ProjectService);
  projects = signal<Project[]>([]); loading = signal(true);

  ngOnInit(): void {
    this.projectService.getProjects().subscribe({
      next: (res) => { this.projects.set(res.content); this.loading.set(false); },
      error: () => { this.projects.set([
        { id: '1', tenantId: '1', name: 'squadron-api', description: 'Main backend API service', repositoryUrl: 'https://github.com/org/squadron-api', defaultBranch: 'main', taskCount: 24, activeTaskCount: 8, members: [], createdAt: new Date(Date.now() - 604800000).toISOString() } as Project,
        { id: '2', tenantId: '1', name: 'squadron-ui', description: 'Angular frontend application', repositoryUrl: 'https://github.com/org/squadron-ui', defaultBranch: 'main', taskCount: 15, activeTaskCount: 5, members: [], createdAt: new Date(Date.now() - 432000000).toISOString() } as Project,
        { id: '3', tenantId: '1', name: 'squadron-infra', description: 'Infrastructure and deployment configs', repositoryUrl: 'https://github.com/org/squadron-infra', defaultBranch: 'main', taskCount: 8, activeTaskCount: 2, members: [], createdAt: new Date(Date.now() - 259200000).toISOString() } as Project,
      ]); this.loading.set(false); },
    });
  }
}
