# NixOS module for SAPL Node PDP server
#
# Provides a fully declarative deployment of the SAPL Node Policy Decision
# Point server. After `nixos-rebuild switch`, the PDP is running as a systemd
# service with policies deployed and the sapl-node CLI available on PATH.
#
# ## Configuration modes
#
# The server configuration (application.yml) can be provided in two ways:
#
#   1. Generated from module options (default). The typed options (port,
#      bindAddress, tls) and freeform `settings` attrset are merged into
#      a single application.yml. Any Spring Boot property can be set via
#      `settings`.
#
#   2. User-supplied file via `configFile`. All generated config is bypassed.
#      Use this when you manage application.yml outside of Nix.
#
# In both modes, `extraSettingsFiles` loads additional YAML files at runtime
# for secrets that must not be stored in the world-readable Nix store.
#
# ## Policy source modes (mutually exclusive)
#
# Choose one of three ways to supply policies to the PDP:
#
#   policies      Nix-managed inline policies. At build time, a .saplbundle
#                 is created from the policy definitions and pdpConfig. On
#                 rebuild, the bundle file is atomically replaced (single
#                 mv) and the PDP hot-reloads in one operation with no
#                 intermediate states. Optionally signed at activation time
#                 using a runtime key from sops-nix or agenix.
#
#   dataDir       Unmanaged directory. Nix points the PDP at this path and
#                 never touches its contents. You manage policies, bundles,
#                 and pdp.json yourself using the sapl-node CLI, CI/CD
#                 pipelines, or any other mechanism. The PDP watches the
#                 directory for changes.
#
#   bundleSources Remote bundle endpoints. The PDP fetches .saplbundle files
#                 over HTTP with ETag-based change detection.
#
# ## Security
#
# TLS is enabled by default. Certificate and key paths are validated to
# reject Nix store paths (which are world-readable). Secrets (API key
# hashes, TLS passwords, OAuth client secrets) should be placed in files
# referenced by `extraSettingsFiles`, managed by sops-nix or agenix.
#
# Bundle signing uses a two-phase approach: the bundle is built unsigned
# in the pure Nix build, then signed at service activation time using a
# runtime private key. The private key never enters the Nix store.
#
# ## Systemd services
#
# sapl-node.service              The PDP server. Restarts only on config
#                                changes (port, TLS, auth settings).
#
# sapl-node-deploy-bundle.service  Oneshot that atomically deploys the
#                                  managed bundle. Runs before the PDP on
#                                  first boot and on policy changes without
#                                  restarting the PDP. The PDP hot-reloads
#                                  from the filesystem change.
#
# ## Example: minimal dev setup
#
#   services.sapl-node = {
#     enable = true;
#     port = 8080;
#     tls.enable = false;
#     settings."io.sapl.node".allowNoAuth = true;
#     policies.tick = ''
#       policy "tick"
#       permit
#         time.secondOf(<time.now>) % 5 == 0
#     '';
#   };
#
# ## Example: production with signed bundles and sops secrets
#
#   services.sapl-node = {
#     enable = true;
#     port = 7443;
#     bindAddress = "0.0.0.0";
#     openFirewall = true;
#     tls = {
#       enable = true;
#       certFile = "/run/secrets/sapl-node/cert.pem";
#       keyFile = "/run/secrets/sapl-node/key.pem";
#     };
#     extraSettingsFiles = [ "/run/secrets/sapl-node/credentials.yml" ];
#     signingKey = "/run/secrets/sapl-node/signing-key.pem";
#     verificationKey = "/etc/sapl-node/verification-key.pub";
#     policies = {
#       allow-read = ''
#         policy "allow-read"
#         permit action == "read"
#       '';
#       deny-all = ''
#         policy "deny-all"
#         deny
#       '';
#     };
#   };
#
# ## Example: unmanaged directory
#
#   services.sapl-node = {
#     enable = true;
#     tls.enable = false;
#     dataDir = "/var/lib/sapl-node/data";
#   };

flake:

{ config, lib, pkgs, ... }:

let
  cfg = config.services.sapl-node;
  settingsFormat = pkgs.formats.yaml { };
  jsonFormat = pkgs.formats.json { };
  sapl-node = cfg.package;

  # Whether to generate application.yml from module options or use the
  # user-supplied configFile as-is.
  useGeneratedConfig = cfg.configFile == null;

  # Exactly one of these can be true. Enforced by assertion below.
  managedPolicies = cfg.policies != { };
  unmanagedDir = cfg.dataDir != null;
  remoteBundles = cfg.bundleSources != { };

  # Runtime path where managed bundles are deployed.
  # Lives inside StateDirectory so DynamicUser can write to it.
  bundlesPath = "/var/lib/sapl-node/bundles";

  # Build an unsigned .saplbundle from inline policies at Nix build time.
  #
  # The derivation runs `sapl-node bundle create` to package all .sapl files
  # and the pdp.json into a single archive. This guarantees that:
  #   - The entire policy set is validated at build time
  #   - Deployment is a single file replacement (atomic via mv)
  #   - The PDP reloads all policies in one operation (no intermediate states)
  #
  # The bundle is unsigned here. Signing happens at activation time if
  # signingKey is configured, keeping the private key out of the Nix store.
  managedBundle = pkgs.runCommand "sapl-node-managed.saplbundle" { } (''
    mkdir -p input $out
    cp ${jsonFormat.generate "pdp.json" cfg.pdpConfig} input/pdp.json
  '' + lib.concatStrings (lib.mapAttrsToList (name: content: ''
    cp ${pkgs.writeText "${name}.sapl" content} input/${name}.sapl
  '') cfg.policies) + ''
    ${sapl-node}/bin/sapl-node bundle create -i input -o $out/managed.saplbundle
  '');

  # Shell fragment that signs the deployed bundle using a runtime key.
  # Only included when signingKey is configured. Runs at activation time
  # (not at Nix build time) so the private key never enters the Nix store.
  signBundleScript = lib.optionalString (cfg.signingKey != null) ''
    ${sapl-node}/bin/sapl-node bundle sign \
      -b ${bundlesPath}/managed.saplbundle \
      -k ${cfg.signingKey} \
      --key-id ${lib.escapeShellArg cfg.signingKeyId}
  '';

  # Merge typed convenience options and policy source configuration into
  # the freeform settings attrset, then generate application.yml.
  #
  # Precedence: typed options (port, tls, etc.) override freeform settings.
  # The policy source mode (managed bundles, unmanaged dir, remote bundles)
  # sets the appropriate io.sapl.pdp.embedded properties automatically.
  effectiveSettings = lib.recursiveUpdate cfg.settings ({
    server = {
      port = cfg.port;
      address = cfg.bindAddress;
      ssl.enabled = cfg.tls.enable;
    } // lib.optionalAttrs (cfg.tls.enable && cfg.tls.certFile != null) {
      ssl.certificate = cfg.tls.certFile;
      ssl.certificate-private-key = cfg.tls.keyFile;
    };
  } // lib.optionalAttrs managedPolicies {
    "io.sapl.pdp.embedded" = {
      pdp-config-type = "BUNDLES";
      policies-path = bundlesPath;
      config-path = bundlesPath;
      # When signing is not configured, allow unsigned bundles. The bundle
      # integrity is guaranteed by the Nix store hash. When signing IS
      # configured, enforce verification so the PDP rejects tampered bundles.
      bundle-security.default-security-policy =
        if cfg.signingKey != null then "ENFORCE_VERIFICATION" else "ALLOW_UNSIGNED";
    } // lib.optionalAttrs (cfg.verificationKey != null) {
      bundle-security.verification-keys = [{
        key-id = cfg.signingKeyId;
        algorithm = "Ed25519";
        key = cfg.verificationKey;
      }];
    };
  } // lib.optionalAttrs unmanagedDir {
    "io.sapl.pdp.embedded" = {
      pdp-config-type = "RESOURCES";
      config-path = cfg.dataDir;
      policies-path = cfg.dataDir;
    };
  } // lib.optionalAttrs remoteBundles {
    "io.sapl.pdp.embedded".pdp-config-type = "BUNDLES";
  });

  generatedConfigFile = settingsFormat.generate "application.yml" effectiveSettings;

  # The config file passed to Spring Boot. Either the Nix-generated one
  # or a user-supplied path.
  activeConfigFile = if useGeneratedConfig then generatedConfigFile else cfg.configFile;

  # Rejects Nix store paths for TLS cert/key options. Store paths are
  # world-readable, which is unacceptable for private keys.
  assertNotStorePath = name: v:
    lib.assertMsg (v == null || !(lib.hasPrefix "/nix/store" v))
      "services.sapl-node.tls.${name} must not be a Nix store path (world-readable)";

in
{
  options.services.sapl-node = {

    enable = lib.mkEnableOption "SAPL Node PDP server";

    package = lib.mkOption {
      type = lib.types.package;
      default = flake.packages.${pkgs.system}.sapl-node;
      defaultText = lib.literalExpression "flake.packages.\${pkgs.system}.sapl-node";
      description = ''
        The sapl-node package to use. Defaults to the binary from this flake
        for the current platform. Override to use a locally built version.
      '';
    };

    # --- Server configuration ---

    configFile = lib.mkOption {
      type = lib.types.nullOr lib.types.str;
      default = null;
      description = ''
        Path to a user-supplied application.yml file. When set, the generated
        configuration from settings, port, bindAddress, and tls options is
        ignored entirely. Use this when you prefer to manage the full
        Spring Boot configuration yourself. extraSettingsFiles still works
        alongside this for runtime secret injection.
      '';
      example = "/etc/sapl-node/application.yml";
    };

    port = lib.mkOption {
      type = lib.types.port;
      default = 7443;
      description = "HTTP(S) listen port.";
    };

    bindAddress = lib.mkOption {
      type = lib.types.str;
      default = "127.0.0.1";
      description = ''
        Network interface to bind to.
        Use 127.0.0.1 when running as a sidecar or behind a reverse proxy.
        Use 0.0.0.0 for direct access or container deployments.
      '';
    };

    openFirewall = lib.mkOption {
      type = lib.types.bool;
      default = false;
      description = "Open the listen port in the firewall.";
    };

    # --- TLS ---

    tls = {
      enable = lib.mkOption {
        type = lib.types.bool;
        default = true;
        description = ''
          Enable TLS. Enabled by default for production safety. Set to false
          for local development or when TLS is terminated by a reverse proxy.
        '';
      };

      certFile = lib.mkOption {
        type = lib.types.nullOr lib.types.str;
        default = null;
        description = ''
          Path to the TLS certificate file on the runtime filesystem.
          Must not be a Nix store path (store paths are world-readable).
          Manage with sops-nix, agenix, or manual placement.
        '';
        apply = v: assert assertNotStorePath "certFile" v; v;
      };

      keyFile = lib.mkOption {
        type = lib.types.nullOr lib.types.str;
        default = null;
        description = ''
          Path to the TLS private key file on the runtime filesystem.
          Must not be a Nix store path (store paths are world-readable).
          Manage with sops-nix, agenix, or manual placement.
        '';
        apply = v: assert assertNotStorePath "keyFile" v; v;
      };
    };

    # --- Freeform settings ---

    settings = lib.mkOption {
      type = lib.types.submodule {
        freeformType = settingsFormat.type;
      };
      default = { };
      description = ''
        Freeform application.yml settings as a Nix attribute set.
        Any Spring Boot or SAPL Node property can be set here. Nested
        attribute sets map to YAML sections (e.g., `server.port = 8080`
        becomes `server: port: 8080`).

        Typed convenience options (port, bindAddress, tls) are merged
        on top of these settings with higher priority. Policy source
        configuration (pdp-config-type, paths) is set automatically
        based on the chosen policy source mode.

        Ignored when configFile is set.
      '';
      example = lib.literalExpression ''
        {
          "io.sapl.node" = {
            allowApiKeyAuth = true;
            allowBasicAuth = false;
          };
          "io.sapl.pdp.embedded" = {
            metrics-enabled = true;
            print-text-report = true;
          };
          management.endpoints.web.exposure.include = "health,info,prometheus";
        }
      '';
    };

    extraSettingsFiles = lib.mkOption {
      type = lib.types.listOf lib.types.str;
      default = [ ];
      description = ''
        Additional YAML config files loaded at runtime via Spring Boot's
        spring.config.additional-location mechanism. Properties in these
        files override the generated configuration.

        Use this for secrets that must not end up in the world-readable
        Nix store: API key hashes (Argon2), TLS keystore passwords,
        OAuth2 client secrets, database credentials.

        Works with both generated config and user-supplied configFile.
        Managed by sops-nix, agenix, or manual placement.
      '';
      example = [ "/run/secrets/sapl-node/credentials.yml" ];
    };

    # --- PDP configuration ---

    pdpConfig = lib.mkOption {
      type = jsonFormat.type;
      default = {
        algorithm = {
          votingMode = "PRIORITY_PERMIT";
          defaultDecision = "DENY";
          errorHandling = "ABSTAIN";
        };
        variables = { };
      };
      description = ''
        PDP configuration included as pdp.json inside the managed bundle.
        Controls the combining algorithm, default decision, error handling,
        and PDP-scoped variables.

        Only used with the policies option. Ignored when dataDir or
        bundleSources is set.
      '';
    };

    # --- Bundle signing ---

    signingKey = lib.mkOption {
      type = lib.types.nullOr lib.types.str;
      default = null;
      description = ''
        Path to an Ed25519 private key file (PEM, PKCS8) for signing
        the managed bundle.

        The bundle is built unsigned during the pure Nix build phase.
        At service activation, the sapl-node-deploy-bundle oneshot signs
        the bundle using this runtime key before placing it in the bundles
        directory. This two-phase approach keeps the private key out of
        the world-readable Nix store.

        When null (default), bundles are deployed unsigned and the PDP
        security policy is set to ALLOW_UNSIGNED.

        Generate a keypair with: sapl-node bundle keygen -o mykey
        Manage the private key with sops-nix or agenix.
      '';
      example = "/run/secrets/sapl-node/signing-key.pem";
    };

    verificationKey = lib.mkOption {
      type = lib.types.nullOr lib.types.str;
      default = null;
      description = ''
        Path to the Ed25519 public key file (PEM, X.509) for bundle
        signature verification. Required when signingKey is set.

        Unlike the private key, this can safely live in the Nix store
        or at a well-known filesystem path. The PDP uses it to verify
        bundle signatures on load.
      '';
      example = "/etc/sapl-node/verification-key.pub";
    };

    signingKeyId = lib.mkOption {
      type = lib.types.str;
      default = "default";
      description = ''
        Key identifier stored in the bundle manifest. Must match the
        key-id configured in the PDP's verification-keys list. The
        module sets this up automatically when signingKey is configured.
      '';
    };

    # --- Policy sources (mutually exclusive) ---

    policies = lib.mkOption {
      type = lib.types.attrsOf lib.types.str;
      default = { };
      description = ''
        Nix-managed SAPL policy definitions. Each attribute name becomes
        a .sapl filename and each value is the policy content.

        At build time, all policies and the pdpConfig are packaged into a
        single .saplbundle archive using `sapl-node bundle create`. This
        guarantees that policy changes are deployed atomically: the entire
        bundle file is replaced in a single rename operation, and the PDP
        reloads all policies at once with no intermediate states.

        On rebuild, only the sapl-node-deploy-bundle oneshot re-runs.
        The PDP service itself is not restarted; it detects the file
        change and hot-reloads the new bundle.
      '';
      example = lib.literalExpression ''
        {
          allow-read = '''
            policy "allow-read"
            permit action == "read"
          ''';
          deny-write = '''
            policy "deny-write"
            deny action == "write"
          ''';
        }
      '';
    };

    dataDir = lib.mkOption {
      type = lib.types.nullOr lib.types.str;
      default = null;
      description = ''
        Path to an unmanaged data directory. The PDP reads .sapl files,
        .saplbundle files, and pdp.json directly from this path and
        watches for changes. Nix does not create, modify, or delete
        anything in this directory.

        You manage the contents yourself using the sapl-node CLI
        (bundle create, bundle sign), CI/CD pipelines, configuration
        management, or manual file placement.

        Mutually exclusive with policies and bundleSources.
      '';
      example = "/var/lib/sapl-node/data";
    };

    bundleSources = lib.mkOption {
      type = lib.types.attrsOf (lib.types.submodule {
        options = {
          url = lib.mkOption {
            type = lib.types.str;
            description = ''
              URL of the remote bundle server endpoint. The PDP polls
              this URL for .saplbundle files using HTTP with ETag-based
              change detection.
            '';
          };

          repeatInterval = lib.mkOption {
            type = lib.types.str;
            default = "60s";
            description = ''
              Polling interval for checking bundle updates. Specified as
              a duration string (e.g., "30s", "5m", "1h").
            '';
          };

          verificationKey = lib.mkOption {
            type = lib.types.nullOr lib.types.str;
            default = null;
            description = ''
              Path to an Ed25519 public key file for verifying the
              signature of bundles fetched from this source.
            '';
          };
        };
      });
      default = { };
      description = ''
        Remote bundle sources. Each attribute defines a bundle endpoint
        that the PDP polls for policy updates. Enables the BUNDLES
        pdp-config-type.

        Mutually exclusive with policies and dataDir.
      '';
    };
  };

  config = lib.mkIf cfg.enable {

    # --- Validation ---

    assertions = [
      {
        # Exactly zero or one policy source mode may be active.
        assertion = lib.count (x: x) [ managedPolicies unmanagedDir remoteBundles ] <= 1;
        message = "services.sapl-node: policies, dataDir, and bundleSources are mutually exclusive. Choose one policy source.";
      }
      {
        # When using generated config with TLS enabled, a certificate must
        # be provided either via the typed options or via freeform settings.
        assertion = !useGeneratedConfig || !cfg.tls.enable
          || (cfg.tls.certFile != null && cfg.tls.keyFile != null)
          || cfg.settings ? server && cfg.settings.server ? ssl && cfg.settings.server.ssl ? key-store;
        message = "services.sapl-node: TLS is enabled but no certificate is configured. Set tls.certFile and tls.keyFile, configure a keystore in settings, or set tls.enable = false.";
      }
      {
        # A signing key is useless without the corresponding verification key.
        assertion = (cfg.signingKey != null) -> (cfg.verificationKey != null);
        message = "services.sapl-node: signingKey requires verificationKey to be set.";
      }
    ];

    # --- System integration ---

    # Make the sapl-node CLI available system-wide for bundle management
    # (bundle create, sign, verify, inspect, keygen) and credential
    # generation (generate basic, generate apikey).
    environment.systemPackages = [ cfg.package ];

    # --- Bundle deployment service ---

    # Oneshot service that atomically deploys the Nix-managed bundle.
    # Only created when the `policies` option is used.
    #
    # Deployment is atomic: the bundle is written to a temporary file on
    # the same filesystem, optionally signed, then renamed into place.
    # The rename(2) syscall is atomic on POSIX filesystems, so the PDP
    # never sees a partially written file.
    #
    # This service runs:
    #   - Before sapl-node.service on first boot (requiredBy + before)
    #   - On rebuild when policies change (restartTriggers on the bundle
    #     derivation), WITHOUT restarting the PDP service
    #
    # The PDP's filesystem watcher detects the file change and hot-reloads
    # the bundle. Since it is a single file, the reload is one atomic
    # operation with no intermediate policy states.
    systemd.services.sapl-node-deploy-bundle = lib.mkIf managedPolicies {
      description = "Deploy SAPL Node managed bundle";
      before = [ "sapl-node.service" ];
      requiredBy = [ "sapl-node.service" ];
      wantedBy = [ "multi-user.target" ];
      restartTriggers = [ managedBundle ];

      serviceConfig = {
        Type = "oneshot";
        RemainAfterExit = true;
        ExecStart = pkgs.writeShellScript "sapl-node-deploy-bundle" ''
          # Ensure the bundles directory exists.
          mkdir -p ${bundlesPath}

          # Write the bundle to a temporary file on the same filesystem.
          # Using the same filesystem guarantees that mv is a rename(2),
          # which is atomic.
          tmp=$(mktemp ${bundlesPath}/.managed.XXXXXX.saplbundle)
          cp ${managedBundle}/managed.saplbundle "$tmp"
          chmod 644 "$tmp"

          # Optionally sign the bundle using the runtime signing key.
          # This runs only when signingKey is configured. The private
          # key is read from a sops/agenix managed path at runtime,
          # never from the Nix store.
          ${signBundleScript}

          # Atomic replacement. rename(2) on the same filesystem is a
          # single directory entry update. The PDP either sees the old
          # bundle or the new one, never a partial state.
          mv "$tmp" ${bundlesPath}/managed.saplbundle
        '';
        ReadWritePaths = [ "/var/lib/sapl-node" ];
      };
    };

    # --- PDP server service ---

    systemd.services.sapl-node = {
      description = "SAPL Node PDP Server";
      after = [ "network.target" ]
        ++ lib.optional managedPolicies "sapl-node-deploy-bundle.service";
      wantedBy = [ "multi-user.target" ];

      # The service restarts only when the application.yml config changes
      # (port, TLS, auth settings, etc.). Policy changes are handled by
      # the deploy-bundle oneshot without restarting the PDP.
      restartTriggers = [ activeConfigFile ];

      serviceConfig =
        let
          # Build the Spring Boot config location arguments.
          # The primary config is always the generated or user-supplied file.
          # Extra settings files are appended via additional-location for
          # runtime secret injection.
          extraLocations = lib.concatMapStringsSep ","
            (f: "file:${f}") cfg.extraSettingsFiles;
          springArgs = lib.concatStringsSep " " ([
            "--spring.config.location=file:${activeConfigFile}"
          ] ++ lib.optional (cfg.extraSettingsFiles != [ ])
            "--spring.config.additional-location=${extraLocations}");
        in
        {
          Type = "simple";

          # DynamicUser allocates a transient UID/GID. No static user
          # definition needed. State persists across restarts because
          # StateDirectory is preserved.
          DynamicUser = true;

          # /var/lib/sapl-node - persistent state (bundles, caches)
          StateDirectory = "sapl-node";
          # /run/sapl-node - ephemeral per-boot data
          RuntimeDirectory = "sapl-node";
          RuntimeDirectoryMode = "0700";

          ExecStart = "${cfg.package}/bin/sapl-node ${springArgs}";

          Restart = "on-failure";
          RestartSec = "5s";

          # --- Hardening ---
          # Prevent privilege escalation via setuid/setgid binaries.
          NoNewPrivileges = true;
          # Mount the root filesystem read-only. Only explicit
          # ReadWritePaths are writable.
          ProtectSystem = "strict";
          # Hide /home, /root, and /run/user from the service.
          ProtectHome = true;
          # Deny access to physical devices (/dev).
          PrivateDevices = true;
          # Use a private /tmp mount.
          PrivateTmp = true;
          # Prevent core dumps that could leak secrets from memory.
          LimitCORE = 0;

          # Writable paths. The state directory is always writable.
          # When using an unmanaged data directory, it must also be writable
          # so the PDP can maintain its internal state files.
          ReadWritePaths = [ "/var/lib/sapl-node" ]
            ++ lib.optional unmanagedDir cfg.dataDir;

          # Allow binding to privileged ports (< 1024) only when needed.
          AmbientCapabilities =
            lib.optional (cfg.port < 1024) "CAP_NET_BIND_SERVICE";
        };
    };

    # --- Firewall ---

    networking.firewall.allowedTCPPorts =
      lib.mkIf cfg.openFirewall [ cfg.port ];
  };
}
