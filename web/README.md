# Vynce Web & Desktop App

High-fidelity music streaming player with 320kbps audio, synced lyrics, DSP equalizer, and cross-platform desktop integration.

## Desktop App (Windows, macOS, Linux)

### Development Mode
Runs the Vite dev server and launches the Electron desktop app with live reload:
```bash
npm run electron:dev
```

### Packaging & Releases
- **Unpacked Directory Test**: `npm run electron:pack`
- **Windows Installer & Portable (`.exe`)**: `npm run electron:build:win`
- **Linux (`.AppImage` & `.deb`)**: `npm run electron:build:linux`
- **macOS (`.dmg` & `.zip`)**: `npm run electron:build:mac`
- **All Desktop Platforms**: `npm run electron:build:all`

Built binaries will be placed in `web/release-desktop/`.

## Web Development
```bash
npm run dev
npm run build
```

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the Oxlint configuration

If you are developing a production application, we recommend enabling type-aware lint rules by installing `oxlint-tsgolint` and editing `.oxlintrc.json`:

```json
{
  "$schema": "./node_modules/oxlint/configuration_schema.json",
  "plugins": ["react", "typescript", "oxc"],
  "options": {
    "typeAware": true
  },
  "rules": {
    "react/rules-of-hooks": "error",
    "react/only-export-components": ["warn", { "allowConstantExport": true }]
  }
}
```

See the [Oxlint rules documentation](https://oxc.rs/docs/guide/usage/linter/rules) for the full list of rules and categories.
