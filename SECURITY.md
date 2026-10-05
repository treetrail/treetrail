# Security Policy

## Supported versions

Security fixes are made for the **latest release**. Please check that the issue still exists there
before reporting it.

## Reporting a vulnerability

**Please do not open a public issue, discussion or pull request for security problems.**

Report vulnerabilities privately via GitHub: open the **Security** tab of
[treetrail/treetrail](https://github.com/treetrail/treetrail) and click **Report a vulnerability**
(`https://github.com/treetrail/treetrail/security/advisories/new`).

Please include:

- the affected module and version (or commit),
- a description of the issue and its impact,
- steps or code to reproduce it, for example the JSONPath expression and document,
- a suggested fix, if you have one.

Treetrail evaluates expressions and regular expressions that may come from untrusted sources, so
reports about excessive CPU or memory use on crafted input are in scope.

## What happens next

Your report is only visible to you and the maintainers. We will acknowledge it, investigate, and
keep you updated in the advisory. Once a fix is released, the advisory is published, crediting you
unless you prefer to stay anonymous.

Treetrail is maintained on a best-effort basis, so please allow some time for a response.
