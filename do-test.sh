rm target/logfile*
lein midje 2> stderr.txt
echo Contents of stderr:
cat stderr.txt

echo .
echo Contents of target/logfile-dyn.log:
cat target/logfile-dyn.log
