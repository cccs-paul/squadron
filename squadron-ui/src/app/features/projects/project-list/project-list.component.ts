import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProjectService } from '../../../core/services/project.service';
import { Project } from '../../../core/models/project.model';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'sq-project-list', standalone: true, imports: [RouterLink, TimeAgoPipe, TranslateModule],
  templateUrl: './project-list.component.html', styleUrl: './project-list.component.scss',
})
export class ProjectListComponent implements OnInit {
  private projectService = inject(ProjectService);
  projects = signal<Project[]>([]); loading = signal(true);

  ngOnInit(): void {
    this.projectService.getProjects().subscribe({
      next: (res) => { this.projects.set(res.content); this.loading.set(false); },
      error: (err) => { console.error('Failed to load projects:', err); this.projects.set([]); this.loading.set(false); },
    });
  }
}
