# Play release checklist

## Context
- Plans 21–38 prepare the app; this plan is the release run itself
- New personal developer accounts must run a closed test with at least 12 testers opted in for 14 continuous days before applying for production access

## Goals
1. Play Console setup: create app, package `com.lakasir.acp`, Play App Signing, store listing (plan 24), privacy policy + Data safety (plan 26), content rating questionnaire, target audience (18+ / not for children), ads: none, app access notes (demo mode, plan 35), FGS declaration (plan 22)
2. Internal testing track: upload the first AAB from CI (plan 33), check the pre-launch report (crashes, accessibility, security warnings)
3. Closed testing: recruit 12+ testers, run 14 days, collect feedback in `docs/play/closed-test-feedback.md`
4. Apply for production; staged rollout 20% → 50% → 100% while watching Android vitals (crash rate < 1.09%, ANR < 0.47%)
5. Record every submission and any policy feedback in this plan's Implementation section

## Notes
- Blockers that must be done before the first upload: 21, 22, 23, 24, 25, 26
- Strongly recommended before closed testing: 27, 28, 31, 35, 36, 37, 38
- Can follow during closed testing: 17, 29, 30, 32, 33, 34
- Check the 12-testers/14-days rule and the target API level in the Play Console at release time; both change over time

## Testing
- Pre-launch report clean; closed test has no crash-free-rate regressions; reviewer can use demo mode

## Tools / Skills
- Play Console (manual)

## Implementation
<!-- Write you've done in here -->
