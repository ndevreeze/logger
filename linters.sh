echo Run Linters: >lint.txt

# Do not show GPG popups for local tests
export LEIN_GPG=
echo ============================ | tee -a lint.txt
echo Linting with 'lein bikeshed' | tee -a lint.txt
lein bikeshed 2>&1 | tee -a lint.txt
echo ============================ | tee -a lint.txt
echo Linting with 'lein eastwood' | tee -a lint.txt
lein eastwood 2>&1 | tee -a lint.txt
echo ============================ | tee -a lint.txt
echo Linting with 'lein kibit' | tee -a lint.txt
lein kibit 2>&1 | tee -a lint.txt
echo ============================ | tee -a lint.txt
# yagni not that useful currently.
# echo Linting with yagni | tee -a lint.txt
# lein yagni 2>&1 | tee -a lint.txt
# echo ============================ | tee -a lint.txt
echo Linting with 'clj-kondo --lint src --lint test' | tee -a lint.txt
clj-kondo --lint src --lint test 2>&1 | tee -a lint.txt
echo ============================ | tee -a lint.txt
echo Linting with 'lein cljfmt check' | tee -a lint.txt
lein cljfmt check 2>&1 | tee -a lint.txt
echo ============================ | tee -a lint.txt
echo Linting with 'lein check-namespace-decls' | tee -a lint.txt
lein check-namespace-decls 2>&1 | tee -a lint.txt
echo ============================ | tee -a lint.txt
echo Linting with 'lein ancient' | tee -a lint.txt
lein ancient 2>&1 | tee -a lint.txt
echo ============================ | tee -a lint.txt
echo Checking 'lein deps :tree' | tee -a lint.txt
lein deps :tree 2>&1 | tee -a lint.txt
echo ============================ | tee -a lint.txt
echo check lint.txt for results
