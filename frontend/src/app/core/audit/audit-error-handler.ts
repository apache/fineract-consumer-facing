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

import { ErrorHandler, Injectable, inject } from '@angular/core';
import { AuditService } from './audit.service';
import { buildDetails } from './pii-scrub';

@Injectable()
export class AuditErrorHandler extends ErrorHandler {
  private readonly audit = inject(AuditService);

  override handleError(error: unknown): void {
    try {
      this.audit.record('CLIENT_ERROR', buildDetails(describeError(error)));
    } catch {}
    super.handleError(error);
  }
}

function describeError(error: unknown): Record<string, string | undefined> {
  if (error instanceof Error) {
    return { message: error.message, topFrame: topStackFrame(error) };
  }
  return { message: String(error) };
}

function topStackFrame(error: Error): string | undefined {
  return error.stack
    ?.split('\n')
    .map((line) => line.trim())
    .find((line) => line.startsWith('at '));
}
