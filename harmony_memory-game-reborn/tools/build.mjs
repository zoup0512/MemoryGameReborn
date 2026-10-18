import { existsSync } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const home = process.env.DEVECO_HOME || 'C:/Program Files/Huawei/DevEco Studio';
const node = join(home, 'tools/node/node.exe');
const hvigor = join(home, 'tools/hvigor/bin/hvigorw.js');
if (!existsSync(hvigor)) {
  console.error('Set DEVECO_HOME to the DevEco Studio installation directory.');
  process.exit(1);
}
const mode = process.argv.includes('--release') ? 'release' : 'debug';
const env = {
  ...process.env,
  DEVECO_SDK_HOME: join(home, 'sdk'),
  OHOS_SDK_HOME: join(home, 'sdk'),
  JAVA_HOME: join(home, 'jbr'),
  PATH: `${join(home, 'tools/node')};${join(home, 'jbr/bin')};${process.env.PATH || ''}`
};
const args = [hvigor, '--mode', 'module', '-p', 'product=default', '-p', `buildMode=${mode}`, '-p', 'module=entry@default', 'assembleHap', '--no-daemon'];
const result = spawnSync(existsSync(node) ? node : process.execPath, args, { cwd: root, env, stdio: 'inherit' });
process.exit(result.status ?? 1);
