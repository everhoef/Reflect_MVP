#!/usr/bin/env node
import { compile } from "json-schema-to-typescript";
import { readFileSync, writeFileSync, mkdirSync } from "fs";
import { join, resolve, dirname } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, "../..");
const schemasDir = join(repoRoot, "shared", "schemas", "sse");
const outputDir = resolve(__dirname, "../src/types/sse-schemas");

mkdirSync(outputDir, { recursive: true });

const schemaFiles = [
  "response-data.schema.json",
  "participant-event.schema.json",
  "participant-name.schema.json",
  "note-updated.schema.json",
  "retro-event-envelope.schema.json",
  "step-id.schema.json",
  "phase-name.schema.json",
  "void-event.schema.json",
  // event-type-registry.json is a documentation registry, not a type schema — excluded from generation
];

for (const filename of schemaFiles) {
  const schemaPath = join(schemasDir, filename);
  const schema = JSON.parse(readFileSync(schemaPath, "utf-8"));

  const ts = await compile(schema, filename, { cwd: schemasDir });

  const outFile = join(outputDir, filename.replace(".json", ".d.ts"));
  writeFileSync(outFile, ts);
  console.log(`Generated: ${outFile}`);
}

console.log("SSE types generated successfully.");
