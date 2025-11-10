#!/bin/bash

set -e

# Color codes for pretty printing
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Global variables
DRY_RUN=false
MANUAL_VERSION=""
REPO_OWNER=""
REPO_NAME=""

# Function to print colored output
print_step() {
    echo -e "${CYAN}==>${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

# Function to ask for user confirmation
confirm() {
    local prompt="$1"
    local response
    echo -e "${YELLOW}${prompt} (y/n):${NC} "
    read -r response
    case "$response" in
        [yY][eE][sS]|[yY]) 
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

# Function to show usage
usage() {
    cat << EOF
Usage: $(basename "$0") [OPTIONS]

Create a new GitHub release and tag for the Sqkon project.

OPTIONS:
    -v, --version VERSION    Manually specify the version (e.g., 1.2.3)
    -d, --dry-run           Show what would be done without making changes
    -h, --help              Show this help message

EXAMPLES:
    $(basename "$0")                      # Auto-increment minor version
    $(basename "$0") -v 2.0.0            # Create release v2.0.0
    $(basename "$0") --dry-run           # Preview changes without executing

EOF
    exit 0
}

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -d|--dry-run)
                DRY_RUN=true
                print_warning "DRY RUN MODE - No changes will be made"
                shift
                ;;
            -v|--version)
                MANUAL_VERSION="$2"
                shift 2
                ;;
            -h|--help)
                usage
                ;;
            *)
                print_error "Unknown option: $1"
                usage
                ;;
        esac
    done
}

# Check if required commands are available
check_requirements() {
    print_step "Checking requirements..."
    
    local missing_commands=()
    
    if ! command -v git &> /dev/null; then
        missing_commands+=("git")
    fi
    
    if ! command -v gh &> /dev/null; then
        missing_commands+=("gh")
    fi
    
    if [ ${#missing_commands[@]} -gt 0 ]; then
        print_error "Missing required commands: ${missing_commands[*]}"
        echo ""
        echo "Please install missing dependencies:"
        for cmd in "${missing_commands[@]}"; do
            case $cmd in
                gh)
                    echo "  GitHub CLI: https://cli.github.com/"
                    ;;
                git)
                    echo "  Git: https://git-scm.com/"
                    ;;
            esac
        done
        exit 1
    fi
    
    # Check if gh is authenticated
    if ! gh auth status &> /dev/null; then
        print_error "GitHub CLI is not authenticated"
        echo "Please run: gh auth login"
        exit 1
    fi
    
    print_success "All requirements satisfied"
}

# Get repository information
get_repo_info() {
    print_step "Getting repository information..."
    
    local remote_url
    remote_url=$(git remote get-url origin 2>/dev/null || echo "")
    
    if [ -z "$remote_url" ]; then
        print_error "No git remote 'origin' found"
        exit 1
    fi
    
    # Parse owner and repo from URL (supports both SSH and HTTPS)
    if [[ $remote_url =~ github\.com[:/]([^/]+)/([^/.]+) ]]; then
        REPO_OWNER="${BASH_REMATCH[1]}"
        REPO_NAME="${BASH_REMATCH[2]}"
    else
        print_error "Could not parse repository information from remote URL"
        exit 1
    fi
    
    print_success "Repository: $REPO_OWNER/$REPO_NAME"
}

# Check current branch
check_branch() {
    print_step "Checking current branch..."
    
    local current_branch
    current_branch=$(git rev-parse --abbrev-ref HEAD)
    
    print_info "Current branch: $current_branch"
    
    if [ "$current_branch" = "main" ] || [ "$current_branch" = "master" ]; then
        print_success "On main branch"
    else
        print_warning "Not on main branch (currently on: $current_branch)"
        print_info "This script must be run from the main/master branch"
        
        # Check if main or master branch exists
        local target_branch=""
        if git show-ref --verify --quiet refs/heads/main; then
            target_branch="main"
        elif git show-ref --verify --quiet refs/heads/master; then
            target_branch="master"
        else
            print_error "Neither 'main' nor 'master' branch found in repository"
            exit 1
        fi
        
        if $DRY_RUN; then
            print_warning "DRY RUN: Would ask to switch to $target_branch branch"
        else
            if confirm "Would you like to switch to the $target_branch branch now?"; then
                print_step "Switching to $target_branch branch..."
                if git checkout "$target_branch"; then
                    print_success "Switched to $target_branch branch"
                else
                    print_error "Failed to switch to $target_branch branch"
                    exit 1
                fi
            else
                print_error "Cannot continue on feature branch. Aborted by user."
                exit 1
            fi
        fi
    fi
    
    # Check if there are uncommitted changes
    if ! git diff-index --quiet HEAD -- 2>/dev/null; then
        print_warning "You have uncommitted changes"
        if ! $DRY_RUN; then
            if ! confirm "Continue anyway?"; then
                print_error "Aborted by user"
                exit 1
            fi
        fi
    fi
}

# Get the latest release version
get_latest_version() {
    print_step "Fetching latest release..." >&2
    
    local latest_release
    latest_release=$(gh release list --repo "$REPO_OWNER/$REPO_NAME" --limit 1 --json tagName --jq '.[0].tagName' 2>/dev/null || echo "")
    
    if [ -z "$latest_release" ]; then
        print_warning "No previous releases found" >&2
        echo "0.0.0"
    else
        print_success "Latest release: $latest_release" >&2
        echo "$latest_release"
    fi
}

# Parse version string (removes 'v' prefix if present)
parse_version() {
    local version="$1"
    # Remove 'v' prefix if present
    version="${version#v}"
    echo "$version"
}

# Increment minor version
increment_minor_version() {
    local version="$1"
    
    # Parse version components
    local major minor patch
    if [[ $version =~ ^([0-9]+)\.([0-9]+)\.([0-9]+) ]]; then
        major="${BASH_REMATCH[1]}"
        minor="${BASH_REMATCH[2]}"
        patch="${BASH_REMATCH[3]}"
        
        # Increment minor version and reset patch
        minor=$((minor + 1))
        patch=0
        
        echo "${major}.${minor}.${patch}"
    else
        print_error "Invalid version format: $version"
        exit 1
    fi
}

# Validate version format
validate_version() {
    local version="$1"
    if [[ ! $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        print_error "Invalid version format: $version (expected: X.Y.Z)"
        exit 1
    fi
}

# Determine the new version
determine_version() {
    print_step "Determining new version..." >&2
    
    local latest_release new_version
    latest_release=$(get_latest_version)
    latest_release=$(parse_version "$latest_release")
    
    if [ -n "$MANUAL_VERSION" ]; then
        new_version=$(parse_version "$MANUAL_VERSION")
        validate_version "$new_version"
        print_info "Using manually specified version: $new_version" >&2
    else
        new_version=$(increment_minor_version "$latest_release")
        print_info "Auto-incrementing minor version: $latest_release -> $new_version" >&2
    fi
    
    echo "$new_version"
}

# Generate release notes
generate_release_notes() {
    local previous_tag="$1"
    local new_version="$2"
    
    print_step "Generating release notes..." >&2
    
    local notes_file
    notes_file=$(mktemp)
    
    {
        echo "## What's Changed"
        echo ""
        
        if [ "$previous_tag" = "v0.0.0" ]; then
            echo "Initial release"
        else
            # Get commit messages between tags
            git log "${previous_tag}..HEAD" --pretty=format:"* %s (%h)" --no-merges 2>/dev/null || echo "* No changes found"
        fi
        
        echo ""
        echo "---"
        echo ""
        echo "**Full Changelog**: https://github.com/$REPO_OWNER/$REPO_NAME/compare/${previous_tag}...${new_version}"
    } > "$notes_file"
    
    echo "$notes_file"
}

# Create the release
create_release() {
    local new_version="$1"
    local previous_tag="$2"
    
    print_step "Preparing to create release $new_version..."
    
    # Generate release notes
    local notes_file
    notes_file=$(generate_release_notes "$previous_tag" "$new_version")
    
    # Show release notes preview
    echo ""
    print_info "Release notes preview:"
    echo "---"
    cat "$notes_file"
    echo "---"
    echo ""
    
    if $DRY_RUN; then
        print_warning "DRY RUN: Would create release $new_version with the above notes"
        print_warning "DRY RUN: Would create tag $new_version"
        rm -f "$notes_file"
        return 0
    fi
    
    # Final confirmation
    print_warning "DANGEROUS OPERATION: This will create a new release and tag"
    print_info "Release: $new_version"
    print_info "Repository: $REPO_OWNER/$REPO_NAME"
    
    if ! confirm "Are you sure you want to create this release?"; then
        print_error "Release creation aborted by user"
        rm -f "$notes_file"
        exit 1
    fi
    
    print_step "Creating release $new_version..."
    
    # Create the release using gh CLI
    if gh release create "$new_version" \
        --repo "$REPO_OWNER/$REPO_NAME" \
        --title "$new_version" \
        --notes-file "$notes_file"; then
        print_success "Release $new_version created successfully!"
        print_info "Release URL: https://github.com/$REPO_OWNER/$REPO_NAME/releases/tag/$new_version"
    else
        print_error "Failed to create release"
        rm -f "$notes_file"
        exit 1
    fi
    
    rm -f "$notes_file"
}

# Update README.MD and gradle.properties with new version
update_readme_version() {
    local new_version="$1"
    
    print_step "Updating README.MD and gradle.properties with version $new_version..."
    
    local readme_path="README.MD"
    local gradle_props_path="gradle.properties"
    
    if [ ! -f "$readme_path" ]; then
        print_error "README.MD not found at $readme_path"
        return 1
    fi
    
    if [ ! -f "$gradle_props_path" ]; then
        print_error "gradle.properties not found at $gradle_props_path"
        return 1
    fi
    
    if $DRY_RUN; then
        print_warning "DRY RUN: Would update README.MD with version $new_version"
        print_warning "DRY RUN: Would update gradle.properties VERSION_NAME to $new_version"
        print_warning "DRY RUN: Would commit changes with message 'Update README and gradle.properties with version $new_version [skip ci]'"
        print_warning "DRY RUN: Would push commit to main branch"
        return 0
    fi
    
    # Backup the files first
    cp "$readme_path" "${readme_path}.bak"
    cp "$gradle_props_path" "${gradle_props_path}.bak"
    
    # Update version in multiplatform dependency example (line 101)
    # Pattern: implementation("com.mercury.sqkon:library:X.Y.Z")
    if sed -i.tmp "s|implementation(\"com.mercury.sqkon:library:[^\"]*\")|implementation(\"com.mercury.sqkon:library:$new_version\")|g" "$readme_path"; then
        print_success "Updated multiplatform dependency version"
    else
        print_error "Failed to update multiplatform dependency version"
        mv "${readme_path}.bak" "$readme_path"
        mv "${gradle_props_path}.bak" "$gradle_props_path"
        return 1
    fi
    
    # Update version in platform-specific dependency example (line 110)
    # Pattern: implementation("com.mercury.sqkon:library-android:X.Y.Z")
    if sed -i.tmp "s|implementation(\"com.mercury.sqkon:library-android:[^\"]*\")|implementation(\"com.mercury.sqkon:library-android:$new_version\")|g" "$readme_path"; then
        print_success "Updated platform-specific dependency version"
    else
        print_error "Failed to update platform-specific dependency version"
        mv "${readme_path}.bak" "$readme_path"
        mv "${gradle_props_path}.bak" "$gradle_props_path"
        return 1
    fi
    
    # Clean up sed temporary file
    rm -f "${readme_path}.tmp"
    
    # Update VERSION_NAME in gradle.properties
    # Pattern: VERSION_NAME=X.Y.Z
    if sed -i.tmp "s|^VERSION_NAME=.*|VERSION_NAME=$new_version|g" "$gradle_props_path"; then
        print_success "Updated VERSION_NAME in gradle.properties"
    else
        print_error "Failed to update VERSION_NAME in gradle.properties"
        mv "${readme_path}.bak" "$readme_path"
        mv "${gradle_props_path}.bak" "$gradle_props_path"
        return 1
    fi
    
    # Clean up sed temporary file
    rm -f "${gradle_props_path}.tmp"
    
    # Check if there are actual changes
    if git diff --quiet "$readme_path" && git diff --quiet "$gradle_props_path"; then
        print_warning "No changes detected in README.MD or gradle.properties (versions might already be up to date)"
        rm -f "${readme_path}.bak"
        rm -f "${gradle_props_path}.bak"
        return 0
    fi
    
    print_success "README.MD and gradle.properties updated successfully"
    
    # Show the diff
    print_info "Changes made to README.MD:"
    git diff "$readme_path"
    echo ""
    print_info "Changes made to gradle.properties:"
    git diff "$gradle_props_path"
    
    # Commit the changes
    print_step "Committing README.MD and gradle.properties changes..."
    if git add "$readme_path" "$gradle_props_path" && git commit -m "Update README and gradle.properties with version $new_version [skip ci]"; then
        print_success "Changes committed"
    else
        print_error "Failed to commit changes"
        mv "${readme_path}.bak" "$readme_path"
        mv "${gradle_props_path}.bak" "$gradle_props_path"
        return 1
    fi
    
    # Push to main
    print_step "Pushing changes to main branch..."
    if git push origin main; then
        print_success "Changes pushed to main branch"
    else
        print_error "Failed to push changes to main branch"
        print_warning "You may need to manually push the commit"
        return 1
    fi
    
    # Clean up backups
    rm -f "${readme_path}.bak"
    rm -f "${gradle_props_path}.bak"
    
    return 0
}

# Main function
main() {
    echo ""
    print_info "Sqkon Release Creation Script"
    echo ""
    
    parse_args "$@"
    check_requirements
    get_repo_info
    
    # Fetch and display version information early
    local latest_release new_version
    latest_release=$(get_latest_version)
    new_version=$(determine_version)
    
    echo ""
    print_info "Release Information:"
    print_info "  Repository:       $REPO_OWNER/$REPO_NAME"
    print_info "  Current release:  $latest_release"
    print_info "  New release:      $new_version"
    if $DRY_RUN; then
        print_warning "  Mode:             DRY RUN"
    fi
    echo ""
    
    # Now check branch (and potentially switch to main)
    check_branch
    
    echo ""
    print_info "Proceeding with release creation..."
    echo ""
    
    create_release "$new_version" "$latest_release"
    
    # Update README and gradle.properties with new version and push to main
    echo ""
    update_readme_version "$new_version"
    
    echo ""
    print_success "Done!"
    echo ""
    
    if ! $DRY_RUN; then
        print_info "The GitHub Actions workflow should now trigger to publish to Maven Central"
        print_info "Monitor the workflow at: https://github.com/$REPO_OWNER/$REPO_NAME/actions"
        print_info "README.MD and gradle.properties have been updated and pushed to main with [skip ci]"
    fi
}

# Run main function with all arguments
main "$@"
