const fs = require("fs");
const path = require("path");
const { spawnSync, spawn } = require("child_process");

const root = path.resolve(__dirname, "..");
const knownJdk = "C:\\Program Files\\Microsoft\\jdk-17.0.21.102-hotspot";
const jdk = process.env.JAVA_HOME || (fs.existsSync(path.join(knownJdk, "bin", "javac.exe")) ? knownJdk : "");
const executable = (name) => jdk
  ? path.join(jdk, "bin", `${name}${process.platform === "win32" ? ".exe" : ""}`)
  : name;
const javac = executable("javac");
const java = executable("java");

fs.mkdirSync(path.join(root, "out"), { recursive: true });
const sources = fs.readdirSync(path.join(root, "src"))
  .filter((file) => file.endsWith(".java"))
  .map((file) => path.join(root, "src", file));

const build = spawnSync(javac, ["-encoding", "UTF-8", "-d", path.join(root, "out"), ...sources], {
  cwd: root,
  stdio: "inherit"
});

if (build.status !== 0) process.exit(build.status ?? 1);
const command = process.argv[2];
if (command !== "dev" && command !== "web") process.exit(0);

const mainClass = command === "web" ? "WebMain" : "Main";
const app = spawn(java, ["--add-modules", "jdk.httpserver", "-cp", "out", mainClass], {
  cwd: root,
  stdio: "inherit",
  detached: false
});

app.on("error", (error) => {
  console.error(error.message);
  process.exit(1);
});
