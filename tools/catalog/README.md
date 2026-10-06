# Patch data maintenance

The plugin reads only public Time Tracking configuration records at runtime.
`patch-catalog.json` contains location names, record keys and varbit decoding
data exported from RuneLite 1.13.1. Its source URL and upstream license are
shipped alongside the data. Unknown record values remain unknown.

The optional offline exporter uses reflection to read RuneLite's internal data
types. It runs only when explicitly requested by a developer, in a separate
Gradle source set. Neither the exporter nor any reflection code is included in
the plugin JAR. Normal builds consume the checked-in JSON without running it.

Regenerate against a pinned release, review the data diff, and run the tests:

```powershell
.\gradlew.bat generatePatchCatalog '-PruneliteVersion=1.13.1'
.\gradlew.bat build '-PruneliteVersion=1.13.1'
```

The decoder tuple is `[crop state, minutes per growth tick, remaining ticks]`.
Arrays are indexed by the stored varbit value. Weeds and scarecrows are mapped
to `EMPTY`. Runtime prediction uses FarmingTracker's public `getTickTime` API
to respect the account's farming tick offset, and applies the leagues speedup.
Allotments and similar patches of the same type at a location share a favorite;
they become ready after every known growing patch there finishes. Different
tree types at the same location have separate IDs and labels.
