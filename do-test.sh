# Do not show GPG popups for local tests
export LEIN_GPG=

# rm target/logfile*
lein midje 2> stderr.txt
echo Contents of stderr:
cat stderr.txt

