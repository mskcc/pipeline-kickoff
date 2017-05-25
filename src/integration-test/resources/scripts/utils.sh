#!/bin/bash

#
#set Colors
#

bold=$(tput bold)
underline=$(tput sgr 0 1)
reset=$(tput sgr0)

purple=$(tput setaf 171)
red=$(tput setaf 1)
green=$(tput setaf 76)
tan=$(tput setaf 3)
blue=$(tput setaf 38)

#
# Headers and  Logging
#

header1() { 
	printf "\n${bold}${purple}==================  %s  ==================${reset}\n" "$@" 
}

header2() {
	printf "\n${blue}=============  %s  =============${reset}\n" "$@" 
}

header3() {
	printf "\n${tan}==========  %s  ==========${reset}\n" "$@" 
}

headerError() {
	printf "\n${red}================ %s ==============${reset}\n" "$@"
}

headerSuccess() {
	printf "\n${green}============== %s ==============${reset}\n" "$@"
}


arrow() { 
	printf "➜ $@\n"
}

success() { 
printf "${green}✔ %s${reset}\n" "$@"
}	

error() { 
	printf "${red}✖ %s${reset}\n" "$@"
}

warning() { 
	printf "${tan}➜ %s${reset}\n" "$@"
}

underline() { 
	printf "${underline}${bold}%s${reset}\n" "$@"
}

bold() { 
	printf "${bold}%s${reset}\n" "$@"
}

note() { 
	printf "${underline}${bold}${blue}Note:${reset}  ${blue}%s${reset}\n" "$@"
}
