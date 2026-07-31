/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  ActivatedRouteSnapshot,
  NavigationCancel,
  NavigationEnd,
  NavigationError,
  NavigationStart,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { TranslatePipe } from '@ngx-translate/core';
import { AuditService } from '../core/audit/audit.service';
import { buildDetails } from '../core/audit/pii-scrub';
import { AuthService } from '../core/auth/auth.service';
import { LanguageSwitcherComponent } from '../shared/language-switcher.component';

const SENSITIVE_ROUTE_VIEWS: Record<string, string> = {
  '/savings/:savingsId': 'BALANCE',
  '/savings/:savingsId/transactions/:transactionId': 'STATEMENT',
  '/loans/:loanId': 'LOAN_DETAIL',
  '/loans/:loanId/transactions/:transactionId': 'LOAN_DETAIL',
  '/beneficiaries': 'BENEFICIARIES',
  '/profile': 'PROFILE',
};

const RESOURCE_ID_PARAMS = ['savingsId', 'loanId'];

@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NgOptimizedImage,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatProgressBarModule,
    MatSidenavModule,
    MatToolbarModule,
    TranslatePipe,
    LanguageSwitcherComponent,
  ],
  template: `
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    <mat-toolbar>
      <button
        mat-icon-button
        class="icon-button-lg nav-toggle-btn"
        [attr.aria-label]="'layout.shell.toggleNav' | translate"
        (click)="toggleSidenav()"
      >
        <mat-icon>menu</mat-icon>
      </button>
      <img ngSrc="/apache-fineract-logo.png" width="32" height="32" alt="" class="brand-icon" priority />
      <span>{{ 'layout.shell.brand' | translate }}</span>
      <span class="spacer"></span>
      <app-language-switcher />
      <button
        mat-icon-button
        class="icon-button-lg logout-btn"
        [attr.aria-label]="'layout.shell.logout' | translate"
        (click)="logout()"
      >
        <mat-icon>logout</mat-icon>
      </button>
    </mat-toolbar>

    <mat-sidenav-container>
      <mat-sidenav mode="side" [opened]="sidenavOpened()">
        <mat-nav-list>
          <a mat-list-item routerLink="/summary" routerLinkActive="active-link">
            <mat-icon matListItemIcon>dashboard</mat-icon>{{ 'layout.nav.summary' | translate }}
          </a>
          <a mat-list-item routerLink="/savings" routerLinkActive="active-link">
            <mat-icon matListItemIcon>savings</mat-icon>{{ 'layout.nav.savings' | translate }}
          </a>
          <a mat-list-item routerLink="/loans" routerLinkActive="active-link">
            <mat-icon matListItemIcon>request_quote</mat-icon>{{ 'layout.nav.loans' | translate }}
          </a>
          <a mat-list-item routerLink="/transfers" routerLinkActive="active-link">
            <mat-icon matListItemIcon>swap_horiz</mat-icon>{{ 'layout.nav.transfers' | translate }}
          </a>
          <a mat-list-item routerLink="/beneficiaries" routerLinkActive="active-link">
            <mat-icon matListItemIcon>group</mat-icon>{{ 'layout.nav.beneficiaries' | translate }}
          </a>
          <a mat-list-item routerLink="/profile" routerLinkActive="active-link">
            <mat-icon matListItemIcon>person</mat-icon>{{ 'layout.nav.profile' | translate }}
          </a>
        </mat-nav-list>
      </mat-sidenav>
      <mat-sidenav-content>
        <main>
          <router-outlet />
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styleUrls: ['../shared/css/icon-button.scss'],
  styles: `
    .brand-icon {
      margin-right: 0.5rem;
    }
    .nav-toggle-btn {
      margin-right: 0.5rem;
    }
    .logout-btn {
      margin-left: 0.75rem;
    }
    .spacer {
      flex: 1 1 auto;
    }
    mat-sidenav-container {
      height: calc(100vh - 64px);
    }
    mat-sidenav {
      width: 15rem;
    }
    main {
      padding: 2rem;
    }
  `,
})
export class ShellComponent {
  private readonly auth = inject(AuthService);
  private readonly audit = inject(AuditService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly loading = signal(false);
  protected readonly sidenavOpened = signal(true);

  constructor() {
    this.router.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((event) => {
      if (event instanceof NavigationStart) {
        this.loading.set(true);
      } else if (event instanceof NavigationEnd) {
        this.loading.set(false);
        this.recordNavigation();
      } else if (event instanceof NavigationCancel || event instanceof NavigationError) {
        this.loading.set(false);
      }
    });
  }

  protected toggleSidenav(): void {
    this.sidenavOpened.update((v) => !v);
  }

  protected logout(): void {
    this.audit.record('LOGOUT');
    this.auth
      .logout()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.router.navigate(['/login']),
        error: () => this.router.navigate(['/login']),
      });
  }

  private recordNavigation(): void {
    const { template, params } = describeRoute(this.router.routerState.snapshot.root);
    this.audit.record('NAVIGATION', buildDetails({ route: template }));
    const view = SENSITIVE_ROUTE_VIEWS[template];
    if (view) {
      const resourceId = RESOURCE_ID_PARAMS.map((name) => params[name]).find((v) => v);
      this.audit.record('SENSITIVE_VIEW', buildDetails({ view, resourceId }));
    }
  }
}

function describeRoute(root: ActivatedRouteSnapshot): {
  template: string;
  params: Record<string, string>;
} {
  const segments: string[] = [];
  const params: Record<string, string> = {};
  let node: ActivatedRouteSnapshot | null = root;
  while (node) {
    const path = node.routeConfig?.path;
    if (path) {
      segments.push(path);
    }
    Object.assign(params, node.params);
    node = node.firstChild;
  }
  return { template: '/' + segments.join('/'), params };
}
