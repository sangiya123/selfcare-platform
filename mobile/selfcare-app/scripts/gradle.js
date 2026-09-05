const fs = require('node:fs');
const { spawnSync } = require('node:child_process');
const path = require('node:path');

const projectRoot = path.resolve(__dirname, '..');
const args = process.argv.slice(2);

function findWindowsJavaHome() {
  if (process.env.MOBILE_JAVA_HOME && fs.existsSync(process.env.MOBILE_JAVA_HOME)) {
    return process.env.MOBILE_JAVA_HOME;
  }

  const searchRoots = [
    'C:\\Program Files\\Java',
    'C:\\Program Files\\Eclipse Adoptium',
  ];

  const compatibleJdks = [];

  for (const root of searchRoots) {
    if (!fs.existsSync(root)) {
      continue;
    }

    for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
      if (!entry.isDirectory()) {
        continue;
      }

      const match = entry.name.match(/(?:jdk|temurin)-((?:17|18|19|20|21)(?:\.\d+){0,2})/i);
      if (!match) {
        continue;
      }

      compatibleJdks.push({
        version: match[1],
        javaHome: path.join(root, entry.name),
      });
    }
  }

  compatibleJdks.sort((left, right) =>
    right.version.localeCompare(left.version, undefined, { numeric: true, sensitivity: 'base' })
  );

  if (compatibleJdks.length > 0) {
    return compatibleJdks[0].javaHome;
  }

  if (process.env.JAVA_HOME && fs.existsSync(process.env.JAVA_HOME)) {
    return process.env.JAVA_HOME;
  }

  return null;
}

if (args.length === 0) {
  console.error('Usage: node scripts/gradle.js <gradle-task> [args...]');
  process.exit(1);
}

const isWindows = process.platform === 'win32';
const windowsJavaHome = isWindows ? findWindowsJavaHome() : null;
const androidDir = path.join(projectRoot, 'android');
const wrapperJar = path.join(androidDir, 'gradle', 'wrapper', 'gradle-wrapper.jar');
const command = isWindows
  ? path.join(windowsJavaHome ?? '', 'bin', 'java.exe')
  : path.join(androidDir, 'gradlew');
const commandArgs = isWindows
  ? ['-classpath', wrapperJar, 'org.gradle.wrapper.GradleWrapperMain', ...args]
  : args;
const env = { ...process.env };

if (isWindows) {
  if (!windowsJavaHome) {
    console.error('No compatible JDK found. Set MOBILE_JAVA_HOME or JAVA_HOME to JDK 17-21.');
    process.exit(1);
  }

  env.JAVA_HOME = windowsJavaHome;
  env.Path = `${path.join(windowsJavaHome, 'bin')};${process.env.Path ?? ''}`;
}

const result = spawnSync(
  command,
  commandArgs,
  {
    cwd: androidDir,
    stdio: 'inherit',
    shell: false,
    env,
  }
);

if (result.error) {
  console.error(result.error.message);
  process.exit(1);
}

process.exit(result.status ?? 1);
