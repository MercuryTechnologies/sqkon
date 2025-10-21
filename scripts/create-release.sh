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
        print_error "Not on main branch (currently on: $current_branch)"
        print_error "This script must be run from the main/master branch"
        print_info "Please run: git checkout main"
        exit 1
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
    print_step "Fetching latest release..."
    
    local latest_release
    latest_release=$(gh release list --repo "$REPO_OWNER/$REPO_NAME" --limit 1 --json tagName --jq '.[0].tagName' 2>/dev/null || echo "")
    
    if [ -z "$latest_release" ]; then
        print_warning "No previous releases found"
        echo "v0.0.0"
    else
        print_success "Latest release: $latest_release"
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
    print_step "Determining new version..."
    
    local latest_release new_version
    latest_release=$(get_latest_version)
    latest_release=$(parse_version "$latest_release")
    
    if [ -n "$MANUAL_VERSION" ]; then
        new_version=$(parse_version "$MANUAL_VERSION")
        validate_version "$new_version"
        print_info "Using manually specified version: v$new_version"
    else
        new_version=$(increment_minor_version "$latest_release")
        print_info "Auto-incrementing minor version: v$latest_release -> v$new_version"
    fi
    
    echo "$new_version"
}

# Generate release notes
generate_release_notes() {
    local previous_tag="$1"
    local new_version="$2"
    
    print_step "Generating release notes..."
    
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
        echo "**Full Changelog**: https://github.com/$REPO_OWNER/$REPO_NAME/compare/${previous_tag}...v${new_version}"
    } > "$notes_file"
    
    echo "$notes_file"
}

# Create the release
create_release() {
    local new_version="$1"
    local previous_tag="$2"
    
    print_step "Preparing to create release v$new_version..."
    
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
        print_warning "DRY RUN: Would create release v$new_version with the above notes"
        print_warning "DRY RUN: Would create tag v$new_version"
        rm -f "$notes_file"
        return 0
    fi
    
    # Final confirmation
    print_warning "DANGEROUS OPERATION: This will create a new release and tag"
    print_info "Release: v$new_version"
    print_info "Repository: $REPO_OWNER/$REPO_NAME"
    
    if ! confirm "Are you sure you want to create this release?"; then
        print_error "Release creation aborted by user"
        rm -f "$notes_file"
        exit 1
    fi
    
    print_step "Creating release v$new_version..."
    
    # Create the release using gh CLI
    if gh release create "v$new_version" \
        --repo "$REPO_OWNER/$REPO_NAME" \
        --title "v$new_version" \
        --notes-file "$notes_file"; then
        print_success "Release v$new_version created successfully!"
        print_info "Release URL: https://github.com/$REPO_OWNER/$REPO_NAME/releases/tag/v$new_version"
    else
        print_error "Failed to create release"
        rm -f "$notes_file"
        exit 1
    fi
    
    rm -f "$notes_file"
}

# Main function
main() {
    echo ""
    print_info "Sqkon Release Creation Script"
    echo ""
    
    parse_args "$@"
    check_requirements
    get_repo_info
    check_branch
    
    local latest_release new_version
    latest_release=$(get_latest_version)
    new_version=$(determine_version)
    
    echo ""
    print_info "Summary:"
    print_info "  Previous release: $latest_release"
    print_info "  New release:      v$new_version"
    print_info "  Repository:       $REPO_OWNER/$REPO_NAME"
    if $DRY_RUN; then
        print_warning "  Mode:             DRY RUN"
    fi
    echo ""
    
    create_release "$new_version" "$latest_release"
    
    echo ""
    print_success "Done!"
    echo ""
    
    if ! $DRY_RUN; then
        print_info "The GitHub Actions workflow should now trigger to publish to Maven Central"
        print_info "Monitor the workflow at: https://github.com/$REPO_OWNER/$REPO_NAME/actions"
    fi
}

# Run main function with all arguments
main "$@"
