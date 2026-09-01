$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$frontend = Join-Path $repoRoot 'tools/language-editor/frontend'
$backend = Join-Path $repoRoot 'tools/language-editor/backend'

if (-not (Test-Path (Join-Path $frontend 'node_modules'))) {
    npm --prefix $frontend ci
}
npm --prefix $frontend run build
& (Join-Path $repoRoot 'mvnw.cmd') -q -f (Join-Path $backend 'pom.xml') package
java -jar (Join-Path $backend 'target/tropicube-language-editor.jar')
