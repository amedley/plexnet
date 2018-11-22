Credentials in the form of: .pem and .pub files are ignored on the master branch.
Be careful to ensure that the local .gitignore ignores ".pem" and ".pub" files before putting any credentials here.
This scripts in this devops folder DO NOT expect to have neighboring folders.

All files without an extension in this directory are bash scripts.
These are mostly helper scripts for testing PlexNet, and they are useful for provisioning, deploying, and running servers.

To use the devops scripts, add $PN to your system/global environment variables. $PN should point to the root of the repository directory.
PlexNet devops expect directories to end in a trailing slash.
Example: C:/workspace/plexnet/

We also recommend adding "PNSCRIPTS" to your system/global environment variables.
Example: PNSCRIPTS = ${PN}devops/

Combining those two examples, you would get:
$PNSCRIPTS = C:/workspace/plexnet/devops/
