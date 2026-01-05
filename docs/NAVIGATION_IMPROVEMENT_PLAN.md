# Navigation Improvement Plan

## Current Problem

The `_config.yml` has **477 lines** of manual `nav_exclude` entries:
- Default: `nav_exclude: true` for all files
- Then manually sets `nav_exclude: false` for each file that should be in navigation

This creates a **maintenance burden** - every new file requires a config entry.

## Solution

Switch to the standard Just the Docs pattern:
1. **Include files by default** (remove global `nav_exclude: true`)
2. **Exclude via front matter** (add `nav_exclude: true` to files that shouldn't be in nav)
3. **Remove all manual entries** from `_config.yml` defaults section

## Benefits

- **Reduce `_config.yml` from 580 lines to ~200 lines** (66% reduction)
- **Easier maintenance** - new files automatically appear in nav
- **Standard pattern** - follows Just the Docs best practices
- **Less error-prone** - no need to remember to add config entries

## Files to Exclude from Navigation

These files should have `nav_exclude: true` in their front matter:

### Internal Documentation Files
- All `DOCUMENTATION_*` files
- All `PHASE_*` files
- All `*_SUMMARY.md` files
- `STYLE_GUIDE.md`
- `TERMINOLOGY_*.md`
- `NAVIGATION_*.md`
- `JEKYLL_IMPLEMENTATION_REVIEW.md`
- `COMPLETION_CERTIFICATE.md`
- `PROJECT_*.md`
- `QUICK_START_GUIDE.md`
- `FINAL_*.md`
- `README_IMPROVEMENTS.md`
- `README_DOCUMENTATION_IMPROVEMENTS.md`

### Internal Tracking Files
- Files in `advanced/` that are summaries (vc-api-*.md)
- Files in `api-reference/` that are internal (API_SCORE.md, TRUST_DSL_EXAMPLES.md)

### Other Files
- Files already in the `exclude:` list (not processed by Jekyll)

## Implementation Steps

1. ✅ Analyze current structure
2. ⏳ Change default to include files
3. ⏳ Add `nav_exclude: true` to files that should be excluded
4. ⏳ Remove all manual `nav_exclude: false` entries from `_config.yml`
5. ⏳ Verify navigation structure

## Risk Assessment

**Low Risk:**
- Just the Docs theme supports this pattern
- Navigation structure is already defined via `nav_order` in front matter
- Can easily revert if needed

**Testing Required:**
- Verify all main documentation pages appear in nav
- Verify excluded files don't appear
- Check navigation order is correct
