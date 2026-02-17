#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────
# rename-project.sh
# Renames the entire Spring/Kotlin template project:
#   - package names, imports, directory structure
#   - Gradle config (group, description, rootProject.name)
#   - application.yaml, docker-compose.yml, .env.example
#   - class names, file names, README
# ─────────────────────────────────────────────────────────────

# ── Current values (hardcoded — this script is single-use) ───
OLD_KEBAB="spring-kotlin-template"
OLD_GROUP="com.starter"
OLD_PACKAGE_SEGMENT="springkotlintemplate"
OLD_FULL_PACKAGE="com.starter.springkotlintemplate"
OLD_PACKAGE_PATH="com/starter/springkotlintemplate"
OLD_CLASS_PREFIX="SpringKotlinTemplate"

# ── User input ───────────────────────────────────────────────
echo "🔧 Spring/Kotlin Template — Project Renamer"
echo ""

read -rp "New project name (kebab-case, e.g. my-awesome-app): " NEW_KEBAB
if [[ -z "$NEW_KEBAB" ]]; then
    echo "❌ Project name cannot be empty." && exit 1
fi

read -rp "Group (e.g. com.mycompany): " NEW_GROUP
if [[ -z "$NEW_GROUP" ]]; then
    echo "❌ Group cannot be empty." && exit 1
fi

# ── Derive all name variants ─────────────────────────────────

# kebab-case → no separators (for package segment): my-awesome-app → myawesomeapp
NEW_PACKAGE_SEGMENT=$(echo "$NEW_KEBAB" | tr -d '-')

# kebab-case → PascalCase: my-awesome-app → MyAwesomeApp
NEW_CLASS_PREFIX=$(echo "$NEW_KEBAB" | sed 's/-/ /g' | awk '{for(i=1;i<=NF;i++) $i=toupper(substr($i,1,1)) substr($i,2)}1' | tr -d ' ')

# Full package
NEW_FULL_PACKAGE="${NEW_GROUP}.${NEW_PACKAGE_SEGMENT}"

# Group path (dots → slashes): com.mycompany → com/mycompany
NEW_GROUP_PATH=$(echo "$NEW_GROUP" | tr '.' '/')
OLD_GROUP_PATH=$(echo "$OLD_GROUP" | tr '.' '/')

# Package path: com/mycompany/myawesomeapp
NEW_PACKAGE_PATH="${NEW_GROUP_PATH}/${NEW_PACKAGE_SEGMENT}"

# ── Summary ──────────────────────────────────────────────────
echo ""
echo "┌──────────────────────────────────────────────────"
echo "│  Project name:   $OLD_KEBAB → $NEW_KEBAB"
echo "│  Group:          $OLD_GROUP → $NEW_GROUP"
echo "│  Package:        $OLD_FULL_PACKAGE → $NEW_FULL_PACKAGE"
echo "│  Class prefix:   ${OLD_CLASS_PREFIX}Application → ${NEW_CLASS_PREFIX}Application"
echo "│  Package path:   $OLD_PACKAGE_PATH → $NEW_PACKAGE_PATH"
echo "└──────────────────────────────────────────────────"
echo ""
read -rp "Proceed? (y/N): " CONFIRM
if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
    echo "Aborted." && exit 0
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

echo ""
echo "📝 Replacing file contents..."

# ── 1. Replace content in all text files ─────────────────────
# Order matters: replace the most specific strings first to avoid partial matches.

find . -type f \
    -not -path './.git/*' \
    -not -path './.gradle/*' \
    -not -path './build/*' \
    -not -path './pgdata/*' \
    -not -path './.idea/*' \
    -not -path './scripts/rename-project.sh' \
    -not -name '*.jar' \
    -not -name '*.class' \
    -not -name 'gradlew' \
    -not -name 'gradlew.bat' \
    -print0 | while IFS= read -r -d '' file; do

    if file --mime-type "$file" | grep -q 'text/'; then
        # Full package (com.starter.springkotlintemplate → com.newgroup.newpackage)
        sed -i '' "s|${OLD_FULL_PACKAGE}|${NEW_FULL_PACKAGE}|g" "$file"

        # Package path in directory references (com/starter/springkotlintemplate)
        sed -i '' "s|${OLD_PACKAGE_PATH}|${NEW_PACKAGE_PATH}|g" "$file"

        # Class prefix (SpringKotlinTemplate → NewPrefix)
        sed -i '' "s|${OLD_CLASS_PREFIX}|${NEW_CLASS_PREFIX}|g" "$file"

        # Group in build.gradle.kts
        sed -i '' "s|group = \"${OLD_GROUP}\"|group = \"${NEW_GROUP}\"|g" "$file"

        # Kebab-case project name (for docker, database, URLs, settings, etc.)
        sed -i '' "s|${OLD_KEBAB}|${NEW_KEBAB}|g" "$file"
    fi
done

echo "📁 Renaming source directories..."

# ── 2. Rename the package directory structure ────────────────
for src_root in src/main/kotlin src/test/kotlin; do
    OLD_DIR="${src_root}/${OLD_PACKAGE_PATH}"
    NEW_DIR="${src_root}/${NEW_PACKAGE_PATH}"

    if [[ -d "$OLD_DIR" ]]; then
        mkdir -p "$NEW_DIR"
        # Move all contents
        if ls -A "$OLD_DIR" 1>/dev/null 2>&1; then
            mv "$OLD_DIR"/* "$NEW_DIR"/ 2>/dev/null || true
            mv "$OLD_DIR"/.* "$NEW_DIR"/ 2>/dev/null || true
        fi

        # Clean up old empty directories
        # Walk up from old package dir and remove empty parents
        dir_to_clean="$OLD_DIR"
        while [[ "$dir_to_clean" != "$src_root" ]]; do
            if [[ -d "$dir_to_clean" ]] && [ -z "$(ls -A "$dir_to_clean")" ]; then
                rmdir "$dir_to_clean"
            else
                break
            fi
            dir_to_clean=$(dirname "$dir_to_clean")
        done
    fi
done

echo "📄 Renaming files..."

# ── 3. Rename files that contain the old class prefix ────────
find . -type f -name "*${OLD_CLASS_PREFIX}*" \
    -not -path './.git/*' \
    -not -path './build/*' \
    -not -path './.gradle/*' | while IFS= read -r file; do
    dir=$(dirname "$file")
    old_name=$(basename "$file")
    new_name="${old_name//${OLD_CLASS_PREFIX}/${NEW_CLASS_PREFIX}}"
    if [[ "$old_name" != "$new_name" ]]; then
        mv "$file" "${dir}/${new_name}"
        echo "   $old_name → $new_name"
    fi
done

# ── 4. Rename .iml file if it exists ─────────────────────────
if [[ -f "${OLD_KEBAB}.iml" ]]; then
    mv "${OLD_KEBAB}.iml" "${NEW_KEBAB}.iml"
    echo "   ${OLD_KEBAB}.iml → ${NEW_KEBAB}.iml"
fi

echo ""
echo "✅ Done! Project renamed to: $NEW_KEBAB ($NEW_FULL_PACKAGE)"
echo ""
echo "Next steps:"
echo "  1. Verify with: ./gradlew compileKotlin"
echo "  2. Reload the project in IntelliJ (File → Invalidate Caches / Restart)"
