# Changelog

## 0.3.0 — Location favorites

- Send named patch-location states and ready times alongside existing category summaries.
- Support multiple favorite locations per type in Discord, with separate ready pings and grouped alerts for other locations.
- Include individual birdhouse locations and distinguish different tree types at the same location.
- Add Customize alerts guidance to the setup window; keep the existing cogwheel layout.
- Preserve the RuneLite 1.13.1 compatibility fixes from 0.2.2.

## 0.2.2 — RuneLite 1.13.1 compatibility

- Fix plugin loading after RuneLite changed how plugins expose services.
- Read Time Tracking configuration directly instead of declaring a dependency on its unexported plugin services.
- Reload saved birdhouse records before publishing a timer snapshot.

Keep RuneLite's Time Tracking plugin enabled to record new patch and birdhouse observations.

## 0.2.1

- Added a gold "New here?" message above the setup directions checkbox to help new users find the setup guide.
- Added a matching downward arrow pointing toward the checkbox.
- The prompt is display-only; setup, account linking, and timer behavior are unchanged.
