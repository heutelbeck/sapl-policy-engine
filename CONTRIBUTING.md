# Contributing

This project welcomes any contributions and enforces the values of openness, usability, correctness, clean code, and rigorous testing through the defined processes.

You can contribute to this project by contributing to issues, discussions, and proposing code changes.

English is the mandated language for all communication and contributions.

This project has adopted a [Code of Conduct](CODE_OF_CONDUCT.md) and it will be enforced in any communication.

## Create an issue

You can report bugs, create feature requests, propose enhancements, or raise questions on the [issue page][issue-link].

Choose and adhere to the provided templates and be as precise as possible to enable an easy evaluation of the issue.
If you're interested in resolving your issue yourself, take a look at the possibility of creating [code contributions](#propose-a-code-change)](#propose-a-code-change).

**Before creating any issue, please look up already closed or open issues!**

### 🐞 Bug report

A bug report should include details regarding any unforeseen or malfunctioning aspects of SAPL (please verify to the best of your ability that this pertains specifically to a dedicated issue with SAPL). Once the bug is reported, any project contributor can assess, engage in discussions, and contribute additional information to the corresponding issue.

After a maintainer evaluates the bug, he or she will add a tag to categorize the bug and describe his examination result and the next steps that need to be taken.

**Do not post bugs with security implications publicly and follow [this](#report-a-security-vulnerability).**

### 🔧 Enhancement request

An enhancement request is any kind of enhancement. It can be code, documentation, or anything else maintained in the repository. Once the request is posted, any project contributor can evaluate it, participate in discussions, and contribute additional information to the issue.

A decision can be made by consensus, ensuring stability and alignment with the direction of the project and module.

### 🚀 Feature request

A feature request is a request to add a new feature to the project. It should provide a new, unknown capability that is not already provided by this project. Once the request is posted, any project member can evaluate it, participate in discussions, and contribute additional information to the corresponding issue.

The decision to add and support a new feature is made by the BDFL.

### ❓ Question

A question is an issue for requesting support on a specific topic, that the requestor can not answer on its own.
Anyone is welcome to help resolve this issue, and the requestor or a maintainer will close this issue.

## Report a security vulnerability

If you find any security vulnerability, please follow the process described [here](SECURITY.md).

## Propose a code change

With a code change, you can actively improve the product itself. To ensure a high quality please follow the following guidelines and the enforced processes.

Before writing code you might want to create an [issue](#create-an-issue) to get feedback before implementing.

### Guidelines

TODO - We need to provide and formalize more guidelines for styling and coding. No automatic tool will resolve all requirements.

#### Pull requests

All code contributions are expected to be reviewed and merged into the master branch via pull requests.

To create a pull request for your code, you need to use a fork (except you are a maintainer). \
Look up [here][github-fork-guide] how to create a fork and [here][github-fork-pr-guide] to create a pull request.

All commits in your code contribution need to be signed. You can find more information 
on signing commits [here][github-signing-commits]

**Apply the default provided template to your pull request!**

Once all required pipelines execute, tests pass and the changes are approved, the pull request will be merged by a maintainer.

We use SonarCloud with SpotBugs and sb-contrib for static analysis of your code. To 
avoid issues on [SonarCloud][sonarcloud], you can use [SonarLint][sonarlint] and the 
[SpotBugs-Plugin][spotbugs] in your IDE to keep your code clean.

#### Code Style

Maintaining a consistent code style is crucial for our project's readability.

We enforce these standards automatically using [formatter-maven-plugin][eclipse-formatter-plugin] based on [Eclipse Code Formatter][eclipse-formatter-definition] configured in the [formatter.xml file](formatter.xml), which checks and ensures adherence to our guidelines.

##### Rules

Beyond the automatic enforcing of formatting by tools, we have style preferences that are described as the following rules.

1. Use `var` instead of explicitly specifying the type whenever possible.
2. Avoid starting interfaces with the letter `I`, e.g., `IFileProvider`.
3. Avoid using the prefix `My` for variables, methods, and classes.
4. Use the default scope for test classes and methods.
5. End the names of test classes with `Tests`.
6. Follow the pattern `whenSOMETHINGthenSOMETHING-ELSE` for naming test methods.
7. Avoid using `@SuppressWarning`, except with explicit permission from maintainers.
8. If a file happens to differ in style from the guidelines, the existing style in that file takes precedence.
9. Use Lombok annotations.
10. Use the @Slf4j annotation for logging.

#### Code coverage

We strive to achieve a test coverage of at least 95%. Use the integrated tools of your 
IDE or our [SonarCloud integration][sonarcloud] to check missing tests for 
branches/lines of code.

<!-- MARKDOWN LINKS & IMAGES -->
<!-- https://www.markdownguide.org/basic-syntax/#reference-style-links -->
[issue-link]: https://github.com/heutelbeck/sapl-policy-engine/issues

[eclipse-formatter-plugin]: https://code.revelc.net/formatter-maven-plugin/
[eclipse-formatter-definition]: https://help.eclipse.org/latest/index.jsp?topic=%2Forg.eclipse.jdt.doc.user%2Freference%2Fpreferences%2Fjava%2Fcodestyle%2Fref-preferences-formatter.htm
[github-fork-pr-guide]: https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request-from-a-fork
[github-fork-guide]: https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/working-with-forks/fork-a-repo
[github-signing-commits]: https://docs.github.com/en/authentication/managing-commit-signature-verification/signing-commits
[sonarcloud]: https://sonarcloud.io/project/overview?id=heutelbeck_sapl-policy-engine
[sonarlint]: https://www.sonarsource.com/products/sonarlint/
[spotbugs]: https://github.com/spotbugs/spotbugs
