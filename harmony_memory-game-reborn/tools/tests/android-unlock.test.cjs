// node --test tools/tests/android-unlock.test.cjs
// Executes real UI decision methods, checks rendered declarations, and checks
// the unmodified ArkUI source with the installed official SDK when available.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');
const ets = path.resolve(__dirname, '../../entry/src/main/ets');
const source = fs.readFileSync(path.join(ets, 'components/AndroidScreens.ets'), 'utf8');
const loader = 'C:/Program Files/Huawei/DevEco Studio/sdk/default/openharmony/ets/build-tools/ets-loader';
let ts;
for (const candidate of [process.env.QUICK_TYPESCRIPT, path.join(loader, 'node_modules/typescript'), 'typescript'].filter(Boolean)) {
  try { ts = require(candidate); break; } catch (_) {}
}
assert.ok(ts, 'Installed TypeScript compiler required');
function evaluate(code, dependencies = {}) {
  const result = ts.transpileModule(code, { compilerOptions: {
    target: ts.ScriptTarget.ES2021, module: ts.ModuleKind.CommonJS
  }, reportDiagnostics: true });
  const errors = (result.diagnostics || []).filter(d => d.category === ts.DiagnosticCategory.Error);
  assert.equal(errors.length, 0, errors.map(d => d.messageText).join('\n'));
  const module = { exports: {} };
  vm.runInNewContext(result.outputText, { module, exports: module.exports,
    require: name => { assert.ok(name in dependencies, `Unexpected dependency ${name}`); return dependencies[name]; },
    $r: name => name });
  return module.exports;
}
const catalog = evaluate(fs.readFileSync(path.join(ets, 'core/GameCatalog.ets'), 'utf8'));
const economy = evaluate(fs.readFileSync(path.join(ets, 'core/Economy.ets'), 'utf8'));
const startHome = source.indexOf('export struct AndroidHome');
const startUnlock = source.indexOf('export struct AndroidUnlock');
function method(start, name) {
  const at = source.indexOf(`  private ${name}(`, start);
  assert.notEqual(at, -1, name);
  const brace = source.indexOf('{', at);
  let depth = 1, end = brace + 1;
  while (depth > 0 && end < source.length) {
    if (source[end] === '{') depth++;
    if (source[end] === '}') depth--;
    end++;
  }
  assert.equal(depth, 0, name);
  return source.slice(at, end);
}
const imports = `import { GAMES } from './catalog'; import { requiresUnlock, unlockCost } from './economy';
function androidGame(index:number) { return GAMES[Math.max(0, Math.min(GAMES.length - 1, Math.floor(index)))]; }
function androidLevel(value:number) { return Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0; }`;
const logic = evaluate(imports + `
export class Home {
  records:number[]=[]; unlockedIds:number[]=[];
  ${['recordAt', 'locked', 'priced', 'rowDescription'].map(name => method(startHome, name)).join('\n')}
}
export class Unlock {
  gameIndex=0; balance=0; busy=false; errorText=''; confirmPending=false; onConfirm=()=>{};
  ${['resetConfirmation', 'price', 'canConfirm', 'confirm'].map(name => method(startUnlock, name)).join('\n')}
}`, { './catalog': catalog, './economy': economy });

test('home defaults fail closed for locked games, uses IDs rather than array indexes', () => {
  assert.match(source, /@Prop coins: number = 0/);
  assert.match(source, /@Prop unlockedIds: number\[\] = \[\]/);
  const home = new logic.Home();
  for (const game of catalog.GAMES) {
    assert.equal(home.locked(game), economy.requiresUnlock(game.id));
    home.unlockedIds = [game.id];
    assert.equal(home.locked(game), false);
    home.unlockedIds = [];
  }
  assert.match(source, /onClick\(\(\) => this\.onSelect\(index\)\)/);
});

test('locked card price and pyramid level exception match Android; purchased rows keep levels', () => {
  const home = new logic.Home();
  catalog.GAMES.forEach((game, index) => {
    home.records[index] = 7;
    const locked = economy.requiresUnlock(game.id);
    assert.equal(home.priced(game), locked && game.id !== 18);
    const description = home.rowDescription(game, index);
    if (locked) assert.match(description, /未解锁/);
    if (locked && game.id !== 18) assert.match(description, new RegExp(`解锁价格 ${economy.unlockCost(game.id)} 金币`));
    else assert.match(description, /等级 7/);
    home.unlockedIds = [game.id];
    assert.equal(home.priced(game), false);
    assert.match(home.rowDescription(game, index), /等级 7/);
    assert.doesNotMatch(home.rowDescription(game, index), /未解锁|解锁价格/);
    home.unlockedIds = [];
  });
  assert.match(source, /if \(this\.locked\(game\)\) \{[\s\S]*?sys\.symbol\.lock_fill/);
  assert.match(source, /if \(this\.priced\(game\)\) \{\s*Image\(media\('ic_coin'\)\)[\s\S]*?Text\(`\$\{unlockCost\(game\.id\)\}`\)/);
  assert.match(source, /Text\('免费解锁'\)/);
  assert.match(source, /Image\(media\('ic_coin'\)\)/);
});

test('confirmation requires enough balance and a purchasable game, blocks repeats and rechecks latest state', () => {
  const unlock = new logic.Unlock();
  unlock.gameIndex = catalog.GAMES.findIndex(game => economy.requiresUnlock(game.id));
  const cost = unlock.price();
  assert.ok(cost > 0);
  for (const balance of [0, cost - 1, NaN, Infinity, -1]) {
    unlock.balance = balance; assert.equal(unlock.canConfirm(), false);
  }
  unlock.balance = cost; assert.equal(unlock.canConfirm(), true);
  let confirmations = 0; unlock.onConfirm = () => { confirmations++; };
  unlock.busy = true; unlock.confirm(); assert.equal(confirmations, 0);
  unlock.busy = false; unlock.confirm(); unlock.confirm(); assert.equal(confirmations, 1);
  unlock.resetConfirmation(); unlock.balance = cost - 1; unlock.confirm(); assert.equal(confirmations, 1);
  unlock.balance = cost; unlock.confirm(); assert.equal(confirmations, 2);
  unlock.busy = true; unlock.resetConfirmation(); assert.equal(unlock.confirmPending, true);
  unlock.busy = false; unlock.resetConfirmation(); assert.equal(unlock.confirmPending, false);
  unlock.gameIndex = catalog.GAMES.findIndex(game => !economy.requiresUnlock(game.id));
  unlock.balance = 100000; assert.equal(unlock.canConfirm(), false);
});

test('secret pyramid remains locked in the list yet can be unlocked at zero balance for zero coins', () => {
  const home = new logic.Home();
  const index = catalog.GAMES.findIndex(game => game.id === 18);
  const pyramid = catalog.GAMES[index];
  assert.equal(home.locked(pyramid), true);
  assert.equal(home.priced(pyramid), false);
  assert.match(home.rowDescription(pyramid, index), /等级/);
  assert.doesNotMatch(home.rowDescription(pyramid, index), /金币/);
  const unlock = new logic.Unlock(); unlock.gameIndex = index; unlock.balance = 0;
  assert.equal(unlock.price(), 0);
  assert.equal(unlock.canConfirm(), true);
  unlock.busy = true; assert.equal(unlock.canConfirm(), false);
  home.unlockedIds = [18]; assert.equal(home.locked(pyramid), false);
});

test('unlock has Chinese price, current balance, exact shortfall and error copy; cancellation only disabled while busy', () => {
  const unlock = source.slice(startUnlock, source.indexOf('export struct AndroidPause'));
  assert.match(unlock, /Text\('解锁游戏'\)/);
  for (const text of ['解锁价格：${this.price()} 金币', '当前余额：${androidLevel(this.balance)} 金币',
    '还差 ${this.price() - androidLevel(this.balance)} 金币', '可游玩已解锁的游戏来获得金币。', '解锁 · ${this.price()} 金币']) {
    assert.ok(unlock.includes(text), text);
  }
  assert.match(unlock, /Text\(this\.errorText\)/);
  assert.match(unlock, /actionEnabled: this\.canConfirm\(\)/);
  assert.match(unlock, /labelText: '取消', actionKind: 'secondary', actionEnabled: !this\.busy/);
  assert.match(unlock, /if \(!this\.busy\) \{ this\.onCancel\(\); \}/);
  assert.match(unlock, /AndroidDialogFrame\(/);
  assert.match(source, /\.enabled\(this\.actionEnabled\)\.opacity\(this\.actionEnabled \? 1 : 0\.5\)/);
  assert.match(source, /if \(this\.actionEnabled\) \{ this\.onAction\(\); \}/);
});

test('official SDK parses and semantically checks AndroidScreens, including unlock ArkUI APIs', t => {
  let ark;
  try { ark = require(process.env.QUICK_ARK_TYPESCRIPT || path.join(loader, 'node_modules/typescript')); }
  catch (_) { t.skip('Official SDK compiler unavailable'); return; }
  if (!ark.ArkTSLinter_1_1 || !fs.existsSync(path.join(loader, 'declarations'))) {
    t.skip('Official SDK declarations/linter unavailable'); return;
  }
  const inputs = ['core/GameCatalog.ets', 'core/Economy.ets', 'components/AndroidScreens.ets'].map(name => path.join(ets, name));
  const declarations = fs.readdirSync(path.join(loader, 'declarations')).filter(name => name.endsWith('.d.ts'))
    .map(name => path.join(loader, 'declarations', name));
  const config = ark.readConfigFile(path.join(loader, 'tsconfig.json'), ark.sys.readFile).config;
  const options = { ...config.compilerOptions, target: ark.ScriptTarget.ES2021, module: ark.ModuleKind.CommonJS,
    moduleResolution: ark.ModuleResolutionKind.NodeJs, noEmit: true, skipLibCheck: true, incremental: true,
    needDoArkTsLinter: true, compatibleSdkVersion: 19, isCompatibleVersion: false, etsLoaderPath: loader,
    lib: ['lib.es2021.d.ts'], strict: true, strictPropertyInitialization: false,
    paths: { '@ohos.*': [path.join(loader, '../../api/@ohos.*.d.ts')] } };
  const host = ark.createIncrementalCompilerHost(options);
  const builder = ark.createIncrementalProgram({ rootNames: [...inputs, ...declarations], options, host });
  const program = builder.getProgram();
  inputs.forEach(file => assert.equal(program.getSourceFile(file).scriptKind, ark.ScriptKind.ETS));
  const errors = [...program.getSyntacticDiagnostics(), ...program.getSemanticDiagnostics(),
    ...ark.ArkTSLinter_1_1.runArkTSLinter(builder, undefined, undefined, 'ArkTS_1_1')]
    .filter(d => d.category === ark.DiagnosticCategory.Error && d.file && inputs.includes(path.normalize(d.file.fileName)));
  assert.equal(errors.length, 0, ark.formatDiagnosticsWithColorAndContext(errors, {
    getCanonicalFileName: name => name, getCurrentDirectory: () => ets, getNewLine: () => '\n'
  }));
});
