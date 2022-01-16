rm lint.txt
bash linters-internal.sh | tee -a lint.txt
echo check lint.txt for results
