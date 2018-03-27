#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

# Taken from https://dev.to/thiht/shell-scripts-matter 

#/ Usage:
#/ Description:
#/ Examples:
#/ Options:
#/   --help: Display this help message
usage() { grep '^#/' "$0" | cut -c4- ; exit 0 ; }
expr "$*" : ".*--help" > /dev/null && usage

# Locate this script.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Import helper functions.
source bash_helpers/utils.sh

cleanup() {
    # Remove temporary files
    # Restart services
    # ...
    info "Cleanup done."
}

environment() {
	# Test for required environment variables.
	# check_null_or_unset ${MY_VAR} "MY_VAR"
	# ...
	info "Environment check passed."
}


if [[ "${BASH_SOURCE[0]}" = "$0" ]]; then
    trap cleanup EXIT
    environment

    # Script goes here
    # ...
    info "Script work done."
fi