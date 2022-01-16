echo Run Linters: >lint.txt

# Do not show GPG popups for local tests
export LEIN_GPG=
echo ============================
echo Linting with 'lein bikeshed'
lein bikeshed 2>&1
echo ============================
echo Linting with 'lein eastwood'
lein eastwood 2>&1
echo ============================
echo Linting with 'lein kibit'
lein kibit 2>&1
echo ============================
# yagni not that useful currently.
# echo Linting with yagni
# lein yagni 2>&1
# echo ============================
echo Linting with 'clj-kondo --lint src --lint test'
clj-kondo --lint src --lint test 2>&1
echo ============================
echo Linting with 'lein cljfmt check'
lein cljfmt check 2>&1
echo ============================
echo Linting with 'lein check-namespace-decls'
lein check-namespace-decls 2>&1
echo ============================
echo Linting with 'lein ancient'
lein ancient 2>&1
echo ============================
echo Checking 'lein deps :tree'
lein deps :tree 2>&1
echo ============================
