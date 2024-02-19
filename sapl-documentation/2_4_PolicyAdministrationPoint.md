---
layout: default
title: PAP
#permalink: /reference/pap/
parent: Reference Architecture
grand_parent: SAPL Reference
nav_order: 4
---

## Policy Administration Point (PAP)

The PAP is an entity that allows managing policies contained in the policy store. In the embedded PDP with the Resources PRP, the policy store can be a simple folder within the local file system containing `.sapl` files. Therefore, any access to files in this folder (e.g., FTP or SSH) can be seen as a straightforward PAP. The PAP may be a separate application or can be included in an existing administration panel.