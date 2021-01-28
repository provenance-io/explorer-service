# Preface 

The Provenance repository is built on the work of many open source projects including
the [Cosmos SDK]](https://github.com/cosmos/cosmos-sdk).  The source code outside of the `/x/`
folder is largely based on a reference implementation of this SDK.  The work inside the modules
folder represents the evolution of blockchain applications started at Figure Technologies in 2018.

This project would not be possible without the dedication and support of the [Cosmos](https://cosmos.network) community.

# Contributing

- [Preface](#preface)
- [Contributing](#contributing)
  - [Architecture Decision Records (ADR)](#architecture-decision-records-adr)
  - [Pull Requests](#pull-requests)
    - [Process for reviewing PRs](#process-for-reviewing-prs)
    - [Updating Documentation](#updating-documentation)
  - [Forking](#forking)
  - [Dependencies](#dependencies)
  - [Branching Model and Release](#branching-model-and-release)
    - [PR Targeting](#pr-targeting)
    - [Development Procedure](#development-procedure)
    - [Pull Merge Procedure](#pull-merge-procedure)
    - [Release Procedure](#release-procedure)
    - [Point Release Procedure](#point-release-procedure)

Thank you for considering making contributions to the Provenance!

Contributing to this repo can mean many things such as participated in
discussion or proposing code changes. To ensure a smooth workflow for all
contributors, the general procedure for contributing has been established:

1. Either [open](https://github.com/provenance-io/explorer-service/issues/new/choose) or
   [find](https://github.com/provenance-io/explorer-service/issues) an issue you'd like to help with
2. Participate in thoughtful discussion on that issue
3. If you would like to contribute:
   1. If the issue is a proposal, ensure that the proposal has been accepted
   2. Ensure that nobody else has already begun working on this issue. If they have,
      make sure to contact them to collaborate
   3. If nobody has been assigned for the issue and you would like to work on it,
      make a comment on the issue to inform the community of your intentions
      to begin work
   4. Follow standard Github best practices: fork the repo, branch from the
      HEAD of `main`, make some commits, and submit a PR to `main`
   5. Be sure to submit the PR in `Draft` mode submit your PR early, even if
      it's incomplete as this indicates to the community you're working on
      something and allows them to provide comments early in the development process
   6. When the code is complete it can be marked `Ready for Review`
   7. Be sure to include a relevant change log entry in the `Unreleased` section
      of `CHANGELOG.md` (see file for log format)

Note that for very small or blatantly obvious problems (such as typos) it is
not required to an open issue to submit a PR, but be aware that for more complex
problems/features, if a PR is opened before an adequate design discussion has
taken place in a github issue, that PR runs a high likelihood of being rejected.

Take a peek at our [coding repo](https://github.com/tendermint/coding) for
overall information on repository workflow and standards. Note, we use `make tools` for installing the linting tools.

Other notes:

- Looking for a good place to start contributing? How about checking out some
  [good first issues](https://github.com/provenance-io/explorer-service/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22)

## Architecture Decision Records (ADR)

When proposing an architecture decision for the SDK, please create an [ADR](./docs/architecture/README.md)
so further discussions can be made. We are following this process so all involved parties are in
agreement before any party begins coding the proposed implementation. If you would like to see some examples
of how these are written refer to [Tendermint ADRs](https://github.com/tendermint/tendermint/tree/main/docs/architecture)

## Pull Requests

To accommodate review process we suggest that PRs are categorically broken up.
Ideally each PR addresses only a single issue. Additionally, as much as possible
code refactoring and cleanup should be submitted as a separate PRs from bugfixes/feature-additions.

### Process for reviewing PRs

All PRs require two Reviews before merge (except docs changes, or variable name-changes which only require one). When reviewing PRs please use the following review explanations:

- `LGTM` without an explicit approval means that the changes look good, but you haven't pulled down the code, run tests locally and thoroughly reviewed it.
- `Approval` through the GH UI means that you understand the code, documentation/spec is updated in the right places, you have pulled down and tested the code locally. In addition:
  - You must also think through anything which ought to be included but is not
  - You must think through whether any added code could be partially combined (DRYed) with existing code
  - You must think through any potential security issues or incentive-compatibility flaws introduced by the changes
  - Naming must be consistent with conventions and the rest of the codebase
  - Code must live in a reasonable location, considering dependency structures (e.g. not importing testing modules in production code, or including example code modules in production code).
  - if you approve of the PR, you are responsible for fixing any of the issues mentioned here and more
- If you sat down with the PR submitter and did a pairing review please note that in the `Approval`, or your PR comments.
- If you are only making "surface level" reviews, submit any notes as `Comments` without adding a review.

### Updating Documentation

If you open a PR on Provenance Explorer Service, it is mandatory to update the relevant documentation in /docs.


## Branching Model and Release

User-facing repos should adhere to the trunk based development branching model: https://trunkbaseddevelopment.com/.

Libraries need not follow the model strictly, but would be wise to.

The SDK utilizes [semantic versioning](https://semver.org/).

### PR Targeting

Ensure that you base and target your PR on the `main` branch.

All feature additions should be targeted against `main`. Bug fixes for an outstanding release candidate
should be targeted against the release candidate branch. Release candidate branches themselves should be the
only pull requests targeted directly against main.

### Development Procedure

- the latest state of development is on `main`

### Pull Merge Procedure

- ensure pull branch is rebased on `main`
- merge pull request

### Release Procedure

- Start on `main`
- Create the release candidate branch `rc/v*` (going forward known as **RC**)
  and ensure it's protected against pushing from anyone except the release
  manager/coordinator
  - **no PRs targeting this branch should be merged unless exceptional circumstances arise**
- On the `RC` branch, prepare a new version section in the `CHANGELOG.md`
  - All links must be link-ified: `$ python ./scripts/linkify_changelog.py CHANGELOG.md`
  - Copy the entries into a `RELEASE_CHANGELOG.md`, this is needed so the bot knows which entries to add to the release page on github.
- Kick off a large round of simulation testing (e.g. 400 seeds for 2k blocks)
- If errors are found during the simulation testing, commit the fixes to `main`
  and create a new `RC` branch (making sure to increment the `rcN`)
- After simulation has successfully completed, create the release branch
  (`release/vX.XX.X`) from the `RC` branch
- Create a PR to `main` to incorporate the `CHANGELOG.md` updates
- Tag the release (use `git tag -a`) and create a release in Github
- Delete the `RC` branches

### Point Release Procedure

At the moment, only a single major release will be supported, so all point releases will be based
off of that release.

In order to alleviate the burden for a single person to have to cherry-pick and handle merge conflicts
of all desired backporting PRs to a point release, we instead maintain a living backport branch, where
all desired features and bug fixes are merged into as separate PRs.

Example:

Current release is `v0.1.0`. We then maintain a (living) branch `sru/release/v0.1.N`, given N as
the next patch release number (currently `0.1.1`) for the `0.1` release series. As bugs are fixed
and PRs are merged into `main`, if a contributor wishes the PR to be released as SRU into the
`v0.1.N` point release, the contributor must:

1. Add `0.1.N-backport` label
2. Pull latest changes on the desired `sru/release/vX.X.N` branch
3. Create a 2nd PR merging the respective SRU PR into `sru/release/v0.38.N`
4. Update the PR's description and ensure it contains the following information:
   - **[Impact]** Explanation of how the bug affects users or developers.
   - **[Test Case]** section with detailed instructions on how to reproduce the bug.
   - **[Regression Potential]** section with a discussion how regressions are most likely to manifest, or might
     manifest even if it's unlikely, as a result of the change. **It is assumed that any SRU candidate PR is
     well-tested before it is merged in and has an overall low risk of regression**.

It is the PR's author's responsibility to fix merge conflicts, update changelog entries, and
ensure CI passes. If a PR originates from an external contributor, it may be a core team member's
responsibility to perform this process instead of the original author.
Lastly, it is core team's responsibility to ensure that the PR meets all the SRU criteria.

Finally, when a point release is ready to be made:

1. Create `release/v0.1.N` branch
2. Ensure changelog entries are verified
   1. Be sure changelog entries are added to `RELEASE_CHANGELOG.md`
3. Add release version date to the changelog
4. Push release branch along with the annotated tag: **git tag -a**
5. Create a PR into `main` containing ONLY `CHANGELOG.md` updates
   1. Do not push `RELEASE_CHANGELOG.md` to `main`

Note, although we aim to support only a single release at a time, the process stated above could be
used for multiple previous versions.
