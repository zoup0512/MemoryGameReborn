// Official DevEco SDK ArkTS 1.1 linter + semantic check on the untouched .ets files.
// node harmony_memory-game-reborn/tools/tests/spatial-arkts.cjs
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const loader = process.env.SPATIAL_ETS_LOADER ||
  'C:/Program Files/Huawei/DevEco Studio/sdk/default/openharmony/ets/build-tools/ets-loader';
const ts = require(path.join(loader, 'node_modules/typescript'));
assert.ok(ts.ArkTSLinter_1_1, 'This test requires the official ArkTS SDK compiler');
const root = path.resolve(__dirname, '../..');
const inputs = [path.join(root, 'entry/src/main/ets/core/Contract.ets'),
  path.join(root, 'entry/src/main/ets/core/SpatialGames.ets'),
  path.join(root, 'entry/src/main/ets/components/SpatialBoard.ets')];
const declarations = path.join(loader, 'declarations');
const declarationFiles = fs.readdirSync(declarations).filter(name => name.endsWith('.d.ts'))
  .map(name => path.join(declarations, name));
const json = ts.readConfigFile(path.join(loader, 'tsconfig.json'), ts.sys.readFile).config;
const options = Object.assign({}, json.compilerOptions, { target: ts.ScriptTarget.ES2021,
  module: ts.ModuleKind.CommonJS, moduleResolution: ts.ModuleResolutionKind.NodeJs,
  noEmit: true, skipLibCheck: true, incremental: true, needDoArkTsLinter: true,
  compatibleSdkVersion: 19, isCompatibleVersion: false, etsLoaderPath: loader,
  tsBuildInfoFile: path.join(os.tmpdir(), 'spatial-arkts.tsbuildinfo'),
  // ArkUI injects required @ObjectLink properties from the parent, not a constructor.
  // Match the SDK build's property initialization mode; pure models are separately
  // checked with full strict initialization in spatial.test.cjs.
  lib: ['lib.es2021.d.ts'], strict: true, strictPropertyInitialization: false });
// Resolve real SDK modules as hvigor's ets-loader does; do not stub image APIs.
options.paths = { '@ohos.*': [path.join(loader, '../../api/@ohos.*.d.ts')] };
const host = ts.createIncrementalCompilerHost(options);
const builder = ts.createIncrementalProgram({ rootNames: [...inputs, ...declarationFiles], options, host });
const program = builder.getProgram();
for (const input of inputs) {
  const source = program.getSourceFile(input);
  assert.ok(source, 'SDK must actually parse ' + input);
  assert.equal(source.scriptKind, ts.ScriptKind.ETS, 'Must be parsed as ArkTS, not plain TypeScript');
  assert.ok(source.statements.length > 0);
}
const diagnostics = [...program.getSyntacticDiagnostics(), ...program.getSemanticDiagnostics()]
  .filter(d => d.file && inputs.includes(path.normalize(d.file.fileName)));
const lint = ts.ArkTSLinter_1_1.runArkTSLinter(builder, undefined, undefined, 'ArkTS_1_1')
  .filter(d => d.file && inputs.includes(path.normalize(d.file.fileName)));
const all = [...diagnostics, ...lint];
const format = ts.formatDiagnosticsWithColorAndContext(all, {
  getCanonicalFileName: name => name, getCurrentDirectory: () => root, getNewLine: () => '\n'
});
if (format) console.log(format);
assert.equal(all.filter(d => d.category === ts.DiagnosticCategory.Error).length, 0,
  'Spatial .ets files must pass official ArkTS semantic checking and linter');
console.log('PASS official SDK ArkTS 1.1 linter + semantic check:', inputs.length, 'real .ets files');
