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


import { readdirSync, readFileSync } from 'node:fs';
import { join, relative, resolve, dirname } from 'node:path';
import ts from 'typescript';
import { parseTemplate, TmplAstRecursiveVisitor, visitAll } from '@angular/compiler';

const TAG_ALLOWLIST = ['mat-icon'];
const STRING_ALLOWLIST = ['SSN', 'Aadhaar'];
const WATCHED_ATTRS = ['title', 'placeholder', 'aria-label', 'matTooltip', 'alt'];

const SRC_ROOT = resolve('src', 'app');
const EXCLUDE_DIR = resolve('src', 'openapi-client');

const hasLetter = (s) => /[A-Za-z]/.test(s);
const allowed = (s) => STRING_ALLOWLIST.includes(s.trim());

function offsetToLineCol(text, offset) {
  let line = 1;
  let col = 1;
  for (let i = 0; i < offset && i < text.length; i++) {
    if (text[i] === '\n') {
      line++;
      col = 1;
    } else {
      col++;
    }
  }
  return { line, col };
}

function collectViolations(nodes, fileText, baseOffset, relPath, violations) {
  const flag = (span, text) => {
    const { line, col } = offsetToLineCol(fileText, baseOffset + span.start.offset);
    violations.push(`${relPath}:${line}:${col}  ${JSON.stringify(text.trim())}`);
  };

  class Visitor extends TmplAstRecursiveVisitor {
    visitText(node) {
      if (hasLetter(node.value) && !allowed(node.value)) {
        flag(node.sourceSpan, node.value);
      }
    }

    visitBoundText(node) {
      for (const chunk of node.value.ast.strings) {
        if (hasLetter(chunk) && !allowed(chunk)) {
          flag(node.sourceSpan, chunk);
          break;
        }
      }
    }

    visitElement(node) {
      for (const attr of node.attributes) {
        if (WATCHED_ATTRS.includes(attr.name) && hasLetter(attr.value) && !allowed(attr.value)) {
          flag(attr.sourceSpan, `${attr.name}="${attr.value}"`);
        }
      }
      if (TAG_ALLOWLIST.includes(node.name)) {
        return;
      }
      super.visitElement(node);
    }
  }

  visitAll(new Visitor(), nodes);
}

function extractInlineTemplate(tsText, fileName) {
  const sf = ts.createSourceFile(fileName, tsText, ts.ScriptTarget.Latest, true);
  let found = null;

  const visit = (node) => {
    if (
      ts.isCallExpression(node) &&
      ts.isIdentifier(node.expression) &&
      node.expression.text === 'Component' &&
      node.arguments.length > 0 &&
      ts.isObjectLiteralExpression(node.arguments[0])
    ) {
      for (const prop of node.arguments[0].properties) {
        if (!ts.isPropertyAssignment(prop) || !ts.isIdentifier(prop.name)) continue;
        if (prop.name.text === 'template') {
          const init = prop.initializer;
          if (ts.isNoSubstitutionTemplateLiteral(init) || ts.isStringLiteral(init)) {
            // Content starts one char after the opening backtick/quote.
            found = { kind: 'inline', text: init.text, contentStart: init.getStart(sf) + 1 };
          }
        } else if (prop.name.text === 'templateUrl') {
          const init = prop.initializer;
          if (ts.isStringLiteral(init)) {
            found = { kind: 'url', url: init.text };
          }
        }
      }
    }
    ts.forEachChild(node, visit);
  };

  visit(sf);
  return found;
}

function listFiles(dir) {
  const out = [];
  for (const entry of readdirSync(dir, { recursive: true })) {
    const abs = join(dir, entry.toString());
    if (abs.startsWith(EXCLUDE_DIR)) continue;
    if (abs.endsWith('.component.ts') || abs.endsWith('.html')) out.push(abs);
  }
  return out;
}

function main() {
  const violations = [];

  for (const abs of listFiles(SRC_ROOT)) {
    const relPath = relative(process.cwd(), abs);

    if (abs.endsWith('.component.ts')) {
      const tsText = readFileSync(abs, 'utf8');
      const tpl = extractInlineTemplate(tsText, abs);
      if (!tpl) continue;
      if (tpl.kind === 'inline') {
        const parsed = parseTemplate(tpl.text, abs);
        collectViolations(parsed.nodes, tsText, tpl.contentStart, relPath, violations);
      } else {
        const htmlAbs = resolve(dirname(abs), tpl.url);
        const htmlText = readFileSync(htmlAbs, 'utf8');
        const parsed = parseTemplate(htmlText, htmlAbs);
        collectViolations(parsed.nodes, htmlText, 0, relative(process.cwd(), htmlAbs), violations);
      }
    } else {
      const htmlText = readFileSync(abs, 'utf8');
      const parsed = parseTemplate(htmlText, abs);
      collectViolations(parsed.nodes, htmlText, 0, relPath, violations);
    }
  }

  if (violations.length > 0) {
    console.error('i18n check failed — hardcoded user-facing text found:\n');
    for (const v of violations) console.error('  ' + v);
    console.error(`\n${violations.length} violation(s). Use the translate pipe, or add a legitimate exception to an allowlist in scripts/check-i18n.mjs.`);
    process.exit(1);
  }

  console.log('i18n check passed — no hardcoded user-facing text found.');
}

main();
