---
layout: default
title: SAPL Server
#permalink: /reference/SAPL-Server/
parent: Testing SAPL policies
grand_parent: SAPL Reference
nav_order: 4
---

## SAPL-Server

The following repository [GitOps Demo](https://github.com/heutelbeck/sapl-server-lt-gitops-example) showcases a deployment pipeline with SAPL policy tests in a [GitOps](https://www.weave.works/blog/gitops-operations-by-pull-request)\-Style for the headless SAPL-Server-LT. Here every change to the policies is introduced via a pull request on the main branch. The CI pipeline executes the policy tests for every pull request and breaks the pipeline run if policy tests are failing. Merging a pull request on the main branch triggers automatic synchronization of the policies to a SAPL-Server-LT instance.

SAPL tests use Java. Therefore, it is impossible to use the SAPL test framework when deploying SAPL-Server-Implementations with GUI-based PAP (i.e., SAPL-Server-CE or SAPL-Server-EE).