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
import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonLabel,
  IonList,
  IonMenu,
  IonProgressBar,
  IonSplitPane,
  IonToolbar,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  cashOutline,
  flaskOutline,
  gridOutline,
  logOutOutline,
  menuOutline,
  peopleOutline,
  personOutline,
  settingsOutline,
  swapHorizontalOutline,
  walletOutline,
} from 'ionicons/icons';
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
  '/settings': 'SETTINGS',
  '/demo': 'DEMO',
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
    IonButton,
    IonButtons,
    IonContent,
    IonHeader,
    IonIcon,
    IonItem,
    IonLabel,
    IonList,
    IonMenu,
    IonProgressBar,
    IonSplitPane,
    IonToolbar,
    TranslatePipe,
    LanguageSwitcherComponent,
  ],
  template: `
    @if (loading()) {
      <ion-progress-bar type="indeterminate" />
    }

    <ion-header class="ion-no-border">
      <ion-toolbar>
        <ion-buttons slot="start">
          <ion-button
            fill="clear"
            class="icon-button-lg nav-toggle-btn"
            [attr.aria-label]="'layout.shell.toggleNav' | translate"
            (click)="toggleSidenav()"
          >
            <ion-icon slot="icon-only" name="menu-outline" aria-hidden="true" />
          </ion-button>
        </ion-buttons>
        <div class="brand" slot="start">
          <img
            ngSrc="/apache-fineract-logo.png"
            width="40"
            height="40"
            alt=""
            class="brand-icon"
            priority
          />
          <span>{{ 'layout.shell.brand' | translate }}</span>
        </div>
        <ion-buttons slot="end">
          <app-language-switcher />
          <ion-button
            fill="clear"
            class="icon-button-lg logout-btn"
            [attr.aria-label]="'layout.shell.logout' | translate"
            (click)="logout()"
          >
            <ion-icon slot="icon-only" name="log-out-outline" aria-hidden="true" />
          </ion-button>
        </ion-buttons>
      </ion-toolbar>
    </ion-header>

    <div class="pane-host">
      <ion-split-pane contentId="shell-content" when="xs" [class.nav-collapsed]="!sidenavOpened()">
        <ion-menu contentId="shell-content" menuId="shell-nav">
          <ion-content>
            <ion-list lines="none">
              <ion-item
                [button]="true"
                [detail]="false"
                routerLink="/summary"
                routerLinkActive="active-link"
              >
                <ion-icon slot="start" name="grid-outline" aria-hidden="true" />
                <ion-label>{{ 'layout.nav.summary' | translate }}</ion-label>
              </ion-item>
              <ion-item
                [button]="true"
                [detail]="false"
                routerLink="/savings"
                routerLinkActive="active-link"
              >
                <ion-icon slot="start" name="wallet-outline" aria-hidden="true" />
                <ion-label>{{ 'layout.nav.savings' | translate }}</ion-label>
              </ion-item>
              <ion-item
                [button]="true"
                [detail]="false"
                routerLink="/loans"
                routerLinkActive="active-link"
              >
                <ion-icon slot="start" name="cash-outline" aria-hidden="true" />
                <ion-label>{{ 'layout.nav.loans' | translate }}</ion-label>
              </ion-item>
              <ion-item
                [button]="true"
                [detail]="false"
                routerLink="/transfers"
                routerLinkActive="active-link"
              >
                <ion-icon slot="start" name="swap-horizontal-outline" aria-hidden="true" />
                <ion-label>{{ 'layout.nav.transfers' | translate }}</ion-label>
              </ion-item>
              <ion-item
                [button]="true"
                [detail]="false"
                routerLink="/beneficiaries"
                routerLinkActive="active-link"
              >
                <ion-icon slot="start" name="people-outline" aria-hidden="true" />
                <ion-label>{{ 'layout.nav.beneficiaries' | translate }}</ion-label>
              </ion-item>
              <ion-item
                [button]="true"
                [detail]="false"
                routerLink="/profile"
                routerLinkActive="active-link"
              >
                <ion-icon slot="start" name="person-outline" aria-hidden="true" />
                <ion-label>{{ 'layout.nav.profile' | translate }}</ion-label>
              </ion-item>
              <ion-item
                [button]="true"
                [detail]="false"
                routerLink="/settings"
                routerLinkActive="active-link"
              >
                <ion-icon slot="start" name="settings-outline" aria-hidden="true" />
                <ion-label>{{ 'layout.nav.settings' | translate }}</ion-label>
              </ion-item>
              <ion-item
                [button]="true"
                [detail]="false"
                routerLink="/demo"
                routerLinkActive="active-link"
              >
                <ion-icon slot="start" name="flask-outline" aria-hidden="true" />
                <ion-label>{{ 'layout.nav.demo' | translate }}</ion-label>
              </ion-item>
            </ion-list>
          </ion-content>
        </ion-menu>
        <div class="shell-content" id="shell-content">
          <main>
            <router-outlet />
          </main>
        </div>
      </ion-split-pane>
    </div>
  `,
  styleUrls: ['../shared/css/icon-button.scss'],
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      height: 100dvh;
    }

    ion-toolbar {
      --background: var(--cobalt-700);
      --color: var(--ion-color-primary-contrast);
      --border-color: var(--cobalt-800);
      --border-style: solid;
      --border-width: 0 0 1px 0;
      --min-height: 4.5rem;
      --toolbar-icon-color: var(--ion-color-primary-contrast);
    }

    ion-buttons ion-button {
      --color: var(--ion-color-primary-contrast);
      --background-hover: var(--ion-color-primary-contrast);
      --background-hover-opacity: 0.12;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 0.625rem;
      font-size: 1.25rem;
      font-weight: 600;
      color: var(--ion-color-primary-contrast);
    }

    .nav-toggle-btn {
      margin-right: 0.25rem;
    }

    .logout-btn {
      margin-left: 0.5rem;
    }

    .pane-host {
      position: relative;
      flex: 1;
      overflow: hidden;
    }

    ion-split-pane {
      --side-width: 17rem;
      --side-min-width: 17rem;
      --side-max-width: 17rem;
    }

    ion-menu {
      --border: 1px solid var(--ion-border-color);
      transition:
        margin-inline-start var(--dur-slow) var(--ease-out),
        visibility 0s;

      ion-content {
        --background: var(--cobalt-50);
      }
    }

    ion-split-pane.nav-collapsed ion-menu {
      margin-inline-start: -17rem;
      visibility: hidden;
      transition:
        margin-inline-start var(--dur-slow) var(--ease-out),
        visibility 0s var(--dur-slow);
    }

    ion-list {
      background: transparent;
      padding: 0.5rem 0;
    }

    ion-item {
      --background: transparent;
      --background-hover: var(--surface);
      --background-hover-opacity: 1;
      --color: var(--slate-600);
      --border-radius: var(--radius-sm);
      --min-height: 3rem;
      margin: 0.25rem 0.5rem;
      font-size: var(--text-md);

      ion-icon {
        color: var(--slate-500);
        font-size: var(--text-2xl);
        margin-inline-end: 0.75rem;
      }

      &.active-link {
        --background: var(--cobalt-100);
        --background-hover: var(--cobalt-100);
        --color: var(--cobalt-800);
        font-weight: 600;

        ion-icon {
          color: var(--cobalt-800);
        }
      }
    }

    .shell-content {
      --border: var(--ion-border-color);
      position: relative;
      flex: 1;
      overflow-y: auto;
      background: var(--page-bg);
    }

    main {
      padding: 1.5rem;
      min-height: 100%;
      display: flex;
      flex-direction: column;
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
    addIcons({
      cashOutline,
      flaskOutline,
      gridOutline,
      logOutOutline,
      menuOutline,
      peopleOutline,
      personOutline,
      settingsOutline,
      swapHorizontalOutline,
      walletOutline,
    });
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
