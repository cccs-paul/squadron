import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { ProjectService } from '../../../core/services/project.service';
import { Project } from '../../../core/models/project.model';

@Component({
  selector: 'sq-project-detail', standalone: true, imports: [RouterLink, TranslateModule],
  template: `
    @if (project(); as p) {
      <div class="detail">
        <a routerLink="/projects" class="back-link">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
          {{ 'projects.detail.backToProjects' | translate }}
        </a>
        <h1>{{ p.name }}</h1>
        <p class="desc">{{ p.description }}</p>
        <div class="info-grid">
          <div class="info-item"><span class="label">{{ 'projects.detail.repository' | translate }}</span><a [href]="p.repositoryUrl" target="_blank">{{ p.repositoryUrl }}</a></div>
          <div class="info-item"><span class="label">{{ 'projects.detail.defaultBranch' | translate }}</span><span>{{ p.defaultBranch }}</span></div>
          <div class="info-item"><span class="label">{{ 'projects.detail.totalTasks' | translate }}</span><span>{{ p.taskCount }}</span></div>
          <div class="info-item"><span class="label">{{ 'projects.detail.activeTasks' | translate }}</span><span>{{ p.activeTaskCount }}</span></div>
        </div>
      </div>
    }
  `,
  styleUrl: './project-detail.component.scss',
})
export class ProjectDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private projectService = inject(ProjectService);
  project = signal<Project | null>(null);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.projectService.getProject(id).subscribe({
        next: (p) => this.project.set(p),
        error: (err) => { console.error('Failed to load project:', err); this.project.set(null); },
      });
    }
  }
}
