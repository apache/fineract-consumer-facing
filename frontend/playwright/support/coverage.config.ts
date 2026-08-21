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

import type { CoverageReportOptions } from 'monocart-coverage-reports';

const EXCLUDED_SOURCES = [
  /^src\/openapi-client\//,
  /^src\/main\.ts$/,
  /^src\/app\/app\.config\.ts$/,
  /^src\/app\/app\.ts$/,
  /\.routes\.ts$/,
  /^src\/environments\//,
  /^src\/app\/api\/consumer-api-error\.ts$/,
];

const coverageOptions: CoverageReportOptions = {
  name: 'frontend e2e coverage',
  outputDir: './coverage/e2e',
  reports: ['v8', 'lcovonly'],
  entryFilter: (entry) =>
    new URL(entry.url).pathname.endsWith('.js') && !!entry.source?.includes('sourceMappingURL'),
  sourceFilter: (sourcePath) =>
    sourcePath.startsWith('src/') && !EXCLUDED_SOURCES.some((pattern) => pattern.test(sourcePath)),
};

export default coverageOptions;
