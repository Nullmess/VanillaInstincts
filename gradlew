#!/bin/sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

# Fast repository maintenance command. Cleaning generated outputs must not need
# a Gradle distribution, Java toolchain or network connection. A standalone
# `./gradlew clean` is intentionally handled here as well: this repository is
# multi-version, so there is no useful reason to boot a specific Gradle/JDK just
# to remove generated build directories.
if [ "$#" -eq 1 ] && [ "${1:-}" = "clean" ]; then
    VI_CLEAN_COUNT=$(find "$APP_HOME" \
        \( -path "$APP_HOME/.gradle" -o -path "$APP_HOME/.git" \) -prune -o \
        -type d -name build -prune -print | wc -l | tr -d ' ')
    find "$APP_HOME" \
        \( -path "$APP_HOME/.gradle" -o -path "$APP_HOME/.git" \) -prune -o \
        -type d -name build -prune -exec rm -rf {} +
    if [ -e "$APP_HOME/.gradle/root-project" ]; then
        rm -rf "$APP_HOME/.gradle/root-project"
        VI_CLEAN_COUNT=$((VI_CLEAN_COUNT + 1))
    fi
    printf 'CLEAN: removed %s generated build director%s; caches/toolchains preserved.\n' \
        "$VI_CLEAN_COUNT" "$(if [ "$VI_CLEAN_COUNT" -eq 1 ]; then printf y; else printf ies; fi)"
    exit 0
fi

case "${1:-}" in
    cleanAll|cleanAllBuilds)
        VI_CLEAN_COUNT=$(find "$APP_HOME" \
            \( -path "$APP_HOME/.gradle" -o -path "$APP_HOME/.git" \) -prune -o \
            -type d -name build -prune -print | wc -l | tr -d ' ')
        find "$APP_HOME" \
            \( -path "$APP_HOME/.gradle" -o -path "$APP_HOME/.git" \) -prune -o \
            -type d -name build -prune -exec rm -rf {} +
        if [ -e "$APP_HOME/.gradle/root-project" ]; then
            rm -rf "$APP_HOME/.gradle/root-project"
            VI_CLEAN_COUNT=$((VI_CLEAN_COUNT + 1))
        fi
        printf 'CLEAN_ALL: removed %s generated build director%s; caches/toolchains preserved.\n' \
            "$VI_CLEAN_COUNT" "$(if [ "$VI_CLEAN_COUNT" -eq 1 ]; then printf y; else printf ies; fi)"
        exit 0
        ;;
esac

# Build every declared Forge/NeoForge target through this wrapper, one target at
# a time. This is intentionally wrapper-level orchestration: historical targets
# need different Gradle/Java bootstraps and cannot safely share one Gradle JVM.
vi_list_build_targets() {
    VI_BUILD_FILTER="$1"
    VI_TARGET_CATALOG="$APP_HOME/gradle/minecraft-targets.json"
    if [ ! -f "$VI_TARGET_CATALOG" ]; then
        printf 'ERROR: missing target catalog: %s\n' "$VI_TARGET_CATALOG" >&2
        return 1
    fi

    # The targets array is the last top-level array in the catalog. Extract only
    # target IDs here so buildAll has no Python/jq dependency. sort -V gives a
    # deterministic Minecraft-version order on the Linux environments supported
    # by this project.
    VI_TARGETS=$(sed -n '/"targets"[[:space:]]*:/,$p' "$VI_TARGET_CATALOG" \
        | grep -E '"id"[[:space:]]*:[[:space:]]*"(forge|neoforge)-' \
        | sed -E 's/.*"id"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/' \
        | sort -V)

    case "$VI_BUILD_FILTER" in
        forge) printf '%s\n' "$VI_TARGETS" | grep '^forge-' ;;
        neoforge) printf '%s\n' "$VI_TARGETS" | grep '^neoforge-' ;;
        all) printf '%s\n' "$VI_TARGETS" ;;
        *) printf 'ERROR: unknown buildAll filter: %s\n' "$VI_BUILD_FILTER" >&2; return 2 ;;
    esac
}

vi_build_all_log_has_network_failure() {
    VI_NETWORK_LOG="$1"
    grep -Eiq 'Could not (GET|HEAD|get resource|download|resolve)|Failed to download( the file from)?|Failed to execute task `download[A-Za-z]*|UnknownHostException|Temporary failure in name resolution|Name or service not known|Connection (reset|timed out|refused)|ConnectException:.*timed out|HttpConnectTimeoutException|HTTP connect timed out|Read timed out|HTTP/[0-9.]+ (408|425|429|500|502|503|504)|Remote host terminated|Connection closed|: Try again' "$VI_NETWORK_LOG"
}

vi_build_all_run_target() {
    VI_RUN_TARGET="$1"
    shift

    VI_RUN_ATTEMPT=1
    VI_RUN_MAX_ATTEMPTS=3
    VI_RUN_LAST_RC=1

    while [ "$VI_RUN_ATTEMPT" -le "$VI_RUN_MAX_ATTEMPTS" ]; do
        VI_RUN_LOG="${TMPDIR:-/tmp}/vanillainstincts-buildAll-$$-${VI_BUILD_INDEX}-${VI_RUN_ATTEMPT}.log"
        VI_RUN_RC_FILE="$VI_RUN_LOG.rc"
        rm -f "$VI_RUN_LOG" "$VI_RUN_RC_FILE"

        # Stream the real Gradle output while keeping a short-lived copy so a
        # deterministic compile failure is not retried, but transient Maven /
        # Mojang / NeoForge download failures can recover automatically.
        (
            set +e
            "$APP_HOME/gradlew" build "-Ptarget=$VI_RUN_TARGET" "$@"
            VI_CHILD_RC=$?
            printf '%s\n' "$VI_CHILD_RC" > "$VI_RUN_RC_FILE"
            exit 0
        ) 2>&1 | tee "$VI_RUN_LOG"

        if [ -f "$VI_RUN_RC_FILE" ]; then
            VI_RUN_LAST_RC=$(sed -n '1p' "$VI_RUN_RC_FILE")
        else
            # A missing rc file normally means Ctrl-C terminated the child
            # before it could report its status.
            VI_RUN_LAST_RC=130
        fi
        rm -f "$VI_RUN_RC_FILE"
        case "$VI_RUN_LAST_RC" in
            ''|*[!0-9]*) VI_RUN_LAST_RC=130 ;;
        esac

        if [ "$VI_RUN_LAST_RC" -eq 0 ]; then
            rm -f "$VI_RUN_LOG"
            return 0
        fi

        case "$VI_RUN_LAST_RC" in
            130|137|143)
                rm -f "$VI_RUN_LOG"
                return "$VI_RUN_LAST_RC"
                ;;
        esac

        if [ "$VI_RUN_ATTEMPT" -lt "$VI_RUN_MAX_ATTEMPTS" ] \
                && vi_build_all_log_has_network_failure "$VI_RUN_LOG"; then
            VI_RUN_DELAY=$((VI_RUN_ATTEMPT * 5))
            printf '\nBUILD_ALL: %s failed because a remote dependency/download was unavailable.\n' \
                "$VI_RUN_TARGET" >&2
            printf 'BUILD_ALL: retrying %s in %ss (attempt %s/%s).\n' \
                "$VI_RUN_TARGET" "$VI_RUN_DELAY" "$((VI_RUN_ATTEMPT + 1))" "$VI_RUN_MAX_ATTEMPTS" >&2
            rm -f "$VI_RUN_LOG"
            sleep "$VI_RUN_DELAY"
            VI_RUN_ATTEMPT=$((VI_RUN_ATTEMPT + 1))
            continue
        fi

        # Preserve the complete output of the final failed attempt so buildAll
        # can print every failure after the OK/FAILED summary. Retry attempts
        # caused by transient network errors are intentionally omitted; only
        # the terminal failure for each target is collected.
        if [ -n "${VI_BUILD_ERRORS:-}" ]; then
            {
                printf '\n================================================================\n'
                printf 'BUILD_ALL ERROR: %s (exit %s)\n' "$VI_RUN_TARGET" "$VI_RUN_LAST_RC"
                printf '================================================================\n'
                cat "$VI_RUN_LOG"
                printf '\n'
            } >> "$VI_BUILD_ERRORS"
        fi

        rm -f "$VI_RUN_LOG"
        return "$VI_RUN_LAST_RC"
    done

    return "$VI_RUN_LAST_RC"
}

vi_build_all() {
    VI_BUILD_FILTER="$1"
    shift

    for VI_EXTRA_ARG in "$@"; do
        case "$VI_EXTRA_ARG" in
            -Ptarget=*)
                printf '%s\n' 'ERROR: buildAll selects targets automatically; do not pass -Ptarget.' >&2
                return 2
                ;;
        esac
    done

    VI_BUILD_TARGETS=$(vi_list_build_targets "$VI_BUILD_FILTER") || return $?
    VI_BUILD_TOTAL=$(printf '%s\n' "$VI_BUILD_TARGETS" | sed '/^[[:space:]]*$/d' | wc -l | tr -d ' ')
    if [ "$VI_BUILD_TOTAL" -eq 0 ]; then
        printf 'ERROR: no %s targets found in gradle/minecraft-targets.json.\n' "$VI_BUILD_FILTER" >&2
        return 2
    fi

    mkdir -p "$APP_HOME/.gradle"
    VI_BUILD_SUMMARY="$APP_HOME/.gradle/buildAll-summary.$$"
    VI_BUILD_ERRORS="$APP_HOME/.gradle/buildAll-errors.$$"
    : > "$VI_BUILD_SUMMARY"
    : > "$VI_BUILD_ERRORS"

    # Ctrl-C is intentionally target-local for buildAll: the foreground child
    # is interrupted, then orchestration continues with the next target. Do not
    # delete the accumulated summary on INT (the old behavior erased every
    # result collected before the interrupt).
    trap 'rm -f "$VI_BUILD_SUMMARY" "$VI_BUILD_ERRORS"' EXIT
    trap 'rm -f "$VI_BUILD_SUMMARY" "$VI_BUILD_ERRORS"; exit 129' HUP
    trap 'rm -f "$VI_BUILD_SUMMARY" "$VI_BUILD_ERRORS"; exit 143' TERM
    trap 'printf "\\nBUILD_ALL: current target interrupted; preserving summary and continuing.\\n" >&2' INT

    VI_BUILD_INDEX=0
    VI_BUILD_OK=0
    VI_BUILD_FAILED=0

    printf 'BUILD_ALL: %s target(s) selected (%s).\n' "$VI_BUILD_TOTAL" "$VI_BUILD_FILTER"
    printf '%s\n' 'BUILD_ALL: each target uses its own validated wrapper/toolchain; build outputs are preserved.'
    printf '%s\n' 'BUILD_ALL: transient dependency/download failures are retried up to 3 times.'

    for VI_BUILD_TARGET in $VI_BUILD_TARGETS; do
        VI_BUILD_INDEX=$((VI_BUILD_INDEX + 1))
        printf '\n================================================================\n'
        printf 'BUILD_ALL [%s/%s]: %s\n' "$VI_BUILD_INDEX" "$VI_BUILD_TOTAL" "$VI_BUILD_TARGET"
        printf '================================================================\n'

        if vi_build_all_run_target "$VI_BUILD_TARGET" "$@"; then
            VI_BUILD_OK=$((VI_BUILD_OK + 1))
            printf 'OK     %s\n' "$VI_BUILD_TARGET" >> "$VI_BUILD_SUMMARY"
        else
            VI_BUILD_RC=$?
            VI_BUILD_FAILED=$((VI_BUILD_FAILED + 1))
            if [ "$VI_BUILD_RC" -eq 130 ]; then
                printf 'INTERRUPTED %s (exit %s)\n' "$VI_BUILD_TARGET" "$VI_BUILD_RC" >> "$VI_BUILD_SUMMARY"
            else
                printf 'FAILED %s (exit %s)\n' "$VI_BUILD_TARGET" "$VI_BUILD_RC" >> "$VI_BUILD_SUMMARY"
            fi
        fi
    done

    printf '\n================ BUILD_ALL SUMMARY ================\n'
    cat "$VI_BUILD_SUMMARY"
    printf '%s\n' '---------------------------------------------------'
    printf 'Total: %s | Successful: %s | Failed: %s\n' \
        "$VI_BUILD_TOTAL" "$VI_BUILD_OK" "$VI_BUILD_FAILED"

    if [ "$VI_BUILD_FAILED" -ne 0 ]; then
        printf '\n================ BUILD_ALL ERROR LOGS ================\n'
        if [ -s "$VI_BUILD_ERRORS" ]; then
            cat "$VI_BUILD_ERRORS"
        else
            printf '%s\n' 'No detailed failed-target output was captured.'
        fi
        printf '%s\n' '================ END BUILD_ALL ERROR LOGS ============'
    fi

    rm -f "$VI_BUILD_SUMMARY" "$VI_BUILD_ERRORS"
    trap - EXIT HUP INT TERM

    if [ "$VI_BUILD_FAILED" -ne 0 ]; then
        printf '%s\n' 'BUILD_ALL: completed with failures.' >&2
        return 1
    fi
    printf '%s\n' 'BUILD_ALL: all selected targets built successfully.'
    return 0
}

case "${1:-}" in
    buildAll|build-all)
        shift
        vi_build_all all "$@"
        exit $?
        ;;
    buildAllForge|build-all-forge)
        shift
        vi_build_all forge "$@"
        exit $?
        ;;
    buildAllNeoForge|build-all-neoforge)
        shift
        vi_build_all neoforge "$@"
        exit $?
        ;;
esac

# A plain `./gradlew build` is ambiguous in this multi-version repository. Fail
# fast before Java/Gradle provisioning and show a known-good concrete example.
VI_REQUESTS_BUILD=false
VI_REQUESTS_GAMETEST=false
VI_HAS_TARGET=false
for VI_ARG in "$@"; do
    case "$VI_ARG" in
        build|:build) VI_REQUESTS_BUILD=true ;;
        gameTest|:gameTest) VI_REQUESTS_GAMETEST=true ;;
        -Ptarget=*) VI_HAS_TARGET=true ;;
    esac
done
if [ "$VI_REQUESTS_BUILD" = true ] && [ "$VI_HAS_TARGET" = false ]; then
    printf '%s\n' 'ERROR: no Minecraft/loader target was specified.' >&2
    printf '%s\n' 'Use: ./gradlew build -Ptarget=<loader>-<minecraft>' >&2
    printf '%s\n' 'Example: ./gradlew build -Ptarget=forge-1.6.4' >&2
    printf '%s\n' 'To build every Forge + NeoForge target: ./gradlew buildAll' >&2
    exit 2
fi
if [ "$VI_REQUESTS_GAMETEST" = true ] && [ "$VI_HAS_TARGET" = false ]; then
    printf '%s\n' 'ERROR: gameTest requires a concrete target.' >&2
    printf '%s\n' 'Use: ./gradlew gameTest -Ptarget=<loader>-<minecraft>' >&2
    printf '%s\n' 'Example: ./gradlew gameTest -Ptarget=neoforge-1.21.10' >&2
    exit 2
fi

WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_MAIN="org.gradle.wrapper.GradleWrapperMain"
FORGE_12111=false
MODERN_26X=false
MODERN_26_FORGE=false
MODERN_26_TARGET=""
STANDARD_MODERN=false
STANDARD_MODERN_TARGET=""
FORGE_116X=false
FORGE_112X=false
FORGE_110X=false
FORGE_LEGACY_TARGET=""
for arg in "$@"; do
    case "$arg" in
        -Ptarget=forge-1.21.11)
            WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper-fg7.jar"
            WRAPPER_MAIN="fr.vanillainstincts.wrapper.Gradle95WrapperMain"
            FORGE_12111=true
            ;;
        -Ptarget=forge-26.1.2|-Ptarget=forge-26.2)
            WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper-fg7.jar"
            WRAPPER_MAIN="fr.vanillainstincts.wrapper.Gradle95WrapperMain"
            MODERN_26X=true
            MODERN_26_FORGE=true
            MODERN_26_TARGET=${arg#-Ptarget=}
            ;;
        -Ptarget=neoforge-26.1|-Ptarget=neoforge-26.1.1|-Ptarget=neoforge-26.1.2|-Ptarget=neoforge-26.2)
            WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper-fg7.jar"
            WRAPPER_MAIN="fr.vanillainstincts.wrapper.Gradle95WrapperMain"
            MODERN_26X=true
            MODERN_26_TARGET=${arg#-Ptarget=}
            ;;
        -Ptarget=forge-1.18*|-Ptarget=forge-1.19*|-Ptarget=forge-1.20*|-Ptarget=forge-1.21*|\
        -Ptarget=neoforge-1.20*|-Ptarget=neoforge-1.21*)
            # These targets use the normal Gradle 8.14.5 wrapper. The custom
            # wrapper client itself is Java 21 bytecode, while shared :core is
            # intentionally compiled with a Java 17 toolchain. Always expose
            # both JDKs instead of inheriting whatever JAVA_HOME the caller used
            # for a previous/legacy build. Forge 1.21.11 is matched above and
            # keeps its dedicated FG7/Gradle 9.5 path.
            STANDARD_MODERN=true
            STANDARD_MODERN_TARGET=${arg#-Ptarget=}
            ;;
        -Ptarget=forge-1.16)
            FORGE_116X=true
            FORGE_LEGACY_TARGET=forge-1.16.1
            printf '%s\n' 'forge-1.16 is an alias for forge-1.16.1 (Minecraft 1.16.1 / Forge 32.0.108).'
            ;;
        -Ptarget=forge-1.12.2|-Ptarget=forge-1.12.1|-Ptarget=forge-1.12)
            FORGE_116X=true
            FORGE_112X=true
            FORGE_LEGACY_TARGET=${arg#-Ptarget=}
            ;;
        -Ptarget=forge-1.6.4|-Ptarget=forge-1.6.3|-Ptarget=forge-1.6.2|-Ptarget=forge-1.6.1|-Ptarget=forge-1.7.10|-Ptarget=forge-1.7.9|-Ptarget=forge-1.7.8|-Ptarget=forge-1.7.7|-Ptarget=forge-1.7.6|-Ptarget=forge-1.7.5|-Ptarget=forge-1.7.4|-Ptarget=forge-1.7.3|-Ptarget=forge-1.7.2|-Ptarget=forge-1.7.1|-Ptarget=forge-1.7|-Ptarget=forge-1.8.9|-Ptarget=forge-1.8.8|-Ptarget=forge-1.8.7|-Ptarget=forge-1.8.6|-Ptarget=forge-1.8.5|-Ptarget=forge-1.8.4|-Ptarget=forge-1.8.3|-Ptarget=forge-1.8.2|-Ptarget=forge-1.8.1|-Ptarget=forge-1.8)
            FORGE_116X=true
            FORGE_112X=true
            FORGE_110X=true
            FORGE_LEGACY_TARGET=${arg#-Ptarget=}
            case "$FORGE_LEGACY_TARGET" in
                forge-1.6.3)
                    printf '%s\n' 'forge-1.6.3: Forge 9.11.0.878 runtime; build uses the proven Forge 1.6.4-9.11.1.964 userdev + SRG reobfuscation path.' >&2
                    ;;
                forge-1.6.2)
                    printf '%s\n' 'forge-1.6.2: Forge 9.10.1.871 runtime; build uses the proven Forge 1.6.4-9.11.1.964 userdev + SRG reobfuscation path.' >&2
                    ;;
                forge-1.6.1)
                    printf '%s\n' 'forge-1.6.1: Forge 8.9.0.775 runtime; build uses the proven Forge 1.6.4-9.11.1.964 userdev + SRG reobfuscation path.' >&2
                    ;;
                forge-1.7.9|forge-1.7.8|forge-1.7.7|forge-1.7.6|forge-1.7.5)
                    printf '%s: compatibility profile using the official Forge 1.7.10 toolchain (upstream published no standalone Forge %s distribution).\n' \
                        "$FORGE_LEGACY_TARGET" "${FORGE_LEGACY_TARGET#forge-}" >&2
                    ;;
                forge-1.7.4|forge-1.7.3|forge-1.7.1|forge-1.7)
                    printf '%s: compatibility profile using the official Forge 1.7.2 toolchain (upstream published no standalone Forge %s distribution).\n' \
                        "$FORGE_LEGACY_TARGET" "${FORGE_LEGACY_TARGET#forge-}" >&2
                    ;;
                forge-1.8.7|forge-1.8.6|forge-1.8.5|forge-1.8.4|forge-1.8.3|forge-1.8.2|forge-1.8.1)
                    printf '%s: compatibility profile using the official Forge 1.8.8 toolchain (upstream published no standalone Forge %s distribution).\n' \
                        "$FORGE_LEGACY_TARGET" "${FORGE_LEGACY_TARGET#forge-}" >&2
                    ;;
            esac
            ;;
        -Ptarget=forge-1.6)
            FORGE_116X=true
            FORGE_112X=true
            FORGE_110X=true
            FORGE_LEGACY_TARGET=forge-1.6.1
            printf '%s\n' 'forge-1.6 is an alias for forge-1.6.1: the first final Horse Update release is Minecraft 1.6.1 and Forge publishes no separate stable 1.6 target.' >&2
            ;;
        -Ptarget=forge-1.9)
            FORGE_116X=true
            FORGE_112X=true
            FORGE_110X=true
            FORGE_LEGACY_TARGET=forge-1.9
            ;;
        -Ptarget=forge-1.9.2|-Ptarget=forge-1.9.1)
            printf '%s\n' 'ERROR: Forge upstream published no standalone Minecraft 1.9.1 or 1.9.2 distribution. Official Forge branches in this family are 1.9 and 1.9.4.' >&2
            printf '%s\n' 'Use -Ptarget=forge-1.9 for native Forge 1.9, or -Ptarget=forge-1.9.4 for native Forge 1.9.4.' >&2
            exit 2
            ;;
        -Ptarget=forge-1.9.4)
            FORGE_116X=true
            FORGE_112X=true
            FORGE_110X=true
            FORGE_LEGACY_TARGET=forge-1.9.4
            ;;
        -Ptarget=forge-1.9.3)
            FORGE_116X=true
            FORGE_112X=true
            FORGE_110X=true
            FORGE_LEGACY_TARGET=forge-1.9.3
            printf '%s\n' 'forge-1.9.3: compatibility profile using the official Forge 1.9.4 toolchain (upstream published no standalone Forge 1.9.3 distribution).' >&2
            ;;
        -Ptarget=forge-1.10.2)
            FORGE_116X=true
            FORGE_112X=true
            FORGE_110X=true
            FORGE_LEGACY_TARGET=forge-1.10.2
            ;;
        -Ptarget=forge-1.10)
            FORGE_116X=true
            FORGE_112X=true
            FORGE_110X=true
            FORGE_LEGACY_TARGET=forge-1.10
            ;;
        -Ptarget=forge-1.10.1)
            printf '%s\n' 'ERROR: Forge did not publish a Minecraft 1.10.1 distribution. Use forge-1.10 or forge-1.10.2.' >&2
            exit 2
            ;;
        -Ptarget=forge-1.16.5|-Ptarget=forge-1.16.4|-Ptarget=forge-1.16.3|-Ptarget=forge-1.16.2|-Ptarget=forge-1.16.1|-Ptarget=forge-1.15.2|-Ptarget=forge-1.15.1|-Ptarget=forge-1.15)
            FORGE_116X=true
            FORGE_LEGACY_TARGET=${arg#-Ptarget=}
            ;;
    esac
done

# ---------------------------------------------------------------------------
# Shared modern JDK bootstrap (Java 17/21).
# ---------------------------------------------------------------------------
# buildAll intentionally executes every target in a fresh wrapper process, but
# that process still inherits the caller's JAVA_HOME. A build started from a
# Java 8 shell therefore used to poison every 1.18+ target before Gradle could
# even start. Keep JDK selection explicit and project-local instead.
vi_boot_java_major() {
    VI_BOOT_HOME="$1"
    if [ ! -x "$VI_BOOT_HOME/bin/java" ] || [ ! -x "$VI_BOOT_HOME/bin/javac" ]; then
        return 1
    fi
    VI_BOOT_LINE=$("$VI_BOOT_HOME/bin/java" -version 2>&1 | sed -n '1p')
    case "$VI_BOOT_LINE" in
        *'version "1.8.'*) printf '8' ;;
        *) printf '%s' "$VI_BOOT_LINE" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' ;;
    esac
}

vi_boot_platform_os() {
    if [ -f /etc/alpine-release ] || (ldd --version 2>&1 | grep -qi musl); then
        printf 'alpine-linux'
    else
        printf 'linux'
    fi
}

vi_boot_arch() {
    case "$(uname -m 2>/dev/null || printf x86_64)" in
        aarch64|arm64) printf 'aarch64' ;;
        *) printf 'x64' ;;
    esac
}

vi_boot_fetch_stdout() {
    VI_BOOT_URL="$1"
    VI_BOOT_ATTEMPT=1
    if command -v curl >/dev/null 2>&1; then
        while [ "$VI_BOOT_ATTEMPT" -le 6 ]; do
            if curl --http1.1 -fsSL --connect-timeout 20 --max-time 120 "$VI_BOOT_URL"; then
                return 0
            fi
            printf 'JDK metadata curl attempt %s/6 failed; retrying...\n' "$VI_BOOT_ATTEMPT" >&2
            sleep 2
            VI_BOOT_ATTEMPT=$((VI_BOOT_ATTEMPT + 1))
        done
    fi
    if command -v wget >/dev/null 2>&1; then
        VI_BOOT_ATTEMPT=1
        while [ "$VI_BOOT_ATTEMPT" -le 6 ]; do
            if wget --timeout=30 --tries=1 -qO- "$VI_BOOT_URL"; then
                return 0
            fi
            printf 'JDK metadata wget attempt %s/6 failed; retrying...\n' "$VI_BOOT_ATTEMPT" >&2
            sleep 2
            VI_BOOT_ATTEMPT=$((VI_BOOT_ATTEMPT + 1))
        done
    fi
    printf 'ERROR: could not fetch JDK metadata from %s.\n' "$VI_BOOT_URL" >&2
    return 1
}

vi_boot_fetch_file() {
    VI_BOOT_URL="$1"
    VI_BOOT_DEST="$2"
    VI_BOOT_ATTEMPT=1
    if command -v curl >/dev/null 2>&1; then
        while [ "$VI_BOOT_ATTEMPT" -le 8 ]; do
            if [ -s "$VI_BOOT_DEST" ]; then
                set +e
                curl --http1.1 -fL --connect-timeout 30 --max-time 1200 \
                    --continue-at - -o "$VI_BOOT_DEST" "$VI_BOOT_URL"
                VI_BOOT_RC=$?
                set -e
                if [ "$VI_BOOT_RC" -eq 0 ]; then return 0; fi
                if [ "$VI_BOOT_RC" -eq 33 ]; then rm -f "$VI_BOOT_DEST"; fi
            else
                if curl --http1.1 -fL --connect-timeout 30 --max-time 1200 \
                        -o "$VI_BOOT_DEST" "$VI_BOOT_URL"; then
                    return 0
                fi
            fi
            VI_BOOT_BYTES=0
            if [ -f "$VI_BOOT_DEST" ]; then
                VI_BOOT_BYTES=$(wc -c < "$VI_BOOT_DEST" | tr -d ' ')
            fi
            printf 'JDK download attempt %s/8 failed; retrying from %s bytes...\n' \
                "$VI_BOOT_ATTEMPT" "$VI_BOOT_BYTES" >&2
            sleep 2
            VI_BOOT_ATTEMPT=$((VI_BOOT_ATTEMPT + 1))
        done
    fi
    if command -v wget >/dev/null 2>&1; then
        VI_BOOT_ATTEMPT=1
        while [ "$VI_BOOT_ATTEMPT" -le 8 ]; do
            if wget -c --timeout=30 --tries=1 -O "$VI_BOOT_DEST" "$VI_BOOT_URL"; then
                return 0
            fi
            printf 'JDK wget attempt %s/8 failed; retrying...\n' "$VI_BOOT_ATTEMPT" >&2
            sleep 2
            VI_BOOT_ATTEMPT=$((VI_BOOT_ATTEMPT + 1))
        done
    fi
    printf 'ERROR: could not download JDK from %s.\n' "$VI_BOOT_URL" >&2
    return 1
}

# Prints a full JDK home for the requested major version. Reuses explicit/system
# JDKs first and otherwise downloads the current GA Temurin build into .gradle.
# The Adoptium "latest" endpoint deliberately avoids hard-coding a quarterly
# patch/build number that would age out of the project.
vi_boot_get_jdk() {
    VI_BOOT_MAJOR="$1"
    VI_BOOT_ENV_HOME=""
    case "$VI_BOOT_MAJOR" in
        17) VI_BOOT_ENV_HOME=${JAVA17_HOME:-} ;;
        21) VI_BOOT_ENV_HOME=${JAVA21_HOME:-} ;;
        *) printf 'ERROR: vi_boot_get_jdk only supports Java 17/21 (got %s).\n' "$VI_BOOT_MAJOR" >&2; return 2 ;;
    esac

    for VI_BOOT_CANDIDATE in \
            "$VI_BOOT_ENV_HOME" \
            "${JAVA_HOME:-}" \
            "/usr/lib/jvm/java-$VI_BOOT_MAJOR-openjdk" \
            "/usr/lib/jvm/java-$VI_BOOT_MAJOR-openjdk-amd64" \
            "/usr/lib/jvm/java-$VI_BOOT_MAJOR" \
            "${HOME:-}/.jdks/java-$VI_BOOT_MAJOR" \
            "${HOME:-}/.jdks/jdk-$VI_BOOT_MAJOR"; do
        if [ -n "$VI_BOOT_CANDIDATE" ] \
                && [ "$(vi_boot_java_major "$VI_BOOT_CANDIDATE" 2>/dev/null || true)" = "$VI_BOOT_MAJOR" ]; then
            printf '%s' "$VI_BOOT_CANDIDATE"
            return 0
        fi
    done

    for VI_BOOT_CANDIDATE in /usr/lib/jvm/* "${HOME:-}/.jdks"/*; do
        if [ -d "$VI_BOOT_CANDIDATE" ] \
                && [ "$(vi_boot_java_major "$VI_BOOT_CANDIDATE" 2>/dev/null || true)" = "$VI_BOOT_MAJOR" ]; then
            printf '%s' "$VI_BOOT_CANDIDATE"
            return 0
        fi
    done

    VI_BOOT_JAVA_ON_PATH=$(command -v java 2>/dev/null || true)
    if [ -n "$VI_BOOT_JAVA_ON_PATH" ]; then
        if command -v readlink >/dev/null 2>&1; then
            VI_BOOT_JAVA_ON_PATH=$(readlink -f "$VI_BOOT_JAVA_ON_PATH" 2>/dev/null || printf '%s' "$VI_BOOT_JAVA_ON_PATH")
        fi
        VI_BOOT_PATH_HOME=$(CDPATH= cd -- "$(dirname -- "$VI_BOOT_JAVA_ON_PATH")/.." 2>/dev/null && pwd || true)
        if [ -n "$VI_BOOT_PATH_HOME" ] \
                && [ "$(vi_boot_java_major "$VI_BOOT_PATH_HOME" 2>/dev/null || true)" = "$VI_BOOT_MAJOR" ]; then
            printf '%s' "$VI_BOOT_PATH_HOME"
            return 0
        fi
    fi

    VI_BOOT_OS=$(vi_boot_platform_os)
    VI_BOOT_ARCH=$(vi_boot_arch)
    VI_BOOT_ROOT="$APP_HOME/.gradle/vanilla-instincts-jdks"
    VI_BOOT_MANAGED="$VI_BOOT_ROOT/temurin-jdk$VI_BOOT_MAJOR-$VI_BOOT_OS-$VI_BOOT_ARCH"
    if [ "$(vi_boot_java_major "$VI_BOOT_MANAGED" 2>/dev/null || true)" = "$VI_BOOT_MAJOR" ]; then
        printf '%s' "$VI_BOOT_MANAGED"
        return 0
    fi

    mkdir -p "$VI_BOOT_ROOT"
    VI_BOOT_URL="https://api.adoptium.net/v3/binary/latest/$VI_BOOT_MAJOR/ga/$VI_BOOT_OS/$VI_BOOT_ARCH/jdk/hotspot/normal/eclipse"
    VI_BOOT_ARCHIVE="$VI_BOOT_ROOT/temurin-jdk$VI_BOOT_MAJOR-$VI_BOOT_OS-$VI_BOOT_ARCH.tar.gz.part"
    printf 'Vanilla Instincts: provisioning Java %s JDK (%s/%s)...\n' \
        "$VI_BOOT_MAJOR" "$VI_BOOT_OS" "$VI_BOOT_ARCH" >&2
    vi_boot_fetch_file "$VI_BOOT_URL" "$VI_BOOT_ARCHIVE" || return 1

    VI_BOOT_STAGE="$VI_BOOT_ROOT/.extract-jdk$VI_BOOT_MAJOR-$$"
    rm -rf "$VI_BOOT_STAGE" "$VI_BOOT_MANAGED"
    mkdir -p "$VI_BOOT_STAGE"
    if ! tar -xzf "$VI_BOOT_ARCHIVE" -C "$VI_BOOT_STAGE"; then
        printf 'ERROR: could not extract downloaded Java %s JDK.\n' "$VI_BOOT_MAJOR" >&2
        rm -rf "$VI_BOOT_STAGE"
        return 1
    fi
    VI_BOOT_EXTRACTED=""
    for VI_BOOT_CANDIDATE in "$VI_BOOT_STAGE"/*; do
        if [ -d "$VI_BOOT_CANDIDATE" ] \
                && [ "$(vi_boot_java_major "$VI_BOOT_CANDIDATE" 2>/dev/null || true)" = "$VI_BOOT_MAJOR" ]; then
            VI_BOOT_EXTRACTED="$VI_BOOT_CANDIDATE"
            break
        fi
    done
    if [ -z "$VI_BOOT_EXTRACTED" ]; then
        printf 'ERROR: downloaded Java %s archive did not contain a usable full JDK.\n' "$VI_BOOT_MAJOR" >&2
        rm -rf "$VI_BOOT_STAGE"
        return 1
    fi
    mv "$VI_BOOT_EXTRACTED" "$VI_BOOT_MANAGED"
    rm -rf "$VI_BOOT_STAGE" "$VI_BOOT_ARCHIVE"
    if [ "$(vi_boot_java_major "$VI_BOOT_MANAGED" 2>/dev/null || true)" != "$VI_BOOT_MAJOR" ]; then
        printf 'ERROR: provisioned Java %s JDK failed validation: %s\n' "$VI_BOOT_MAJOR" "$VI_BOOT_MANAGED" >&2
        return 1
    fi
    printf '%s' "$VI_BOOT_MANAGED"
}

if [ "$FORGE_116X" = true ]; then
    printf '%s: Vanilla Instincts 1.0.0\n' "$FORGE_LEGACY_TARGET"
fi

# Forge 1.16.x is a native Java 8 target. Alpine installations commonly
# have only newer Java versions available, so for these targets expose an
# existing JAVA8_HOME or provision a project-local BellSoft Liberica JDK 8.
if [ "$FORGE_116X" = true ]; then
    # Keep the 1.16.x toolchain registry isolated from the user-wide Gradle
    # configuration. Gradle gives GRADLE_USER_HOME/gradle.properties higher
    # precedence than the project file, and also scans GRADLE_USER_HOME/jdks.
    GRADLE_USER_HOME="$APP_HOME/.gradle/forge-legacy-java8-user-home"
    export GRADLE_USER_HOME
    mkdir -p "$GRADLE_USER_HOME/jdks"

    vi116_java_major() {
        _line=$("$1/bin/java" -version 2>&1 | sed -n '1p')
        case "$_line" in
            *'version "1.8.'*) printf '8' ;;
            *) printf '%s' "$_line" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' ;;
        esac
    }

    vi116_fetch_stdout() {
        _url="$1"
        if command -v curl >/dev/null 2>&1; then
            _attempt=1
            while [ "$_attempt" -le 4 ]; do
                if curl --http1.1 -fsSL --connect-timeout 20 --max-time 120 "$_url"; then
                    return 0
                fi
                printf 'Java 8 metadata curl attempt %s/4 failed; retrying...\n' "$_attempt" >&2
                sleep 2
                _attempt=$((_attempt + 1))
            done
            if command -v wget >/dev/null 2>&1; then
                printf 'Java 8 metadata: curl failed; falling back to wget...\n' >&2
            fi
        fi
        if command -v wget >/dev/null 2>&1; then
            _attempt=1
            while [ "$_attempt" -le 4 ]; do
                if wget --timeout=30 --tries=1 -qO- "$_url"; then
                    return 0
                fi
                printf 'Java 8 metadata wget attempt %s/4 failed; retrying...\n' "$_attempt" >&2
                sleep 2
                _attempt=$((_attempt + 1))
            done
        fi
        printf 'ERROR: could not fetch Java 8 metadata with curl or wget for %s.\n' "$FORGE_LEGACY_TARGET" >&2
        return 1
    }

    # Generic binary fetch helper. Keep every scratch variable namespaced:
    # POSIX /bin/sh has no portable `local`, so generic names such as _dest
    # leaked back into callers and corrupted the Forge 1.6.x cache verifier.
    vi116_fetch_file() {
        VI_FETCH_URL="$1"
        VI_FETCH_DEST="$2"
        VI_FETCH_ATTEMPT=1
        if command -v curl >/dev/null 2>&1; then
            while [ "$VI_FETCH_ATTEMPT" -le 5 ]; do
                if [ -s "$VI_FETCH_DEST" ]; then
                    if curl --http1.1 -fL --connect-timeout 30 --max-time 900 \
                            --continue-at - -o "$VI_FETCH_DEST" "$VI_FETCH_URL"; then
                        return 0
                    else
                        VI_FETCH_RC=$?
                        if [ "$VI_FETCH_RC" -eq 33 ]; then
                            rm -f "$VI_FETCH_DEST"
                        fi
                    fi
                else
                    if curl --http1.1 -fL --connect-timeout 30 --max-time 900 \
                            -o "$VI_FETCH_DEST" "$VI_FETCH_URL"; then
                        return 0
                    fi
                fi
                VI_FETCH_BYTES=0
                if [ -f "$VI_FETCH_DEST" ]; then
                    VI_FETCH_BYTES=$(wc -c < "$VI_FETCH_DEST" | tr -d ' ')
                fi
                printf 'Download curl attempt %s/5 failed; retrying from %s bytes...\n' \
                    "$VI_FETCH_ATTEMPT" "$VI_FETCH_BYTES" >&2
                sleep 2
                VI_FETCH_ATTEMPT=$((VI_FETCH_ATTEMPT + 1))
            done
            if command -v wget >/dev/null 2>&1; then
                printf 'Download: curl failed; falling back to wget...\n' >&2
            fi
        fi
        if command -v wget >/dev/null 2>&1; then
            VI_FETCH_ATTEMPT=1
            while [ "$VI_FETCH_ATTEMPT" -le 5 ]; do
                if wget -c --timeout=30 --tries=1 -O "$VI_FETCH_DEST" "$VI_FETCH_URL"; then
                    return 0
                fi
                printf 'Download wget attempt %s/5 failed; retrying...\n' "$VI_FETCH_ATTEMPT" >&2
                sleep 2
                VI_FETCH_ATTEMPT=$((VI_FETCH_ATTEMPT + 1))
            done
        fi
        printf 'ERROR: could not download %s with curl or wget for %s.\n' \
            "$VI_FETCH_URL" "$FORGE_LEGACY_TARGET" >&2
        return 1
    }

    JDK8_ROOT="$APP_HOME/.gradle/forge-legacy-java8-toolchains"
    JDK8_MANAGED_HOME="$JDK8_ROOT/liberica-jdk8"
    JDK8_HOME="$JDK8_MANAGED_HOME"
    JDK8_EXTERNAL=false

    if [ -n "${JAVA8_HOME:-}" ]; then
        JDK8_HOME="$JAVA8_HOME"
        JDK8_EXTERNAL=true
    else
        # Reuse JAVA_HOME automatically when it already points to a complete
        # Java 8 JDK. This avoids needless network provisioning on legacy builds.
        if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
            if [ "$(vi116_java_major "$JAVA_HOME")" = "8" ]; then
                JDK8_HOME="$JAVA_HOME"
            fi
        fi
        if [ ! -x "$JDK8_HOME/bin/java" ]; then
            for _candidate in \
                    /usr/lib/jvm/java-8-openjdk \
                    /usr/lib/jvm/java-1.8-openjdk \
                    /usr/lib/jvm/java-8-openjdk-amd64 \
                    "$HOME/.jdks/jdk8" \
                    "$HOME/.jdks/java-8"; do
                if [ -x "$_candidate/bin/java" ] && [ -x "$_candidate/bin/javac" ]; then
                    if [ "$(vi116_java_major "$_candidate")" = "8" ]; then
                        JDK8_HOME="$_candidate"
                        break
                    fi
                fi
            done
        fi
    fi

    JDK8_MAJOR=""
    if [ -x "$JDK8_HOME/bin/java" ]; then
        JDK8_MAJOR=$(vi116_java_major "$JDK8_HOME")
    fi

    if [ "$JDK8_MAJOR" != "8" ]; then
        if [ "$JDK8_EXTERNAL" = true ]; then
            printf 'ERROR: JAVA8_HOME must point to a full Java 8 JDK: %s\n' "$JAVA8_HOME" >&2
            exit 1
        fi

        rm -rf "$JDK8_MANAGED_HOME"
        mkdir -p "$JDK8_ROOT"

        BELL8_OS="linux"
        if [ -f /etc/alpine-release ] || (ldd --version 2>&1 | grep -qi musl); then
            BELL8_OS="linux-musl"
        fi
        BELL8_ARCH="x86"
        case "$(uname -m 2>/dev/null || printf x86_64)" in
            aarch64|arm64) BELL8_ARCH="arm" ;;
        esac
        BELL8_API="https://api.bell-sw.com/v1/liberica/releases?version=8u502%2B9&bitness=64&os=$BELL8_OS&arch=$BELL8_ARCH&package-type=tar.gz&bundle-type=jdk"

        printf '%s: provisioning Java 8 toolchain (%s/%s)...\n' \
            "$FORGE_LEGACY_TARGET" "$BELL8_OS" "$BELL8_ARCH" >&2
        JDK8_META=$(vi116_fetch_stdout "$BELL8_API") || exit 1
        JDK8_URL=$(printf '%s' "$JDK8_META" | tr '\n' ' ' | sed -n 's/.*"downloadUrl"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
        JDK8_SHA1=$(printf '%s' "$JDK8_META" | tr '\n' ' ' | sed -n 's/.*"sha1"[[:space:]]*:[[:space:]]*"\([0-9a-fA-F]*\)".*/\1/p')

        if [ -z "$JDK8_URL" ] || [ -z "$JDK8_SHA1" ]; then
            printf 'ERROR: BellSoft API did not return Java 8 download metadata.\n' >&2
            exit 1
        fi

        JDK8_ARCHIVE="$JDK8_ROOT/bellsoft-jdk8.tar.gz"
        JDK8_PART="$JDK8_ARCHIVE.part"
        vi116_fetch_file "$JDK8_URL" "$JDK8_PART" || exit 1

        if ! command -v sha1sum >/dev/null 2>&1; then
            printf 'ERROR: sha1sum is required to verify the Java 8 download.\n' >&2
            rm -f "$JDK8_PART"
            exit 1
        fi
        JDK8_ACTUAL_SHA1=$(sha1sum "$JDK8_PART" | awk '{print $1}')
        if [ "$JDK8_ACTUAL_SHA1" != "$JDK8_SHA1" ]; then
            printf 'ERROR: BellSoft Java 8 checksum mismatch.\n' >&2
            rm -f "$JDK8_PART"
            exit 1
        fi
        mv "$JDK8_PART" "$JDK8_ARCHIVE"

        JDK8_STAGE="$JDK8_ROOT/.jdk8-extract-$$"
        rm -rf "$JDK8_STAGE"
        mkdir -p "$JDK8_STAGE"
        if ! tar -xzf "$JDK8_ARCHIVE" -C "$JDK8_STAGE"; then
            printf 'ERROR: Could not extract BellSoft Java 8 archive.\n' >&2
            rm -rf "$JDK8_STAGE"
            exit 1
        fi

        JDK8_EXTRACTED=""
        for _candidate in "$JDK8_STAGE"/*; do
            if [ -d "$_candidate" ] && [ -x "$_candidate/bin/java" ] && [ -x "$_candidate/bin/javac" ]; then
                JDK8_EXTRACTED="$_candidate"
                break
            fi
        done
        if [ -z "$JDK8_EXTRACTED" ]; then
            printf 'ERROR: BellSoft Java 8 archive did not contain a usable JDK.\n' >&2
            rm -rf "$JDK8_STAGE"
            exit 1
        fi

        mv "$JDK8_EXTRACTED" "$JDK8_MANAGED_HOME"
        rm -rf "$JDK8_STAGE"
        JDK8_HOME="$JDK8_MANAGED_HOME"
        JDK8_MAJOR=$(vi116_java_major "$JDK8_HOME")
        if [ "$JDK8_MAJOR" != "8" ]; then
            printf 'ERROR: Provisioned BellSoft toolchain is not Java 8.\n' >&2
            exit 1
        fi
    fi

    if [ ! -x "$JDK8_HOME/bin/javac" ]; then
        printf 'ERROR: Java 8 toolchain is not a full JDK (missing javac): %s\n' "$JDK8_HOME" >&2
        exit 1
    fi

    JAVA8_HOME="$JDK8_HOME"
    JAVA_HOME_8="$JDK8_HOME"
    JAVA_HOME_8_X64="$JDK8_HOME"
    export JAVA8_HOME JAVA_HOME_8 JAVA_HOME_8_X64

    # Forge 1.15/1.16 itself stays on Java 8, but the shared :core module is
    # deliberately Java 17 bytecode. The previous isolated user-home exposed
    # only Java 8, which made every one of these targets fail at :core:compileJava.
    # Legacy 1.6-1.12 exits through its isolated project before :core is used.
    VI116_JDK17_HOME=""
    if [ "$FORGE_112X" != true ]; then
        VI116_JDK17_HOME=$(vi_boot_get_jdk 17) || exit 1
        JAVA17_HOME="$VI116_JDK17_HOME"
        JAVA_HOME_17="$VI116_JDK17_HOME"
        JAVA_HOME_17_X64="$VI116_JDK17_HOME"
        export JAVA17_HOME JAVA_HOME_17 JAVA_HOME_17_X64
    fi

    # Register the provisioned JDK through both Gradle-supported local
    # discovery routes: an explicit installation path and the User Home jdks
    # directory used for provisioned toolchains. This avoids relying on JVM
    # system properties, which ForgeGradle's nested MCP setup did not honor.
    VI116_JDK_LINK="$GRADLE_USER_HOME/jdks/vanilla-instincts-jdk8"
    if [ -e "$VI116_JDK_LINK" ] || [ -L "$VI116_JDK_LINK" ]; then
        rm -rf "$VI116_JDK_LINK"
    fi
    ln -s "$JDK8_HOME" "$VI116_JDK_LINK"

    VI116_USER_PROPS="$GRADLE_USER_HOME/gradle.properties"
    cat > "$VI116_USER_PROPS" <<EOF
# Forge 1.16.x is a native Java 8 target. Keep Gradle and ForgeGradle on
# the same explicit JDK so HackyJavaCompile sees one consistent toolchain.
org.gradle.java.installations.auto-detect=true
org.gradle.java.installations.auto-download=false
org.gradle.java.installations.fromEnv=$(if [ -n "$VI116_JDK17_HOME" ]; then printf 'JAVA8_HOME,JAVA17_HOME'; else printf 'JAVA8_HOME'; fi)
org.gradle.java.installations.paths=$(if [ -n "$VI116_JDK17_HOME" ]; then printf '%s,%s' "$JDK8_HOME" "$VI116_JDK17_HOME"; else printf '%s' "$JDK8_HOME"; fi)
EOF

    if [ "$FORGE_112X" = true ]; then
        # Minecraft 1.12.x and 1.10.x predate the modern ForgeGradle pipeline.
        # Keep the public command unchanged, but execute each target in its
        # isolated legacy Gradle project on the same native Java 8 JDK.
        case "$FORGE_LEGACY_TARGET" in
            forge-1.6.4|forge-1.6.3|forge-1.6.2|forge-1.6.1)
                # ForgeGradle 1.0-era 1.6.4 build (also used for the 1.6.1-1.6.3 SRG compatibility profiles) runs on Gradle 3.0 with Java 8.
                VI112_GRADLE_VERSION="3.0"
                VI112_GRADLE_SHA256="39c906941a474444afbddc38144ed44166825acb0a57b0551dddb04bbf157f80"
                ;;
            forge-1.7.10|forge-1.7.9|forge-1.7.8|forge-1.7.7|forge-1.7.6|forge-1.7.5|forge-1.7.4|forge-1.7.3|forge-1.7.2|forge-1.7.1|forge-1.7)
                # Maintained ForgeGradle 1.2 fork supports Gradle 4.4.1 for legacy 1.7.2/1.7.10 builds.
                VI112_GRADLE_VERSION="4.4.1"
                VI112_GRADLE_SHA256="e7cf7d1853dfc30c1c44f571d3919eeeedef002823b66b6a988d27e919686389"
                ;;
            forge-1.12.2)
                VI112_GRADLE_VERSION="5.6.4"
                VI112_GRADLE_SHA256="1f3067073041bc44554d0efe5d402a33bc3d3c93cc39ab684f308586d732a80d"
                ;;
            forge-1.12.1|forge-1.12)
                VI112_GRADLE_VERSION="4.10.3"
                VI112_GRADLE_SHA256="8626cbf206b4e201ade7b87779090690447054bc93f052954c78480fa6ed186e"
                ;;
            forge-1.8.9|forge-1.8.8|forge-1.8.7|forge-1.8.6|forge-1.8.5|forge-1.8.4|forge-1.8.3|forge-1.8.2|forge-1.8.1|forge-1.8|forge-1.9)
                # Gradle 2.7 hosts ForgeGradle 2.1 for 1.8.8-1.9 compatibility targets and ForgeGradle 2.0 for native 1.8.
                VI112_GRADLE_VERSION="2.7"
                VI112_GRADLE_SHA256="cde43b90945b5304c43ee36e58aab4cc6fb3a3d5f9bd9449bb1709a68371cb06"
                ;;
            forge-1.9.4|forge-1.9.3|forge-1.10.2|forge-1.10)
                VI112_GRADLE_VERSION="2.14"
                VI112_GRADLE_SHA256="993b4f33b652c689e9721917d8e021cab6bbd3eae81b39ab2fd46fdb19a928d5"
                ;;
            *)
                printf 'ERROR: unknown legacy Forge target: %s\n' "$FORGE_LEGACY_TARGET" >&2
                exit 1
                ;;
        esac

        VI112_GRADLE_BIN=""
        for _dist_root in \
                "$GRADLE_USER_HOME/wrapper/dists" \
                "${HOME:-}/.gradle/wrapper/dists" \
                "$APP_HOME/.gradle/wrapper/dists"; do
            if [ -n "$_dist_root" ] && [ -d "$_dist_root" ]; then
                VI112_GRADLE_BIN=$(find "$_dist_root" -type f \
                    -path "*/gradle-$VI112_GRADLE_VERSION/bin/gradle" \
                    -perm -u+x -print -quit 2>/dev/null || true)
                if [ -n "$VI112_GRADLE_BIN" ]; then
                    break
                fi
            fi
        done

        if [ -z "$VI112_GRADLE_BIN" ]; then
            VI112_DIST_ROOT="$GRADLE_USER_HOME/wrapper/dists/vanilla-instincts-$VI112_GRADLE_VERSION"
            VI112_DIST_ARCHIVE="$VI112_DIST_ROOT/gradle-$VI112_GRADLE_VERSION-bin.zip"
            mkdir -p "$VI112_DIST_ROOT"
            printf '%s: provisioning Gradle %s for legacy Forge...\n' \
                "$FORGE_LEGACY_TARGET" "$VI112_GRADLE_VERSION" >&2
            vi116_fetch_file \
                "https://services.gradle.org/distributions/gradle-$VI112_GRADLE_VERSION-bin.zip" \
                "$VI112_DIST_ARCHIVE.part" || exit 1
            if ! command -v sha256sum >/dev/null 2>&1; then
                printf 'ERROR: sha256sum is required to verify Gradle %s.\n' "$VI112_GRADLE_VERSION" >&2
                exit 1
            fi
            VI112_ACTUAL_SHA=$(sha256sum "$VI112_DIST_ARCHIVE.part" | awk '{print $1}')
            if [ "$VI112_ACTUAL_SHA" != "$VI112_GRADLE_SHA256" ]; then
                printf 'ERROR: Gradle %s checksum mismatch.\n' "$VI112_GRADLE_VERSION" >&2
                rm -f "$VI112_DIST_ARCHIVE.part"
                exit 1
            fi
            mv "$VI112_DIST_ARCHIVE.part" "$VI112_DIST_ARCHIVE"
            rm -rf "$VI112_DIST_ROOT/gradle-$VI112_GRADLE_VERSION"
            if command -v unzip >/dev/null 2>&1; then
                unzip -q "$VI112_DIST_ARCHIVE" -d "$VI112_DIST_ROOT"
            else
                (cd "$VI112_DIST_ROOT" && "$JDK8_HOME/bin/jar" xf "$VI112_DIST_ARCHIVE")
            fi
            VI112_GRADLE_BIN="$VI112_DIST_ROOT/gradle-$VI112_GRADLE_VERSION/bin/gradle"
            chmod +x "$VI112_GRADLE_BIN"
        fi

        JAVA_HOME="$JDK8_HOME"
        export JAVA_HOME
        PATH="$JAVA_HOME/bin:$PATH"
        export PATH

        # ForgeGradle 1.0 hardcodes the retired HTTP S3 Minecraft.Download
        # endpoint for the vanilla 1.6.4 client/server. Seed the exact cache
        # files it expects from Mojang's content-addressed HTTPS CDN before
        # Gradle configures :downloadClient / :downloadServer. This applies to
        # native 1.6.4 and to the 1.6.1-1.6.3 SRG compatibility profiles, whose
        # build-time userdev toolchain is deliberately 1.6.4.
        if [ "$FORGE_LEGACY_TARGET" = "forge-1.6.4" ] || [ "$FORGE_LEGACY_TARGET" = "forge-1.6.3" ] || [ "$FORGE_LEGACY_TARGET" = "forge-1.6.2" ] || [ "$FORGE_LEGACY_TARGET" = "forge-1.6.1" ]; then
            VI164_CLIENT_SHA1="1703704407101cf72bd88e68579e3696ce733ecd"
            VI164_SERVER_SHA1="050f93c1f3fe9e2052398f7bd6aca10c63d64a87"
            VI164_CLIENT_CACHE="$GRADLE_USER_HOME/caches/minecraft/net/minecraft/minecraft/1.6.4/minecraft-1.6.4.jar"
            VI164_SERVER_CACHE="$GRADLE_USER_HOME/caches/minecraft/net/minecraft/minecraft_server/1.6.4/minecraft_server-1.6.4.jar"

            vi164_sha1_ok() {
                VI164_CHECK_FILE="$1"
                VI164_CHECK_EXPECTED="$2"
                [ -s "$VI164_CHECK_FILE" ] || return 1
                VI164_CHECK_ACTUAL=$(sha1sum "$VI164_CHECK_FILE" 2>/dev/null | awk '{print $1}')
                [ "$VI164_CHECK_ACTUAL" = "$VI164_CHECK_EXPECTED" ]
            }

            vi164_seed_minecraft_jar() {
                VI164_SEED_KIND="$1"
                VI164_SEED_DEST="$2"
                VI164_SEED_SHA1="$3"
                shift 3

                if ! command -v sha1sum >/dev/null 2>&1; then
                    printf 'ERROR: sha1sum is required to verify Minecraft 1.6.4 %s.jar.\n' "$VI164_SEED_KIND" >&2
                    return 1
                fi

                if vi164_sha1_ok "$VI164_SEED_DEST" "$VI164_SEED_SHA1"; then
                    printf 'forge-1.6.x bootstrap: verified cached Minecraft 1.6.4 %s.jar.\n' "$VI164_SEED_KIND" >&2
                    return 0
                fi

                rm -f "$VI164_SEED_DEST" "$VI164_SEED_DEST.part"
                mkdir -p "$(dirname "$VI164_SEED_DEST")"
                for VI164_SEED_URL in "$@"; do
                    printf 'forge-1.6.x bootstrap: fetching Minecraft 1.6.4 %s.jar from Mojang HTTPS CDN...\n' "$VI164_SEED_KIND" >&2
                    if vi116_fetch_file "$VI164_SEED_URL" "$VI164_SEED_DEST.part"; then
                        VI164_SEED_ACTUAL=$(sha1sum "$VI164_SEED_DEST.part" 2>/dev/null | awk '{print $1}')
                        if [ "$VI164_SEED_ACTUAL" = "$VI164_SEED_SHA1" ]; then
                            mv "$VI164_SEED_DEST.part" "$VI164_SEED_DEST"
                            printf 'forge-1.6.x bootstrap: cached and verified Minecraft 1.6.4 %s.jar.\n' "$VI164_SEED_KIND" >&2
                            return 0
                        fi
                        printf 'forge-1.6.x bootstrap: SHA-1 mismatch for %s.jar (expected %s, got %s); trying next Mojang endpoint.\n' \
                            "$VI164_SEED_KIND" "$VI164_SEED_SHA1" "${VI164_SEED_ACTUAL:-unavailable}" >&2
                    fi
                    rm -f "$VI164_SEED_DEST.part"
                done

                printf 'ERROR: could not obtain a verified Minecraft 1.6.4 %s.jar from Mojang.\n' "$VI164_SEED_KIND" >&2
                return 1
            }

            vi164_seed_minecraft_jar client "$VI164_CLIENT_CACHE" "$VI164_CLIENT_SHA1" \
                "https://piston-data.mojang.com/v1/objects/$VI164_CLIENT_SHA1/client.jar" \
                "https://launcher.mojang.com/v1/objects/$VI164_CLIENT_SHA1/client.jar" \
                "https://launcher.mojang.com/mc/game/1.6.4/client/$VI164_CLIENT_SHA1/client.jar" || exit 1

            vi164_seed_minecraft_jar server "$VI164_SERVER_CACHE" "$VI164_SERVER_SHA1" \
                "https://piston-data.mojang.com/v1/objects/$VI164_SERVER_SHA1/server.jar" \
                "https://launcher.mojang.com/v1/objects/$VI164_SERVER_SHA1/server.jar" \
                "https://launcher.mojang.com/mc/game/1.6.4/server/$VI164_SERVER_SHA1/server.jar" || exit 1
        fi

        printf '%s: native Java 8 at %s; launching isolated Gradle %s.\n' \
            "$FORGE_LEGACY_TARGET" "$JDK8_HOME" "$VI112_GRADLE_VERSION" >&2

        # Pass an explicit Gradle project property rather than relying on an
        # environment variable: an already-running Gradle 3 daemon may have
        # been started before this wrapper exported new environment values.
        if [ "$FORGE_LEGACY_TARGET" = "forge-1.6.4" ] || [ "$FORGE_LEGACY_TARGET" = "forge-1.6.3" ] || [ "$FORGE_LEGACY_TARGET" = "forge-1.6.2" ] || [ "$FORGE_LEGACY_TARGET" = "forge-1.6.1" ]; then
            exec "$VI112_GRADLE_BIN" \
                -PviLegacy164Seeded=true \
                --settings-file "$APP_HOME/platforms/$FORGE_LEGACY_TARGET/settings.gradle" \
                -p "$APP_HOME/platforms/$FORGE_LEGACY_TARGET" "$@"
        fi

        if [ "$FORGE_110X" = true ]; then
            # ForgeGradle 2.0 hardcodes the now-dead MCPBot HTTP endpoint.
            # Native 1.8 therefore gets a loopback-only compatibility proxy:
            # fetch a maintained HTTPS copy of versions.json, serve it on
            # 127.0.0.1, and route only the dead MCPBot host through it.
            if [ "$FORGE_LEGACY_TARGET" = "forge-1.8" ]; then
                VI18_MCPBOT_ROOT="$GRADLE_USER_HOME/vanilla-instincts-mcpbot"
                VI18_MCPBOT_JSON="$VI18_MCPBOT_ROOT/versions.json"
                VI18_MCPBOT_PART="$VI18_MCPBOT_JSON.part"
                VI18_PROXY_CLASSES="$VI18_MCPBOT_ROOT/classes"
                VI18_PROXY_PORT_FILE="$VI18_MCPBOT_ROOT/proxy.port"
                VI18_PROXY_LOG="$VI18_MCPBOT_ROOT/proxy.log"
                mkdir -p "$VI18_MCPBOT_ROOT" "$VI18_PROXY_CLASSES"

                vi18_fetch_mcpbot_index() {
                    _dest="$1"
                    for _url in \
                        "https://maven.minecraftforge.net/de/oceanlabs/mcp/versions.json" \
                        "https://raw.githubusercontent.com/Aizistral-Studios/MCP-Archive/dungeon-master/versions.json" \
                        "https://mcp.zeith.org/versions.json"; do
                        rm -f "$_dest"
                        if command -v curl >/dev/null 2>&1; then
                            if curl --http1.1 -fL --connect-timeout 20 --max-time 120 -o "$_dest" "$_url"; then
                                if [ -s "$_dest" ]; then return 0; fi
                            fi
                        fi
                        rm -f "$_dest"
                        if command -v wget >/dev/null 2>&1; then
                            if wget --timeout=20 --tries=2 -O "$_dest" "$_url"; then
                                if [ -s "$_dest" ]; then return 0; fi
                            fi
                        fi
                        printf 'forge-1.8: MCP index mirror failed: %s; trying next mirror...\n' "$_url" >&2
                    done
                    rm -f "$_dest"
                    return 1
                }

                # MCP's 1.8 mapping index is historical and immutable for our
                # stable_18 target. Download it once per extracted project and
                # then reuse the cached copy on subsequent builds.
                if [ ! -s "$VI18_MCPBOT_JSON" ]; then
                    if vi18_fetch_mcpbot_index "$VI18_MCPBOT_PART"; then
                        mv "$VI18_MCPBOT_PART" "$VI18_MCPBOT_JSON"
                    else
                        printf '%s\n' 'ERROR: forge-1.8 could not obtain the archived MCP versions index from any HTTPS mirror.' >&2
                        exit 1
                    fi
                fi

                rm -rf "$VI18_PROXY_CLASSES"
                mkdir -p "$VI18_PROXY_CLASSES"
                "$JDK8_HOME/bin/javac" -source 8 -target 8 -encoding UTF-8 \
                    -d "$VI18_PROXY_CLASSES" \
                    "$APP_HOME/gradle/legacy/McpbotIndexProxy.java"
                rm -f "$VI18_PROXY_PORT_FILE" "$VI18_PROXY_LOG"
                "$JDK8_HOME/bin/java" -cp "$VI18_PROXY_CLASSES" \
                    fr.vanillainstincts.legacy.McpbotIndexProxy \
                    "$VI18_MCPBOT_JSON" "$VI18_PROXY_PORT_FILE" \
                    >"$VI18_PROXY_LOG" 2>&1 &
                VI18_PROXY_PID=$!

                VI18_USER_PROPS_BACKUP="$VI18_MCPBOT_ROOT/gradle.properties.before-proxy"
                cp "$VI116_USER_PROPS" "$VI18_USER_PROPS_BACKUP"

                vi18_cleanup_proxy() {
                    kill "$VI18_PROXY_PID" 2>/dev/null || true
                    wait "$VI18_PROXY_PID" 2>/dev/null || true
                    if [ -f "$VI18_USER_PROPS_BACKUP" ]; then
                        cp "$VI18_USER_PROPS_BACKUP" "$VI116_USER_PROPS" 2>/dev/null || true
                        rm -f "$VI18_USER_PROPS_BACKUP"
                    fi
                }
                trap 'vi18_cleanup_proxy' EXIT HUP INT TERM

                _wait=0
                while [ ! -s "$VI18_PROXY_PORT_FILE" ] && [ "$_wait" -lt 100 ]; do
                    if ! kill -0 "$VI18_PROXY_PID" 2>/dev/null; then break; fi
                    sleep 0.1
                    _wait=$((_wait + 1))
                done
                if [ ! -s "$VI18_PROXY_PORT_FILE" ]; then
                    printf '%s\n' 'ERROR: forge-1.8 MCPBot compatibility proxy failed to start.' >&2
                    if [ -s "$VI18_PROXY_LOG" ]; then cat "$VI18_PROXY_LOG" >&2; fi
                    exit 1
                fi
                VI18_PROXY_PORT=$(sed -n '1p' "$VI18_PROXY_PORT_FILE")
                printf 'forge-1.8: redirecting dead MCPBot endpoint through local archive proxy on 127.0.0.1:%s.\n' \
                    "$VI18_PROXY_PORT" >&2

                VI18_NON_PROXY='localhost|127.*|[::1]|files.minecraftforge.net|maven.minecraftforge.net|libraries.minecraft.net|*.minecraft.net|*.minecraftforge.net|*.mojang.com|*.gradle.org|repo1.maven.org|repo.maven.apache.org'
                cat >> "$VI116_USER_PROPS" <<EOF
# Native Forge 1.8 / ForgeGradle 2.0 compatibility: MCPBot was retired.
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=$VI18_PROXY_PORT
systemProp.http.nonProxyHosts=$VI18_NON_PROXY
EOF
                set +e
                "$VI112_GRADLE_BIN" \
                    "-Dhttp.proxyHost=127.0.0.1" \
                    "-Dhttp.proxyPort=$VI18_PROXY_PORT" \
                    "-Dhttp.nonProxyHosts=$VI18_NON_PROXY" \
                    --settings-file "$APP_HOME/platforms/$FORGE_LEGACY_TARGET/settings.gradle" \
                    -p "$APP_HOME/platforms/$FORGE_LEGACY_TARGET" "$@"
                VI18_GRADLE_RC=$?
                set -e
                vi18_cleanup_proxy
                trap - EXIT HUP INT TERM
                exit "$VI18_GRADLE_RC"
            fi

            # Gradle 2.14 predates settings.pluginManagement(). Force the
            # isolated legacy settings file so Gradle never walks up to the
            # repository's modern root settings.gradle.
            exec "$VI112_GRADLE_BIN" \
                --settings-file "$APP_HOME/platforms/$FORGE_LEGACY_TARGET/settings.gradle" \
                -p "$APP_HOME/platforms/$FORGE_LEGACY_TARGET" "$@"
        fi
        exec "$VI112_GRADLE_BIN" -p "$APP_HOME/platforms/$FORGE_LEGACY_TARGET" "$@"
    fi

    # The repository's tiny custom gradle-wrapper.jar is intentionally built
    # with the modern project JDK (Java 21 bytecode). It therefore cannot be
    # used as the bootstrap client once JAVA_HOME is switched to Java 8. For
    # Forge 1.16.x only, bypass that custom client completely: locate (or
    # install) the declared Gradle distribution in shell, then run its native
    # bin/gradle launcher directly on the provisioned Java 8 JDK. Gradle
    # 8.14.x still supports Java 8 as its runtime, and ForgeGradle/MCP then sees
    # Java 8 as the current JVM instead of trying to discover a secondary JDK.
    VI116_GRADLE_VERSION="8.14.5"
    VI116_GRADLE_BIN=""
    for _dist_root in \
            "$GRADLE_USER_HOME/wrapper/dists" \
            "${HOME:-}/.gradle/wrapper/dists" \
            "$APP_HOME/.gradle/wrapper/dists"; do
        if [ -n "$_dist_root" ] && [ -d "$_dist_root" ]; then
            VI116_GRADLE_BIN=$(find "$_dist_root" -type f \
                -path "*/gradle-$VI116_GRADLE_VERSION/bin/gradle" \
                -perm -u+x -print -quit 2>/dev/null || true)
            if [ -n "$VI116_GRADLE_BIN" ]; then
                break
            fi
        fi
    done

    if [ -z "$VI116_GRADLE_BIN" ]; then
        VI116_WRAPPER_PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
        VI116_DIST_URL=$(sed -n 's/^distributionUrl=//p' "$VI116_WRAPPER_PROPS" | head -n 1 | sed 's/\\:/:/g')
        VI116_DIST_SHA=$(sed -n 's/^distributionSha256Sum=//p' "$VI116_WRAPPER_PROPS" | head -n 1)
        if [ -z "$VI116_DIST_URL" ] || [ -z "$VI116_DIST_SHA" ]; then
            printf 'ERROR: Missing Gradle distribution metadata for %s.\n' "$FORGE_LEGACY_TARGET" >&2
            exit 1
        fi

        VI116_DIST_ROOT="$GRADLE_USER_HOME/wrapper/dists/vanilla-instincts-$VI116_GRADLE_VERSION"
        VI116_DIST_ARCHIVE="$VI116_DIST_ROOT/gradle-$VI116_GRADLE_VERSION-bin.zip"
        mkdir -p "$VI116_DIST_ROOT"
        printf '%s: provisioning Gradle %s for the Java 8 launcher...\n' \
            "$FORGE_LEGACY_TARGET" "$VI116_GRADLE_VERSION" >&2
        vi116_fetch_file "$VI116_DIST_URL" "$VI116_DIST_ARCHIVE.part" || exit 1

        if ! command -v sha256sum >/dev/null 2>&1; then
            printf 'ERROR: sha256sum is required to verify Gradle %s.\n' "$VI116_GRADLE_VERSION" >&2
            exit 1
        fi
        VI116_DIST_ACTUAL_SHA=$(sha256sum "$VI116_DIST_ARCHIVE.part" | awk '{print $1}')
        if [ "$VI116_DIST_ACTUAL_SHA" != "$VI116_DIST_SHA" ]; then
            printf 'ERROR: Gradle %s checksum mismatch.\n' "$VI116_GRADLE_VERSION" >&2
            rm -f "$VI116_DIST_ARCHIVE.part"
            exit 1
        fi
        mv "$VI116_DIST_ARCHIVE.part" "$VI116_DIST_ARCHIVE"

        rm -rf "$VI116_DIST_ROOT/gradle-$VI116_GRADLE_VERSION"
        if command -v unzip >/dev/null 2>&1; then
            unzip -q "$VI116_DIST_ARCHIVE" -d "$VI116_DIST_ROOT"
        else
            (cd "$VI116_DIST_ROOT" && "$JDK8_HOME/bin/jar" xf "$VI116_DIST_ARCHIVE")
        fi
        VI116_GRADLE_BIN="$VI116_DIST_ROOT/gradle-$VI116_GRADLE_VERSION/bin/gradle"
        chmod +x "$VI116_GRADLE_BIN"
    fi

    if [ ! -x "$VI116_GRADLE_BIN" ]; then
        printf 'ERROR: Gradle %s launcher was not found after provisioning.\n' "$VI116_GRADLE_VERSION" >&2
        exit 1
    fi

    JAVA_HOME="$JDK8_HOME"
    export JAVA_HOME
    PATH="$JAVA_HOME/bin:$PATH"
    export PATH

    printf '%s: native Java 8 at %s; launching Gradle %s directly.\n' \
        "$FORGE_LEGACY_TARGET" "$JDK8_HOME" "$VI116_GRADLE_VERSION" >&2
    cd "$APP_HOME"
    exec "$VI116_GRADLE_BIN" "$@"
fi

# Normal Forge 1.18-1.21.10 and NeoForge 1.20.2-1.21.11 targets use
# Gradle 8.14.5. The wrapper client in this repository is Java 21 bytecode, so
# explicitly launch it on Java 21 even when buildAll was started from Java 8.
# :core still requests Java 17, while newer Minecraft targets request Java 21;
# make both JDKs visible to Gradle in one isolated user-home.
if [ "$STANDARD_MODERN" = true ]; then
    VI_STANDARD_JDK17=$(vi_boot_get_jdk 17) || exit 1
    VI_STANDARD_JDK21=$(vi_boot_get_jdk 21) || exit 1

    JAVA17_HOME="$VI_STANDARD_JDK17"
    JAVA21_HOME="$VI_STANDARD_JDK21"
    JAVA_HOME_17="$VI_STANDARD_JDK17"
    JAVA_HOME_17_X64="$VI_STANDARD_JDK17"
    JAVA_HOME_21="$VI_STANDARD_JDK21"
    JAVA_HOME_21_X64="$VI_STANDARD_JDK21"
    export JAVA17_HOME JAVA21_HOME JAVA_HOME_17 JAVA_HOME_17_X64 JAVA_HOME_21 JAVA_HOME_21_X64

    JAVA_HOME="$VI_STANDARD_JDK21"
    export JAVA_HOME
    PATH="$JAVA_HOME/bin:$PATH"
    export PATH

    GRADLE_USER_HOME="$APP_HOME/.gradle/modern-standard-user-home"
    export GRADLE_USER_HOME
    mkdir -p "$GRADLE_USER_HOME"
    cat > "$GRADLE_USER_HOME/gradle.properties" <<EOF
org.gradle.java.home=$VI_STANDARD_JDK21
org.gradle.java.installations.auto-detect=false
org.gradle.java.installations.auto-download=false
org.gradle.java.installations.fromEnv=JAVA17_HOME,JAVA21_HOME
org.gradle.java.installations.paths=$VI_STANDARD_JDK17,$VI_STANDARD_JDK21
EOF

    VI_STANDARD_TOOLCHAIN_PROPS="-Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.auto-download=false -Dorg.gradle.java.installations.fromEnv=JAVA17_HOME,JAVA21_HOME -Dorg.gradle.java.installations.paths=$VI_STANDARD_JDK17,$VI_STANDARD_JDK21"
    GRADLE_JAVA_ARGS="-Dorg.gradle.java.home=$VI_STANDARD_JDK21 $VI_STANDARD_TOOLCHAIN_PROPS"
    printf 'Vanilla Instincts 1.0.0: %s -> Gradle launcher Java 21; toolchains Java 17 + 21\n' \
        "$STANDARD_MODERN_TARGET" >&2
fi

# Minecraft 26.1+ requires Java 25 and Gradle 9.1+. The 26.x targets use
# the project-local Gradle 9.5 wrapper and a dedicated Java 25 toolchain. Keep
# this branch independent from Forge 1.21.11. Forge 26.x still needs a small
# Mavenizer Java 8 compatibility shim on Alpine/musl; NeoForge 26.x does not.
if [ "$MODERN_26X" = true ]; then
    GRADLE_USER_HOME="$APP_HOME/.gradle/modern-26x-user-home"
    export GRADLE_USER_HOME
    mkdir -p "$GRADLE_USER_HOME"

    JDK25_VERSION="25.0.4+9"
    JDK25_ROOT="$APP_HOME/.gradle/modern-26x-jdk25"
    VI26_BELL_OS="linux"
    if [ -f /etc/alpine-release ] || (ldd --version 2>&1 | grep -qi musl); then
        VI26_BELL_OS="linux-musl"
    fi
    VI26_BELL_ARCH="x86"
    case "$(uname -m 2>/dev/null || printf x86_64)" in
        aarch64|arm64) VI26_BELL_ARCH="arm" ;;
    esac
    JDK25_MANAGED_HOME="$JDK25_ROOT/liberica-jdk25-$VI26_BELL_OS"
    JDK25_HOME=""

    vi26_java_major() {
        _home="$1"
        if [ -x "$_home/bin/java" ] && [ -x "$_home/bin/javac" ]; then
            "$_home/bin/java" -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p'
        fi
    }

    # Shared download helpers must be defined for the entire 26.x branch. Forge
    # can need to provision the Mavenizer Java 8 compatibility JDK even when a
    # Java 25 JDK was already found/reused, so these cannot live only inside the
    # Java 25 provisioning block.
    vi26_fetch_stdout() {
        _url="$1"
        if command -v curl >/dev/null 2>&1; then
            curl --http1.1 -fsSL --retry 6 --retry-delay 2 \
                --connect-timeout 20 --max-time 120 "$_url"
        elif command -v wget >/dev/null 2>&1; then
            wget --timeout=30 --tries=6 -qO- "$_url"
        else
            printf 'ERROR: curl or wget is required to provision Java toolchains for %s.\n' "$MODERN_26_TARGET" >&2
            return 1
        fi
    }

    vi26_fetch_file() {
        _url="$1"
        _dest="$2"
        if command -v curl >/dev/null 2>&1; then
            curl --http1.1 -fL --retry 8 --retry-delay 2 \
                --connect-timeout 30 --max-time 1200 --continue-at - \
                -o "$_dest" "$_url"
        elif command -v wget >/dev/null 2>&1; then
            wget -c --timeout=30 --tries=8 -O "$_dest" "$_url"
        else
            printf 'ERROR: curl or wget is required to provision Java toolchains for %s.\n' "$MODERN_26_TARGET" >&2
            return 1
        fi
    }

    # Respect an explicit Java 25 home first.
    if [ -n "${JAVA25_HOME:-}" ] && [ "$(vi26_java_major "$JAVA25_HOME")" = "25" ]; then
        JDK25_HOME="$JAVA25_HOME"
    fi

    # Then reuse JAVA_HOME or a Java 25 JDK already on PATH.
    if [ -z "$JDK25_HOME" ] && [ -n "${JAVA_HOME:-}" ] \
            && [ "$(vi26_java_major "$JAVA_HOME")" = "25" ]; then
        JDK25_HOME="$JAVA_HOME"
    fi
    if [ -z "$JDK25_HOME" ]; then
        _java_on_path=$(command -v java 2>/dev/null || true)
        if [ -n "$_java_on_path" ]; then
            if command -v readlink >/dev/null 2>&1; then
                _java_on_path=$(readlink -f "$_java_on_path" 2>/dev/null || printf '%s' "$_java_on_path")
            fi
            _java_home=$(CDPATH= cd -- "$(dirname -- "$_java_on_path")/.." 2>/dev/null && pwd || true)
            if [ -n "$_java_home" ] && [ "$(vi26_java_major "$_java_home")" = "25" ]; then
                JDK25_HOME="$_java_home"
            fi
        fi
    fi

    # Reuse the managed JDK if a previous 26.x/1.21.11 build provisioned it.
    if [ -z "$JDK25_HOME" ] && [ "$(vi26_java_major "$JDK25_MANAGED_HOME")" = "25" ]; then
        JDK25_HOME="$JDK25_MANAGED_HOME"
    fi
    if [ -z "$JDK25_HOME" ]; then
        _forge_jdk="$APP_HOME/.gradle/forge-1.21.11-user-home/manual-jdks/liberica-jdk25-$VI26_BELL_OS"
        if [ "$(vi26_java_major "$_forge_jdk")" = "25" ]; then
            JDK25_HOME="$_forge_jdk"
        fi
    fi

    if [ -z "$JDK25_HOME" ]; then
        mkdir -p "$JDK25_ROOT"
        VI26_BELL_API="https://api.bell-sw.com/v1/liberica/releases?version=25.0.4%2B9&bitness=64&os=$VI26_BELL_OS&arch=$VI26_BELL_ARCH&package-type=tar.gz&bundle-type=jdk"

        printf '%s: provisioning Java 25 toolchain...\n' "$MODERN_26_TARGET" >&2
        VI26_META=$(vi26_fetch_stdout "$VI26_BELL_API") || exit 1
        VI26_URL=$(printf '%s' "$VI26_META" | tr '\n' ' ' | sed -n 's/.*"downloadUrl"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
        VI26_SHA1=$(printf '%s' "$VI26_META" | tr '\n' ' ' | sed -n 's/.*"sha1"[[:space:]]*:[[:space:]]*"\([0-9a-fA-F]*\)".*/\1/p')
        if [ -z "$VI26_URL" ] || [ -z "$VI26_SHA1" ]; then
            printf 'ERROR: BellSoft API did not return Java 25 download metadata for %s.\n' "$MODERN_26_TARGET" >&2
            exit 1
        fi
        VI26_ARCHIVE="$JDK25_ROOT/bellsoft-jdk25-musl.tar.gz.part"
        vi26_fetch_file "$VI26_URL" "$VI26_ARCHIVE" || exit 1
        if command -v sha1sum >/dev/null 2>&1; then
            VI26_ACTUAL_SHA1=$(sha1sum "$VI26_ARCHIVE" | awk '{print $1}')
            if [ "$VI26_ACTUAL_SHA1" != "$VI26_SHA1" ]; then
                printf 'ERROR: Java 25 checksum mismatch for %s.\n' "$MODERN_26_TARGET" >&2
                rm -f "$VI26_ARCHIVE"
                exit 1
            fi
        fi
        VI26_EXTRACT="$JDK25_ROOT/extract"
        rm -rf "$VI26_EXTRACT" "$JDK25_MANAGED_HOME"
        mkdir -p "$VI26_EXTRACT"
        tar -xzf "$VI26_ARCHIVE" -C "$VI26_EXTRACT"
        VI26_EXTRACTED=$(find "$VI26_EXTRACT" -mindepth 1 -maxdepth 1 -type d -print -quit)
        if [ -z "$VI26_EXTRACTED" ] || [ ! -x "$VI26_EXTRACTED/bin/javac" ]; then
            printf 'ERROR: Java 25 archive for %s did not contain a JDK.\n' "$MODERN_26_TARGET" >&2
            exit 1
        fi
        mv "$VI26_EXTRACTED" "$JDK25_MANAGED_HOME"
        rm -rf "$VI26_EXTRACT" "$VI26_ARCHIVE"
        JDK25_HOME="$JDK25_MANAGED_HOME"
    fi

    if [ "$(vi26_java_major "$JDK25_HOME")" != "25" ]; then
        printf 'ERROR: Java 25 JDK is required for %s: %s\n' "$MODERN_26_TARGET" "$JDK25_HOME" >&2
        exit 1
    fi

    # Keep the real Java 25 JDK for the Gradle daemon. Forge 26.x also uses
    # Minecraft Mavenizer 0.5.x, whose modifyAccess step still asks its own
    # JavaProvisioner for a Java 8 JDK. Alpine/musl has no matching Disco
    # download, so give Mavenizer the same proven local Java 8 probe shim used
    # by the Forge 1.21.11 path. NeoForge 26.x does not need this workaround.
    VI26_JAVA25_REAL_HOME="$JDK25_HOME"
    VI26_JAVA25_TOOLCHAIN_HOME="$JDK25_HOME"

    if [ "$MODERN_26_FORGE" = true ]; then
        VI26_JDK8_ROOT="$GRADLE_USER_HOME/manual-jdks"
        VI26_JDK8_MANAGED_HOME="$VI26_JDK8_ROOT/liberica-jdk8-$VI26_BELL_OS"
        VI26_JDK8_REAL_HOME=""

        vi26_java8_major() {
            _home="$1"
            if [ -x "$_home/bin/java" ] && [ -x "$_home/bin/javac" ]; then
                _line=$("$_home/bin/java" -version 2>&1 | sed -n '1p')
                case "$_line" in
                    *'version "1.8.'*) printf '8' ;;
                    *) printf '%s' "$_line" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' ;;
                esac
            fi
        }

        if [ -n "${JAVA8_HOME:-}" ] && [ "$(vi26_java8_major "$JAVA8_HOME")" = "8" ]; then
            VI26_JDK8_REAL_HOME="$JAVA8_HOME"
        fi
        if [ -z "$VI26_JDK8_REAL_HOME" ] && [ "$(vi26_java8_major "$VI26_JDK8_MANAGED_HOME")" = "8" ]; then
            VI26_JDK8_REAL_HOME="$VI26_JDK8_MANAGED_HOME"
        fi
        if [ -z "$VI26_JDK8_REAL_HOME" ]; then
            _vi26_12111_jdk8="$APP_HOME/.gradle/forge-1.21.11-user-home/manual-jdks/liberica-jdk8-$VI26_BELL_OS"
            if [ "$(vi26_java8_major "$_vi26_12111_jdk8")" = "8" ]; then
                VI26_JDK8_REAL_HOME="$_vi26_12111_jdk8"
            fi
        fi
        if [ -z "$VI26_JDK8_REAL_HOME" ]; then
            _vi26_legacy_jdk8="$APP_HOME/.gradle/forge-legacy-java8-toolchains/liberica-jdk8"
            if [ "$(vi26_java8_major "$_vi26_legacy_jdk8")" = "8" ]; then
                VI26_JDK8_REAL_HOME="$_vi26_legacy_jdk8"
            fi
        fi

        if [ -z "$VI26_JDK8_REAL_HOME" ]; then
            mkdir -p "$VI26_JDK8_ROOT"
            VI26_BELL8_API="https://api.bell-sw.com/v1/liberica/releases?version=8u502%2B9&bitness=64&os=$VI26_BELL_OS&arch=$VI26_BELL_ARCH&package-type=tar.gz&bundle-type=jdk"
            printf '%s: provisioning Mavenizer Java 8 compatibility toolchain...\n' "$MODERN_26_TARGET" >&2
            VI26_JDK8_META=$(vi26_fetch_stdout "$VI26_BELL8_API") || exit 1
            VI26_JDK8_URL=$(printf '%s' "$VI26_JDK8_META" | tr '\n' ' ' | sed -n 's/.*"downloadUrl"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
            VI26_JDK8_SHA1=$(printf '%s' "$VI26_JDK8_META" | tr '\n' ' ' | sed -n 's/.*"sha1"[[:space:]]*:[[:space:]]*"\([0-9a-fA-F]*\)".*/\1/p')
            if [ -z "$VI26_JDK8_URL" ] || [ -z "$VI26_JDK8_SHA1" ]; then
                printf 'ERROR: BellSoft API did not return Java 8 musl metadata for %s.\n' "$MODERN_26_TARGET" >&2
                exit 1
            fi
            VI26_JDK8_ARCHIVE="$VI26_JDK8_ROOT/bellsoft-jdk8-musl.tar.gz.part"
            vi26_fetch_file "$VI26_JDK8_URL" "$VI26_JDK8_ARCHIVE" || exit 1
            if command -v sha1sum >/dev/null 2>&1; then
                VI26_JDK8_ACTUAL_SHA1=$(sha1sum "$VI26_JDK8_ARCHIVE" | awk '{print $1}')
                if [ "$VI26_JDK8_ACTUAL_SHA1" != "$VI26_JDK8_SHA1" ]; then
                    printf 'ERROR: Java 8 checksum mismatch for %s.\n' "$MODERN_26_TARGET" >&2
                    rm -f "$VI26_JDK8_ARCHIVE"
                    exit 1
                fi
            fi
            VI26_JDK8_EXTRACT="$VI26_JDK8_ROOT/extract-jdk8"
            rm -rf "$VI26_JDK8_EXTRACT" "$VI26_JDK8_MANAGED_HOME"
            mkdir -p "$VI26_JDK8_EXTRACT"
            tar -xzf "$VI26_JDK8_ARCHIVE" -C "$VI26_JDK8_EXTRACT"
            VI26_JDK8_EXTRACTED=$(find "$VI26_JDK8_EXTRACT" -mindepth 1 -maxdepth 1 -type d -print -quit)
            if [ -z "$VI26_JDK8_EXTRACTED" ] || [ ! -x "$VI26_JDK8_EXTRACTED/bin/javac" ]; then
                printf 'ERROR: Java 8 archive for %s did not contain a full JDK.\n' "$MODERN_26_TARGET" >&2
                exit 1
            fi
            mv "$VI26_JDK8_EXTRACTED" "$VI26_JDK8_MANAGED_HOME"
            rm -rf "$VI26_JDK8_EXTRACT" "$VI26_JDK8_ARCHIVE"
            VI26_JDK8_REAL_HOME="$VI26_JDK8_MANAGED_HOME"
        fi

        if [ "$(vi26_java8_major "$VI26_JDK8_REAL_HOME")" != "8" ]; then
            printf 'ERROR: Java 8 compatibility JDK is invalid for %s: %s\n' "$MODERN_26_TARGET" "$VI26_JDK8_REAL_HOME" >&2
            exit 1
        fi

        VI26_JDK8_PROBE_HOME="$VI26_JDK8_ROOT/mavenizer-jdk8-probe"
        rm -rf "$VI26_JDK8_PROBE_HOME"
        mkdir -p "$VI26_JDK8_PROBE_HOME/bin"
        ln -s "$VI26_JDK8_REAL_HOME" "$VI26_JDK8_PROBE_HOME/.real-home"
        for _item in "$VI26_JDK8_REAL_HOME"/*; do
            _base=$(basename "$_item")
            if [ "$_base" != "bin" ]; then
                ln -s "$_item" "$VI26_JDK8_PROBE_HOME/$_base"
            fi
        done
        for _tool in "$VI26_JDK8_REAL_HOME"/bin/*; do
            _base=$(basename "$_tool")
            if [ "$_base" != "java" ]; then
                ln -s "$_tool" "$VI26_JDK8_PROBE_HOME/bin/$_base"
            fi
        done
        cat > "$VI26_JDK8_PROBE_HOME/bin/java" <<'EOF_VI26_JAVA8_PROBE'
#!/bin/sh
set -eu
SHIM_HOME=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
REAL_HOME=$(readlink -f "$SHIM_HOME/.real-home" 2>/dev/null || printf '%s' "$SHIM_HOME/.real-home")
for _arg in "$@"; do
    if [ "$_arg" = "JavaProbe" ]; then
        printf '%s\n' \
            "JAVA_PROBE: java.home $SHIM_HOME" \
            'JAVA_PROBE: java.version 1.8.0_502' \
            'JAVA_PROBE: java.vendor BellSoft' \
            'JAVA_PROBE: java.runtime.name OpenJDK Runtime Environment' \
            'JAVA_PROBE: java.runtime.version 1.8.0_502-b09' \
            'JAVA_PROBE: java.vm.name OpenJDK 64-Bit Server VM' \
            'JAVA_PROBE: java.vm.version 25.502-b09' \
            'JAVA_PROBE: java.vm.vendor BellSoft' \
            'JAVA_PROBE: os.arch amd64'
        exit 0
    fi
done
exec "$REAL_HOME/bin/java" "$@"
EOF_VI26_JAVA8_PROBE
        chmod +x "$VI26_JDK8_PROBE_HOME/bin/java"

        # The Gradle daemon stays on the real JDK 25. Only Java 25 toolchain
        # subprocesses (including Mavenizer) use this launcher shim, which
        # exposes Java 8 through JAVA_HOME/JAVA_HOME_8 before executing JDK 25.
        VI26_JAVA25_SHIM_HOME="$VI26_JDK8_ROOT/mavenizer-jdk25-env"
        rm -rf "$VI26_JAVA25_SHIM_HOME"
        mkdir -p "$VI26_JAVA25_SHIM_HOME/bin"
        ln -s "$VI26_JAVA25_REAL_HOME" "$VI26_JAVA25_SHIM_HOME/.real-home"
        ln -s "$VI26_JDK8_PROBE_HOME" "$VI26_JAVA25_SHIM_HOME/.java8-home"
        for _item in "$VI26_JAVA25_REAL_HOME"/*; do
            _base=$(basename "$_item")
            if [ "$_base" != "bin" ]; then
                ln -s "$_item" "$VI26_JAVA25_SHIM_HOME/$_base"
            fi
        done
        for _tool in "$VI26_JAVA25_REAL_HOME"/bin/*; do
            _base=$(basename "$_tool")
            if [ "$_base" != "java" ]; then
                ln -s "$_tool" "$VI26_JAVA25_SHIM_HOME/bin/$_base"
            fi
        done
        cat > "$VI26_JAVA25_SHIM_HOME/bin/java" <<'EOF_VI26_JAVA25_SHIM'
#!/bin/sh
set -eu
SHIM_HOME=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAVA8_TARGET=$(readlink -f "$SHIM_HOME/.java8-home" 2>/dev/null || printf '%s' "$SHIM_HOME/.java8-home")
export JAVA_HOME="$JAVA8_TARGET"
export JAVA_HOME_8="$JAVA8_TARGET"
export JAVA_HOME_8_X64="$JAVA8_TARGET"
export JAVA8_HOME="$JAVA8_TARGET"
export JAVA_HOME_25="$SHIM_HOME"
export JAVA_HOME_25_X64="$SHIM_HOME"
export JAVA25_HOME="$SHIM_HOME"
exec "$SHIM_HOME/.real-home/bin/java" "$@"
EOF_VI26_JAVA25_SHIM
        chmod +x "$VI26_JAVA25_SHIM_HOME/bin/java"
        VI26_JAVA25_TOOLCHAIN_HOME="$VI26_JAVA25_SHIM_HOME"

        JAVA_HOME_8="$VI26_JDK8_PROBE_HOME"
        JAVA_HOME_8_X64="$VI26_JDK8_PROBE_HOME"
        export JAVA_HOME_8 JAVA_HOME_8_X64

        # Seed the cache layouts Mavenizer 0.5.x scans as a fallback. Do not
        # touch actual downloaded artifacts in the named cache slots.
        VI26_MAVENIZER_CACHE="$GRADLE_USER_HOME/caches/minecraftforge/forgegradle/mavenizer/caches"
        mkdir -p "$VI26_MAVENIZER_CACHE" "$VI26_MAVENIZER_CACHE/jdks"
        for _candidate in \
            "$VI26_MAVENIZER_CACHE/jdk8-musl" \
            "$VI26_MAVENIZER_CACHE/jdks/jdk8-musl"; do
            rm -rf "$_candidate"
            ln -s "$VI26_JDK8_PROBE_HOME" "$_candidate"
        done
    fi

    JAVA_HOME="$VI26_JAVA25_REAL_HOME"
    JAVA25_HOME="$VI26_JAVA25_TOOLCHAIN_HOME"
    export JAVA_HOME JAVA25_HOME
    PATH="$JAVA_HOME/bin:$PATH"
    export PATH
    JAVA_CMD="$JAVA_HOME/bin/java"

    VI26_TOOLCHAIN_PROPS="-Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.auto-download=false -Dorg.gradle.java.installations.fromEnv=JAVA25_HOME -Dorg.gradle.java.installations.paths=$JAVA25_HOME"
    JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }$VI26_TOOLCHAIN_PROPS"
    export JAVA_TOOL_OPTIONS
    GRADLE_JAVA_ARGS="-Dorg.gradle.java.home=$VI26_JAVA25_REAL_HOME $VI26_TOOLCHAIN_PROPS"
    printf 'Vanilla Instincts 1.0.0: %s -> Java 25 / Gradle 9.5\n' "$MODERN_26_TARGET" >&2
fi

# ForgeGradle 7 / Minecraft Mavenizer provisions Java separately from the
# Gradle launcher. On Alpine, Foojay/Disco has no matching JDK package, so
# make the already-installed local JDK explicit. This is intentionally scoped
# to Forge 1.21.11; every other target keeps the original wrapper behaviour.
if [ "$FORGE_12111" = true ]; then
    JAVA21_HOME=$(vi_boot_get_jdk 21) || exit 1
    if [ "$(vi_boot_java_major "$JAVA21_HOME" 2>/dev/null || true)" != "21" ]; then
        printf 'ERROR: could not resolve a full Java 21 JDK for Forge 1.21.11: %s\n' "$JAVA21_HOME" >&2
        exit 1
    fi
    JAVA_HOME="$JAVA21_HOME"
    export JAVA_HOME

    # Keep the real Java 21 home in a dedicated variable. The parent Gradle
    # process remains on Java 21; Mavenizer receives its legacy Java 8 probe
    # environment only inside the dedicated Java 25 launcher shim below.
    JAVA21_HOME="$JAVA_HOME"
    export JAVA21_HOME
    export PATH="$JAVA21_HOME/bin:$PATH"
    JAVA_CMD="$JAVA21_HOME/bin/java"

    # ForgeGradle 7 itself can run on Java 21, but Minecraft Mavenizer 0.5.x
    # is built for Java 25. On Alpine, generic Linux JDK downloads are often
    # glibc builds and cannot run on musl. Provision a BellSoft Liberica JDK
    # 25 musl archive locally for this target only, while keeping Minecraft
    # compilation/runtime on the system Java 21 JDK.
    GRADLE_USER_HOME="$APP_HOME/.gradle/forge-1.21.11-user-home"
    export GRADLE_USER_HOME
    mkdir -p "$GRADLE_USER_HOME"

    JDK25_VERSION="25.0.4+9"
    JDK25_ROOT="$GRADLE_USER_HOME/manual-jdks"
    VI12111_BELL_OS="linux"
    if [ -f /etc/alpine-release ] || (ldd --version 2>&1 | grep -qi musl); then
        VI12111_BELL_OS="linux-musl"
    fi
    VI12111_BELL_ARCH="x86"
    case "$(uname -m 2>/dev/null || printf x86_64)" in
        aarch64|arm64) VI12111_BELL_ARCH="arm" ;;
    esac
    JDK25_MANAGED_HOME="$JDK25_ROOT/liberica-jdk25-$VI12111_BELL_OS"
    JDK25_HOME="$JDK25_MANAGED_HOME"
    JDK25_EXTERNAL=false

    if [ -n "${JAVA25_HOME:-}" ]; then
        JDK25_HOME="$JAVA25_HOME"
        JDK25_EXTERNAL=true
    fi

    JDK25_MAJOR=""
    if [ -x "$JDK25_HOME/bin/java" ]; then
        JDK25_MAJOR=$("$JDK25_HOME/bin/java" -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
    fi

    if [ "$JDK25_MAJOR" != "25" ]; then
        if [ "$JDK25_EXTERNAL" = true ]; then
            printf 'ERROR: JAVA25_HOME must point to a Java 25 JDK: %s\n' "$JAVA25_HOME" >&2
            exit 1
        fi
        JDK25_HOME="$JDK25_MANAGED_HOME"
        rm -rf "$JDK25_HOME"
        mkdir -p "$JDK25_ROOT"

        BELL_API="https://api.bell-sw.com/v1/liberica/releases?version=25.0.4%2B9&bitness=64&os=$VI12111_BELL_OS&arch=$VI12111_BELL_ARCH&package-type=tar.gz&bundle-type=jdk"

        # Network provisioning is deliberately resumable. Alpine/curl and some
        # CDN edges can occasionally reset HTTP/2 streams; force HTTP/1.1 and
        # keep .part files so the next attempt resumes instead of restarting a
        # 100-250 MiB JDK download from byte zero.
        fetch_stdout() {
            _url="$1"
            if command -v curl >/dev/null 2>&1; then
                _attempt=1
                while [ "$_attempt" -le 8 ]; do
                    if curl --http1.1 -fsSL --connect-timeout 20 --max-time 120 "$_url"; then
                        return 0
                    fi
                    printf 'Network metadata attempt %s/8 failed; retrying...\n' "$_attempt" >&2
                    sleep 2
                    _attempt=$((_attempt + 1))
                done
                return 1
            elif command -v wget >/dev/null 2>&1; then
                _attempt=1
                while [ "$_attempt" -le 8 ]; do
                    if wget --timeout=30 --tries=1 -qO- "$_url"; then
                        return 0
                    fi
                    printf 'Network metadata attempt %s/8 failed; retrying...\n' "$_attempt" >&2
                    sleep 2
                    _attempt=$((_attempt + 1))
                done
                return 1
            else
                printf 'ERROR: curl or wget is required to provision Java toolchains for Forge 1.21.11.\n' >&2
                return 1
            fi
        }

        fetch_file() {
            _url="$1"
            _dest="$2"
            if command -v curl >/dev/null 2>&1; then
                _attempt=1
                while [ "$_attempt" -le 10 ]; do
                    if [ -s "$_dest" ]; then
                        if curl --http1.1 -fL --connect-timeout 30 --max-time 900 \
                                --continue-at - -o "$_dest" "$_url"; then
                            return 0
                        else
                            _rc=$?
                            # curl 33 means the server refused a resumed transfer.
                            # Restart once from zero, then future failures can resume.
                            if [ "$_rc" -eq 33 ]; then
                                rm -f "$_dest"
                            fi
                        fi
                    else
                        if curl --http1.1 -fL --connect-timeout 30 --max-time 900 \
                                -o "$_dest" "$_url"; then
                            return 0
                        fi
                    fi
                    _bytes=0
                    if [ -f "$_dest" ]; then
                        _bytes=$(wc -c < "$_dest" | tr -d ' ')
                    fi
                    printf 'Download attempt %s/10 failed; retrying from %s bytes...\n' \
                        "$_attempt" "$_bytes" >&2
                    sleep 2
                    _attempt=$((_attempt + 1))
                done
                return 1
            elif command -v wget >/dev/null 2>&1; then
                _attempt=1
                while [ "$_attempt" -le 10 ]; do
                    if wget -c --timeout=30 --tries=1 -O "$_dest" "$_url"; then
                        return 0
                    fi
                    printf 'Download attempt %s/10 failed; retrying...\n' "$_attempt" >&2
                    sleep 2
                    _attempt=$((_attempt + 1))
                done
                return 1
            else
                printf 'ERROR: curl or wget is required to provision Java toolchains for Forge 1.21.11.\n' >&2
                return 1
            fi
        }

        printf 'Forge 1.21.11: provisioning platform-compatible Java 25 toolchain...\n' >&2
        JDK25_META=$(fetch_stdout "$BELL_API") || exit 1
        JDK25_URL=$(printf '%s' "$JDK25_META" | tr '\n' ' ' | sed -n 's/.*"downloadUrl"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
        JDK25_SHA1=$(printf '%s' "$JDK25_META" | tr '\n' ' ' | sed -n 's/.*"sha1"[[:space:]]*:[[:space:]]*"\([0-9a-fA-F]*\)".*/\1/p')

        if [ -z "$JDK25_URL" ] || [ -z "$JDK25_SHA1" ]; then
            printf 'ERROR: BellSoft API did not return Java 25 musl download metadata.\n' >&2
            exit 1
        fi

        JDK25_ARCHIVE="$JDK25_ROOT/bellsoft-jdk25-musl.tar.gz"
        JDK25_PART="$JDK25_ARCHIVE.part"
        fetch_file "$JDK25_URL" "$JDK25_PART" || exit 1

        if ! command -v sha1sum >/dev/null 2>&1; then
            printf 'ERROR: sha1sum is required to verify the BellSoft Java 25 download.\n' >&2
            rm -f "$JDK25_PART"
            exit 1
        fi
        JDK25_ACTUAL_SHA1=$(sha1sum "$JDK25_PART" | awk '{print $1}')
        if [ "$JDK25_ACTUAL_SHA1" != "$JDK25_SHA1" ]; then
            printf 'ERROR: BellSoft Java 25 checksum mismatch.\n' >&2
            rm -f "$JDK25_PART"
            exit 1
        fi
        mv "$JDK25_PART" "$JDK25_ARCHIVE"

        JDK25_STAGE="$JDK25_ROOT/.jdk25-extract-$$"
        rm -rf "$JDK25_STAGE"
        mkdir -p "$JDK25_STAGE"
        if ! tar -xzf "$JDK25_ARCHIVE" -C "$JDK25_STAGE"; then
            printf 'ERROR: Could not extract BellSoft Java 25 archive.\n' >&2
            rm -rf "$JDK25_STAGE"
            exit 1
        fi

        JDK25_EXTRACTED=""
        for candidate in "$JDK25_STAGE"/*; do
            if [ -d "$candidate" ] && [ -x "$candidate/bin/java" ]; then
                JDK25_EXTRACTED="$candidate"
                break
            fi
        done
        if [ -z "$JDK25_EXTRACTED" ]; then
            printf 'ERROR: BellSoft Java 25 archive did not contain a usable JDK.\n' >&2
            rm -rf "$JDK25_STAGE"
            exit 1
        fi

        mv "$JDK25_EXTRACTED" "$JDK25_HOME"
        rm -rf "$JDK25_STAGE"

        JDK25_MAJOR=$("$JDK25_HOME/bin/java" -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
        if [ "$JDK25_MAJOR" != "25" ]; then
            printf 'ERROR: Provisioned BellSoft toolchain is not Java 25.\n' >&2
            exit 1
        fi
    fi

    # MinecraftMavenizer also uses a legacy Java 8 tool for the official
    # mappings conversion step. Disco does not publish Alpine packages for
    # the Forge provisioner, so provide an explicit BellSoft Liberica JDK 8
    # musl toolchain alongside Java 21 and Java 25.
    JDK8_VERSION="8u502+9"
    JDK8_ROOT="$GRADLE_USER_HOME/manual-jdks"
    JDK8_MANAGED_HOME="$JDK8_ROOT/liberica-jdk8-$VI12111_BELL_OS"
    JDK8_HOME="$JDK8_MANAGED_HOME"
    JDK8_EXTERNAL=false

    if [ -n "${JAVA8_HOME:-}" ]; then
        JDK8_HOME="$JAVA8_HOME"
        JDK8_EXTERNAL=true
    fi

    java_major() {
        _line=$("$1/bin/java" -version 2>&1 | sed -n '1p')
        case "$_line" in
            *'version "1.8.'*) printf '8' ;;
            *) printf '%s' "$_line" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' ;;
        esac
    }

    JDK8_MAJOR=""
    if [ -x "$JDK8_HOME/bin/java" ]; then
        JDK8_MAJOR=$(java_major "$JDK8_HOME")
    fi

    if [ "$JDK8_MAJOR" != "8" ]; then
        if [ "$JDK8_EXTERNAL" = true ]; then
            printf 'ERROR: JAVA8_HOME must point to a Java 8 JDK: %s\n' "$JAVA8_HOME" >&2
            exit 1
        fi
        JDK8_HOME="$JDK8_MANAGED_HOME"
        rm -rf "$JDK8_HOME"
        mkdir -p "$JDK8_ROOT"

        BELL8_API="https://api.bell-sw.com/v1/liberica/releases?version=8u502%2B9&bitness=64&os=$VI12111_BELL_OS&arch=$VI12111_BELL_ARCH&package-type=tar.gz&bundle-type=jdk"
        printf 'Forge 1.21.11: provisioning platform-compatible Java 8 toolchain...\n' >&2
        JDK8_META=$(vi_boot_fetch_stdout "$BELL8_API") || exit 1
        JDK8_URL=$(printf '%s' "$JDK8_META" | tr '\n' ' ' | sed -n 's/.*"downloadUrl"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
        JDK8_SHA1=$(printf '%s' "$JDK8_META" | tr '\n' ' ' | sed -n 's/.*"sha1"[[:space:]]*:[[:space:]]*"\([0-9a-fA-F]*\)".*/\1/p')

        if [ -z "$JDK8_URL" ] || [ -z "$JDK8_SHA1" ]; then
            printf 'ERROR: BellSoft API did not return Java 8 musl download metadata.\n' >&2
            exit 1
        fi

        JDK8_ARCHIVE="$JDK8_ROOT/bellsoft-jdk8-musl.tar.gz"
        JDK8_PART="$JDK8_ARCHIVE.part"
        vi_boot_fetch_file "$JDK8_URL" "$JDK8_PART" || exit 1

        if ! command -v sha1sum >/dev/null 2>&1; then
            printf 'ERROR: sha1sum is required to verify the BellSoft Java 8 download.\n' >&2
            rm -f "$JDK8_PART"
            exit 1
        fi
        JDK8_ACTUAL_SHA1=$(sha1sum "$JDK8_PART" | awk '{print $1}')
        if [ "$JDK8_ACTUAL_SHA1" != "$JDK8_SHA1" ]; then
            printf 'ERROR: BellSoft Java 8 checksum mismatch.\n' >&2
            rm -f "$JDK8_PART"
            exit 1
        fi
        mv "$JDK8_PART" "$JDK8_ARCHIVE"

        JDK8_STAGE="$JDK8_ROOT/.jdk8-extract-$$"
        rm -rf "$JDK8_STAGE"
        mkdir -p "$JDK8_STAGE"
        if ! tar -xzf "$JDK8_ARCHIVE" -C "$JDK8_STAGE"; then
            printf 'ERROR: Could not extract BellSoft Java 8 archive.\n' >&2
            rm -rf "$JDK8_STAGE"
            exit 1
        fi

        JDK8_EXTRACTED=""
        for candidate in "$JDK8_STAGE"/*; do
            if [ -d "$candidate" ] && [ -x "$candidate/bin/java" ]; then
                JDK8_EXTRACTED="$candidate"
                break
            fi
        done
        if [ -z "$JDK8_EXTRACTED" ]; then
            printf 'ERROR: BellSoft Java 8 archive did not contain a usable JDK.\n' >&2
            rm -rf "$JDK8_STAGE"
            exit 1
        fi

        mv "$JDK8_EXTRACTED" "$JDK8_HOME"
        rm -rf "$JDK8_STAGE"

        JDK8_MAJOR=$(java_major "$JDK8_HOME")
        if [ "$JDK8_MAJOR" != "8" ]; then
            printf 'ERROR: Provisioned BellSoft toolchain is not Java 8.\n' >&2
            exit 1
        fi
    fi

    JAVA8_REAL_HOME="$JDK8_HOME"
    JAVA25_REAL_HOME="$JDK25_HOME"
    export JAVA8_REAL_HOME JAVA25_REAL_HOME

    # JavaProvisioner validates every candidate by executing:
    #   <jdk>/bin/java -classpath <mavenizer> JavaProbe
    # On Alpine the real Liberica 8 runtime works for normal commands but the
    # Mavenizer probe path can fail classification, after which Disco has no
    # Java 8 ALPINE/AMD64 download to fall back to. Provide a complete shim
    # home: only the JavaProbe invocation is answered locally; every real Java
    # command and every other JDK tool is delegated to the real Liberica 8.
    if [ ! -x "$JAVA8_REAL_HOME/bin/javac" ]; then
        printf 'ERROR: Provisioned Java 8 toolchain is not a full JDK (missing javac): %s\n' "$JAVA8_REAL_HOME" >&2
        exit 1
    fi

    JAVA8_PROBE_SHIM_HOME="$JDK8_ROOT/mavenizer-jdk8-probe"
    rm -rf "$JAVA8_PROBE_SHIM_HOME"
    mkdir -p "$JAVA8_PROBE_SHIM_HOME/bin"
    ln -s "$JAVA8_REAL_HOME" "$JAVA8_PROBE_SHIM_HOME/.real-home"

    for _item in "$JAVA8_REAL_HOME"/*; do
        _base=$(basename "$_item")
        if [ "$_base" != "bin" ]; then
            ln -s "$_item" "$JAVA8_PROBE_SHIM_HOME/$_base"
        fi
    done
    for _tool in "$JAVA8_REAL_HOME"/bin/*; do
        _base=$(basename "$_tool")
        if [ "$_base" != "java" ]; then
            ln -s "$_tool" "$JAVA8_PROBE_SHIM_HOME/bin/$_base"
        fi
    done

    cat > "$JAVA8_PROBE_SHIM_HOME/bin/java" <<'EOF_JAVA8_PROBE_SHIM'
#!/bin/sh
set -eu
SHIM_HOME=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
REAL_HOME=$(readlink -f "$SHIM_HOME/.real-home" 2>/dev/null || printf '%s' "$SHIM_HOME/.real-home")

for _arg in "$@"; do
    if [ "$_arg" = "JavaProbe" ]; then
        printf '%s\n' \
            "JAVA_PROBE: java.home $SHIM_HOME" \
            'JAVA_PROBE: java.version 1.8.0_502' \
            'JAVA_PROBE: java.vendor BellSoft' \
            'JAVA_PROBE: java.runtime.name OpenJDK Runtime Environment' \
            'JAVA_PROBE: java.runtime.version 1.8.0_502-b09' \
            'JAVA_PROBE: java.vm.name OpenJDK 64-Bit Server VM' \
            'JAVA_PROBE: java.vm.version 25.502-b09' \
            'JAVA_PROBE: java.vm.vendor BellSoft' \
            'JAVA_PROBE: os.arch amd64'
        exit 0
    fi
done

exec "$REAL_HOME/bin/java" "$@"
EOF_JAVA8_PROBE_SHIM
    chmod +x "$JAVA8_PROBE_SHIM_HOME/bin/java"

    JAVA8_HOME="$JAVA8_PROBE_SHIM_HOME"
    export JAVA8_HOME

    # ForgeGradle launches Mavenizer in a Java 25 toolchain process. Build a
    # tiny Java 25 shim JDK whose java launcher injects Java 8 as JAVA_HOME
    # immediately before Mavenizer starts. This survives Gradle daemon/process
    # environment normalization and guarantees JavaProvisioner sees Java 8.
    JAVA25_SHIM_HOME="$JDK25_ROOT/mavenizer-jdk25-env"
    rm -rf "$JAVA25_SHIM_HOME"
    mkdir -p "$JAVA25_SHIM_HOME/bin"
    ln -s "$JAVA25_REAL_HOME" "$JAVA25_SHIM_HOME/.real-home"
    ln -s "$JAVA8_HOME" "$JAVA25_SHIM_HOME/.java8-home"
    ln -s "$JAVA21_HOME" "$JAVA25_SHIM_HOME/.java21-home"

    for _item in "$JAVA25_REAL_HOME"/*; do
        _base=$(basename "$_item")
        if [ "$_base" != "bin" ]; then
            ln -s "$_item" "$JAVA25_SHIM_HOME/$_base"
        fi
    done
    for _tool in "$JAVA25_REAL_HOME"/bin/*; do
        _base=$(basename "$_tool")
        if [ "$_base" != "java" ]; then
            ln -s "$_tool" "$JAVA25_SHIM_HOME/bin/$_base"
        fi
    done

    cat > "$JAVA25_SHIM_HOME/bin/java" <<'EOF_JAVA25_SHIM'
#!/bin/sh
set -eu
SHIM_HOME=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAVA8_TARGET=$(readlink -f "$SHIM_HOME/.java8-home" 2>/dev/null || printf '%s' "$SHIM_HOME/.java8-home")
JAVA21_TARGET=$(readlink -f "$SHIM_HOME/.java21-home" 2>/dev/null || printf '%s' "$SHIM_HOME/.java21-home")
export JAVA_HOME="$JAVA8_TARGET"
export JAVA_HOME_8="$JAVA8_TARGET"
export JAVA_HOME_8_X64="$JAVA8_TARGET"
export JAVA_HOME_21="$JAVA21_TARGET"
export JAVA_HOME_21_X64="$JAVA21_TARGET"
export JAVA_HOME_25="$SHIM_HOME"
export JAVA_HOME_25_X64="$SHIM_HOME"
export JAVA8_HOME="$JAVA8_TARGET"
export JAVA21_HOME="$JAVA21_TARGET"
export JAVA25_HOME="$SHIM_HOME"
exec "$SHIM_HOME/.real-home/bin/java" "$@"
EOF_JAVA25_SHIM
    chmod +x "$JAVA25_SHIM_HOME/bin/java"

    JAVA25_HOME="$JAVA25_SHIM_HOME"
    export JAVA25_HOME

    # Mavenizer gets its Java 8 environment from the dedicated Java 25 shim
    # above. Keep the parent Gradle process on the real Java 21 home so Gradle
    # never probes the Mavenizer-only Java 8 shim as the current JVM.
    JAVA_HOME_8="$JAVA8_HOME"
    JAVA_HOME_8_X64="$JAVA8_HOME"
    JAVA_HOME_21="$JAVA21_HOME"
    JAVA_HOME_21_X64="$JAVA21_HOME"
    JAVA_HOME_25="$JAVA25_HOME"
    JAVA_HOME_25_X64="$JAVA25_HOME"
    export JAVA_HOME_8 JAVA_HOME_8_X64 JAVA_HOME_21 JAVA_HOME_21_X64 JAVA_HOME_25 JAVA_HOME_25_X64

    JAVA_HOME="$JAVA21_HOME"
    export JAVA_HOME

    # Minecraft Mavenizer also scans its dedicated Disco/JDK cache. Seed all
    # three complete JDK homes there as a second discovery path.
    MAVENIZER_JDK_CACHE="$GRADLE_USER_HOME/caches/minecraftforge/forgegradle/mavenizer/caches"
    MAVENIZER_NESTED_JDK_CACHE="$MAVENIZER_JDK_CACHE/jdks"
    mkdir -p "$MAVENIZER_JDK_CACHE" "$MAVENIZER_NESTED_JDK_CACHE"

    # Remove only launcher files created by older project revisions in normal
    # Mavenizer artifact-cache slots. Do not remove downloaded artifacts.
    for _slot in maven mcp minecraft_tasks forge; do
        _slot_path="$MAVENIZER_JDK_CACHE/$_slot"
        if [ -d "$_slot_path/bin" ]; then
            rm -f "$_slot_path/bin/java" "$_slot_path/bin/javac"
            rmdir "$_slot_path/bin" 2>/dev/null || true
        fi
        if [ -L "$_slot_path/release" ]; then
            rm -f "$_slot_path/release"
        fi
    done

    seed_mavenizer_jdk_candidate() {
        _name="$1"
        _home="$2"
        _candidate="$MAVENIZER_JDK_CACHE/$_name"

        if [ -e "$_candidate" ] || [ -L "$_candidate" ]; then
            rm -rf "$_candidate"
        fi
        ln -s "$_home" "$_candidate"
    }

    seed_mavenizer_jdk_candidate jdk8-musl "$JAVA8_HOME"
    seed_mavenizer_jdk_candidate jdk21-alpine "$JAVA21_HOME"
    seed_mavenizer_jdk_candidate jdk25-musl "$JAVA25_HOME"

    # Older ForgeGradle/Mavenizer combinations documented this cache as a
    # nested `jdks` directory. Seed that layout too. The active 0.5.20 run
    # currently reports the parent directory as JDK Cache, so keeping both
    # layouts makes the project robust across ForgeGradle's argument wiring.
    for _pair in \
        "jdk8-musl|$JAVA8_HOME" \
        "jdk21-alpine|$JAVA21_HOME" \
        "jdk25-musl|$JAVA25_HOME"; do
        _name=${_pair%%|*}
        _home=${_pair#*|}
        _candidate="$MAVENIZER_NESTED_JDK_CACHE/$_name"
        if [ -e "$_candidate" ] || [ -L "$_candidate" ]; then
            rm -rf "$_candidate"
        fi
        ln -s "$_home" "$_candidate"
    done

    # Gradle itself only needs Java 21 for Minecraft/SlimeLauncher and Java 25
    # for Mavenizer. Java 8 stays completely outside Gradle toolchain discovery
    # because Gradle's Java 8 probing is unreliable on mixed musl/Alpine JDKs.
    TOOLCHAIN_PATHS="$JAVA21_HOME,$JAVA25_HOME"
    TOOLCHAIN_FROM_ENV="JAVA21_HOME,JAVA25_HOME"
    TOOLCHAIN_PROPERTIES="$GRADLE_USER_HOME/gradle.properties"
    cat > "$TOOLCHAIN_PROPERTIES" <<EOF
org.gradle.java.home=$JAVA21_HOME
org.gradle.java.installations.auto-detect=false
org.gradle.java.installations.auto-download=false
org.gradle.java.installations.fromEnv=$TOOLCHAIN_FROM_ENV
org.gradle.java.installations.paths=$TOOLCHAIN_PATHS
EOF

    TOOLCHAIN_SYS_PROPS="-Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.auto-download=false -Dorg.gradle.java.installations.fromEnv=$TOOLCHAIN_FROM_ENV -Dorg.gradle.java.installations.paths=$TOOLCHAIN_PATHS"
    if [ -n "${JAVA_TOOL_OPTIONS:-}" ]; then
        JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS $TOOLCHAIN_SYS_PROPS"
    else
        JAVA_TOOL_OPTIONS="$TOOLCHAIN_SYS_PROPS"
    fi
    export JAVA_TOOL_OPTIONS

    # One concise diagnostic line makes the effective split obvious in logs.
    printf 'Vanilla Instincts 1.0.0: Forge 1.21.11 -> FG 7.0.34 / Mavenizer 0.5.20; FG7 UserDev mixins enabled\n' >&2

    # Keep the Gradle daemon itself on Java 21. Gradle sees only Java 21 and
    # Java 25 toolchains; Mavenizer's Java 8 probe remains outside Gradle.
    TOOLCHAIN_OPTS="-Dorg.gradle.java.home=$JAVA21_HOME $TOOLCHAIN_SYS_PROPS"
    if [ -n "${GRADLE_OPTS:-}" ]; then
        GRADLE_OPTS="$GRADLE_OPTS $TOOLCHAIN_OPTS"
    else
        GRADLE_OPTS="$TOOLCHAIN_OPTS"
    fi
    export GRADLE_OPTS
    GRADLE_JAVA_ARGS="$TOOLCHAIN_OPTS"
elif [ "$MODERN_26X" != true ]; then
    JAVA_CMD="${JAVA_HOME:+$JAVA_HOME/bin/}java"
    GRADLE_JAVA_ARGS="${GRADLE_JAVA_ARGS:-}"
fi

if ! command -v "$JAVA_CMD" >/dev/null 2>&1; then
    printf 'ERROR: A compatible Java JDK is required to run Gradle.\n' >&2
    exit 1
fi

# ForgeGradle 7 normally splits a SourceSet into classes and resources output
# directories. FML 61 validates each development mod root independently, so a
# metadata-only resources root is rejected. ForgeGradle provides an official
# switch that unifies both outputs; enable it only for Forge 1.21.11.
if [ "$FORGE_12111" = true ]; then
    set -- "$@" "-Pnet.minecraftforge.gradle.merge-source-sets=true"
fi

cd "$APP_HOME"
# shellcheck disable=SC2086
exec "$JAVA_CMD" $GRADLE_JAVA_ARGS -classpath "$WRAPPER_JAR" "$WRAPPER_MAIN" "$@"
