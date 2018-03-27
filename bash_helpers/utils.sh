# Logging helpers, taken from https://dev.to/thiht/shell-scripts-matter
readonly LOG_FILE="/tmp/$(basename "$0").log"
info()    { echo "[INFO]    $*" | tee -a "$LOG_FILE" >&2 ; }
warning() { echo "[WARNING] $*" | tee -a "$LOG_FILE" >&2 ; }
error()   { echo "[ERROR]   $*" | tee -a "$LOG_FILE" >&2 ; }
fatal()   { echo "[FATAL]   $*" | tee -a "$LOG_FILE" >&2 ; exit 1 ; }

# Check if the current user is a root user. Abort otherwise.
check_root() {
	if [[ $EUID -ne 0 ]]; then
   		fatal "This script must be run as root." 
	fi
}

# Check if variable is set and non-empty. Abort otherwise.
# Example: check_null_or_unset ${MY_VAR} "MY_VAR"
check_null_or_unset() {
	local readonly var_val=${1}
	local readonly var_name=${2}
	if [[ -z ${var_val:+x} ]] ; then
		fatal "${var_name} is null or unset."
	fi
}

# Check if variable is directory. Abort otherwise.
# Example: check_file ${MY_VAR} "MY_VAR"
check_dir() {
	local readonly path=${1}
	local readonly var_name=${2}
	check_null_or_unset ${path} ${var_name}
	if [[ ! -d ${path} ]] ; then
		fatal "${var_name} is not a directory."
	fi
}

# Check if variable is file. Abort otherwise.
# Example: check_file ${MY_VAR} "MY_VAR"
check_file() {
	local readonly path=${1}
	local readonly var_name=${2}
	check_null_or_unset ${path} ${var_name}
	if [[ ! -f ${path} ]] ; then
		fatal "${var_name} is not a file."
	fi
}

# Set os and ver to distro name and version respectively. 
set_os_and_ver() {
    if [ -f /etc/os-release ]; then
        # freedesktop.org and systemd
        . /etc/os-release
        os=$NAME
        ver=$VERSION_ID
    elif type lsb_release >/dev/null 2>&1; then
        # linuxbase.org
        os=$(lsb_release -si)
        ver=$(lsb_release -sr)
    elif [ -f /etc/lsb-release ]; then
        # For some versions of Debian/Ubuntu without lsb_release command
        . /etc/lsb-release
        os=$DISTRIB_ID
        ver=$DISTRIB_RELEASE
    elif [ -f /etc/debian_version ]; then
        # Older Debian/Ubuntu/etc.
        os=Debian
        ver=$(cat /etc/debian_version)
    elif [ -f /etc/SuSe-release ]; then
        # Older SuSE/etc.
        os=$(uname -s)
        ver=$(uname -r)
    elif [ -f /etc/redhat-release ]; then
        # Older Red Hat, CentOS, etc.
        os=$(uname -s)
        ver=$(uname -r)
    else
        # Fall back to uname, e.g. "Linux <version>", also works for BSD, etc.
        os=$(uname -s)
        ver=$(uname -r)
    fi
}