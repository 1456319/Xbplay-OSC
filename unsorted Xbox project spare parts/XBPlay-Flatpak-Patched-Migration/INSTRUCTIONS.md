# XBPlay Flatpak Reassembly Instructions

This environment lacks the required `flatpak-builder` and `bwrap` seccomp capabilities to test the application or run an internal code review via the `jules` CLI.

To recreate the flatpak packaging configuration successfully on a compatible instance, execute the following steps exactly:

1. **Install Build Requirements**:
    Ensure the environment has `flatpak-builder` installed.
    ```bash
    sudo apt update && sudo apt install -y flatpak-builder
    ```

2. **Install Flatpak Runtimes**:
    Ensure the Electron SDK and Freedesktop platforms are available.
    ```bash
    flatpak remote-add --user --if-not-exists flathub https://flathub.org/repo/flathub.flatpakrepo
    flatpak install --user -y flathub org.freedesktop.Sdk//23.08 org.freedesktop.Platform//23.08 org.electronjs.Electron2.BaseApp//23.08
    ```

3. **Clone Flatpak Node Generator**:
    Fetch the missing `flatpak-builder-tools` dependency since the submodules in the git repo were disconnected.
    ```bash
    mkdir -p flatpak && git clone https://github.com/flatpak/flatpak-builder-tools.git flatpak/flatpak-builder-tools
    ```

4. **Install Python Dependencies for Generator**:
    The `flatpak-node-generator.py` requires `aiohttp`, `pyyaml`, and `jsonschema`.
    ```bash
    pip install aiohttp pyyaml jsonschema
    ```

5. **Generate Node Sources**:
    Use the generator to create the `generated-sources.json` dependency map.
    ```bash
    PYTHONPATH=flatpak/flatpak-builder-tools/node python3 -m flatpak_node_generator npm package-lock.json
    mv generated-sources.json flatpak/
    ```

6. **Create the App Runner Script**:
    Create the wrapper that launches the Electron app within the Flatpak sandbox.
    ```bash
    cat << 'WRAPPER_EOF' > flatpak/run.sh
    #!/bin/sh
    exec zypak-wrapper /app/xbplay "$@"
    WRAPPER_EOF
    chmod +x flatpak/run.sh
    ```

7. **Create the Flatpak Manifest**:
    Recreate the missing YAML manifest specifying the Electron 2 BaseApp.
    ```bash
    cat << 'MANIFEST_EOF' > flatpak/net.studio08.xbplay.yml
    app-id: net.studio08.xbplay
    runtime: org.freedesktop.Platform
    runtime-version: '23.08'
    sdk: org.freedesktop.Sdk
    base: org.electronjs.Electron2.BaseApp
    base-version: '23.08'
    command: run.sh
    separate-locales: false
    finish-args:
      - --share=ipc
      - --socket=fallback-x11
      - --socket=wayland
      - --socket=pulseaudio
      - --share=network
      - --device=all
    modules:
      - name: xbplay
        buildsystem: simple
        build-commands:
          - npm_config_cache=$PWD/flatpak-node/npm-cache npm_config_nodedir=$PWD/flatpak-node/node-gyp npm ci --offline && npm run dist-all
          - cp -r dist/linux-unpacked/* /app/
          - install -Dm755 flatpak/run.sh /app/bin/run.sh
        sources:
          - type: dir
            path: ..
          - generated-sources.json
    MANIFEST_EOF
    ```

8. **Update `package.json` Scripts**:
    Add the build commands referenced in the original repo's `README.md`.
    ```python
    import json

    with open("package.json", "r") as f:
        pkg = json.load(f)

    pkg["scripts"] = {
        "build:generate-sources": "node ./flatpak/flatpak-builder-tools/node/flatpak-node-generator.js -r npm package-lock.json",
        "package-flatpak": "rm -rf .flatpak-builder/build && rm -rf archives/build && flatpak-builder --force-clean archives/build flatpak/net.studio08.xbplay.yml",
        "upload-flatpak": "echo 'uploading...'",
        "dist-all": "electron-builder --linux dir"
    }

    with open("package.json", "w") as f:
        json.dump(pkg, f, indent=2)
    ```

9. **Update `.gitignore`**:
    Ignore the newly cloned tools and the auto-generated flatpak output directories.
    ```bash
    cat << 'GITIGNORE_EOF' >> .gitignore
    .flatpak-builder/
    archives/
    flatpak/flatpak-builder-tools
    GITIGNORE_EOF
    ```

10. **Build and Test the Flatpak**:
    Test the package. If the system is capable, this will succeed.
    ```bash
    flatpak-builder --force-clean archives/build flatpak/net.studio08.xbplay.yml
    flatpak-builder --run archives/build flatpak/net.studio08.xbplay.yml run.sh
    ```

11. **Perform Code Review**:
    Finally, run the internal peer review.
    ```bash
    jules remote new --repo . --session "Perform a comprehensive code review of the latest changes on this branch"
    ```
